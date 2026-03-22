package de.papenhagen.gateway.application;

import de.papenhagen.gateway.domain.GatewaySession;
import de.papenhagen.gateway.port.GatewaySessionRepository;
import de.papenhagen.gateway.port.ModelProviderPort;
import de.papenhagen.protocol.ClientEvent;
import de.papenhagen.protocol.ResponseCancelledPayload;
import de.papenhagen.protocol.ResponseCompletedPayload;
import de.papenhagen.protocol.ResponseCreate;
import de.papenhagen.protocol.ResponseCreatedPayload;
import de.papenhagen.protocol.ResponseErrorPayload;
import de.papenhagen.protocol.ResponseOutputTextDeltaPayload;
import de.papenhagen.protocol.ServerEvent;
import de.papenhagen.provider.ProviderRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Application service orchestrating response lifecycle events for one gateway session stream.
 */
public class ResponseLifecycleService {

    private final ClientEventParser parser;
    private final ModelProviderPort provider;
    private final GatewaySessionRepository sessions;
    private final String defaultModel;
    private final int maxHistoryItems;
    private final int maxStoredResponses;
    private final Duration providerTimeout;

    public ResponseLifecycleService(
        final ClientEventParser eventParser,
        final ModelProviderPort modelProvider,
        final GatewaySessionRepository sessionRepository,
        final String configuredDefaultModel,
        final int configuredMaxHistoryItems,
        final int configuredMaxStoredResponses,
        final Duration configuredProviderTimeout
    ) {
        this.parser = eventParser;
        this.provider = modelProvider;
        this.sessions = sessionRepository;
        this.defaultModel = configuredDefaultModel;
        this.maxHistoryItems = configuredMaxHistoryItems;
        this.maxStoredResponses = configuredMaxStoredResponses;
        this.providerTimeout = configuredProviderTimeout;
    }

    /**
     * Handles one inbound client message and emits normalized server protocol events.
     *
     * @param sessionId websocket session identifier
     * @param rawMessage websocket text payload
     * @return stream of protocol events
     */
    public Flux<ServerEvent> handle(final String sessionId, final String rawMessage) {
        final GatewaySession session = sessions.getOrCreate(sessionId);
        return parser.parse(rawMessage)
            .flatMapMany(event -> routeEvent(session, event))
            .onErrorResume(e -> Flux.just(errorEvent(null, "invalid_request", e.getMessage(), false)));
    }

    private Flux<ServerEvent> routeEvent(final GatewaySession session, final ClientEvent event) {
        return switch (event.type()) {
            case "response.create" -> createResponse(session, event.payload());
            case "response.cancel" -> cancelResponse(session, event.payload());
            default -> Flux.just(
                errorEvent(null, "invalid_request", "Unsupported event type: " + event.type(), false)
            );
        };
    }

    private Flux<ServerEvent> cancelResponse(final GatewaySession session, final JsonNode payload) {
        final String responseId = payload.path("response_id").asText(null);
        if (responseId == null || responseId.isBlank()) {
            return Flux.just(errorEvent(null, "invalid_request", "response_id is required for response.cancel", false));
        }
        final GatewaySession.ActiveResponse active = session.activeResponses().remove(responseId);
        if (active == null) {
            return Flux.just(errorEvent(responseId, "response_not_active", "response_id is not active", false));
        }
        active.cancel();
        storeResponse(session, active.asStoredResponse("cancelled"));
        return Flux.just(
            new ServerEvent("response.cancelled", new ResponseCancelledPayload(responseId, "client_requested"))
        );
    }

    private Flux<ServerEvent> createResponse(final GatewaySession session, final JsonNode payload) {
        final ResponseCreate request;
        try {
            request = toResponseCreate(payload);
        } catch (IllegalArgumentException e) {
            return Flux.just(errorEvent(null, "invalid_request", e.getMessage(), false));
        }
        final String responseId = "resp_" + UUID.randomUUID().toString().replace("-", "");
        session.setLastResponseId(responseId);

        final List<String> input = buildInputWithContinuation(session, request);
        input.forEach(session.history()::add);
        trimHistory(session);

        final ServerEvent created = new ServerEvent(
            "response.created",
            new ResponseCreatedPayload(responseId, request.previous_response_id(), "in_progress"));

        final ProviderRequest providerRequest = new ProviderRequest(
            request.model() == null || request.model().isBlank() ? defaultModel : request.model(),
            List.copyOf(input),
            responseId,
            request.previous_response_id()
        );
        final GatewaySession.ActiveResponse active =
            new GatewaySession.ActiveResponse(responseId, request.previous_response_id(), input);
        session.activeResponses().put(responseId, active);

        final Flux<ServerEvent> stream = provider.stream(providerRequest)
            .timeout(providerTimeout)
            .map(token -> {
                if (active.isTerminal()) {
                    return null;
                }
                active.appendOutput(token);
                return new ServerEvent("response.output_text.delta",
                    new ResponseOutputTextDeltaPayload(responseId, token));
            })
            .filter(Objects::nonNull)
            .concatWith(Mono.fromSupplier(() -> completeResponse(session, responseId, active)))
            .filter(Objects::nonNull)
            .onErrorResume(error -> failResponse(session, responseId, active, error))
            .doFinally(signal -> session.activeResponses().remove(responseId, active));

        return Flux.concat(Flux.just(created), stream)
            .doOnSubscribe(subscription -> active.setSubscription(subscription::cancel));
    }

