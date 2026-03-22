
package de.papenhagen.ws;

import de.papenhagen.agent.AgentService;
import de.papenhagen.protocol.ServerEvent;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

public class OpenResponsesHandler implements WebSocketHandler {

    private final AgentService agent;
    private final ObjectMapper objectMapper;

    public OpenResponsesHandler(final AgentService agentService, final ObjectMapper mappedObjectMapper) {
        this.agent = agentService;
        this.objectMapper = mappedObjectMapper;
    }

    /**
     * Bridges raw WebSocket text frames to gateway protocol events.
     *
     * <p>Why: using one reactive pipeline keeps request processing streaming-friendly, while {@code doFinally}
     * guarantees session cleanup regardless of close reason.</p>
     */
    @Override
    public Mono<Void> handle(final WebSocketSession session) {
        return session.send(
            session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(msg -> agent.handle(session.getId(), msg))
                .map(this::toJson)
                .map(session::textMessage)
        ).doFinally(signal -> agent.closeSession(session.getId()));
    }

    /**
     * Serializes protocol events to JSON.
     *
     * <p>Why: if serialization fails, we still emit a protocol-level error payload so clients get a structured
     * failure signal instead of a silent stream interruption.</p>
     */
    private String toJson(final ServerEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            final String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return "{\"type\":\"response.error\",\"payload\":{\"code\":\"serialization_error\",\"message\":\""
                + message.replace("\"", "\\\"") + "\",\"retryable\":false}}";
        }
    }
}
