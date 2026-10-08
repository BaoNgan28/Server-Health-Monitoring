package vn.edu.hutech.group10.monitoring.agent.metrics;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.InternetProtocolStats;
import oshi.software.os.OperatingSystem;
import vn.edu.hutech.group10.monitoring.common.model.AgentInfoMessage;
import vn.edu.hutech.group10.monitoring.common.util.ValidationUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects the six reported metrics from the operating system through OSHI.
 *
 * <p>Every reading is wrapped, because a metric that is unavailable on one
 * Windows build or needs privileges the agent does not have must not stop the
 * agent: the sample is reported with a safe value and a warning is logged once
 * in a while rather than on every tick.
 *
 * <p>Network interfaces are enumerated once and refreshed periodically, since
 * enumeration is far more expensive than reading the counters of a known
 * interface. See {@link NetworkRateTracker} for how the counters become rates.
 */
public class OshiSystemMetricsCollector implements SystemMetricsCollector {

    private static final Logger LOG = Logger.getLogger(OshiSystemMetricsCollector.class.getName());

    /** How often the interface list is rebuilt, to notice a NIC appearing. */
    private static final long INTERFACE_REFRESH_MS = 60_000L;

    /** Warnings about a failing metric are logged at most this often. */
    private static final long WARN_INTERVAL_MS = 60_000L;

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem operatingSystem;
    private final CentralProcessor processor;
    private final GlobalMemory memory;

    /**
     * One rate tracker per interface and direction, keyed by interface name.
     *
     * <p>Rates are computed per interface and then added up, rather than by
     * tracking the sum of the counters. The difference matters: the set of
     * interfaces changes as adapters come up, go down or are added, and an
     * interface joining the sum would make the total jump by everything that
     * interface had transferred since boot, which would read as an enormous
     * burst of traffic that never happened. Per interface, a newcomer simply
     * starts its own baseline and reports zero until its second sample.
     */
    private final Map<String, NetworkRateTracker> inboundTrackers = new HashMap<>();
    private final Map<String, NetworkRateTracker> outboundTrackers = new HashMap<>();

    private long[] previousCpuTicks;
    private List<NetworkIF> interfaces = Collections.emptyList();
    private long interfacesRefreshedAt;
    private long lastWarnAt;

