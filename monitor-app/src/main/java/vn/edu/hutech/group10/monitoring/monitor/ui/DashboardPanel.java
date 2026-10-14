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
import java.awt.Font;
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

    // UI styling constants (self-contained to avoid external dependency issues)
    private static final Color STATUS_ONLINE = new Color(0x1B8A3A);
    private static final Color STATUS_WARNING = new Color(0xC97A00);
    private static final Color STATUS_OFFLINE = new Color(0xC62828);
    private static final Color STATUS_UNKNOWN = new Color(0x666666);

    private static final Color PANEL_BACKGROUND = new Color(0xF5F6F8);
    private static final Color CARD_BACKGROUND = Color.WHITE;

    private static final Font VALUE_FONT = new Font("SansSerif", Font.BOLD, 22);
    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 12);

    private final MonitorApplication application;
    private final Consumer<String> agentSelectionListener;

    private final AgentTableModel tableModel = new AgentTableModel();
    private final JTable table = new JTable(tableModel);

    private final JLabel totalValue = new JLabel("0", SwingConstants.CENTER);
    private final JLabel onlineValue = new JLabel("0", SwingConstants.CENTER);
    private final JLabel warningValue = new JLabel("0", SwingConstants.CENTER);
    private final JLabel offlineValue = new JLabel("0", SwingConstants.CENTER);

    private final JButton connectButton = new JButton("Connect");
    private final JButton disconnectButton = new JButton("Disconnect");
    private final JButton configureButton = new JButton("Configure");

    public DashboardPanel(MonitorApplication application, Consumer<String> agentSelectionListener) {
        super(new BorderLayout(8, 8));
        this.application = application;
        this.agentSelectionListener = agentSelectionListener;

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(PANEL_BACKGROUND);

        add(buildSummary(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        reload();
    }

    private JPanel buildSummary() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 8, 0));
        panel.setOpaque(false);
        panel.add(card("TOTAL SERVERS", totalValue, STATUS_UNKNOWN));
        panel.add(card("ONLINE", onlineValue, STATUS_ONLINE));
        panel.add(card("WARNING", warningValue, STATUS_WARNING));
        panel.add(card("OFFLINE", offlineValue, STATUS_OFFLINE));
        return panel;
    }

    private JPanel card(String title, JLabel valueLabel, Color color) {
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(LABEL_FONT);
        titleLabel.setForeground(STATUS_UNKNOWN);

        valueLabel.setFont(VALUE_FONT);
        valueLabel.setForeground(color);

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BACKGROUND);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xDDDDDD)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.setPreferredSize(new Dimension(140, 76));
        return card;
    }

    private static Color colorOf(AgentStatus status) {
        if (status == null) {
            return STATUS_UNKNOWN;
        }
        switch (status) {
            case ONLINE:
                return STATUS_ONLINE;
            case WARNING:
                return STATUS_WARNING;
            case OFFLINE:
                return STATUS_OFFLINE;
            default:
                return STATUS_UNKNOWN;
        }
    }

    private JScrollPane buildTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        table.setFillsViewportHeight(true);

        // Colour the status column so the state is readable at a glance.
        table.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable owner, Object value,
                                                           boolean selected, boolean focused,
                                                           int row, int column) {
                Component component = super.getTableCellRendererComponent(owner, value, selected,
                        focused, row, column);
                AgentStatus status = parseStatus(value);
                component.setForeground(selected ? Color.WHITE : colorOf(status));
                return component;
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

        return new JScrollPane(table);
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
        JButton add = new JButton("Add Agent");
        add.addActionListener(event -> onAdd());

        JButton edit = new JButton("Edit Agent");
        edit.addActionListener(event -> onEdit());

        JButton delete = new JButton("Delete Agent");
        delete.addActionListener(event -> onDelete());

        connectButton.addActionListener(event -> onConnect());
        disconnectButton.addActionListener(event -> onDisconnect());
        configureButton.addActionListener(event -> onConfigure());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setOpaque(false);
        panel.add(add);
        panel.add(edit);
        panel.add(delete);
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