    private ResponseCreate toResponseCreate(final JsonNode payload) {
        final String model = payload.path("model").asText(defaultModel);
        final String previousResponseId = payload.path("previous_response_id").asText(null);
        final JsonNode inputNode = payload.get("input");
        List<String> input = normalizeInput(inputNode);
        if (input.isEmpty()) {
            input = List.of("");
        }
        return new ResponseCreate(model, input, previousResponseId);
    }

    private List<String> buildInputWithContinuation(final GatewaySession session, final ResponseCreate request) {
        final List<String> result = new ArrayList<>();
        if (request.previous_response_id() != null) {
            final GatewaySession.StoredResponse previous = session.responses().get(request.previous_response_id());
            if (previous != null && previous.output() != null && !previous.output().isBlank()) {
                result.add(previous.output());
            }
        }
        if (request.input() != null) {
            result.addAll(request.input());
        }
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }

    private List<String> normalizeInput(final JsonNode inputNode) {
        if (inputNode == null || inputNode.isNull()) {
            return List.of("");
        }
        if (inputNode.isTextual()) {
            return List.of(inputNode.asText());
        }
        if (!inputNode.isArray()) {
            throw new IllegalArgumentException("input must be a string or an array");
        }

        final List<String> items = new ArrayList<>();
        for (final JsonNode node : inputNode) {
            if (node.isTextual()) {
                items.add(node.asText());
                continue;
            }
            if (node.isObject()) {
                if (node.path("text").isTextual()) {
                    items.add(node.path("text").asText());
                    continue;
                }
                final JsonNode content = node.path("content");
                if (content.isTextual()) {
                    items.add(content.asText());
                    continue;
                }
                if (content.isArray()) {
                    for (final JsonNode contentItem : content) {
                        if (contentItem.path("text").isTextual()) {
                            items.add(contentItem.path("text").asText());
                        } else {
                            throw new IllegalArgumentException("Unsupported content item in input array");
                        }
                    }
                    continue;
                }
            }
            throw new IllegalArgumentException("Unsupported input item type");
        }
        return items;
    }

    private ServerEvent completeResponse(
        final GatewaySession session,
        final String responseId,
        final GatewaySession.ActiveResponse active
    ) {
        if (!active.markTerminal()) {
            return null;
        }
        storeResponse(session, active.asStoredResponse("completed"));
        return new ServerEvent("response.completed",
            new ResponseCompletedPayload(responseId, "completed", active.outputText()));
    }

    private Flux<ServerEvent> failResponse(
        final GatewaySession session,
        final String responseId,
        final GatewaySession.ActiveResponse active,
        final Throwable error
    ) {
        if (!active.markTerminal()) {
            return Flux.empty();
        }
        storeResponse(session, active.asStoredResponse("failed"));
        final String outputText = active.outputText();
        return Flux.just(
            errorEvent(responseId, errorCode(error), safeMessage(error), isRetryable(error)),
            new ServerEvent("response.completed", new ResponseCompletedPayload(responseId, "failed", outputText))
        );
    }

    private void storeResponse(final GatewaySession session, final GatewaySession.StoredResponse stored) {
        session.responses().put(stored.id(), stored);
        session.responseOrder().addLast(stored.id());
        while (session.responseOrder().size() > maxStoredResponses) {
            final String oldest = session.responseOrder().pollFirst();
            if (oldest != null) {
                session.responses().remove(oldest);
            }
        }
    }

    private void trimHistory(final GatewaySession session) {
        while (session.history().size() > maxHistoryItems) {
            if (!session.history().isEmpty()) {
                session.history().remove(0);
            }
        }
    }

    /**
     * Closes and removes one session and cancels any active responses.
     *
     * @param sessionId websocket session identifier
     */
    public void closeSession(final String sessionId) {
        final GatewaySession session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        session.clear();
    }

    private ServerEvent errorEvent(
        final String responseId,
        final String code,
        final String message,
        final boolean retryable
    ) {
        return new ServerEvent("response.error", new ResponseErrorPayload(responseId, code, message, retryable));
    }

    private String safeMessage(final Throwable error) {
        final String message = error.getMessage();
        return (message == null || message.isBlank()) ? error.getClass().getSimpleName() : message;
    }

    private String errorCode(final Throwable error) {
        if (error instanceof TimeoutException) {
            return "provider_timeout";
        }
        return "provider_error";
    }

    private boolean isRetryable(final Throwable error) {
        return error instanceof TimeoutException;
    }
}
