package vn.edu.hutech.group10.monitoring.common.security;

/**
 * Supplies the shared secret registered for an agent.
 *
 * <p>The monitoring client backs this with the {@code agents} table; an unknown
 * agent must yield {@code null} so that its packets are dropped instead of being
 * validated against some default key.
 */
@FunctionalInterface
public interface SecretProvider {

    /**
     * @return the shared secret for {@code agentId}, or {@code null} when the
     *         agent is not registered
     */
    String secretFor(String agentId);
}
