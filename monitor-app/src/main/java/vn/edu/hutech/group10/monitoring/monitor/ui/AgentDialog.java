package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;
import vn.edu.hutech.group10.monitoring.monitor.service.AgentManagementService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Locale;

/**
 * Modal dialog for adding or editing an agent.
 *
 * <p>The shared secret uses a {@link JPasswordField} and is read as a character
 * array, so it is never turned into a string that could end up in a log or a
 * screenshot. When an existing agent is edited the field starts empty and the
 * stored secret is kept unless something new is typed.
 */
public class AgentDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JTextField agentIdField = new JTextField(18);
    private final JTextField nameField = new JTextField(18);
    private final JTextField hostField = new JTextField(18);
    private final JTextField portField = new JTextField(18);
    private final JPasswordField secretField = new JPasswordField(18);
    private final JTextField cpuField = new JTextField(18);
    private final JTextField memoryField = new JTextField(18);
    private final JTextField networkInField = new JTextField(18);
    private final JTextField networkOutField = new JTextField(18);

    private final AgentRecord original;
    private AgentRecord result;

    private AgentDialog(Window owner, String title, AgentRecord existing) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.original = existing;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        if (existing == null) {
            agentIdField.setText("");
            hostField.setText("127.0.0.1");
            portField.setText(String.valueOf(ProtocolConstants.DEFAULT_AGENT_TCP_PORT));
            cpuField.setText("85.0");
            memoryField.setText("85.0");
            networkInField.setText("50.0");
            networkOutField.setText("50.0");
        } else {
            agentIdField.setText(existing.getAgentId());
            agentIdField.setEditable(false);
            agentIdField.setToolTipText("The agent id cannot be changed once it is registered");
            nameField.setText(existing.getName());
            hostField.setText(existing.getHost());
            portField.setText(String.valueOf(existing.getTcpPort()));
            secretField.setToolTipText("Leave empty to keep the current shared secret");
            cpuField.setText(format(existing.getCpuThreshold()));
            memoryField.setText(format(existing.getMemoryThreshold()));
            networkInField.setText(format(existing.getNetworkInThresholdMbps()));
            networkOutField.setText(format(existing.getNetworkOutThresholdMbps()));
        }

        pack();
        setMinimumSize(new Dimension(420, getHeight()));
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog for a new agent; returns {@code null} when cancelled. */
    public static AgentRecord showAddDialog(Window owner) {
        AgentDialog dialog = new AgentDialog(owner, "Add Agent", null);
        dialog.setVisible(true);
        return dialog.result;
    }

    /** Shows the dialog for an existing agent; returns {@code null} when cancelled. */
    public static AgentRecord showEditDialog(Window owner, AgentRecord existing) {
        AgentDialog dialog = new AgentDialog(owner, "Edit Agent - " + existing.getAgentId(),
                existing.copy());
        dialog.setVisible(true);
        return dialog.result;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.anchor = GridBagConstraints.LINE_END;
        labelConstraints.insets = new Insets(4, 4, 4, 8);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 1.0d;
        fieldConstraints.insets = new Insets(4, 0, 4, 4);

        int row = 0;
        row = addRow(form, labelConstraints, fieldConstraints, row, "Agent ID:", agentIdField);
        row = addRow(form, labelConstraints, fieldConstraints, row, "Name:", nameField);
        row = addRow(form, labelConstraints, fieldConstraints, row, "Host / IP:", hostField);
        row = addRow(form, labelConstraints, fieldConstraints, row, "TCP Port:", portField);
        row = addRow(form, labelConstraints, fieldConstraints, row, "Shared Secret:", secretField);
        row = addRow(form, labelConstraints, fieldConstraints, row, "CPU Threshold (%):", cpuField);
        row = addRow(form, labelConstraints, fieldConstraints, row, "Memory Threshold (%):", memoryField);
        row = addRow(form, labelConstraints, fieldConstraints, row,
                "Network IN Threshold (Mbps):", networkInField);
        addRow(form, labelConstraints, fieldConstraints, row,
                "Network OUT Threshold (Mbps):", networkOutField);

        return form;
    }

    private int addRow(JPanel form, GridBagConstraints labelConstraints,
                       GridBagConstraints fieldConstraints, int row, String label, JComponent field) {
        labelConstraints.gridy = row;
        fieldConstraints.gridy = row;
        form.add(new JLabel(label), labelConstraints);
        form.add(field, fieldConstraints);
        return row + 1;
    }

    private JPanel buildButtons() {
        JButton save = new JButton("Save");
        save.addActionListener(event -> onSave());

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        Box box = Box.createHorizontalBox();
        box.add(save);
        box.add(Box.createHorizontalStrut(8));
        box.add(cancel);
        panel.add(box);

        getRootPane().setDefaultButton(save);
        return panel;
    }

    private void onSave() {
        try {
            AgentRecord record = original == null ? new AgentRecord() : original;
            record.setAgentId(agentIdField.getText().trim());
            record.setName(nameField.getText().trim());
            record.setHost(hostField.getText().trim());
            record.setTcpPort(parseInt(portField.getText(), "TCP Port"));

            char[] secret = secretField.getPassword();
            try {
                if (secret.length > 0) {
                    record.setSharedSecret(new String(secret).trim());
                } else if (original == null) {
                    throw new IllegalArgumentException("Shared Secret must not be empty");
                }
            } finally {
                // Clear the copy the field handed us as soon as it is no longer needed.
                java.util.Arrays.fill(secret, '\0');
            }

            record.setCpuThreshold(parseDouble(cpuField.getText(), "CPU Threshold"));
            record.setMemoryThreshold(parseDouble(memoryField.getText(), "Memory Threshold"));
            record.setNetworkInThresholdMbps(
                    parseDouble(networkInField.getText(), "Network IN Threshold"));
            record.setNetworkOutThresholdMbps(
                    parseDouble(networkOutField.getText(), "Network OUT Threshold"));

            AgentManagementService.validate(record);
            result = record;
            dispose();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Invalid input",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private static int parseInt(String raw, String name) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a whole number");
        }
    }

    private static double parseDouble(String raw, String name) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number, for example 85.0");
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
