package de.papenhagen.ws;

import de.papenhagen.agent.AgentService;
import de.papenhagen.protocol.ServerEvent;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenResponsesHandlerTest {

    @Test
    void givenInboundMessage_whenHandle_thenDelegateToAgentAndCloseSession() {
        // given
        AgentService agentService = mock(AgentService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        OpenResponsesHandler handler = new OpenResponsesHandler(agentService, objectMapper);

        WebSocketSession session = mock(WebSocketSession.class);
        WebSocketMessage inbound = mock(WebSocketMessage.class);
        WebSocketMessage outbound = mock(WebSocketMessage.class);

        when(session.getId()).thenReturn("s1");
        when(inbound.getPayloadAsText()).thenReturn("hello");
        when(session.receive()).thenReturn(Flux.just(inbound));
        when(agentService.handle("s1", "hello"))
            .thenReturn(Flux.just(new ServerEvent("response.completed", null)));
        when(session.textMessage(anyString())).thenReturn(outbound);
        doAnswer(invocation -> Flux.from((Publisher<WebSocketMessage>) invocation.getArgument(0)).then())
            .when(session).send(any(Publisher.class));

        // when
        StepVerifier.create(handler.handle(session)).verifyComplete();

        // then
        verify(agentService).handle("s1", "hello");
        verify(agentService).closeSession("s1");
    }

    @Test
    void givenSerializationFailure_whenHandle_thenSendProtocolErrorPayload() {
        // given
        AgentService agentService = mock(AgentService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        OpenResponsesHandler handler = new OpenResponsesHandler(agentService, objectMapper);

        WebSocketSession session = mock(WebSocketSession.class);
        WebSocketMessage inbound = mock(WebSocketMessage.class);
        WebSocketMessage outbound = mock(WebSocketMessage.class);

        when(session.getId()).thenReturn("s1");
        when(inbound.getPayloadAsText()).thenReturn("hello");
        when(session.receive()).thenReturn(Flux.just(inbound));
        when(agentService.handle("s1", "hello"))
            .thenReturn(Flux.just(new ServerEvent("response.completed", null)));
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));
        when(session.textMessage(anyString())).thenReturn(outbound);
        doAnswer(invocation -> Flux.from((Publisher<WebSocketMessage>) invocation.getArgument(0)).then())
            .when(session).send(any(Publisher.class));

        // when
        StepVerifier.create(handler.handle(session)).verifyComplete();

        // then
        verify(session).textMessage(org.mockito.ArgumentMatchers.contains("\"serialization_error\""));
        verify(agentService).closeSession("s1");
    }
}
