package de.papenhagen.agent;

import de.papenhagen.protocol.ResponseCompletedPayload;
import de.papenhagen.protocol.ResponseCreatedPayload;
import de.papenhagen.protocol.ResponseErrorPayload;
import de.papenhagen.protocol.ResponseOutputTextDeltaPayload;
import de.papenhagen.protocol.ServerEvent;
import de.papenhagen.provider.ModelProvider;
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

    private ModelProvider provider;
    private AgentService service;

    @BeforeEach
    void setUp() {
        provider = mock(ModelProvider.class);
        service = new AgentService(new ObjectMapper(), provider, "gpt-4.1-mini", 100, 100, Duration.ofSeconds(90));
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
        when(provider.stream(any(ProviderRequest.class))).thenReturn(Flux.error(new java.util.concurrent.TimeoutException("timeout")));
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
    void givenPreviousResponseId_whenCreate_thenProviderReceivesContinuationInput() {
        // given
        when(provider.stream(any(ProviderRequest.class)))
            .thenReturn(Flux.just("old answer"))
            .thenReturn(Flux.just("new answer"));

        String first = "{\"type\":\"response.create\",\"payload\":{\"input\":[\"first\"]}}";
        List<ServerEvent> firstEvents = service.handle("s1", first).collectList().block(Duration.ofSeconds(3));
        String firstResponseId = ((ResponseCreatedPayload) firstEvents.get(0).payload()).response_id();

        String second = "{\"type\":\"response.create\",\"payload\":{\"input\":[\"second\"],\"previous_response_id\":\"" + firstResponseId + "\"}}";

        // when
        service.handle("s1", second).collectList().block();

        // then
        ArgumentCaptor<ProviderRequest> captor = ArgumentCaptor.forClass(ProviderRequest.class);
        verify(provider, org.mockito.Mockito.times(2)).stream(captor.capture());
        List<ProviderRequest> calls = captor.getAllValues();
        assertThat(calls.get(1).input()).contains("old answer", "second");
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

    @Test
    void givenCloseSession_whenCalled_thenFutureContinuationCannotUsePriorResponse() {
        // given
        when(provider.stream(any(ProviderRequest.class)))
            .thenReturn(Flux.just("old answer"))
            .thenReturn(Flux.just("new answer"));

        String first = "{\"type\":\"response.create\",\"payload\":{\"input\":[\"first\"]}}";
        List<ServerEvent> firstEvents = service.handle("s1", first).collectList().block(Duration.ofSeconds(3));
        String firstResponseId = ((ResponseCreatedPayload) firstEvents.get(0).payload()).response_id();
        service.closeSession("s1");

        String second = "{\"type\":\"response.create\",\"payload\":{\"input\":[\"second\"],\"previous_response_id\":\"" + firstResponseId + "\"}}";

        // when
        service.handle("s1", second).collectList().block();

        // then
        ArgumentCaptor<ProviderRequest> captor = ArgumentCaptor.forClass(ProviderRequest.class);
        verify(provider, org.mockito.Mockito.times(2)).stream(captor.capture());
        List<ProviderRequest> calls = captor.getAllValues();
        assertThat(calls.get(1).input()).containsExactly("second");
    }
}
