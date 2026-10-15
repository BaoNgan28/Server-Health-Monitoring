package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.common.model.AlertState;
import vn.edu.hutech.group10.monitoring.common.util.TimeUtil;
import vn.edu.hutech.group10.monitoring.monitor.core.MonitorApplication;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The alert history: what fired, when, and whether it is still open.
 *
 * <p>Open rows are shown in bold and in the severity colour so an incident that
 * is still running is obvious next to one that has already cleared.
 */
public class AlertPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(AlertPanel.class.getName());
    private static final int MAX_ROWS = 500;

    private final MonitorApplication application;
    private final AlertTableModel tableModel = new AlertTableModel();
    private final JTable table = new JTable(tableModel);
    private final JCheckBox openOnly = new JCheckBox("Open alerts only");
    private final JLabel summaryLabel = new JLabel(" ");

    public AlertPanel(MonitorApplication application) {
        super(new BorderLayout(8, 8));
        this.application = application;

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(UiConstants.PANEL_BACKGROUND);

        add(buildControls(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);

        reload();
    }

    private JPanel buildControls() {
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(event -> reload());

        openOnly.addActionListener(event -> reload());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.add(refresh);
        row.add(openOnly);
        row.add(summaryLabel);
        return row;
    }

    private JScrollPane buildTable() {
        table.setRowHeight(22);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        table.setFillsViewportHeight(true);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable owner, Object value,
                                                           boolean selected, boolean focused,
                                                           int row, int column) {
                Component component = super.getTableCellRendererComponent(owner, value, selected,
                        focused, row, column);
                AlertEvent event = tableModel.getRow(owner.convertRowIndexToModel(row));
                if (event == null) {
                    return component;
                }
                boolean open = event.getState() == AlertState.OPEN;
                component.setFont(component.getFont().deriveFont(open ? Font.BOLD : Font.PLAIN));
                if (!selected) {
                    component.setForeground(open ? severityColor(event) : Color.DARK_GRAY);
                }
                return component;
            }
        };
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        table.getColumnModel().getColumn(5).setPreferredWidth(320);

        return new JScrollPane(table);
    }

    private static Color severityColor(AlertEvent event) {
        switch (event.getSeverity()) {
            case CRITICAL:
                return UiConstants.OFFLINE;
            case WARNING:
                return UiConstants.WARNING;
            default:
                return UiConstants.ONLINE;
        }
    }

    /** Reloads the list from the database in the background. */
    public void reload() {
        boolean onlyOpen = openOnly.isSelected();

        new SwingWorker<List<AlertEvent>, Void>() {
            @Override
            protected List<AlertEvent> doInBackground() {
                return onlyOpen
                        ? application.getAlertRepository().findAllOpen()
                        : application.getAlertRepository().findRecent(MAX_ROWS);
            }

            @Override
            protected void done() {
                try {
                    List<AlertEvent> events = get();
                    tableModel.setRows(events);
                    summaryLabel.setText(events.size() + " alert(s) shown, "
                            + application.getAlertService().getOpenAlertCount() + " currently open");
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    LOG.log(Level.WARNING, "Loading alerts failed", cause);
                    JOptionPane.showMessageDialog(AlertPanel.this,
                            "The alerts could not be loaded." + System.lineSeparator()
                                    + "Reason: " + cause.getMessage(),
                            "Alerts", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Table model over alert events. */
    private static final class AlertTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private static final String[] COLUMNS = {
                "Time", "Agent", "Alert Type", "Severity", "State", "Message"
        };

        private final List<AlertEvent> rows = new ArrayList<>();

        void setRows(List<AlertEvent> events) {
            rows.clear();
            if (events != null) {
                rows.addAll(events);
            }
            fireTableDataChanged();
        }

        AlertEvent getRow(int index) {
            return index < 0 || index >= rows.size() ? null : rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            AlertEvent event = rows.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return TimeUtil.formatTimestamp(event.getOpenedAt());
                case 1:
                    return event.getAgentId();
                case 2:
                    return event.getAlertType().name();
                case 3:
                    return event.getSeverity().name();
                case 4:
                    return event.getState() == AlertState.OPEN ? "OPEN"
                            : "RESOLVED " + TimeUtil.formatTimeOnly(event.getResolvedAt());
                case 5:
                    return event.getMessage();
                default:
                    return "";
            }
        }
    }
}
