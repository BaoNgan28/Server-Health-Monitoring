package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
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
import javax.swing.SwingConstants;
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
    private JScrollPane tableScrollPane;

    public AlertPanel(MonitorApplication application) {
        super(new BorderLayout(8, 8));
        this.application = application;

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(UiConstants.getPanelBackground());

        add(buildControls(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);

        reload();
    }

    private JPanel buildControls() {
        JButton refresh = new JButton("🔄 Refresh");
        refresh.setFont(UiConstants.SUBTITLE_FONT);
        refresh.addActionListener(event -> reload());

        openOnly.setFont(UiConstants.LABEL_FONT);
        openOnly.setForeground(UiConstants.getTextPrimary());
        openOnly.addActionListener(event -> reload());

        summaryLabel.setFont(UiConstants.SUBTITLE_FONT);
        summaryLabel.setForeground(UiConstants.getAccentCyan());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        row.setOpaque(false);
        row.add(refresh);
        row.add(openOnly);
        row.add(summaryLabel);
        return row;
    }

    private JScrollPane buildTable() {
        table.setRowHeight(34);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        table.setFillsViewportHeight(true);
        table.setFont(UiConstants.LABEL_FONT);
        table.getTableHeader().setFont(UiConstants.SUBTITLE_FONT);

        // Zebra base renderer
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

        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(baseRenderer);
        }

        // Severity Badge Renderer (col 3)
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
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

                String sev = getText();
                Color fg = sev.contains("CRITICAL") ? UiConstants.colorOf(AgentStatus.OFFLINE) : (sev.contains("WARNING") ? UiConstants.colorOf(AgentStatus.WARNING) : UiConstants.getAccentCyan());
                Color bg = sev.contains("CRITICAL") ? UiConstants.bgColorOf(AgentStatus.OFFLINE) : (sev.contains("WARNING") ? UiConstants.bgColorOf(AgentStatus.WARNING) : (UiConstants.isDarkMode() ? new Color(0x0C, 0x2D, 0x48) : new Color(0xE0, 0xF2, 0xFE)));

                int padX = 8;
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
                    int textX = padX + (w - fm.stringWidth(sev)) / 2;
                    int textY = padY + (h - fm.getHeight()) / 2 + fm.getAscent();
                    g2.setFont(getFont());
                    g2.drawString(sev, textX, textY);
                }
                g2.dispose();
            }
        });

        // State Badge Renderer (col 4)
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
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

                String state = getText();
                boolean isOpen = state.startsWith("OPEN");
                Color fg = isOpen ? UiConstants.colorOf(AgentStatus.OFFLINE) : UiConstants.colorOf(AgentStatus.ONLINE);
                Color bg = isOpen ? UiConstants.bgColorOf(AgentStatus.OFFLINE) : UiConstants.bgColorOf(AgentStatus.ONLINE);

                int padX = 6;
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
                    String display = isOpen ? "● OPEN" : "✓ RESOLVED";
                    int textX = padX + (w - fm.stringWidth(display)) / 2;
                    int textY = padY + (h - fm.getHeight()) / 2 + fm.getAscent();
                    g2.setFont(getFont());
                    g2.drawString(display, textX, textY);
                }
                g2.dispose();
            }
        });

        table.getColumnModel().getColumn(5).setPreferredWidth(340);
        tableScrollPane = new JScrollPane(table);
        tableScrollPane.setBorder(BorderFactory.createLineBorder(UiConstants.getCardBorder(), 1));
        return tableScrollPane;
    }

    public void applyTheme(boolean isDark) {
        setBackground(UiConstants.getPanelBackground());
        openOnly.setForeground(UiConstants.getTextPrimary());
        summaryLabel.setForeground(UiConstants.getAccentCyan());
        if (tableScrollPane != null) {
            tableScrollPane.setBorder(BorderFactory.createLineBorder(UiConstants.getCardBorder(), 1));
        }
        table.repaint();
        revalidate();
        repaint();
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
