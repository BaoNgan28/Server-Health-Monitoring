package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.util.ByteRateFormatter;
import vn.edu.hutech.group10.monitoring.common.util.TimeUtil;
import vn.edu.hutech.group10.monitoring.monitor.core.MonitorApplication;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentSnapshot;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;

/**
 * Live detail of one agent: its identity, the six metrics as numbers, and three
 * charts fed one point per accepted sample.
 *
 * <p>The charts keep a bounded window of points (see
 * {@link TimeSeriesChartPanel}), so a client left running overnight does not
 * accumulate memory one sample per second.
 */
public class ServerDetailPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String SERIES_CPU = "CPU %";
    private static final String SERIES_MEMORY = "Memory %";
    private static final String SERIES_IN = "Network IN";
    private static final String SERIES_OUT = "Network OUT";

    private final MonitorApplication application;

    private final JComboBox<String> agentSelector = new JComboBox<>();
    private final JLabel statusValue = new JLabel("-");
    private final JLabel nameValue = new JLabel("-");
    private final JLabel addressValue = new JLabel("-");
    private final JLabel lastSeenValue = new JLabel("-");
    private final JLabel cpuValue = new JLabel("-");
    private final JLabel memoryValue = new JLabel("-");
    private final JLabel connectionsValue = new JLabel("-");
    private final JLabel uptimeValue = new JLabel("-");
    private final JLabel networkInValue = new JLabel("-");
    private final JLabel networkOutValue = new JLabel("-");
    private final JLabel packetsValue = new JLabel("-");

    private final TimeSeriesChartPanel cpuChart;
    private final TimeSeriesChartPanel memoryChart;
    private final TimeSeriesChartPanel networkChart;

    /** Guards against the selector's own event reloading the panel recursively. */
    private boolean updatingSelector;

    public ServerDetailPanel(MonitorApplication application) {
        super(new BorderLayout(8, 8));
        this.application = application;

        int window = application.getConfig().getChartWindowSamples();
        cpuChart = new TimeSeriesChartPanel("CPU Usage", "%", window, true, SERIES_CPU);
        memoryChart = new TimeSeriesChartPanel("Memory Usage", "%", window, true, SERIES_MEMORY);
        networkChart = new TimeSeriesChartPanel("Network Throughput", "bytes/s", window, false,
                SERIES_IN, SERIES_OUT);

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(UiConstants.PANEL_BACKGROUND);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCharts(), BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel selectorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        selectorRow.setOpaque(false);
        selectorRow.add(new JLabel("Agent:"));
        agentSelector.addActionListener(event -> {
            if (!updatingSelector) {
                onAgentSelected();
            }
        });
        selectorRow.add(agentSelector);

        JPanel grid = new JPanel(new GridLayout(0, 4, 10, 4));
        grid.setBackground(UiConstants.CARD_BACKGROUND);
        grid.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xDDDDDD)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        addField(grid, "Name", nameValue);
        addField(grid, "Address", addressValue);
        addField(grid, "Status", statusValue);
        addField(grid, "Last Seen", lastSeenValue);
        addField(grid, "CPU", cpuValue);
        addField(grid, "Memory", memoryValue);
        addField(grid, "Connections", connectionsValue);
        addField(grid, "Uptime", uptimeValue);
        addField(grid, "Network IN", networkInValue);
        addField(grid, "Network OUT", networkOutValue);
        addField(grid, "Packets", packetsValue);

        JPanel header = new JPanel(new BorderLayout(4, 4));
        header.setOpaque(false);
        header.add(selectorRow, BorderLayout.NORTH);
        header.add(grid, BorderLayout.CENTER);
        return header;
    }

    private void addField(JPanel grid, String label, JLabel valueLabel) {
        JLabel title = new JLabel(label, SwingConstants.LEFT);
        title.setFont(UiConstants.LABEL_FONT);
        title.setForeground(UiConstants.UNKNOWN);

        JPanel cell = new JPanel(new BorderLayout());
        cell.setOpaque(false);
        cell.add(title, BorderLayout.NORTH);
        cell.add(valueLabel, BorderLayout.CENTER);
        grid.add(cell);
    }

    private JPanel buildCharts() {
        JPanel charts = new JPanel(new GridLayout(3, 1, 0, 8));
        charts.setOpaque(false);
        charts.add(cpuChart);
        charts.add(memoryChart);
        charts.add(networkChart);
        return charts;
    }

    /** Refreshes the agent list in the selector, keeping the current choice. */
    public void reloadAgents() {
        String selected = (String) agentSelector.getSelectedItem();
        List<AgentSnapshot> snapshots = application.getRegistry().snapshots();

        updatingSelector = true;
        try {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            for (AgentSnapshot snapshot : snapshots) {
                model.addElement(snapshot.getAgentId());
            }
            agentSelector.setModel(model);
            if (selected != null && model.getIndexOf(selected) >= 0) {
                agentSelector.setSelectedItem(selected);
            } else if (model.getSize() > 0) {
                agentSelector.setSelectedIndex(0);
            }
        } finally {
            updatingSelector = false;
        }
        onAgentSelected();
    }

    /** Selects one agent from outside, for example when a table row is clicked. */
    public void selectAgent(String agentId) {
        if (agentId == null || agentId.equals(agentSelector.getSelectedItem())) {
            return;
        }
        for (int i = 0; i < agentSelector.getItemCount(); i++) {
            if (agentId.equals(agentSelector.getItemAt(i))) {
                agentSelector.setSelectedIndex(i);
                return;
            }
        }
    }

    private void onAgentSelected() {
        String agentId = (String) agentSelector.getSelectedItem();
        // Points belong to the agent that produced them, so start clean.
        cpuChart.clear();
        memoryChart.clear();
        networkChart.clear();

        if (agentId == null) {
            clearFields();
            return;
        }
        application.getRegistry().snapshot(agentId).ifPresent(this::showSnapshot);
    }

    /** Applies a live update; ignored when it belongs to another agent. */
    public void applyUpdate(AgentSnapshot snapshot) {
        String selected = (String) agentSelector.getSelectedItem();
        if (selected == null || !selected.equals(snapshot.getAgentId())) {
            return;
        }
        showSnapshot(snapshot);

        if (snapshot.hasMetrics()) {
            long timestamp = snapshot.getLastMetricTimestamp();
            cpuChart.addPoint(SERIES_CPU, timestamp, snapshot.getCpuUsage());
            memoryChart.addPoint(SERIES_MEMORY, timestamp, snapshot.getMemoryUsage());
            networkChart.addPoint(SERIES_IN, timestamp, snapshot.getNetworkInBytesPerSecond());
            networkChart.addPoint(SERIES_OUT, timestamp, snapshot.getNetworkOutBytesPerSecond());
        }
    }

    private void showSnapshot(AgentSnapshot snapshot) {
        nameValue.setText(snapshot.getName());
        addressValue.setText(snapshot.getHost() + ":" + snapshot.getTcpPort());
        statusValue.setText(snapshot.getStatus().name());
        statusValue.setForeground(UiConstants.colorOf(snapshot.getStatus()));
        lastSeenValue.setText(TimeUtil.formatTimestamp(snapshot.getLastSeen()));

        if (snapshot.hasMetrics()) {
            cpuValue.setText(percent(snapshot.getCpuUsage()));
            memoryValue.setText(percent(snapshot.getMemoryUsage()));
            connectionsValue.setText(String.valueOf(snapshot.getConnectionCount()));
            uptimeValue.setText(TimeUtil.formatUptime(snapshot.getUptimeSeconds()));
            networkInValue.setText(ByteRateFormatter.formatRate(snapshot.getNetworkInBytesPerSecond()));
            networkOutValue.setText(ByteRateFormatter.formatRate(snapshot.getNetworkOutBytesPerSecond()));
        } else {
            cpuValue.setText("-");
            memoryValue.setText("-");
            connectionsValue.setText("-");
            uptimeValue.setText("-");
            networkInValue.setText("-");
            networkOutValue.setText("-");
        }
        packetsValue.setText(snapshot.getAcceptedPackets() + " accepted / "
                + snapshot.getRejectedPackets() + " rejected");
    }

    private void clearFields() {
        for (JLabel label : new JLabel[]{nameValue, addressValue, lastSeenValue, cpuValue,
                memoryValue, connectionsValue, uptimeValue, networkInValue, networkOutValue,
                packetsValue}) {
            label.setText("-");
        }
        statusValue.setText("-");
        statusValue.setForeground(UiConstants.UNKNOWN);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f %%", value);
    }
}
