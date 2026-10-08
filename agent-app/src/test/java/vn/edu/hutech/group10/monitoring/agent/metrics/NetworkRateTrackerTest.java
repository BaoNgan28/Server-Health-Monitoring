package vn.edu.hutech.group10.monitoring.agent.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkRateTrackerTest {

    private static final long ONE_SECOND_NANOS = 1_000_000_000L;

    private NetworkRateTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new NetworkRateTracker();
    }

    @Test
    @DisplayName("The first sample only sets a baseline and reports zero")
    void firstSampleIsBaseline() {
        assertFalse(tracker.hasBaseline());
        assertEquals(0.0d, tracker.update(5_000_000L, 0L), 0.0001d,
                "the total since boot is not a rate");
        assertTrue(tracker.hasBaseline());
    }

    @Test
    @DisplayName("The rate is the byte delta divided by the elapsed time, not the total")
    void computesDeltaRate() {
        tracker.update(1_000_000L, 0L);

        assertEquals(500_000.0d, tracker.update(1_500_000L, ONE_SECOND_NANOS), 0.0001d);
    }

    @Test
    @DisplayName("A half-second interval doubles the per-second rate")
    void scalesWithElapsedTime() {
        tracker.update(0L, 0L);

        double rate = tracker.update(50_000L, ONE_SECOND_NANOS / 2L);

        assertEquals(100_000.0d, rate, 0.0001d);
    }

    @Test
    @DisplayName("A counter reset re-baselines instead of reporting a negative rate")
    void counterResetIsAbsorbed() {
        tracker.update(10_000_000L, 0L);
        tracker.update(10_500_000L, ONE_SECOND_NANOS);

        // The NIC was reset, or an interface left the sum: the total went backwards.
        assertEquals(0.0d, tracker.update(1_000L, 2 * ONE_SECOND_NANOS), 0.0001d);

        // The next interval measures from the new baseline.
        assertEquals(2_000.0d, tracker.update(3_000L, 3 * ONE_SECOND_NANOS), 0.0001d);
    }

    @Test
    @DisplayName("Two samples inside the same clock tick repeat the previous rate")
    void nonPositiveElapsedRepeatsLastRate() {
        tracker.update(0L, 0L);
        double first = tracker.update(1_000L, ONE_SECOND_NANOS);

        assertEquals(first, tracker.update(9_999L, ONE_SECOND_NANOS), 0.0001d,
                "zero elapsed time must not divide by zero");
        assertEquals(first, tracker.update(9_999L, ONE_SECOND_NANOS - 5L), 0.0001d,
                "a backwards clock must not produce a negative rate");
    }

    @Test
    @DisplayName("An unreadable counter keeps the last known rate")
    void negativeTotalKeepsLastRate() {
        tracker.update(0L, 0L);
        double rate = tracker.update(4_000L, ONE_SECOND_NANOS);

        assertEquals(rate, tracker.update(-1L, 2 * ONE_SECOND_NANOS), 0.0001d);
    }

    @Test
    @DisplayName("An idle link reports exactly zero, not a residual value")
    void idleLinkReportsZero() {
        tracker.update(7_000L, 0L);

        assertEquals(0.0d, tracker.update(7_000L, ONE_SECOND_NANOS), 0.0001d);
    }

    @Test
    @DisplayName("Reset drops the baseline so the next sample starts a new series")
    void resetDropsBaseline() {
        tracker.update(1_000L, 0L);
        tracker.update(2_000L, ONE_SECOND_NANOS);
        tracker.reset();

        assertFalse(tracker.hasBaseline());
        assertEquals(0.0d, tracker.getLastRate(), 0.0001d);
        assertEquals(0.0d, tracker.update(9_000L, 2 * ONE_SECOND_NANOS), 0.0001d);
    }
}
