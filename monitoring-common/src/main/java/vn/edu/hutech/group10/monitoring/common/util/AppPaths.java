package vn.edu.hutech.group10.monitoring.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the {@code config}, {@code data} and {@code logs} folders relative to
 * the process working directory, so the release folder can be copied anywhere
 * without editing or rebuilding anything.
 *
 * <p>The base directory may be overridden with the {@code monitoring.home}
 * system property, which is what the batch scripts use when they are started
 * from somewhere other than the project root.
 */
public final class AppPaths {

    public static final String HOME_PROPERTY = "monitoring.home";

    private AppPaths() {
    }

    /** Project root: {@code -Dmonitoring.home} when set, otherwise the working directory. */
    public static Path home() {
        String override = System.getProperty(HOME_PROPERTY);
        if (!ValidationUtil.isBlank(override)) {
            return Paths.get(override.trim()).toAbsolutePath().normalize();
        }
        return Paths.get("").toAbsolutePath().normalize();
    }

    public static Path configDir() {
        return home().resolve("config");
    }

    public static Path dataDir() {
        return home().resolve("data");
    }

    public static Path logsDir() {
        return home().resolve("logs");
    }

    public static Path configFile(String fileName) {
        return configDir().resolve(fileName);
    }

    /** Creates {@code dir} when missing and returns it. */
    public static Path ensureDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }
}
