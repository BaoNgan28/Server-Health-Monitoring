package vn.edu.hutech.group10.monitoring.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayProtectorTest {

    private static final String AGENT = "AGENT-01";
    private static final String SESSION = "session-a";

    private ReplayProtector protector;

    @BeforeEach
    void setUp() {
        protector = new ReplayProtector();
    }

    @Test
    @DisplayName("A rising sequence is accepted and a repeat is not")
    void acceptsForwardProgressAndRefusesRepeats() {
        assertAll(
                () -> assertTrue(protector.accept(AGENT, SESSION, 1L), "first packet"),
                () -> assertTrue(protector.accept(AGENT, SESSION, 2L), "next packet"),
                () -> assertTrue(protector.accept(AGENT, SESSION, 10L), "gaps are allowed"),
                () -> assertFalse(protector.accept(AGENT, SESSION, 10L), "exact replay"),
                () -> assertFalse(protector.accept(AGENT, SESSION, 2L), "already seen"),
                () -> assertTrue(protector.accept(AGENT, SESSION, 11L), "progress resumes"));
    }

    @Test
    @DisplayName("A packet that merely arrived late is accepted once, then refused")
    void acceptsLatePacketsInsideTheWindow() {
        // Packets 10 and 12 arrive; 11 was overtaken on the way and lands after them.
        assertTrue(protector.accept(AGENT, SESSION, 10L));
        assertTrue(protector.accept(AGENT, SESSION, 12L));

        assertTrue(protector.accept(AGENT, SESSION, 11L),
                "an unseen packet inside the window is genuine, not a replay");
        assertFalse(protector.accept(AGENT, SESSION, 11L), "but the second copy of it is");
        assertEquals(12L, protector.lastSequenceOf(AGENT, SESSION),
                "a late packet does not move the high-water mark");
    }

    @Test
    @DisplayName("A packet older than the window is refused")
    void refusesPacketsOlderThanTheWindow() {
        assertTrue(protector.accept(AGENT, SESSION, 1000L));

        assertAll(
                () -> assertTrue(protector.accept(AGENT, SESSION,
                        1000L - ReplayProtector.WINDOW_SIZE + 1), "just inside the window"),
                () -> assertFalse(protector.accept(AGENT, SESSION,
                        1000L - ReplayProtector.WINDOW_SIZE), "the edge is already too old"),
                () -> assertFalse(protector.accept(AGENT, SESSION, 1L), "far outside the window"));
    }

    @Test
    @DisplayName("A jump beyond the window clears it, so old sequences cannot return")
    void aLargeJumpClearsTheWindow() {
        assertTrue(protector.accept(AGENT, SESSION, 1L));
        assertTrue(protector.accept(AGENT, SESSION, 5L));
        assertTrue(protector.accept(AGENT, SESSION, 1000L));

        assertAll(
                () -> assertFalse(protector.accept(AGENT, SESSION, 5L), "sequence 5 is long gone"),
                () -> assertTrue(protector.accept(AGENT, SESSION, 999L), "999 is merely late"),
                () -> assertFalse(protector.accept(AGENT, SESSION, 999L), "but never twice"));
    }

    @Test
    @DisplayName("A restarted agent gets a fresh window and is not locked out")
    void aRestartedAgentIsAccepted() {
        // The agent has been running for a while.
        for (long sequence = 1; sequence <= 500; sequence++) {
            assertTrue(protector.accept(AGENT, "session-before-restart", sequence));
        }

        // It restarts a few seconds later and starts counting from 1 again.
        assertTrue(protector.accept(AGENT, "session-after-restart", 1L),
                "a restart must not look like a replay");
        assertTrue(protector.accept(AGENT, "session-after-restart", 2L));

        assertEquals(2, protector.sessionCountOf(AGENT), "both sessions are remembered");
    }

    @Test
    @DisplayName("A capture from the previous session is still refused after a restart")
    void oldSessionPacketsAreStillRefusedAfterARestart() {
        assertTrue(protector.accept(AGENT, "session-before-restart", 42L));
        assertTrue(protector.accept(AGENT, "session-after-restart", 1L));

        assertFalse(protector.accept(AGENT, "session-before-restart", 42L),
                "the old session keeps its own window, so its packets cannot be replayed");
    }

    @Test
    @DisplayName("A second sender cannot push the real agent's window out of range")
    void sessionsDoNotInterfereWithEachOther() {
        assertTrue(protector.accept(AGENT, "real-agent-session", 5L));

        // The simulator impersonates the agent with a huge sequence of its own.
        assertTrue(protector.accept(AGENT, "simulator-session", 1_790_668_331L));

        assertTrue(protector.accept(AGENT, "real-agent-session", 6L),
                "the real agent must keep working");
    }

    @Test
    @DisplayName("Only the most recent sessions are remembered")
    void oldSessionsAreEvicted() {
        for (int i = 0; i <= ReplayProtector.MAX_SESSIONS_PER_AGENT; i++) {
            assertTrue(protector.accept(AGENT, "session-" + i, 1L));
        }

        assertEquals(ReplayProtector.MAX_SESSIONS_PER_AGENT, protector.sessionCountOf(AGENT));
        assertEquals(-1L, protector.lastSequenceOf(AGENT, "session-0"),
                "the oldest session has been dropped");
    }

    @Test
    @DisplayName("Each agent has its own independent state")
    void agentsAreIndependent() {
        assertTrue(protector.accept("AGENT-01", SESSION, 100L));
        assertTrue(protector.accept("AGENT-02", SESSION, 1L),
                "AGENT-02 starts its own series under the same session name");
        assertFalse(protector.accept("AGENT-01", SESSION, 1L),
                "AGENT-02 must not unlock a replay against AGENT-01");
        assertEquals(2, protector.trackedAgentCount());
    }

    @Test
    @DisplayName("A packet with no session id is handled rather than rejected outright")
    void missingSessionIdFallsBackToOneWindow() {
        assertTrue(protector.accept(AGENT, null, 1L));
        assertTrue(protector.accept(AGENT, null, 2L));
        assertFalse(protector.accept(AGENT, null, 2L));
    }

    @Test
    @DisplayName("Unknown lookups report -1 and reset clears state")
    void bookkeeping() {
        assertEquals(-1L, protector.lastSequenceOf("NOBODY", SESSION));
        assertEquals(0, protector.sessionCountOf("NOBODY"));

        protector.accept(AGENT, SESSION, 9L);
        assertEquals(9L, protector.lastSequenceOf(AGENT, SESSION));

        protector.reset(AGENT);
        assertEquals(-1L, protector.lastSequenceOf(AGENT, SESSION));

        protector.accept(AGENT, SESSION, 3L);
        protector.resetAll();
        assertEquals(0, protector.trackedAgentCount());
    }

    @Test
    @DisplayName("A blank agent id or a negative sequence is never accepted")
    void rejectsBadInput() {
        assertAll(
                () -> assertFalse(protector.accept(null, SESSION, 1L)),
                () -> assertFalse(protector.accept("", SESSION, 1L)),
                () -> assertFalse(protector.accept(AGENT, SESSION, -1L)));
    }

    @Test
    @DisplayName("Concurrent receivers accept a given sequence exactly once")
    void concurrentAcceptIsAtomic() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (protector.accept(AGENT, SESSION, 42L)) {
                        accepted.incrementAndGet();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "workers did not finish");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, accepted.get(),
                "exactly one of the racing threads may accept the same sequence");
    }
}
