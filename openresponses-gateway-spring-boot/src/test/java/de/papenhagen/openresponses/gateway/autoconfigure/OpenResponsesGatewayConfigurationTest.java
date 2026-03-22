package de.papenhagen.openresponses.gateway.autoconfigure;

import de.papenhagen.agent.AgentService;
import de.papenhagen.gateway.application.ClientEventParser;
import de.papenhagen.gateway.application.ResponseLifecycleService;
import de.papenhagen.gateway.port.GatewaySessionRepository;
import de.papenhagen.gateway.port.ModelProviderPort;
import de.papenhagen.provider.ModelProvider;
import de.papenhagen.provider.OpenAiProvider;
import de.papenhagen.tools.EchoTools;
import de.papenhagen.ws.OpenResponsesHandler;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenResponsesGatewayConfigurationTest {

    @Test
    void givenProperties_whenUsingDefaults_thenExposeExpectedValues() {
        OpenResponsesGatewayProperties properties = new OpenResponsesGatewayProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getPath()).isEqualTo("/ws/responses");
        assertThat(properties.getDefaultModel()).isEqualTo("gpt-4.1-mini");
        assertThat(properties.getMaxHistoryItems()).isEqualTo(100);
        assertThat(properties.getMaxStoredResponses()).isEqualTo(100);
        assertThat(properties.getProviderTimeout().toSeconds()).isEqualTo(90);
    }

    @Test
    void givenProperties_whenSettersCalled_thenExposeConfiguredValues() {
        OpenResponsesGatewayProperties properties = new OpenResponsesGatewayProperties();

        properties.setEnabled(false);
        properties.setPath("/ws/custom");
        properties.setDefaultModel("gpt-4o");
        properties.setMaxHistoryItems(7);
        properties.setMaxStoredResponses(8);
        properties.setProviderTimeout(java.time.Duration.ofSeconds(15));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getPath()).isEqualTo("/ws/custom");
        assertThat(properties.getDefaultModel()).isEqualTo("gpt-4o");
        assertThat(properties.getMaxHistoryItems()).isEqualTo(7);
        assertThat(properties.getMaxStoredResponses()).isEqualTo(8);
        assertThat(properties.getProviderTimeout().toSeconds()).isEqualTo(15);
    }

    @Test
    void givenProperties_whenSettingSessionTtl_thenExposeConfiguredValue() {
        OpenResponsesGatewayProperties properties = new OpenResponsesGatewayProperties();
        assertThat(properties.getSessionTtl().toMinutes()).isEqualTo(30);

        properties.setSessionTtl(java.time.Duration.ofMinutes(60));
        assertThat(properties.getSessionTtl().toMinutes()).isEqualTo(60);
    }

    @Test
    void givenConfiguration_whenCreatingBeans_thenReturnWiredInstances() {
        OpenResponsesGatewayAutoConfiguration autoConfiguration = new OpenResponsesGatewayAutoConfiguration();
        OpenResponsesGatewayProperties properties = new OpenResponsesGatewayProperties();
        ObjectMapper objectMapper = new ObjectMapper();

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));

        List<Object> toolList = autoConfiguration.gatewayTools(new EchoTools());
        ModelProvider modelProvider = autoConfiguration.modelProvider(builder, toolList);
        GatewaySessionRepository sessionRepository = autoConfiguration.inMemoryGatewaySessionRepository();
        ClientEventParser parser = autoConfiguration.clientEventParser(objectMapper, properties);
        ModelProviderPort providerPort = modelProvider;
        ResponseLifecycleService lifecycleService = autoConfiguration.responseLifecycleService(
            parser, providerPort, sessionRepository, properties);
        AgentService agentService = autoConfiguration.agentService(lifecycleService);
        OpenResponsesHandler handler = autoConfiguration.openResponsesHandler(agentService, objectMapper);

        assertThat(toolList).isNotNull();
        assertThat(modelProvider).isInstanceOf(OpenAiProvider.class);
        assertThat(sessionRepository).isNotNull();
        assertThat(parser).isNotNull();
        assertThat(lifecycleService).isNotNull();
        assertThat(agentService).isNotNull();
        assertThat(handler).isNotNull();
    }

    @Test
    void givenWebSocketConfiguration_whenCreatingMapping_thenUseConfiguredPath() {
        OpenResponsesGatewayWebSocketConfiguration configuration = new OpenResponsesGatewayWebSocketConfiguration();
        OpenResponsesGatewayProperties properties = new OpenResponsesGatewayProperties();
        properties.setPath("/ws/test");
        OpenResponsesHandler handler = mock(OpenResponsesHandler.class);

        HandlerMapping mapping = configuration.openResponsesHandlerMapping(handler, properties);
        WebSocketHandlerAdapter adapter = configuration.webSocketHandlerAdapter();

        assertThat(mapping).isInstanceOf(SimpleUrlHandlerMapping.class);
        assertThat(((SimpleUrlHandlerMapping) mapping).getUrlMap().get("/ws/test"))
            .isSameAs(handler);
        assertThat(adapter).isNotNull();
    }
}
