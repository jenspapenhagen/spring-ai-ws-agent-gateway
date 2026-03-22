package de.papenhagen.gateway.decorator;

import de.papenhagen.gateway.domain.GatewaySession;
import de.papenhagen.gateway.port.GatewaySessionRepository;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGatewaySessionRepository implements GatewaySessionRepository {
    private final ConcurrentHashMap<String, GatewaySession> sessions = new ConcurrentHashMap<>();

    @Override
    public GatewaySession getOrCreate(final String sessionId) {
        return sessions.computeIfAbsent(sessionId, key -> new GatewaySession());
    }

    @Override
    public GatewaySession remove(final String sessionId) {
        return sessions.remove(sessionId);
    }
}
