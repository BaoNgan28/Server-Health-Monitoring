package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.util.AppPaths;
import vn.edu.hutech.group10.monitoring.monitor.config.EmailConfig;
import vn.edu.hutech.group10.monitoring.monitor.config.EmailConfigLoader;
import vn.edu.hutech.group10.monitoring.monitor.core.MonitorApplication;
import vn.edu.hutech.group10.monitoring.monitor.notification.NotificationResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Settings and notifications.
 *
 * <p>Shows whether the e-mail channel is configured, which server and recipient
 * it would use, and offers a real test send. The app password is never
 * displayed, and the panel never reads it: it asks the notifier to send, and the
 * notifier holds the credential.
 */
public class SettingsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(SettingsPanel.class.getName());

    private final MonitorApplication application;

    private final JLabel emailStatusValue = new JLabel();
    private final JLabel senderValue = new JLabel();
    private final JLabel recipientValue = new JLabel();
    private final JLabel serverValue = new JLabel();
    private final JLabel secretsFileValue = new JLabel();
    private final JLabel reasonValue = new JLabel();

    private final JButton testButton = new JButton("Send Test Email");
    private final JButton reloadButton = new JButton("Reload Email Configuration");

    public SettingsPanel(MonitorApplication application) {
        super(new BorderLayout(8, 8));
        this.application = application;

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(UiConstants.PANEL_BACKGROUND);

        add(buildNotificationCard(), BorderLayout.NORTH);
        add(buildRuntimeCard(), BorderLayout.CENTER);

        refresh();
    }

    private JPanel buildNotificationCard() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBackground(UiConstants.CARD_BACKGROUND);
        grid.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Notifications"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        GridBagConstraints labels = new GridBagConstraints();
        labels.gridx = 0;
        labels.anchor = GridBagConstraints.LINE_END;
        labels.insets = new Insets(3, 4, 3, 10);

        GridBagConstraints values = new GridBagConstraints();
        values.gridx = 1;
        values.anchor = GridBagConstraints.LINE_START;
        values.fill = GridBagConstraints.HORIZONTAL;
        values.weightx = 1.0d;
        values.insets = new Insets(3, 0, 3, 4);

        int row = 0;
        row = addRow(grid, labels, values, row, "Email Notification Status:", emailStatusValue);
        row = addRow(grid, labels, values, row, "Sender:", senderValue);
        row = addRow(grid, labels, values, row, "Recipient:", recipientValue);
        row = addRow(grid, labels, values, row, "SMTP:", serverValue);
        row = addRow(grid, labels, values, row, "Secrets File:", secretsFileValue);
        addRow(grid, labels, values, row, "Reason:", reasonValue);

        testButton.addActionListener(event -> onSendTestEmail());
        reloadButton.addActionListener(event -> onReload());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttons.setOpaque(false);
        buttons.add(testButton);
        buttons.add(reloadButton);

        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);
        card.add(grid, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildRuntimeCard() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBackground(UiConstants.CARD_BACKGROUND);
        grid.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Runtime"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        GridBagConstraints labels = new GridBagConstraints();
        labels.gridx = 0;
        labels.anchor = GridBagConstraints.LINE_END;
        labels.insets = new Insets(3, 4, 3, 10);

        GridBagConstraints values = new GridBagConstraints();
        values.gridx = 1;
        values.anchor = GridBagConstraints.LINE_START;
        values.fill = GridBagConstraints.HORIZONTAL;
        values.weightx = 1.0d;
        values.insets = new Insets(3, 0, 3, 4);

        var config = application.getConfig();
        int row = 0;
        row = addRow(grid, labels, values, row, "Profile:", value(config.getProfileName()));
        row = addRow(grid, labels, values, row, "UDP Listen Port:",
                value(String.valueOf(config.getUdpPort())));
        row = addRow(grid, labels, values, row, "Heartbeat Timeout:",
                value(config.getHeartbeatTimeoutMs() + " ms"));
        row = addRow(grid, labels, values, row, "Timestamp Tolerance:",
                value(config.getTimestampToleranceMs() + " ms"));
        row = addRow(grid, labels, values, row, "Raw Retention:",
                value(config.getRawRetentionMs() / 1000 + " s"));
        row = addRow(grid, labels, values, row, "Aggregation Bucket:",
                value(config.getAggregationBucketMs() / 1000 + " s"));
        row = addRow(grid, labels, values, row, "Aggregate Retention:",
                value(config.getAggregateRetentionMs() / 1000 + " s"));
        row = addRow(grid, labels, values, row, "Database:",
                value(config.getDatabasePath().toString()));
        addRow(grid, labels, values, row, "Log Directory:", value(AppPaths.logsDir().toString()));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(grid, BorderLayout.NORTH);
        return wrapper;
    }

    private JLabel value(String text) {
        JLabel label = new JLabel(text, SwingConstants.LEFT);
        label.setFont(UiConstants.MONO_FONT);
        return label;
    }

    private int addRow(JPanel grid, GridBagConstraints labels, GridBagConstraints values,
                       int row, String label, JLabel valueLabel) {
        labels.gridy = row;
        values.gridy = row;
        grid.add(new JLabel(label), labels);
        grid.add(valueLabel, values);
        return row + 1;
    }

    /** Redraws the notification card from the current configuration. */
    public void refresh() {
        EmailConfig config = application.getEmailConfig();
        Path secretsFile = AppPaths.configFile(EmailConfigLoader.DEFAULT_FILE_NAME);

        boolean configured = config != null && config.isConfigured();
        emailStatusValue.setText(configured ? "CONFIGURED" : "NOT CONFIGURED");
        emailStatusValue.setForeground(configured ? UiConstants.ONLINE : UiConstants.OFFLINE);

        senderValue.setText(config == null || config.getUsername() == null
                ? "-" : config.getUsername());
        recipientValue.setText(config == null || config.getRecipient() == null
                ? "-" : config.getRecipient());
        serverValue.setText(config == null ? "-" : config.describeServer());
        secretsFileValue.setText(secretsFile.toString());
        reasonValue.setText(configured ? "-" : String.valueOf(config == null
                ? "no configuration loaded" : config.getDisabledReason()));
        reasonValue.setForeground(configured ? Color.DARK_GRAY : UiConstants.WARNING);
    }

    private void onSendTestEmail() {
        EmailConfig config = application.getEmailConfig();
        if (config == null || !config.isConfigured()) {
            Path secretsFile = AppPaths.configFile(EmailConfigLoader.DEFAULT_FILE_NAME);
            JOptionPane.showMessageDialog(this,
                    "Email notification is not configured." + System.lineSeparator()
                            + System.lineSeparator()
                            + "Please edit:" + System.lineSeparator()
                            + secretsFile + System.lineSeparator()
                            + System.lineSeparator()
                            + "and set:" + System.lineSeparator()
                            + "  smtp.appPassword" + System.lineSeparator()
                            + "  smtp.recipient" + System.lineSeparator()
                            + System.lineSeparator()
                            + "Then press \"Reload Email Configuration\".",
                    "Email not configured", JOptionPane.WARNING_MESSAGE);
            return;
        }

        testButton.setEnabled(false);
        // SMTP can take seconds; never run it on the event dispatch thread.
        new SwingWorker<NotificationResult, Void>() {
            @Override
            protected NotificationResult doInBackground() {
                return application.getNotifier().sendTestMessage();
            }

            @Override
            protected void done() {
                try {
                    NotificationResult result = get();
                    JOptionPane.showMessageDialog(SettingsPanel.this, result.getMessage(),
                            "Send Test Email",
                            result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE
                                    : JOptionPane.ERROR_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    LOG.log(Level.WARNING, "Sending the test email failed", cause);
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                            "The test email could not be sent." + System.lineSeparator()
                                    + "Reason: " + cause.getMessage(),
                            "Send Test Email", JOptionPane.ERROR_MESSAGE);
                } finally {
                    testButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void onReload() {
        reloadButton.setEnabled(false);
        new SwingWorker<EmailConfig, Void>() {
            @Override
            protected EmailConfig doInBackground() {
                return application.reloadEmailConfiguration();
            }

            @Override
            protected void done() {
                try {
                    EmailConfig reloaded = get();
                    refresh();
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                            reloaded.isConfigured()
                                    ? "Email notification is now CONFIGURED."
                                    : "Email notification is still NOT CONFIGURED."
                                    + System.lineSeparator() + reloaded.getDisabledReason(),
                            "Reload Email Configuration",
                            reloaded.isConfigured() ? JOptionPane.INFORMATION_MESSAGE
                                    : JOptionPane.WARNING_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    LOG.log(Level.WARNING, "Reloading the email configuration failed", cause);
                } finally {
                    reloadButton.setEnabled(true);
                }
            }
        }.execute();
    }
}
