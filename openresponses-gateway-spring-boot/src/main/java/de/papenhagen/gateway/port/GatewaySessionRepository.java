package de.papenhagen.gateway.port;

import de.papenhagen.gateway.domain.GatewaySession;

/**
 * Port for session state storage.
 */
public interface GatewaySessionRepository {
    /**
     * Returns an existing session or creates a new one.
     *
     * @param sessionId transport session identifier
     * @return mutable session state
     */
    GatewaySession getOrCreate(String sessionId);

    /**
     * Removes a session from storage.
     *
     * @param sessionId transport session identifier
     * @return removed session or {@code null}
     */
    GatewaySession remove(String sessionId);
}
