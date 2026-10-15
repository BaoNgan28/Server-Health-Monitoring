package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.monitor.core.MonitorApplication;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentSnapshot;
import vn.edu.hutech.group10.monitoring.monitor.model.HistoryPoint;
import vn.edu.hutech.group10.monitoring.monitor.service.HistoryService;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Historical charts for one agent over a chosen range.
 *
 * <p>The query runs in a {@link SwingWorker}: thirty days of data is a large
 * read, and running it on the event dispatch thread would freeze the window.
 * Which table answers the question is decided by {@link HistoryService}; this
 * panel only asks for a range.
 */
public class HistoryPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(HistoryPanel.class.getName());

    private static final String SERIES_CPU = "CPU %";
    private static final String SERIES_MEMORY = "Memory %";
    private static final String SERIES_IN = "Network IN";
    private static final String SERIES_OUT = "Network OUT";

    /** Long ranges are down-sampled to this many points before plotting. */
    private static final int MAX_PLOTTED_POINTS = 2000;

    private final MonitorApplication application;

    private final JComboBox<String> agentSelector = new JComboBox<>();
    private final JComboBox<HistoryService.Range> rangeSelector =
            new JComboBox<>(HistoryService.Range.values());
    private final JButton refreshButton = new JButton("Load");
    private final JLabel sourceLabel = new JLabel(" ");

    private final TimeSeriesChartPanel usageChart;
    private final TimeSeriesChartPanel networkChart;

    public HistoryPanel(MonitorApplication application) {
        super(new BorderLayout(8, 8));
        this.application = application;

        usageChart = new TimeSeriesChartPanel("CPU and Memory", "%", MAX_PLOTTED_POINTS, true,
                SERIES_CPU, SERIES_MEMORY);
        networkChart = new TimeSeriesChartPanel("Network Throughput", "bytes/s", MAX_PLOTTED_POINTS,
                false, SERIES_IN, SERIES_OUT);

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(UiConstants.PANEL_BACKGROUND);

        add(buildControls(), BorderLayout.NORTH);
        add(buildCharts(), BorderLayout.CENTER);
    }

    private JPanel buildControls() {
        refreshButton.addActionListener(event -> loadHistory());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.add(new JLabel("Agent:"));
        row.add(agentSelector);
        row.add(new JLabel("Range:"));
        row.add(rangeSelector);
        row.add(refreshButton);
        row.add(sourceLabel);
        return row;
    }

    private JPanel buildCharts() {
        JPanel charts = new JPanel(new GridLayout(2, 1, 0, 8));
        charts.setOpaque(false);
        charts.add(usageChart);
        charts.add(networkChart);
        return charts;
    }

    /** Refreshes the agent list, keeping the current choice when it still exists. */
    public void reloadAgents() {
        String selected = (String) agentSelector.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (AgentSnapshot snapshot : application.getRegistry().snapshots()) {
            model.addElement(snapshot.getAgentId());
        }
        agentSelector.setModel(model);
        if (selected != null && model.getIndexOf(selected) >= 0) {
            agentSelector.setSelectedItem(selected);
        } else if (model.getSize() > 0) {
            agentSelector.setSelectedIndex(0);
        }
    }

    private void loadHistory() {
        String agentId = (String) agentSelector.getSelectedItem();
        if (agentId == null) {
            return;
        }
        HistoryService.Range range = (HistoryService.Range) rangeSelector.getSelectedItem();
        refreshButton.setEnabled(false);
        sourceLabel.setText("Loading ...");

        new SwingWorker<List<HistoryPoint>, Void>() {
            @Override
            protected List<HistoryPoint> doInBackground() {
                return application.getHistoryService().load(agentId, range);
            }

            @Override
            protected void done() {
                try {
                    plot(get());
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    LOG.log(Level.WARNING, "Loading history failed", cause);
                    sourceLabel.setText(" ");
                    JOptionPane.showMessageDialog(HistoryPanel.this,
                            "The history could not be loaded." + System.lineSeparator()
                                    + "Reason: " + cause.getMessage(),
                            "History", JOptionPane.ERROR_MESSAGE);
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void plot(List<HistoryPoint> points) {
        if (points.isEmpty()) {
            usageChart.clear();
            networkChart.clear();
            sourceLabel.setText("No data in this range yet.");
            return;
        }

        // Plotting every point of a long range would be slower than the eye can
        // tell apart, so keep an evenly spaced subset.
        int step = Math.max(1, points.size() / MAX_PLOTTED_POINTS);
        int count = (points.size() + step - 1) / step;

        long[] timestamps = new long[count];
        double[] cpu = new double[count];
        double[] memory = new double[count];
        double[] inbound = new double[count];
        double[] outbound = new double[count];

        int index = 0;
        for (int i = 0; i < points.size() && index < count; i += step, index++) {
            HistoryPoint point = points.get(i);
            timestamps[index] = point.getTimestamp();
            cpu[index] = point.getCpuUsage();
            memory[index] = point.getMemoryUsage();
            inbound[index] = point.getNetworkInBytesPerSecond();
            outbound[index] = point.getNetworkOutBytesPerSecond();
        }

        usageChart.setPoints(SERIES_CPU, timestamps, cpu);
        usageChart.setPoints(SERIES_MEMORY, timestamps, memory);
        networkChart.setPoints(SERIES_IN, timestamps, inbound);
        networkChart.setPoints(SERIES_OUT, timestamps, outbound);

        boolean aggregated = points.get(0).isAggregated();
        sourceLabel.setText(points.size() + " point(s) from "
                + (aggregated ? "aggregated buckets (metric_5m)" : "raw samples (metric_raw)"));
    }
}
