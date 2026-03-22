
package de.papenhagen.agent;

import de.papenhagen.gateway.adapter.provider.LegacyModelProviderAdapter;
import de.papenhagen.gateway.adapter.session.InMemoryGatewaySessionRepository;
import de.papenhagen.gateway.application.ClientEventParser;
import de.papenhagen.gateway.application.ResponseLifecycleService;
import de.papenhagen.gateway.port.GatewaySessionRepository;
import de.papenhagen.gateway.port.ModelProviderPort;
import de.papenhagen.protocol.ServerEvent;
import de.papenhagen.provider.ModelProvider;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;

public class AgentService {

    private final ResponseLifecycleService responseLifecycleService;

    public AgentService(
        final ObjectMapper mappedObjectMapper,
        final ModelProvider modelProvider,
        final String configuredDefaultModel,
        final int configuredMaxHistoryItems,
        final int configuredMaxStoredResponses,
        final Duration configuredProviderTimeout
    ) {
        final ModelProviderPort providerPort = new LegacyModelProviderAdapter(modelProvider);
        final GatewaySessionRepository sessionRepository = new InMemoryGatewaySessionRepository();
        final ClientEventParser parser = new ClientEventParser(mappedObjectMapper, configuredDefaultModel);
        this.responseLifecycleService = new ResponseLifecycleService(
            parser,
            providerPort,
            sessionRepository,
            configuredDefaultModel,
            configuredMaxHistoryItems,
            configuredMaxStoredResponses,
            configuredProviderTimeout
        );
    }

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
