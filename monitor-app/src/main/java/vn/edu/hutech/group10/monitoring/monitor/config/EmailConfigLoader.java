package vn.edu.hutech.group10.monitoring.monitor.config;

import vn.edu.hutech.group10.monitoring.common.util.AppPaths;
import vn.edu.hutech.group10.monitoring.common.util.ValidationUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Builds an {@link EmailConfig} from, in order of priority:
 * <ol>
 *   <li>the environment variables {@code SMTP_USERNAME},
 *       {@code SMTP_APP_PASSWORD} and {@code ALERT_RECIPIENT_EMAIL};</li>
 *   <li>the external file {@code config/email-secrets.properties}.</li>
 * </ol>
 *
 * <p>The file is read from the working directory rather than from inside the
 * jar, so credentials can be filled in and the client restarted with no rebuild.
 *
 * <p>When a credential is missing, still a placeholder, or obviously not an
 * e-mail address, the result is a disabled configuration carrying the reason.
 * That is a normal state, not a startup failure: no SMTP login is ever attempted
 * with a placeholder.
 */
public final class EmailConfigLoader {

    private static final Logger LOG = Logger.getLogger(EmailConfigLoader.class.getName());

    public static final String DEFAULT_FILE_NAME = "email-secrets.properties";

    public static final String ENV_USERNAME = "SMTP_USERNAME";
    public static final String ENV_APP_PASSWORD = "SMTP_APP_PASSWORD";
    public static final String ENV_RECIPIENT = "ALERT_RECIPIENT_EMAIL";

    public static final String KEY_USERNAME = "smtp.username";
    public static final String KEY_APP_PASSWORD = "smtp.appPassword";
    public static final String KEY_RECIPIENT = "smtp.recipient";
    public static final String KEY_HOST = "smtp.host";
    public static final String KEY_PORT = "smtp.port";
    public static final String KEY_STARTTLS = "smtp.starttls";
    public static final String KEY_AUTH = "smtp.auth";

    public static final String DEFAULT_HOST = "smtp.gmail.com";
    public static final int DEFAULT_PORT = 587;

    /** Text the template ships with; seeing it back means nothing was filled in. */
    public static final String PLACEHOLDER_PASSWORD = "PASTE_GMAIL_APP_PASSWORD_HERE";
    public static final String PLACEHOLDER_RECIPIENT = "PASTE_RECIPIENT_EMAIL_HERE";

    /** Message logged, once, when e-mail stays off. */
    public static final String DISABLED_LOG_MESSAGE =
            "Email notifications disabled: credentials not configured";

    private EmailConfigLoader() {
    }

    /** Loads from {@code config/email-secrets.properties} under the project root. */
    public static EmailConfig load() {
        return load(AppPaths.configFile(DEFAULT_FILE_NAME), System::getenv);
    }

    /** Loads from an explicit file, taking environment variables from {@code env}. */
    public static EmailConfig load(Path file, Function<String, String> env) {
        Properties properties = readFile(file);

        String host = text(properties, KEY_HOST, DEFAULT_HOST);
        int port = port(properties);
        boolean startTls = bool(properties, KEY_STARTTLS, true);
        boolean auth = bool(properties, KEY_AUTH, true);

        // Environment first, file second: production can inject secrets without
        // a file, while the demo only has to edit one file.
        String username = firstUsable(value(env, ENV_USERNAME), properties.getProperty(KEY_USERNAME));
        String password = firstUsable(value(env, ENV_APP_PASSWORD), properties.getProperty(KEY_APP_PASSWORD));
        String recipient = firstUsable(value(env, ENV_RECIPIENT), properties.getProperty(KEY_RECIPIENT));

        String problem = findProblem(username, password, recipient);
        if (problem != null) {
            LOG.info(DISABLED_LOG_MESSAGE + " (" + problem + ")");
            return EmailConfig.disabled(host, port, startTls, auth,
                    ValidationUtil.isBlank(username) ? null : username.trim(),
                    isUsable(recipient) ? recipient.trim() : null,
                    problem);
        }

        LOG.info("Email notifications enabled for recipient " + recipient.trim()
                + " through " + host + ":" + port);
        return EmailConfig.configured(host, port, startTls, auth,
                username.trim(), password.trim(), recipient.trim());
    }

    /** The reason e-mail must stay disabled, or {@code null} when everything is present. */
    private static String findProblem(String username, String password, String recipient) {
        if (!isUsable(username)) {
            return "smtp.username is empty";
        }
        if (!isUsable(password)) {
            return "smtp.appPassword is empty or still the placeholder";
        }
        if (!isUsable(recipient)) {
            return "smtp.recipient is empty or still the placeholder";
        }
        if (!ValidationUtil.looksLikeEmail(username)) {
            return "smtp.username is not a valid e-mail address";
        }
        if (!ValidationUtil.looksLikeEmail(recipient)) {
            return "smtp.recipient is not a valid e-mail address";
        }
        return null;
    }

    /** True when a value is present and is not one of the shipped placeholders. */
    public static boolean isUsable(String value) {
        if (ValidationUtil.isBlank(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase(PLACEHOLDER_PASSWORD)
                || trimmed.equalsIgnoreCase(PLACEHOLDER_RECIPIENT)) {
            return false;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return !upper.startsWith("PASTE_") && !upper.startsWith("YOUR_");
    }

    private static Properties readFile(Path file) {
        Properties properties = new Properties();
        if (file == null || !Files.isRegularFile(file)) {
            LOG.info("No e-mail secrets file at "
                    + (file == null ? "(unset)" : file.toAbsolutePath()));
            return properties;
        }
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            // Never include the file contents in the message.
            LOG.warning("Could not read " + file.toAbsolutePath() + ": " + e.getMessage());
        }
        return properties;
    }

    private static String value(Function<String, String> env, String name) {
        return env == null ? null : env.apply(name);
    }

    private static String firstUsable(String preferred, String fallback) {
        return isUsable(preferred) ? preferred : fallback;
    }

    private static String text(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return ValidationUtil.isBlank(value) ? fallback : value.trim();
    }

    private static int port(Properties properties) {
        String raw = properties.getProperty(KEY_PORT);
        if (ValidationUtil.isBlank(raw)) {
            return DEFAULT_PORT;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1 || value > 65535) {
                LOG.warning("Ignoring out-of-range " + KEY_PORT + "=" + value);
                return DEFAULT_PORT;
            }
            return value;
        } catch (NumberFormatException e) {
            LOG.warning("Ignoring non-numeric " + KEY_PORT + "=" + raw);
            return DEFAULT_PORT;
        }
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        if (ValidationUtil.isBlank(raw)) {
            return fallback;
        }
        return Boolean.parseBoolean(raw.trim());
    }
}
