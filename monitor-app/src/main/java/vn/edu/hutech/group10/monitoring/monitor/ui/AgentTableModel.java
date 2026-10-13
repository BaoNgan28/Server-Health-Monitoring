package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
import vn.edu.hutech.group10.monitoring.common.util.ByteRateFormatter;
import vn.edu.hutech.group10.monitoring.common.util.TimeUtil;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentSnapshot;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Table model behind the dashboard grid.
 *
 * <p>Holds immutable {@link AgentSnapshot}s, so a repaint always sees a
 * consistent row. The shared secret is not among the columns and is never
 * rendered anywhere in the table.
 */
public class AgentTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {
            "Name", "IP", "CPU", "RAM", "Connections", "Network IN", "Network OUT",
            "Status", "Last Seen"
    };

    private final List<AgentSnapshot> rows = new ArrayList<>();

    /** Replaces every row; called on the event dispatch thread. */
    public void setRows(List<AgentSnapshot> snapshots) {
        rows.clear();
        if (snapshots != null) {
            rows.addAll(snapshots);
        }
        fireTableDataChanged();
    }

    /**
     * Updates the row of one agent in place, so a live metric update repaints one
     * line instead of rebuilding the whole table and losing the selection.
     *
     * @return true when the agent was found
     */
    public boolean updateRow(AgentSnapshot snapshot) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getAgentId().equals(snapshot.getAgentId())) {
                rows.set(i, snapshot);
                fireTableRowsUpdated(i, i);
                return true;
            }
        }
        return false;
    }

    public AgentSnapshot getRow(int index) {
        if (index < 0 || index >= rows.size()) {
            return null;
        }
        return rows.get(index);
    }

    public int indexOfAgent(String agentId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getAgentId().equals(agentId)) {
                return i;
            }
        }
        return -1;
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
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AgentSnapshot row = rows.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return row.getName();
            case 1:
                return row.getHost() + ":" + row.getTcpPort();
            case 2:
                return row.hasMetrics() ? percent(row.getCpuUsage()) : "-";
            case 3:
                return row.hasMetrics() ? percent(row.getMemoryUsage()) : "-";
            case 4:
                return row.hasMetrics() ? String.valueOf(row.getConnectionCount()) : "-";
            case 5:
                return row.hasMetrics()
                        ? ByteRateFormatter.formatRate(row.getNetworkInBytesPerSecond()) : "-";
            case 6:
                return row.hasMetrics()
                        ? ByteRateFormatter.formatRate(row.getNetworkOutBytesPerSecond()) : "-";
            case 7:
                return status(row.getStatus());
            case 8:
                return TimeUtil.formatTimestamp(row.getLastSeen());
            default:
                return "";
        }
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f %%", value);
    }

    private static String status(AgentStatus status) {
        return status == null ? AgentStatus.UNKNOWN.name() : status.name();
    }
}
