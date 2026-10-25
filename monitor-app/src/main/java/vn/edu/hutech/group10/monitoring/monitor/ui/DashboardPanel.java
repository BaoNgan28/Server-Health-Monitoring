package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.model.AgentInfoMessage;
import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
import vn.edu.hutech.group10.monitoring.common.model.ConfigAckMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigUpdateMessage;
import vn.edu.hutech.group10.monitoring.common.util.ByteRateFormatter;
import vn.edu.hutech.group10.monitoring.monitor.core.MonitorApplication;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The dashboard: a summary of how many agents are in each state, the live agent
 * grid, and the management buttons.
 *
 * <p>Every action that touches the network or the database runs in a
 * {@link SwingWorker}, so clicking Connect on an unreachable machine cannot
 * freeze the window for the length of a TCP timeout.
 */
public class DashboardPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(DashboardPanel.class.getName());

    private final MonitorApplication application;
    private final Consumer<String> agentSelectionListener;

    private final AgentTableModel tableModel = new AgentTableModel();
    private final JTable table = new JTable(tableModel);

    private final JLabel totalValue = new JLabel("0", SwingConstants.CENTER);
    private final JLabel onlineValue = new JLabel("0", SwingConstants.CENTER);
    private final JLabel warningValue = new JLabel("0", SwingConstants.CENTER);
    private final JLabel offlineValue = new JLabel("0", SwingConstants.CENTER);

    private JPanel cardTotal, cardOnline, cardWarning, cardOffline;
    private JLabel titleTotal, titleOnline, titleWarning, titleOffline;
    private JLabel subTotal, subOnline, subWarning, subOffline;
    private JScrollPane tableScrollPane;

    private final JButton connectButton = new JButton("Connect");
    private final JButton disconnectButton = new JButton("Disconnect");
    private final JButton configureButton = new JButton("Configure");
    private final JButton deleteButton = new JButton("✕ Delete Agent");

    public DashboardPanel(MonitorApplication application, Consumer<String> agentSelectionListener) {
        super(new BorderLayout(8, 8));
        this.application = application;
        this.agentSelectionListener = agentSelectionListener;

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(UiConstants.getPanelBackground());

        add(buildSummary(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        reload();
    }

    private JPanel buildSummary() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 12, 0));
        panel.setOpaque(false);

        titleTotal = new JLabel("🖥  TOTAL SERVERS");
        subTotal = new JLabel("Registered nodes");
        cardTotal = buildCardPanel(titleTotal, subTotal, totalValue, UiConstants.getAccentCyan());

        titleOnline = new JLabel("●  ONLINE");
        subOnline = new JLabel("Active & healthy");
        cardOnline = buildCardPanel(titleOnline, subOnline, onlineValue, UiConstants.colorOf(AgentStatus.ONLINE));

        titleWarning = new JLabel("▲  WARNING");
        subWarning = new JLabel("Threshold exceeded");
        cardWarning = buildCardPanel(titleWarning, subWarning, warningValue, UiConstants.colorOf(AgentStatus.WARNING));

        titleOffline = new JLabel("✖  OFFLINE");
        subOffline = new JLabel("Heartbeat missed");
        cardOffline = buildCardPanel(titleOffline, subOffline, offlineValue, UiConstants.colorOf(AgentStatus.OFFLINE));

        panel.add(cardTotal);
        panel.add(cardOnline);
        panel.add(cardWarning);
        panel.add(cardOffline);
        return panel;
    }

    private JPanel buildCardPanel(JLabel titleLabel, JLabel subLabel, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(UiConstants.getCardBackground());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, accentColor),
                UiConstants.createCardBorder()));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        titleLabel.setFont(UiConstants.SUBTITLE_FONT);
        titleLabel.setForeground(UiConstants.getTextMuted());

        JLabel dot = new JLabel("●");
        dot.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 10));
        dot.setForeground(accentColor);

        top.add(titleLabel, BorderLayout.WEST);
        top.add(dot, BorderLayout.EAST);

        valueLabel.setFont(UiConstants.VALUE_FONT);
        valueLabel.setForeground(accentColor);
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);

        subLabel.setFont(UiConstants.LABEL_FONT);
        subLabel.setForeground(UiConstants.getTextDim());

        card.add(top, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(subLabel, BorderLayout.SOUTH);
        card.setPreferredSize(new Dimension(170, 86));
        return card;
    }

    private JScrollPane buildTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(34);
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        table.setFillsViewportHeight(true);
        table.setFont(UiConstants.LABEL_FONT);
        table.getTableHeader().setFont(UiConstants.SUBTITLE_FONT);

        // Custom zebra stripe renderer for general columns
        DefaultTableCellRenderer baseRenderer = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable owner, Object value,
                                                           boolean selected, boolean focused,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(owner, value, selected, focused, row, column);
                if (!selected) {
                    c.setBackground(row % 2 == 0 ? UiConstants.getCardBackground() : UiConstants.getTableRowStripe());
                    c.setForeground(UiConstants.getTextPrimary());
                }
                return c;
            }
        };

        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(baseRenderer);
        }

        // Custom CPU and RAM usage renderer with severity color
        DefaultTableCellRenderer metricRenderer = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable owner, Object value,
                                                           boolean selected, boolean focused,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(owner, value, selected, focused, row, column);
                if (!selected) {
                    c.setBackground(row % 2 == 0 ? UiConstants.getCardBackground() : UiConstants.getTableRowStripe());
                    String valStr = value != null ? value.toString() : "";
                    if (valStr.endsWith("%")) {
                        try {
                            double pct = Double.parseDouble(valStr.replace("%", "").trim());
                            if (pct >= 85.0) {
                                c.setForeground(UiConstants.colorOf(AgentStatus.OFFLINE));
                            } else if (pct >= 70.0) {
                                c.setForeground(UiConstants.colorOf(AgentStatus.WARNING));
                            } else {
                                c.setForeground(UiConstants.colorOf(AgentStatus.ONLINE));
                            }
                        } catch (NumberFormatException ignored) {
                            c.setForeground(UiConstants.getTextPrimary());
                        }
                    } else {
                        c.setForeground(UiConstants.getTextMuted());
                    }
                }
                return c;
            }
        };
        table.getColumnModel().getColumn(2).setCellRenderer(metricRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(metricRenderer);

        // Custom Status Badge Capsule Renderer
        table.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable owner, Object value,
                                                           boolean selected, boolean focused,
                                                           int row, int column) {
                super.getTableCellRendererComponent(owner, value, selected, focused, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(UiConstants.BADGE_FONT);
                if (!selected) {
                    setBackground(row % 2 == 0 ? UiConstants.getCardBackground() : UiConstants.getTableRowStripe());
                }
                return this;
            }

            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());

                AgentStatus status = parseStatus(getText());
                Color fg = UiConstants.colorOf(status);
                Color bg = UiConstants.bgColorOf(status);

                int padX = 10;
                int padY = 5;
                int w = getWidth() - (padX * 2);
                int h = getHeight() - (padY * 2);
                if (w > 20 && h > 10) {
                    g2.setColor(bg);
                    g2.fillRoundRect(padX, padY, w, h, h, h);
                    g2.setColor(fg);
                    g2.setStroke(new java.awt.BasicStroke(1.2f));
                    g2.drawRoundRect(padX, padY, w, h, h, h);

                    java.awt.FontMetrics fm = g2.getFontMetrics(getFont());
                    String label = (status == AgentStatus.ONLINE ? "● " : (status == AgentStatus.WARNING ? "▲ " : "✖ ")) + status.name();
                    int textX = padX + (w - fm.stringWidth(label)) / 2;
                    int textY = padY + (h - fm.getHeight()) / 2 + fm.getAscent();
                    g2.setFont(getFont());
                    g2.drawString(label, textX, textY);
                }
                g2.dispose();
            }
        });

        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            AgentSnapshot selected = selectedSnapshot();
            updateButtonState(selected);
            if (selected != null && agentSelectionListener != null) {
                agentSelectionListener.accept(selected.getAgentId());
            }
        });

        tableScrollPane = new JScrollPane(table);
        tableScrollPane.setBorder(BorderFactory.createLineBorder(UiConstants.getCardBorder(), 1));
        return tableScrollPane;
    }

    public void applyTheme(boolean isDark) {
        setBackground(UiConstants.getPanelBackground());
        if (tableScrollPane != null) {
            tableScrollPane.setBorder(BorderFactory.createLineBorder(UiConstants.getCardBorder(), 1));
        }
        deleteButton.setForeground(UiConstants.colorOf(AgentStatus.OFFLINE));
        updateCardTheme(cardTotal, titleTotal, subTotal, totalValue, UiConstants.getAccentCyan());
        updateCardTheme(cardOnline, titleOnline, subOnline, onlineValue, UiConstants.colorOf(AgentStatus.ONLINE));
        updateCardTheme(cardWarning, titleWarning, subWarning, warningValue, UiConstants.colorOf(AgentStatus.WARNING));
        updateCardTheme(cardOffline, titleOffline, subOffline, offlineValue, UiConstants.colorOf(AgentStatus.OFFLINE));
        table.repaint();
        revalidate();
        repaint();
    }

    private void updateCardTheme(JPanel card, JLabel title, JLabel sub, JLabel val, Color accent) {
        if (card == null) return;
        card.setBackground(UiConstants.getCardBackground());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
                UiConstants.createCardBorder()));
        if (title != null) title.setForeground(UiConstants.getTextMuted());
        if (sub != null) sub.setForeground(UiConstants.getTextDim());
        if (val != null) val.setForeground(accent);
    }

    private static AgentStatus parseStatus(Object value) {
        if (value == null) {
            return AgentStatus.UNKNOWN;
        }
        try {
            return AgentStatus.valueOf(value.toString());
        } catch (IllegalArgumentException e) {
            return AgentStatus.UNKNOWN;
        }
    }

    private JPanel buildButtons() {
        JButton add = new JButton("+ Add Agent");
        add.setFont(UiConstants.SUBTITLE_FONT);
        add.addActionListener(event -> onAdd());

        JButton edit = new JButton("✎ Edit Agent");
        edit.setFont(UiConstants.SUBTITLE_FONT);
        edit.addActionListener(event -> onEdit());

        deleteButton.setFont(UiConstants.SUBTITLE_FONT);
        deleteButton.setForeground(UiConstants.colorOf(AgentStatus.OFFLINE));
        deleteButton.addActionListener(event -> onDelete());

        connectButton.setText("⚡ Connect");
        connectButton.setFont(UiConstants.SUBTITLE_FONT);
        connectButton.addActionListener(event -> onConnect());

        disconnectButton.setText("⏹ Disconnect");
        disconnectButton.setFont(UiConstants.SUBTITLE_FONT);
        disconnectButton.addActionListener(event -> onDisconnect());

        configureButton.setText("⚙ Configure");
        configureButton.setFont(UiConstants.SUBTITLE_FONT);
        configureButton.addActionListener(event -> onConfigure());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        panel.setOpaque(false);
        panel.add(add);
        panel.add(edit);
        panel.add(deleteButton);
        panel.add(connectButton);
        panel.add(disconnectButton);
        panel.add(configureButton);

        updateButtonState(null);
        return panel;
    }

    private void updateButtonState(AgentSnapshot selected) {
        boolean hasSelection = selected != null;
        boolean connected = hasSelection
                && application.getAgentManagementService().isConnected(selected.getAgentId());
        connectButton.setEnabled(hasSelection && !connected);
        disconnectButton.setEnabled(connected);
        configureButton.setEnabled(hasSelection);
    }

    /** Reloads every row and the summary from the registry. */
    public void reload() {
        List<AgentSnapshot> snapshots = application.getRegistry().snapshots();
        String previous = selectedAgentId();

        tableModel.setRows(snapshots);
        updateSummary();

        if (previous != null) {
            int index = tableModel.indexOfAgent(previous);
            if (index >= 0) {
                int viewIndex = table.convertRowIndexToView(index);
                table.getSelectionModel().setSelectionInterval(viewIndex, viewIndex);
            }
        }
        updateButtonState(selectedSnapshot());
    }

    /** Applies one agent's update without rebuilding the table. */
    public void applyUpdate(AgentSnapshot snapshot) {
        if (!tableModel.updateRow(snapshot)) {
            reload();
            return;
        }
        updateSummary();
    }

    private void updateSummary() {
        totalValue.setText(String.valueOf(application.getRegistry().size()));
        onlineValue.setText(String.valueOf(
                application.getRegistry().countByStatus(AgentStatus.ONLINE)));
        warningValue.setText(String.valueOf(
                application.getRegistry().countByStatus(AgentStatus.WARNING)));
        offlineValue.setText(String.valueOf(
                application.getRegistry().countByStatus(AgentStatus.OFFLINE)));
    }

    private AgentSnapshot selectedSnapshot() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return tableModel.getRow(table.convertRowIndexToModel(viewRow));
    }

    /** Agent id currently selected, or {@code null}. */
    public String selectedAgentId() {
        AgentSnapshot snapshot = selectedSnapshot();
        return snapshot == null ? null : snapshot.getAgentId();
    }

    private void onAdd() {
        AgentRecord record = AgentDialog.showAddDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this));
        if (record == null) {
            return;
        }
        try {
            application.getAgentManagementService().addAgent(record);
            reload();
        } catch (RuntimeException e) {
            showError("The agent could not be added.", e);
        }
    }

    private void onEdit() {
        AgentSnapshot selected = selectedSnapshot();
        if (selected == null) {
            return;
        }
        AgentRecord edited = AgentDialog.showEditDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), selected.getRecord());
        if (edited == null) {
            return;
        }
        try {
            application.getAgentManagementService().updateAgent(edited);
            reload();
        } catch (RuntimeException e) {
            showError("The agent could not be saved.", e);
        }
    }

    private void onDelete() {
        AgentSnapshot selected = selectedSnapshot();
        if (selected == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete agent " + selected.getAgentId() + "?" + System.lineSeparator()
                        + "Its metrics and alerts will be deleted as well.",
                "Delete Agent", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            application.getAgentManagementService().deleteAgent(selected.getAgentId());
            reload();
        } catch (RuntimeException e) {
            showError("The agent could not be deleted.", e);
        }
    }

    private void onConnect() {
        AgentSnapshot selected = selectedSnapshot();
        if (selected == null) {
            return;
        }
        AgentRecord record = selected.getRecord();
        connectButton.setEnabled(false);

        new SwingWorker<AgentInfoMessage, Void>() {
            @Override
            protected AgentInfoMessage doInBackground() throws Exception {
                return application.getAgentManagementService().connect(record);
            }

            @Override
            protected void done() {
                try {
                    AgentInfoMessage info = get();
                    JOptionPane.showMessageDialog(DashboardPanel.this,
                            describe(info), "Connected to " + record.getAgentId(),
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    showError("Could not connect to " + record.getAgentId()
                            + " at " + record.getHost() + ":" + record.getTcpPort() + ".", e);
                } finally {
                    updateButtonState(selectedSnapshot());
                }
            }
        }.execute();
    }

    private String describe(AgentInfoMessage info) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Agent ID   : ").append(info.getAgentId()).append(System.lineSeparator());
        sb.append("Name       : ").append(info.getAgentName()).append(System.lineSeparator());
        sb.append("Hostname   : ").append(info.getHostname()).append(System.lineSeparator());
        sb.append("OS         : ").append(info.getOsName()).append(' ')
                .append(info.getOsVersion()).append(System.lineSeparator());
        sb.append("CPU        : ").append(info.getCpuModel()).append(" (")
                .append(info.getCpuLogicalCores()).append(" logical cores)")
                .append(System.lineSeparator());
        sb.append("Memory     : ").append(ByteRateFormatter.formatBytes(info.getTotalMemoryBytes()))
                .append(System.lineSeparator());
        sb.append("Intervals  : metric ").append(info.getMetricIntervalMs())
                .append(" ms, heartbeat ").append(info.getHeartbeatIntervalMs()).append(" ms");
        return sb.toString();
    }

    private void onDisconnect() {
        AgentSnapshot selected = selectedSnapshot();
        if (selected == null) {
            return;
        }
        application.getAgentManagementService().disconnect(selected.getAgentId());
        updateButtonState(selectedSnapshot());
    }

    private void onConfigure() {
        AgentSnapshot selected = selectedSnapshot();
        if (selected == null) {
            return;
        }
        AgentRecord record = selected.getRecord();
        ConfigUpdateMessage update = ConfigureAgentDialog.show(
                javax.swing.SwingUtilities.getWindowAncestor(this), record);
        if (update == null) {
            return;
        }
        configureButton.setEnabled(false);

        new SwingWorker<ConfigAckMessage, Void>() {
            @Override
            protected ConfigAckMessage doInBackground() throws Exception {
                return application.getAgentManagementService().pushConfiguration(record, update);
            }

            @Override
            protected void done() {
                try {
                    ConfigAckMessage ack = get();
                    String detail = "Status : " + ack.getStatus() + System.lineSeparator()
                            + "Message: " + ack.getMessage() + System.lineSeparator()
                            + System.lineSeparator()
                            + "Effective on the agent:" + System.lineSeparator()
                            + "  metric interval    : " + ack.getEffectiveMetricIntervalMs() + " ms"
                            + System.lineSeparator()
                            + "  heartbeat interval : " + ack.getEffectiveHeartbeatIntervalMs() + " ms"
                            + System.lineSeparator()
                            + "  CPU threshold      : " + ack.getEffectiveCpuThreshold() + " %"
                            + System.lineSeparator()
                            + "  memory threshold   : " + ack.getEffectiveMemoryThreshold() + " %";
                    JOptionPane.showMessageDialog(DashboardPanel.this, detail,
                            "CONFIG_ACK from " + record.getAgentId(),
                            ack.isSuccess() ? JOptionPane.INFORMATION_MESSAGE
                                    : JOptionPane.ERROR_MESSAGE);
                    reload();
                } catch (Exception e) {
                    showError("The configuration could not be sent to " + record.getAgentId() + ".", e);
                } finally {
                    updateButtonState(selectedSnapshot());
                }
            }
        }.execute();
    }

    private void showError(String headline, Exception e) {
        Throwable cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null
                ? e.getCause() : e;
        LOG.log(Level.WARNING, headline, cause);
        JOptionPane.showMessageDialog(this,
                headline + System.lineSeparator() + "Reason: " + cause.getMessage(),
                "Operation failed", JOptionPane.ERROR_MESSAGE);
    }
}
