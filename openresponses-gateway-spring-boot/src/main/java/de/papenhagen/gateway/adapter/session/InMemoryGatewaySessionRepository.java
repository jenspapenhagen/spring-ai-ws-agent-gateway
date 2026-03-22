package de.papenhagen.gateway.adapter.session;

import de.papenhagen.gateway.domain.GatewaySession;
import de.papenhagen.gateway.port.GatewaySessionRepository;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session repository for single-node runtime and tests.
 */
public class InMemoryGatewaySessionRepository implements GatewaySessionRepository {
    private final ConcurrentHashMap<String, GatewaySession> sessions = new ConcurrentHashMap<>();

    /**
     * @param sessionId transport session identifier
     * @return existing or newly created session
     */
    @Override
    public GatewaySession getOrCreate(final String sessionId) {
        return sessions.computeIfAbsent(sessionId, key -> new GatewaySession());
    }

    /**
     * @param sessionId transport session identifier
     * @return removed session or {@code null}
     */
    @Override
    public GatewaySession remove(final String sessionId) {
        return sessions.remove(sessionId);
    }
}
