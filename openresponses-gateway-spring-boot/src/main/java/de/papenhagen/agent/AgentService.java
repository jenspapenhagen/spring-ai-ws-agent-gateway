
package de.papenhagen.agent;

import de.papenhagen.gateway.application.ResponseLifecycleService;
import de.papenhagen.protocol.ServerEvent;
import reactor.core.publisher.Flux;

public class AgentService {

    private final ResponseLifecycleService responseLifecycleService;

    public AgentService(final ResponseLifecycleService lifecycleService) {
        this.responseLifecycleService = lifecycleService;
    }

    public Flux<ServerEvent> handle(final String sessionId, final String rawMessage) {
        return responseLifecycleService.handle(sessionId, rawMessage);
    }

    public void closeSession(final String sessionId) {
        responseLifecycleService.closeSession(sessionId);
    }
}