    public OshiSystemMetricsCollector() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.operatingSystem = systemInfo.getOperatingSystem();
        this.processor = hardware.getProcessor();
        this.memory = hardware.getMemory();
        this.previousCpuTicks = processor.getSystemCpuLoadTicks();
        refreshInterfaces(System.currentTimeMillis());
    }

    @Override
    public SystemMetrics collect() {
        long now = System.currentTimeMillis();
        return new SystemMetrics(now,
                readCpuUsage(),
                readMemoryUsage(),
                readConnectionCount(),
                readUptimeSeconds(),
                readNetworkRate(now, true),
                readNetworkRate(now, false));
    }

    /**
     * CPU utilisation over the interval since the previous call, which is what an
     * administrator expects to see, rather than an average since boot.
     */
    private double readCpuUsage() {
        try {
            long[] ticks = processor.getSystemCpuLoadTicks();
            double load = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100.0d;
            previousCpuTicks = ticks;
            return ValidationUtil.clampPercentage(load);
        } catch (RuntimeException e) {
            warnOccasionally("CPU usage unavailable", e);
            return 0.0d;
        }
    }

    private double readMemoryUsage() {
        try {
            long total = memory.getTotal();
            if (total <= 0L) {
                return 0.0d;
            }
            long used = total - memory.getAvailable();
            return ValidationUtil.clampPercentage(used * 100.0d / total);
        } catch (RuntimeException e) {
            warnOccasionally("Memory usage unavailable", e);
            return 0.0d;
        }
    }

    /**
     * Established TCP connections over IPv4 and IPv6. This counter is cheap and
     * available without elevation, unlike enumerating every socket.
     */
    private int readConnectionCount() {
        try {
            InternetProtocolStats stats = operatingSystem.getInternetProtocolStats();
            long established = stats.getTCPv4Stats().getConnectionsEstablished()
                    + stats.getTCPv6Stats().getConnectionsEstablished();
            if (established < 0L) {
                return 0;
            }
            return (int) Math.min(established, Integer.MAX_VALUE);
        } catch (RuntimeException e) {
            warnOccasionally("Connection count unavailable", e);
            return 0;
        }
    }

    private long readUptimeSeconds() {
        try {
            return Math.max(0L, operatingSystem.getSystemUptime());
        } catch (RuntimeException e) {
            warnOccasionally("System uptime unavailable", e);
            return 0L;
        }
    }

    /**
     * Sums the current throughput of every interface that is present, having
     * measured each one separately. Interfaces that have gone away are dropped,
     * so a vanished adapter stops contributing instead of leaving a stale rate.
     */
    private double readNetworkRate(long nowMillis, boolean inbound) {
        Map<String, NetworkRateTracker> trackers = inbound ? inboundTrackers : outboundTrackers;
        try {
            if (nowMillis - interfacesRefreshedAt >= INTERFACE_REFRESH_MS) {
                refreshInterfaces(nowMillis);
            }
            long sampleNanos = System.nanoTime();
            Set<String> present = new HashSet<>();
            double total = 0.0d;

            for (NetworkIF nif : interfaces) {
                try {
                    nif.updateAttributes();
                    String name = nif.getName();
                    present.add(name);
                    long bytes = inbound ? nif.getBytesRecv() : nif.getBytesSent();
                    total += trackers.computeIfAbsent(name, key -> new NetworkRateTracker())
                            .update(bytes, sampleNanos);
                } catch (RuntimeException perInterface) {
                    // An interface can disappear between enumeration and reading;
                    // skip it and let it fall out of the set below.
                    LOG.log(Level.FINE, "Skipping network interface " + nif.getName(), perInterface);
                }
            }

            trackers.keySet().retainAll(present);
            return total;
        } catch (RuntimeException e) {
            warnOccasionally("Network counters unavailable", e);
            return sumLastRates(trackers);
        }
    }

    private static double sumLastRates(Map<String, NetworkRateTracker> trackers) {
        double total = 0.0d;
        for (NetworkRateTracker tracker : trackers.values()) {
            total += tracker.getLastRate();
        }
        return total;
    }

    private void refreshInterfaces(long nowMillis) {
        try {
            // The no-argument form skips loopback and interfaces that are down,
            // so the reported rate reflects real traffic leaving the machine.
            interfaces = hardware.getNetworkIFs();
        } catch (RuntimeException e) {
            warnOccasionally("Network interface enumeration failed", e);
            interfaces = Collections.emptyList();
        }
        interfacesRefreshedAt = nowMillis;
    }

    @Override
    public void describe(AgentInfoMessage message) {
        try {
            message.setOsName(operatingSystem.getFamily());
            message.setOsVersion(operatingSystem.getVersionInfo().toString());
            message.setCpuModel(processor.getProcessorIdentifier().getName());
            message.setCpuLogicalCores(processor.getLogicalProcessorCount());
            message.setTotalMemoryBytes(memory.getTotal());
            message.setUptimeSeconds(readUptimeSeconds());
        } catch (RuntimeException e) {
            warnOccasionally("System description unavailable", e);
        }
    }

    private void warnOccasionally(String message, Throwable cause) {
        long now = System.currentTimeMillis();
        if (now - lastWarnAt >= WARN_INTERVAL_MS) {
            lastWarnAt = now;
            LOG.log(Level.WARNING, message + ": " + cause.getMessage(), cause);
        }
    }

    @Override
    public void close() {
        interfaces = Collections.emptyList();
        inboundTrackers.clear();
        outboundTrackers.clear();
    }
}
