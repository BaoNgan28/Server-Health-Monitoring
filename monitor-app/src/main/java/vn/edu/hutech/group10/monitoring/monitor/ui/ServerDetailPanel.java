package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
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

    private JLabel targetNodeLabel;
    private final List<JPanel> metricCards = new ArrayList<>();
    private final List<JLabel> cardTitles = new ArrayList<>();
    private final List<Color> cardAccents = new ArrayList<>();

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
        setBackground(UiConstants.getPanelBackground());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCharts(), BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel selectorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        selectorRow.setOpaque(false);
        targetNodeLabel = new JLabel("Target Node:");
        targetNodeLabel.setFont(UiConstants.SUBTITLE_FONT);
        targetNodeLabel.setForeground(UiConstants.getTextMuted());
        selectorRow.add(targetNodeLabel);

        agentSelector.setFont(UiConstants.SUBTITLE_FONT);
        agentSelector.setPreferredSize(new Dimension(180, 30));
        agentSelector.addActionListener(event -> {
            if (!updatingSelector) {
                onAgentSelected();
            }
        });
        selectorRow.add(agentSelector);

        // Status chip
        statusValue.setFont(UiConstants.BADGE_FONT);
        statusValue.setForeground(UiConstants.colorOf(AgentStatus.ONLINE));
        statusValue.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiConstants.getCardBorder(), 1),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        selectorRow.add(statusValue);

        JPanel grid = new JPanel(new GridLayout(2, 4, 10, 10));
        grid.setOpaque(false);

        grid.add(createMetricCard("CPU USAGE", cpuValue, UiConstants.getAccentCyan(), "⚡"));
        grid.add(createMetricCard("MEMORY USAGE", memoryValue, UiConstants.ACCENT_PURPLE, "💾"));
        grid.add(createMetricCard("NETWORK IN", networkInValue, UiConstants.colorOf(AgentStatus.ONLINE), "↓"));
        grid.add(createMetricCard("NETWORK OUT", networkOutValue, UiConstants.colorOf(AgentStatus.WARNING), "↑"));
        grid.add(createMetricCard("CONNECTIONS", connectionsValue, UiConstants.getTextPrimary(), "🔗"));
        grid.add(createMetricCard("SYSTEM UPTIME", uptimeValue, UiConstants.getTextPrimary(), "⏱"));
        grid.add(createMetricCard("NODE ADDRESS", addressValue, UiConstants.getTextPrimary(), "🌐"));
        grid.add(createMetricCard("PACKETS STATS", packetsValue, UiConstants.getTextMuted(), "📦"));

        JPanel header = new JPanel(new BorderLayout(6, 6));
        header.setOpaque(false);
        header.add(selectorRow, BorderLayout.NORTH);
        header.add(grid, BorderLayout.CENTER);
        return header;
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, Color accentColor, String icon) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(UiConstants.getCardBackground());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, accentColor),
                UiConstants.createCardBorder()));

        JLabel titleLabel = new JLabel(icon + "  " + title);
        titleLabel.setFont(UiConstants.BADGE_FONT);
        titleLabel.setForeground(UiConstants.getTextMuted());

        valueLabel.setFont(UiConstants.METRIC_FONT);
        valueLabel.setForeground(accentColor);
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        metricCards.add(card);
        cardTitles.add(titleLabel);
        cardAccents.add(accentColor);
        return card;
    }

    public void applyTheme(boolean isDark) {
        setBackground(UiConstants.getPanelBackground());
        if (targetNodeLabel != null) {
            targetNodeLabel.setForeground(UiConstants.getTextMuted());
        }
        statusValue.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiConstants.getCardBorder(), 1),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        for (int i = 0; i < metricCards.size(); i++) {
            JPanel card = metricCards.get(i);
            card.setBackground(UiConstants.getCardBackground());
            Color acc = cardAccents.get(i);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, acc),
                    UiConstants.createCardBorder()));
            if (i < cardTitles.size()) {
                cardTitles.get(i).setForeground(UiConstants.getTextMuted());
            }
        }
        connectionsValue.setForeground(UiConstants.getTextPrimary());
        uptimeValue.setForeground(UiConstants.getTextPrimary());
        addressValue.setForeground(UiConstants.getTextPrimary());
        packetsValue.setForeground(UiConstants.getTextMuted());

        cpuChart.applyTheme(isDark);
        memoryChart.applyTheme(isDark);
        networkChart.applyTheme(isDark);

        revalidate();
        repaint();
    }

    private JPanel buildCharts() {
        JPanel charts = new JPanel(new GridLayout(2, 1, 0, 8));
        charts.setOpaque(false);

        JPanel topRow = new JPanel(new GridLayout(1, 2, 8, 0));
        topRow.setOpaque(false);
        topRow.add(cpuChart);
        topRow.add(memoryChart);

        charts.add(topRow);
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
