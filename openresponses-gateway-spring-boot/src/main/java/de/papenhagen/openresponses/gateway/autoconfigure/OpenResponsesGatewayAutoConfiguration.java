package de.papenhagen.openresponses.gateway.autoconfigure;

import de.papenhagen.agent.AgentService;
import de.papenhagen.gateway.decorator.CircuitBreakerModelProviderAdapter;
import de.papenhagen.gateway.decorator.InMemoryGatewaySessionRepository;
import de.papenhagen.gateway.decorator.RedisGatewaySessionRepository;
import de.papenhagen.gateway.application.ClientEventParser;
import de.papenhagen.gateway.application.ResponseLifecycleService;
import de.papenhagen.gateway.port.GatewaySessionRepository;
import de.papenhagen.gateway.port.ModelProviderPort;
import de.papenhagen.provider.ModelProvider;
import de.papenhagen.provider.OpenAiProvider;
import de.papenhagen.tools.EchoTools;
import de.papenhagen.ws.OpenResponsesHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.reactive.socket.WebSocketHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Autoconfigures the Open Responses WebSocket gateway for Spring Boot applications.
 *
 * <p>Starter consumers get sensible defaults without manual wiring. Conditional bean creation
 * keeps the gateway overridable for custom providers, tools, and handlers.</p>
 */
@AutoConfiguration
@ConditionalOnClass({WebSocketHandler.class, ChatClient.class})
@ConditionalOnProperty(
    prefix = "openresponses.gateway",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(OpenResponsesGatewayProperties.class)
@Import(OpenResponsesGatewayWebSocketConfiguration.class)
@Validated
public class OpenResponsesGatewayAutoConfiguration {

    /**
     * Creates the default tool bindings.
     *
     * <p>EchoTools is a demo placeholder. Override this bean to wire real tool implementations.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public List<Object> gatewayTools(final EchoTools echoTools) {
        return List.of(echoTools);
    }

    @Bean
    @ConditionalOnMissingBean(ModelProvider.class)
    public ModelProvider modelProvider(final ChatClient.Builder builder, final List<Object> gatewayTools) {
        return new OpenAiProvider(builder, gatewayTools);
    }

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelProviderPort modelProviderPort(
        final ModelProvider provider,
        final CircuitBreakerRegistry registry
    ) {
        return new CircuitBreakerModelProviderAdapter(provider, registry);
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "openresponses.gateway",
        name = "session-store",
        havingValue = "redis"
    )
    @ConditionalOnMissingBean(GatewaySessionRepository.class)
    public GatewaySessionRepository redisGatewaySessionRepository(
        final StringRedisTemplate template,
        final ObjectMapper objectMapper,
        final OpenResponsesGatewayProperties properties
    ) {
        return new RedisGatewaySessionRepository(template, objectMapper, properties.getSessionTtl());
    }

    @Bean
    @ConditionalOnMissingBean(GatewaySessionRepository.class)
    public GatewaySessionRepository inMemoryGatewaySessionRepository() {
        return new InMemoryGatewaySessionRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientEventParser clientEventParser(
        final ObjectMapper objectMapper,
        final OpenResponsesGatewayProperties properties
    ) {
        return new ClientEventParser(objectMapper, properties.getDefaultModel());
    }

    @Bean
    @ConditionalOnMissingBean
    public ResponseLifecycleService responseLifecycleService(
        final ClientEventParser parser,
        final ModelProviderPort providerPort,
        final GatewaySessionRepository sessionRepository,
        final OpenResponsesGatewayProperties properties
    ) {
        return new ResponseLifecycleService(
            parser,
            providerPort,
            sessionRepository,
            properties.getDefaultModel(),
            properties.getMaxHistoryItems(),
            properties.getMaxStoredResponses(),
            properties.getProviderTimeout()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentService agentService(final ResponseLifecycleService lifecycleService) {
        return new AgentService(lifecycleService);
    }

    /**
     * Exposes the WebSocket protocol handler.
     *
     * <p>The handler is a replaceable integration point for custom framing/serialization policies.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenResponsesHandler openResponsesHandler(
        final AgentService agentService,
        final ObjectMapper objectMapper
    ) {
        return new OpenResponsesHandler(agentService, objectMapper);
    }
}
