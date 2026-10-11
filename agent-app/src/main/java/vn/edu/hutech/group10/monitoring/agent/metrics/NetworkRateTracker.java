package vn.edu.hutech.group10.monitoring.agent.metrics;

/**
 * Turns the monotonically increasing byte counters the operating system exposes
 * into a throughput rate.
 *
 * <p>A counter is a total, not a rate; the rate is
 * {@code (current - previous) / elapsedSeconds}. The cases that would otherwise
 * produce nonsense are handled explicitly:
 * <ul>
 *   <li><b>first sample</b> - there is no previous total, so the rate is 0
 *       rather than "everything since boot arrived in one second";</li>
 *   <li><b>counter reset</b> - a NIC being reset, or an interface disappearing
 *       from the sum, makes the total go backwards; that sample is used to
 *       re-baseline and reports 0 instead of a negative rate;</li>
 *   <li><b>elapsed &lt;= 0</b> - two samples inside the same clock tick would
 *       divide by zero, so the previous rate is repeated;</li>
 *   <li><b>several NICs</b> - the caller sums every interface before calling in,
 *       so an interface appearing or vanishing is just a counter jump.</li>
 * </ul>
 *
 * <p>Elapsed time comes from {@link System#nanoTime()}, which is monotonic, so a
 * wall-clock adjustment cannot produce a spike.
 */
public class NetworkRateTracker {

    private long previousBytes = -1L;
    private long previousNanos;
    private double lastRate;

    /**
     * Feeds a new cumulative counter reading.
     *
     * @param totalBytes cumulative bytes reported by the operating system
     * @param nowNanos   reading from a monotonic clock, in nanoseconds
     * @return bytes per second since the previous reading
     */
    public double update(long totalBytes, long nowNanos) {
        if (totalBytes < 0) {
            // Nothing usable; keep whatever the last good rate was.
            return lastRate;
        }
        if (previousBytes < 0) {
            previousBytes = totalBytes;
            previousNanos = nowNanos;
            lastRate = 0.0d;
            return 0.0d;
        }

        long elapsedNanos = nowNanos - previousNanos;
        if (elapsedNanos <= 0L) {
            return lastRate;
        }

        long deltaBytes = totalBytes - previousBytes;
        previousBytes = totalBytes;
        previousNanos = nowNanos;

        if (deltaBytes < 0L) {
            // Counter reset or an interface left the sum: re-baseline silently.
            lastRate = 0.0d;
            return 0.0d;
        }

        double elapsedSeconds = elapsedNanos / 1_000_000_000.0d;
        lastRate = deltaBytes / elapsedSeconds;
        return lastRate;
    }

    /** Most recently computed rate, in bytes per second. */
    public double getLastRate() {
        return lastRate;
    }

    /** True once a baseline reading has been taken. */
    public boolean hasBaseline() {
        return previousBytes >= 0L;
    }

    /** Drops the baseline, so the next reading starts a new series at 0. */
    public void reset() {
        previousBytes = -1L;
        previousNanos = 0L;
        lastRate = 0.0d;
    }
}
