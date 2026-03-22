package de.papenhagen.agent;

import de.papenhagen.gateway.adapter.session.InMemoryGatewaySessionRepository;
import de.papenhagen.gateway.application.ClientEventParser;
import de.papenhagen.gateway.application.ResponseLifecycleService;
import de.papenhagen.gateway.port.GatewaySessionRepository;
import de.papenhagen.gateway.port.ModelProviderPort;
import de.papenhagen.protocol.ResponseCompletedPayload;
import de.papenhagen.protocol.ResponseCreatedPayload;
import de.papenhagen.protocol.ResponseErrorPayload;
import de.papenhagen.protocol.ResponseOutputTextDeltaPayload;
import de.papenhagen.protocol.ServerEvent;
import de.papenhagen.provider.ProviderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceTest {

    private ModelProviderPort provider;
    private AgentService service;

    @BeforeEach
    void setUp() {
        provider = mock(ModelProviderPort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GatewaySessionRepository sessionRepo = new InMemoryGatewaySessionRepository();
        ClientEventParser parser = new ClientEventParser(objectMapper, "gpt-4.1-mini");
        ResponseLifecycleService lifecycle = new ResponseLifecycleService(
            parser, provider, sessionRepo, "gpt-4.1-mini", 100, 100, Duration.ofSeconds(90)
        );
        service = new AgentService(lifecycle);
    }

    @Test
    void givenResponseCreate_whenProviderStreams_thenEmitLifecycleAndDeltas() {
        // given
        when(provider.stream(any(ProviderRequest.class))).thenReturn(Flux.just("hello ", "world"));
        String input = "{\"type\":\"response.create\",\"payload\":{\"model\":\"gpt-4.1-mini\",\"input\":[\"hi\"]}}";

        // when
        List<ServerEvent> events = service.handle("s1", input).collectList().block();

        // then
        assertThat(events).hasSize(4);
        assertThat(events.get(0).type()).isEqualTo("response.created");
        assertThat(events.get(1).type()).isEqualTo("response.output_text.delta");
        assertThat(events.get(2).type()).isEqualTo("response.output_text.delta");
        assertThat(events.get(3).type()).isEqualTo("response.completed");

        ResponseCreatedPayload created = (ResponseCreatedPayload) events.get(0).payload();
        ResponseOutputTextDeltaPayload delta1 = (ResponseOutputTextDeltaPayload) events.get(1).payload();
        ResponseCompletedPayload completed = (ResponseCompletedPayload) events.get(3).payload();

        assertThat(created.status()).isEqualTo("in_progress");
        assertThat(delta1.delta()).isEqualTo("hello ");
        assertThat(completed.output_text()).isEqualTo("hello world");
    }

    @Test
    void givenTimeoutFromProvider_whenHandle_thenEmitResponseErrorRetryable() {
        // given
        when(provider.stream(any(ProviderRequest.class)))
            .thenReturn(Flux.error(new java.util.concurrent.TimeoutException("timeout")));
        String input = "{\"type\":\"response.create\",\"payload\":{\"input\":[\"hi\"]}}";

        // when
        List<ServerEvent> events = service.handle("s1", input).collectList().block();

        // then
        assertThat(events).hasSize(3);
        assertThat(events.get(1).type()).isEqualTo("response.error");
        ResponseErrorPayload error = (ResponseErrorPayload) events.get(1).payload();
        assertThat(error.code()).isEqualTo("provider_timeout");
        assertThat(error.retryable()).isTrue();
        ResponseCompletedPayload completed = (ResponseCompletedPayload) events.get(2).payload();
        assertThat(completed.status()).isEqualTo("failed");
    }

    @Test
    void givenResponseCancelWithoutId_whenHandle_thenEmitInvalidRequest() {
        // given
        String input = "{\"type\":\"response.cancel\",\"payload\":{}}";

        // when
        List<ServerEvent> events = service.handle("s1", input).collectList().block();

        // then
        assertThat(events).singleElement().extracting(ServerEvent::type).isEqualTo("response.error");
        ResponseErrorPayload payload = (ResponseErrorPayload) events.get(0).payload();
        assertThat(payload.code()).isEqualTo("invalid_request");
    }

    @Test
    void givenPlainTextMessage_whenHandle_thenFallbackToCreateRequest() {
        // given
        when(provider.stream(any(ProviderRequest.class))).thenReturn(Flux.just("ok"));

        // when
        StepVerifier.create(service.handle("s1", "just text"))
            // then
            .assertNext(event -> assertThat(event.type()).isEqualTo("response.created"))
            .assertNext(event -> assertThat(event.type()).isEqualTo("response.output_text.delta"))
            .assertNext(event -> assertThat(event.type()).isEqualTo("response.completed"))
            .verifyComplete();
    }

    @Test
    void givenUnsupportedInputShape_whenHandle_thenEmitInvalidRequestError() {
        // given
        String input = "{\"type\":\"response.create\",\"payload\":{\"input\":123}}";

        // when
        List<ServerEvent> events = service.handle("s1", input).collectList().block();

        // then
        assertThat(events).singleElement().extracting(ServerEvent::type).isEqualTo("response.error");
        ResponseErrorPayload payload = (ResponseErrorPayload) events.get(0).payload();
        assertThat(payload.code()).isEqualTo("invalid_request");
    }
}
