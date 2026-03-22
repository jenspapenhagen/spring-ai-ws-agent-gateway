package de.papenhagen.gateway.application;

import de.papenhagen.protocol.ClientEvent;
import de.papenhagen.protocol.ResponseCreate;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * Parses raw inbound messages into normalized client protocol events.
 */
public class ClientEventParser {

    private final ObjectMapper objectMapper;
    private final String defaultModel;

    public ClientEventParser(final ObjectMapper mappedObjectMapper, final String configuredDefaultModel) {
        this.objectMapper = mappedObjectMapper;
        this.defaultModel = configuredDefaultModel;
    }

    /**
     * Parses one raw inbound text message.
     *
     * <p>If parsing fails, the message is treated as plain text and converted to an implicit
     * {@code response.create} event.</p>
     *
     * @param rawMessage websocket text payload
     * @return normalized protocol event
     */
    public Mono<ClientEvent> parse(final String rawMessage) {
        try {
            final JsonNode root = objectMapper.readTree(rawMessage);
            final String type = root.path("type").asText("");
            final JsonNode payload = root.has("payload") ? root.path("payload") : root;
            final String normalizedType = type.isBlank() ? "response.create" : type;
            return Mono.just(new ClientEvent(normalizedType, payload));
        } catch (Exception e) {
            final ResponseCreate fallback = new ResponseCreate(defaultModel, List.of(rawMessage), null);
            final JsonNode payload = objectMapper.valueToTree(fallback);
            return Mono.just(new ClientEvent("response.create", payload));
        }
    }
}
