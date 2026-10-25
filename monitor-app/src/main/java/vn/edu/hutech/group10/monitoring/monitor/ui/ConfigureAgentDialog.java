package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.model.ConfigUpdateMessage;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
 * Collects the settings to push to an agent over TCP as a CONFIG_UPDATE.
 *
 * <p>An empty field means "leave this alone", which is why the message carries
 * boxed values: the agent applies only what was actually filled in.
 */
public class ConfigureAgentDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JTextField metricIntervalField = new JTextField(14);
    private final JTextField heartbeatIntervalField = new JTextField(14);
    private final JTextField cpuField = new JTextField(14);
    private final JTextField memoryField = new JTextField(14);
    private final JTextField networkInField = new JTextField(14);
    private final JTextField networkOutField = new JTextField(14);

    private ConfigUpdateMessage result;

    private ConfigureAgentDialog(Window owner, AgentRecord agent) {
        super(owner, "Configure Agent - " + agent.getAgentId(), ModalityType.APPLICATION_MODAL);

        cpuField.setText(format(agent.getCpuThreshold()));
        memoryField.setText(format(agent.getMemoryThreshold()));
        networkInField.setText(format(agent.getNetworkInThresholdMbps()));
        networkOutField.setText(format(agent.getNetworkOutThresholdMbps()));

        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(430, getHeight()));
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns {@code null} when cancelled. */
    public static ConfigUpdateMessage show(Window owner, AgentRecord agent) {
        ConfigureAgentDialog dialog = new ConfigureAgentDialog(owner, agent);
        dialog.setVisible(true);
        return dialog.result;
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        panel.add(new JLabel("<html>Settings are sent to the agent over TCP."
                + "<br>Leave a field empty to keep the agent's current value.</html>"));
        return panel;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.anchor = GridBagConstraints.LINE_END;
        labelConstraints.insets = new Insets(4, 4, 4, 8);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 1.0d;
        fieldConstraints.insets = new Insets(4, 0, 4, 4);

        String[] labels = {
                "Metric Interval (ms):", "Heartbeat Interval (ms):", "CPU Threshold (%):",
                "Memory Threshold (%):", "Network IN Threshold (Mbps):",
                "Network OUT Threshold (Mbps):"
        };
        JTextField[] fields = {
                metricIntervalField, heartbeatIntervalField, cpuField, memoryField,
                networkInField, networkOutField
        };

        for (int row = 0; row < labels.length; row++) {
            labelConstraints.gridy = row;
            fieldConstraints.gridy = row;
            form.add(new JLabel(labels[row]), labelConstraints);
            form.add(fields[row], fieldConstraints);
        }
        return form;
    }

    private JPanel buildButtons() {
        JButton send = new JButton("Send CONFIG_UPDATE");
        send.addActionListener(event -> onSend());

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        Box box = Box.createHorizontalBox();
        box.add(send);
        box.add(Box.createHorizontalStrut(8));
        box.add(cancel);
        panel.add(box);

        getRootPane().setDefaultButton(send);
        return panel;
    }

    private void onSend() {
        try {
            ConfigUpdateMessage update = new ConfigUpdateMessage();
            update.setMetricIntervalMs(optionalLong(metricIntervalField.getText(), "Metric Interval"));
            update.setHeartbeatIntervalMs(
                    optionalLong(heartbeatIntervalField.getText(), "Heartbeat Interval"));
            update.setCpuThreshold(optionalDouble(cpuField.getText(), "CPU Threshold"));
            update.setMemoryThreshold(optionalDouble(memoryField.getText(), "Memory Threshold"));
            update.setNetworkInThresholdMbps(
                    optionalDouble(networkInField.getText(), "Network IN Threshold"));
            update.setNetworkOutThresholdMbps(
                    optionalDouble(networkOutField.getText(), "Network OUT Threshold"));

            result = update;
            dispose();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Invalid input",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private static Long optionalLong(String raw, String name) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a whole number of milliseconds");
        }
    }

    private static Double optionalDouble(String raw, String name) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number, for example 85.0");
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
