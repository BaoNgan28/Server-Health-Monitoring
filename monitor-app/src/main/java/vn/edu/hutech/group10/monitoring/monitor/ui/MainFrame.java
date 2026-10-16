package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.common.util.TimeUtil;
import vn.edu.hutech.group10.monitoring.monitor.core.MonitorApplication;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentSnapshot;
import vn.edu.hutech.group10.monitoring.monitor.service.MonitorEventListener;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * The monitoring client window.
 *
 * <p>Threading: every service callback arrives on a worker thread, so each one
 * is forwarded with {@link SwingUtilities#invokeLater}; nothing here touches a
 * component from a socket or timer thread. In the other direction, any action
 * that could block - connecting, pushing a configuration, loading history,
 * sending mail - is run by the panels in a {@code SwingWorker} rather than on
 * the event dispatch thread.
 *
 * <p>Live metric updates arrive up to once a second per agent. Repainting the
 * table on each one would waste time in a repaint rather than a redraw, so
 * updates are collected and flushed by a Swing timer.
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    /** How often collected updates are pushed into the table. */
    private static final int REPAINT_INTERVAL_MS = 500;

    private final MonitorApplication application;

    private final DashboardPanel dashboardPanel;
    private final ServerDetailPanel serverDetailPanel;
    private final HistoryPanel historyPanel;
    private final AlertPanel alertPanel;
    private final SettingsPanel settingsPanel;

    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel packetsLabel = new JLabel(" ");
    private final JLabel lastAlertLabel = new JLabel(" ");

    private final Timer statusTimer;

    public MainFrame(MonitorApplication application) {
        super("Group 10 - Server Health Monitoring System  [profile: "
                + application.getConfig().getProfileName() + "]");
        this.application = application;

        serverDetailPanel = new ServerDetailPanel(application);
        historyPanel = new HistoryPanel(application);
        alertPanel = new AlertPanel(application);
        settingsPanel = new SettingsPanel(application);
        dashboardPanel = new DashboardPanel(application, this::onAgentSelected);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dashboard", dashboardPanel);
        tabs.addTab("Server Detail", serverDetailPanel);
        tabs.addTab("History", historyPanel);
        tabs.addTab("Alerts", alertPanel);
        tabs.addTab("Settings", settingsPanel);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        serverDetailPanel.reloadAgents();
        historyPanel.reloadAgents();

        application.getEventBus().addListener(new UiEventListener());

        statusTimer = new Timer(REPAINT_INTERVAL_MS, event -> updateStatusBar());
        statusTimer.start();

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                shutdown();
            }
        });

        setMinimumSize(new Dimension(1000, 640));
        setSize(1200, 780);
        setLocationRelativeTo(null);
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(0xDDDDDD)));
        statusLabel.setFont(UiConstants.LABEL_FONT);
        packetsLabel.setFont(UiConstants.LABEL_FONT);
        lastAlertLabel.setFont(UiConstants.LABEL_FONT);
        bar.add(statusLabel);
        bar.add(packetsLabel);
        bar.add(lastAlertLabel);
        return bar;
    }

    private void updateStatusBar() {
        var receiver = application.getUdpReceiver();
        var telemetry = application.getTelemetryService();

        statusLabel.setText("UDP " + application.getConfig().getUdpPort()
                + " | agents: " + application.getRegistry().size()
                + " | email: " + (application.getEmailConfig() != null
                && application.getEmailConfig().isConfigured() ? "CONFIGURED" : "NOT CONFIGURED"));

        if (receiver != null && telemetry != null) {
            packetsLabel.setText("packets: " + receiver.getReceivedCount() + " received, "
                    + telemetry.getAcceptedCount() + " accepted, "
                    + telemetry.getRejectedCount() + " rejected");
        }
    }

    private void onAgentSelected(String agentId) {
        serverDetailPanel.selectAgent(agentId);
    }

    /** Closes the application after the window is closed. */
    private void shutdown() {
        statusTimer.stop();
        setVisible(false);
        // Releasing the sockets and the database can take a moment; keep it off
        // the event dispatch thread so the window disappears immediately.
        Thread closer = new Thread(() -> {
            application.close();
            System.exit(0);
        }, "monitor-shutdown");
        closer.setDaemon(false);
        closer.start();
    }

    /** Forwards service events onto the event dispatch thread. */
    private final class UiEventListener implements MonitorEventListener {

        @Override
        public void onAgentUpdated(AgentSnapshot snapshot) {
            SwingUtilities.invokeLater(() -> {
                dashboardPanel.applyUpdate(snapshot);
                serverDetailPanel.applyUpdate(snapshot);
            });
        }

        @Override
        public void onAgentListChanged() {
            SwingUtilities.invokeLater(() -> {
                dashboardPanel.reload();
                serverDetailPanel.reloadAgents();
                historyPanel.reloadAgents();
            });
        }

        @Override
        public void onAlert(AlertEvent event) {
            SwingUtilities.invokeLater(() -> {
                lastAlertLabel.setText("last alert: " + event.getAlertType()
                        + " on " + event.getAgentId()
                        + " at " + TimeUtil.formatTimeOnly(event.getOpenedAt()));
                alertPanel.reload();
            });
        }
    }
}
