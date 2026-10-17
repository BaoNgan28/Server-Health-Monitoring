package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.common.util.TimeUtil;
import vn.edu.hutech.group10.monitoring.monitor.core.MonitorApplication;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentSnapshot;
import vn.edu.hutech.group10.monitoring.monitor.service.MonitorEventListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import java.awt.GridLayout;
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

    private JPanel headerPanel;
    private JLabel logoLabel;
    private JLabel titleLabel;
    private JLabel subtitleLabel;
    private JLabel profileBadge;
    private JLabel liveBadge;
    private JButton themeToggleBtn;
    private JPanel statusBar;

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
        tabs.setFont(UiConstants.SUBTITLE_FONT);
        tabs.addTab("  Dashboard  ", dashboardPanel);
        tabs.addTab("  Server Detail  ", serverDetailPanel);
        tabs.addTab("  History  ", historyPanel);
        tabs.addTab("  Alerts  ", alertPanel);
        tabs.addTab("  Settings  ", settingsPanel);

        setLayout(new BorderLayout());
        add(buildHeaderBar(), BorderLayout.NORTH);
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

        setMinimumSize(new Dimension(1050, 680));
        setSize(1240, 800);
        setLocationRelativeTo(null);
    }

    private JPanel buildHeaderBar() {
        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UiConstants.getHeaderBackground());
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiConstants.getCardBorder()),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        logoLabel = new JLabel("⚡");
        logoLabel.setFont(new java.awt.Font("Segoe UI Emoji", java.awt.Font.BOLD, 22));
        logoLabel.setForeground(UiConstants.getAccentCyan());

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setOpaque(false);

        titleLabel = new JLabel("SERVER HEALTH MONITORING SYSTEM");
        titleLabel.setFont(UiConstants.HEADER_FONT);
        titleLabel.setForeground(UiConstants.getTextPrimary());

        subtitleLabel = new JLabel("Group 10  •  HUTECH Computer Network Programming");
        subtitleLabel.setFont(UiConstants.LABEL_FONT);
        subtitleLabel.setForeground(UiConstants.getTextMuted());

        titleBlock.add(titleLabel);
        titleBlock.add(subtitleLabel);

        left.add(logoLabel);
        left.add(titleBlock);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        right.setOpaque(false);

        profileBadge = new JLabel(" PROFILE: " + application.getConfig().getProfileName().toUpperCase() + " ");
        profileBadge.setFont(UiConstants.BADGE_FONT);
        profileBadge.setForeground(UiConstants.getAccentCyan());
        profileBadge.setOpaque(true);
        profileBadge.setBackground(UiConstants.isDarkMode() ? new java.awt.Color(0x0C, 0x2D, 0x48) : new java.awt.Color(0xE0, 0xF2, 0xFE));
        profileBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiConstants.getAccentCyan(), 1),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));

        liveBadge = new JLabel("● UDP 6000 LISTENING");
        liveBadge.setFont(UiConstants.BADGE_FONT);
        liveBadge.setForeground(UiConstants.colorOf(AgentStatus.ONLINE));
        liveBadge.setOpaque(true);
        liveBadge.setBackground(UiConstants.bgColorOf(AgentStatus.ONLINE));
        liveBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiConstants.colorOf(AgentStatus.ONLINE), 1),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));

        themeToggleBtn = new JButton(UiConstants.isDarkMode() ? "☀️ Light Mode" : "🌙 Dark Mode");
        themeToggleBtn.setFont(UiConstants.SUBTITLE_FONT);
        themeToggleBtn.setFocusPainted(false);
        themeToggleBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        themeToggleBtn.setToolTipText(UiConstants.isDarkMode() ? "Switch to Light Theme (Original UI)" : "Switch to Dark Theme (DevOps Dashboard)");
        themeToggleBtn.addActionListener(event -> toggleTheme());

        right.add(profileBadge);
        right.add(liveBadge);
        right.add(themeToggleBtn);

        headerPanel.add(left, BorderLayout.WEST);
        headerPanel.add(right, BorderLayout.EAST);
        return headerPanel;
    }

    private JPanel buildStatusBar() {
        statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        statusBar.setBackground(UiConstants.getHeaderBackground());
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiConstants.getCardBorder()));
        statusLabel.setFont(UiConstants.LABEL_FONT);
        statusLabel.setForeground(UiConstants.getTextMuted());
        packetsLabel.setFont(UiConstants.LABEL_FONT);
        packetsLabel.setForeground(UiConstants.getTextMuted());
        lastAlertLabel.setFont(UiConstants.LABEL_FONT);
        lastAlertLabel.setForeground(UiConstants.colorOf(AgentStatus.WARNING));
        statusBar.add(statusLabel);
        statusBar.add(packetsLabel);
        statusBar.add(lastAlertLabel);
        return statusBar;
    }

    private void toggleTheme() {
        boolean newMode = !UiConstants.isDarkMode();
        UiConstants.setDarkMode(newMode);
        try {
            if (newMode) {
                com.formdev.flatlaf.FlatDarkLaf.setup();
            } else {
                com.formdev.flatlaf.FlatLightLaf.setup();
            }
            com.formdev.flatlaf.FlatLaf.updateUI();
        } catch (Exception ignored) {
        }
        applyTheme(newMode);
    }

    public void applyTheme(boolean isDark) {
        if (headerPanel != null) {
            headerPanel.setBackground(UiConstants.getHeaderBackground());
            headerPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, UiConstants.getCardBorder()),
                    BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        }
        if (logoLabel != null) {
            logoLabel.setForeground(UiConstants.getAccentCyan());
        }
        if (titleLabel != null) {
            titleLabel.setForeground(UiConstants.getTextPrimary());
        }
        if (subtitleLabel != null) {
            subtitleLabel.setForeground(UiConstants.getTextMuted());
        }
        if (profileBadge != null) {
            profileBadge.setForeground(UiConstants.getAccentCyan());
            profileBadge.setBackground(isDark ? new java.awt.Color(0x0C, 0x2D, 0x48) : new java.awt.Color(0xE0, 0xF2, 0xFE));
            profileBadge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiConstants.getAccentCyan(), 1),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        }
        if (liveBadge != null) {
            liveBadge.setForeground(UiConstants.colorOf(AgentStatus.ONLINE));
            liveBadge.setBackground(UiConstants.bgColorOf(AgentStatus.ONLINE));
            liveBadge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiConstants.colorOf(AgentStatus.ONLINE), 1),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        }
        if (themeToggleBtn != null) {
            themeToggleBtn.setText(isDark ? "☀️ Light Mode" : "🌙 Dark Mode");
            themeToggleBtn.setToolTipText(isDark ? "Switch to Light Theme (Original UI)" : "Switch to Dark Theme (DevOps Dashboard)");
        }
        if (statusBar != null) {
            statusBar.setBackground(UiConstants.getHeaderBackground());
            statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiConstants.getCardBorder()));
        }
        statusLabel.setForeground(UiConstants.getTextMuted());
        packetsLabel.setForeground(UiConstants.getTextMuted());
        lastAlertLabel.setForeground(UiConstants.colorOf(AgentStatus.WARNING));

        dashboardPanel.applyTheme(isDark);
        serverDetailPanel.applyTheme(isDark);
        historyPanel.applyTheme(isDark);
        alertPanel.applyTheme(isDark);
        settingsPanel.applyTheme(isDark);

        SwingUtilities.updateComponentTreeUI(this);
    }

    private void updateStatusBar() {
        var receiver = application.getUdpReceiver();
        var telemetry = application.getTelemetryService();

        boolean emailOk = application.getEmailConfig() != null && application.getEmailConfig().isConfigured();
        statusLabel.setText("UDP: " + application.getConfig().getUdpPort()
                + "   |   Registered Agents: " + application.getRegistry().size()
                + "   |   Alert Email: " + (emailOk ? "READY (" + application.getEmailConfig().getRecipient() + ")" : "OFF"));

        if (receiver != null && telemetry != null) {
            packetsLabel.setText("Telemetry Packets: " + receiver.getReceivedCount() + " received  •  "
                    + telemetry.getAcceptedCount() + " accepted  •  "
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
