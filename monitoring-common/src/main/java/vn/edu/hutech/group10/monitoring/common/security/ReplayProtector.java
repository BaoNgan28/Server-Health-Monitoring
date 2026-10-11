package vn.edu.hutech.group10.monitoring.common.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rejects a packet that has been seen before, which is what defeats a captured
 * datagram being sent again.
 *
 * <h2>A window, not "must be larger than the last one"</h2>
 * UDP does not promise order. A packet can overtake another on the network, and
 * a receiver that spreads work across threads can finish them out of order too.
 * Insisting that each sequence be larger than the previous one would therefore
 * throw away perfectly genuine packets that merely arrived late.
 *
 * <p>So each sender gets a sliding window, the shape IPsec uses: the highest
 * sequence accepted so far, plus a 64-bit map of which of the previous 64
 * sequences have already been seen. A packet is accepted when it is newer than
 * anything seen so far, or when it falls inside the window and its bit is still
 * clear. It is rejected when it repeats a sequence already recorded, or when it
 * is so old that it has fallen out of the window - which is what a replayed
 * capture looks like.
 *
 * <h2>Why the window is per session, not per agent</h2>
 * An agent that restarts begins counting from 1 again. Against a single
 * per-agent window that is indistinguishable from a replay, and the agent would
 * be locked out until the whole window aged away - a restarted machine would sit
 * there OFFLINE for no good reason.
 *
 * <p>Each agent run therefore carries a session id, generated when it starts and
 * covered by the signature. Windows are kept per {@code (agentId, sessionId)}, so
 * a restart simply opens a fresh window while the previous session keeps its own.
 * A capture from the old session replayed into the new one still lands in the old
 * session's window, where its bit is already set, so it is still rejected. The
 * last {@value #MAX_SESSIONS_PER_AGENT} sessions of an agent are remembered, and
 * the oldest is dropped after that.
 *
 * <p>This also keeps senders from interfering with each other: the test
 * simulator uses its own session, so a packet it sends with a large sequence
 * cannot push the real agent's window out of range.
 *
 * <p>Thread safe; the UDP receiver may call it from several worker threads.
 */
public class ReplayProtector {

    /** How far back a late packet may be and still be accepted. */
    public static final int WINDOW_SIZE = 64;

    /** How many recent sessions of one agent keep their own window. */
    public static final int MAX_SESSIONS_PER_AGENT = 4;

    /** Used when a packet carries no session id at all. */
    private static final String NO_SESSION = "";

    private static final class Window {
        long highest;
        /** Bit i is set when sequence {@code highest - i} has been seen. */
        long seen;

        Window(long highest, long seen) {
            this.highest = highest;
            this.seen = seen;
        }
    }

    /** The sessions of one agent, newest used last, oldest evicted first. */
    private static final class AgentSessions extends LinkedHashMap<String, Window> {

        private static final long serialVersionUID = 1L;

        AgentSessions() {
            super(8, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
            return size() > MAX_SESSIONS_PER_AGENT;
        }
    }

    private final Map<String, AgentSessions> agents = new ConcurrentHashMap<>();

    public ReplayProtector() {
    }

    /**
     * Records {@code sequence} for one run of one agent if it has not been seen.
     *
     * @param sessionId the sending agent's session; {@code null} is treated as a
     *                  single unnamed session, so an older sender still works
     * @return {@code true} when the packet is fresh, {@code false} when it
     *         repeats one already accepted or is older than the window
     */
    public boolean accept(String agentId, String sessionId, long sequence) {
        if (agentId == null || agentId.isEmpty() || sequence < 0) {
            return false;
        }
        String session = sessionId == null ? NO_SESSION : sessionId;

        // compute() runs atomically per agent, so the decision and the update
        // cannot interleave with another receiver thread for the same agent.
        boolean[] accepted = new boolean[1];
        agents.compute(agentId, (key, sessions) -> {
            AgentSessions target = sessions == null ? new AgentSessions() : sessions;
            Window window = target.get(session);

            if (window == null) {
                // A session nobody has seen before: a starting or restarting agent.
                target.put(session, new Window(sequence, 1L));
                accepted[0] = true;
                return target;
            }

            if (sequence > window.highest) {
                long shift = sequence - window.highest;
                window.seen = shift >= WINDOW_SIZE ? 1L : (window.seen << shift) | 1L;
                window.highest = sequence;
                accepted[0] = true;
                return target;
            }

            long age = window.highest - sequence;
            if (age >= WINDOW_SIZE) {
                // Too old to tell a late packet from a replayed capture.
                accepted[0] = false;
                return target;
            }
            long bit = 1L << age;
            if ((window.seen & bit) != 0L) {
                accepted[0] = false;
                return target;
            }
            // Genuinely late, not a repeat: fill in its slot.
            window.seen |= bit;
            accepted[0] = true;
            return target;
        });
        return accepted[0];
    }

    /**
     * Highest sequence accepted for one session, or {@code -1} when that session
     * has not been seen.
     */
    public long lastSequenceOf(String agentId, String sessionId) {
        if (agentId == null) {
            return -1L;
        }
        String session = sessionId == null ? NO_SESSION : sessionId;
        long[] result = {-1L};
        // Read under compute as well: the session map is access-ordered, so even
        // a lookup reorders it and must not run beside a writer.
        agents.computeIfPresent(agentId, (key, sessions) -> {
            Window window = sessions.get(session);
            if (window != null) {
                result[0] = window.highest;
            }
            return sessions;
        });
        return result[0];
    }

    /** How many sessions of {@code agentId} currently have a window. */
    public int sessionCountOf(String agentId) {
        if (agentId == null) {
            return 0;
        }
        int[] result = {0};
        agents.computeIfPresent(agentId, (key, sessions) -> {
            result[0] = sessions.size();
            return sessions;
        });
        return result[0];
    }

    /** Forgets one agent, for example after it has been deleted from the registry. */
    public void reset(String agentId) {
        agents.remove(agentId);
    }

    /** Forgets every agent. */
    public void resetAll() {
        agents.clear();
    }

    public int trackedAgentCount() {
        return agents.size();
    }
}
