package vn.edu.hutech.group10.monitoring.monitor.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loader is driven with a temporary file and a fake environment, so these
 * tests never read the real {@code config/email-secrets.properties} and never
 * need a real App Password.
 */
class EmailConfigLoaderTest {

    private static final Map<String, String> NO_ENV = new HashMap<>();

    private static Path writeSecrets(Path dir, String contents) throws IOException {
        Path file = dir.resolve("email-secrets.properties");
        Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String template(String password, String recipient) {
        return "smtp.username=bocon2663@gmail.com\n"
                + "smtp.appPassword=" + password + "\n"
                + "smtp.recipient=" + recipient + "\n"
                + "smtp.host=smtp.gmail.com\n"
                + "smtp.port=587\n"
                + "smtp.starttls=true\n"
                + "smtp.auth=true\n";
    }

    @Test
    @DisplayName("A filled-in file enables e-mail")
    void loadsCompleteConfiguration(@TempDir Path tempDir) throws IOException {
        Path file = writeSecrets(tempDir, template("abcdefghijklmnop", "ops@example.com"));

        EmailConfig config = EmailConfigLoader.load(file, NO_ENV::get);

        assertAll(
                () -> assertTrue(config.isConfigured()),
                () -> assertEquals("bocon2663@gmail.com", config.getUsername()),
                () -> assertEquals("ops@example.com", config.getRecipient()),
                () -> assertEquals("smtp.gmail.com", config.getHost()),
                () -> assertEquals(587, config.getPort()),
                () -> assertTrue(config.isStartTls()),
                () -> assertTrue(config.isAuth()),
                () -> assertEquals("Group 10 Server Health Monitoring", config.getSenderName()),
                () -> assertEquals("smtp.gmail.com:587 STARTTLS", config.describeServer()),
                () -> assertNull(config.getDisabledReason()));
    }

    @Test
    @DisplayName("The shipped placeholders leave e-mail disabled, not broken")
    void placeholdersDisableEmail(@TempDir Path tempDir) throws IOException {
        Path file = writeSecrets(tempDir, template(
                EmailConfigLoader.PLACEHOLDER_PASSWORD, EmailConfigLoader.PLACEHOLDER_RECIPIENT));

        EmailConfig config = EmailConfigLoader.load(file, NO_ENV::get);

        assertAll(
                () -> assertFalse(config.isConfigured(), "no SMTP login may be attempted"),
                () -> assertTrue(config.getDisabledReason().contains("appPassword"),
                        config.getDisabledReason()),
                () -> assertNull(config.getAppPassword()),
                () -> assertEquals("bocon2663@gmail.com", config.getUsername(),
                        "the sender is still shown in the settings screen"));
    }

    @Test
    @DisplayName("A placeholder recipient alone is enough to disable e-mail")
    void placeholderRecipientDisablesEmail(@TempDir Path tempDir) throws IOException {
        Path file = writeSecrets(tempDir,
                template("abcdefghijklmnop", EmailConfigLoader.PLACEHOLDER_RECIPIENT));

        EmailConfig config = EmailConfigLoader.load(file, NO_ENV::get);

        assertAll(
                () -> assertFalse(config.isConfigured()),
                () -> assertTrue(config.getDisabledReason().contains("recipient"),
                        config.getDisabledReason()));
    }

    @Test
    @DisplayName("A missing file disables e-mail without throwing")
    void missingFileDisablesEmail(@TempDir Path tempDir) {
        EmailConfig config = EmailConfigLoader.load(tempDir.resolve("absent.properties"), NO_ENV::get);

        assertAll(
                () -> assertFalse(config.isConfigured()),
                () -> assertEquals(EmailConfigLoader.DEFAULT_HOST, config.getHost()),
                () -> assertEquals(EmailConfigLoader.DEFAULT_PORT, config.getPort()));
    }

    @Test
    @DisplayName("Environment variables take priority over the file")
    void environmentWinsOverFile(@TempDir Path tempDir) throws IOException {
        Path file = writeSecrets(tempDir, template("file-password", "file@example.com"));

        Map<String, String> env = new HashMap<>();
        env.put(EmailConfigLoader.ENV_USERNAME, "env-sender@example.com");
        env.put(EmailConfigLoader.ENV_APP_PASSWORD, "env-password");
        env.put(EmailConfigLoader.ENV_RECIPIENT, "env-recipient@example.com");

        EmailConfig config = EmailConfigLoader.load(file, env::get);

        assertAll(
                () -> assertTrue(config.isConfigured()),
                () -> assertEquals("env-sender@example.com", config.getUsername()),
                () -> assertEquals("env-recipient@example.com", config.getRecipient()),
                () -> assertEquals("env-password", config.getAppPassword()));
    }

    @Test
    @DisplayName("Environment variables alone are enough, with no file at all")
    void environmentAloneIsEnough(@TempDir Path tempDir) {
        Map<String, String> env = new HashMap<>();
        env.put(EmailConfigLoader.ENV_USERNAME, "sender@example.com");
        env.put(EmailConfigLoader.ENV_APP_PASSWORD, "secret");
        env.put(EmailConfigLoader.ENV_RECIPIENT, "recipient@example.com");

        EmailConfig config = EmailConfigLoader.load(tempDir.resolve("absent.properties"), env::get);

        assertTrue(config.isConfigured());
    }

    @Test
    @DisplayName("An unset environment variable falls back to the file")
    void partialEnvironmentFallsBackToFile(@TempDir Path tempDir) throws IOException {
        Path file = writeSecrets(tempDir, template("file-password", "file@example.com"));

        Map<String, String> env = new HashMap<>();
        env.put(EmailConfigLoader.ENV_RECIPIENT, "env-recipient@example.com");

        EmailConfig config = EmailConfigLoader.load(file, env::get);

        assertAll(
                () -> assertTrue(config.isConfigured()),
                () -> assertEquals("file-password", config.getAppPassword()),
                () -> assertEquals("env-recipient@example.com", config.getRecipient()));
    }

    @Test
    @DisplayName("An address that is not an e-mail address is refused")
    void rejectsMalformedAddresses(@TempDir Path tempDir) throws IOException {
        Path file = writeSecrets(tempDir, template("password", "not-an-email"));

        EmailConfig config = EmailConfigLoader.load(file, NO_ENV::get);

        assertAll(
                () -> assertFalse(config.isConfigured()),
                () -> assertTrue(config.getDisabledReason().contains("valid e-mail"),
                        config.getDisabledReason()));
    }

    @Test
    @DisplayName("Anything still looking like a template value counts as unset")
    void detectsTemplateValues() {
        assertAll(
                () -> assertFalse(EmailConfigLoader.isUsable(null)),
                () -> assertFalse(EmailConfigLoader.isUsable("")),
                () -> assertFalse(EmailConfigLoader.isUsable("   ")),
                () -> assertFalse(EmailConfigLoader.isUsable(
                        EmailConfigLoader.PLACEHOLDER_PASSWORD)),
                () -> assertFalse(EmailConfigLoader.isUsable("PASTE_SOMETHING_HERE")),
                () -> assertFalse(EmailConfigLoader.isUsable("YOUR_16_CHARACTER_APP_PASSWORD")),
                () -> assertTrue(EmailConfigLoader.isUsable("a-real-value")));
    }

    @Test
    @DisplayName("An unreadable port falls back to 587 rather than failing")
    void badPortFallsBack(@TempDir Path tempDir) throws IOException {
        Path file = writeSecrets(tempDir,
                "smtp.username=a@b.com\nsmtp.appPassword=p\nsmtp.recipient=c@d.com\n"
                        + "smtp.port=not-a-number\n");

        EmailConfig config = EmailConfigLoader.load(file, NO_ENV::get);

        assertAll(
                () -> assertTrue(config.isConfigured()),
                () -> assertEquals(EmailConfigLoader.DEFAULT_PORT, config.getPort()));
    }

    @Test
    @DisplayName("The description never contains the password")
    void descriptionHidesThePassword(@TempDir Path tempDir) throws IOException {
        Path file = writeSecrets(tempDir, template("super-secret-password", "ops@example.com"));

        EmailConfig config = EmailConfigLoader.load(file, NO_ENV::get);

        assertAll(
                () -> assertFalse(config.toString().contains("super-secret-password")),
                () -> assertFalse(config.describeServer().contains("super-secret-password")));
    }
}
