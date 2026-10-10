package vn.edu.hutech.group10.monitoring.monitor.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorConfigLoaderTest {

    @Test
    @DisplayName("Built-in defaults match the documented production behaviour")
    void appliesProductionDefaults() {
        MonitorConfig config = MonitorConfigLoader.fromProperties(new Properties());

        assertAll(
                () -> assertEquals(6000, config.getUdpPort()),
                () -> assertEquals(15_000L, config.getHeartbeatTimeoutMs()),
                () -> assertEquals(30_000L, config.getTimestampToleranceMs()),
                () -> assertEquals(60L * 60_000L, config.getRawRetentionMs(), "one hour of raw data"),
                () -> assertEquals(5L * 60_000L, config.getAggregationBucketMs(), "five-minute buckets"),
                () -> assertEquals(30L * 86_400_000L, config.getAggregateRetentionMs(),
                        "thirty days of aggregates"),
                () -> assertEquals(120, config.getChartWindowSamples()));
    }

    @Test
    @DisplayName("The production profile is read in minutes and days")
    void readsProductionUnits() {
        Properties properties = new Properties();
        properties.setProperty(MonitorConfigLoader.KEY_RAW_RETENTION_MINUTES, "60");
        properties.setProperty(MonitorConfigLoader.KEY_BUCKET_MINUTES, "5");
        properties.setProperty(MonitorConfigLoader.KEY_AGGREGATE_RETENTION_DAYS, "30");

        MonitorConfig config = MonitorConfigLoader.fromProperties(properties);

        assertAll(
                () -> assertEquals(3_600_000L, config.getRawRetentionMs()),
                () -> assertEquals(300_000L, config.getAggregationBucketMs()),
                () -> assertEquals(2_592_000_000L, config.getAggregateRetentionMs()));
    }

    @Test
    @DisplayName("The demo profile is read in seconds and overrides the coarse units")
    void secondsOverrideCoarseUnits() {
        Properties properties = new Properties();
        properties.setProperty(MonitorConfigLoader.KEY_RAW_RETENTION_MINUTES, "60");
        properties.setProperty(MonitorConfigLoader.KEY_RAW_RETENTION_SECONDS, "60");
        properties.setProperty(MonitorConfigLoader.KEY_BUCKET_SECONDS, "10");
        properties.setProperty(MonitorConfigLoader.KEY_AGGREGATE_RETENTION_SECONDS, "300");

        MonitorConfig config = MonitorConfigLoader.fromProperties(properties);

        assertAll(
                () -> assertEquals(60_000L, config.getRawRetentionMs(),
                        "the seconds form must win over the minutes form"),
                () -> assertEquals(10_000L, config.getAggregationBucketMs()),
                () -> assertEquals(300_000L, config.getAggregateRetentionMs()));
    }

    @Test
    @DisplayName("Out-of-range values are refused with a message naming the property")
    void rejectsOutOfRangeValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> {
                    Properties p = new Properties();
                    p.setProperty(MonitorConfigLoader.KEY_UDP_PORT, "70000");
                    MonitorConfigLoader.fromProperties(p);
                }),
                () -> assertThrows(IllegalArgumentException.class, () -> {
                    Properties p = new Properties();
                    p.setProperty(MonitorConfigLoader.KEY_CHART_WINDOW, "10000");
                    MonitorConfigLoader.fromProperties(p);
                }),
                () -> assertThrows(IllegalArgumentException.class, () -> {
                    Properties p = new Properties();
                    p.setProperty(MonitorConfigLoader.KEY_RAW_RETENTION_SECONDS, "0");
                    MonitorConfigLoader.fromProperties(p);
                }),
                () -> assertThrows(IllegalArgumentException.class, () -> {
                    Properties p = new Properties();
                    p.setProperty(MonitorConfigLoader.KEY_UDP_PORT, "not-a-port");
                    MonitorConfigLoader.fromProperties(p);
                }));
    }

    @Test
    @DisplayName("A relative database path hangs off the project root")
    void resolvesRelativeDatabasePath() {
        Properties properties = new Properties();
        properties.setProperty(MonitorConfigLoader.KEY_DATABASE_PATH, "data/monitoring.db");

        MonitorConfig config = MonitorConfigLoader.fromProperties(properties);

        assertAll(
                () -> assertTrue(config.getDatabasePath().isAbsolute(),
                        "the path must be resolved so the working directory cannot change it later"),
                () -> assertTrue(config.getDatabasePath().endsWith(Path.of("data", "monitoring.db"))));
    }

    @Test
    @DisplayName("A missing configuration file falls back to the defaults instead of failing")
    void missingFileUsesDefaults(@TempDir Path tempDir) {
        MonitorConfig config = MonitorConfigLoader.load(tempDir.resolve("absent.properties"));

        assertEquals(6000, config.getUdpPort());
    }

    @Test
    @DisplayName("A real file on disk is read")
    void readsFileFromDisk(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("monitor.properties");
        Files.write(file, ("profile.name=demo\n"
                + "monitor.udp.port=6123\n"
                + "raw.retention.seconds=60\n"
                + "aggregation.bucket.seconds=10\n").getBytes(StandardCharsets.UTF_8));

        MonitorConfig config = MonitorConfigLoader.load(file);

        assertAll(
                () -> assertEquals("demo", config.getProfileName()),
                () -> assertEquals(6123, config.getUdpPort()),
                () -> assertEquals(60_000L, config.getRawRetentionMs()),
                () -> assertEquals(10_000L, config.getAggregationBucketMs()));
    }
}
