package de.papenhagen.openresponses.gateway.test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.reactive.socket.client.JettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public class OpenResponsesTestClient {

    private final WebSocketClient client;
    private final URI uri;
    private final List<String> receivedMessages = new ArrayList<>();
    private final Sinks.Many<String> outgoing = Sinks.many().unicast().onBackpressureBuffer();
    private final Sinks.Many<String> incoming = Sinks.many().multicast().onBackpressureBuffer();

    private OpenResponsesTestClient(final WebSocketClient theClient, final URI theUri) {
        this.client = theClient;
        this.uri = theUri;
    }

    public static OpenResponsesTestClient connect(final String url) {
        return connect(new JettyWebSocketClient(), URI.create(url));
    }

    public static OpenResponsesTestClient connect(
        final WebSocketClient theClient, final URI theUri
    ) {
        return new OpenResponsesTestClient(theClient, theUri);
    }

    public OpenResponsesTestClient connect() {
        client.execute(uri, this::handleSession).block();
        return this;
    }

    private Mono<Void> handleSession(
        final org.springframework.web.reactive.socket.WebSocketSession session
    ) {
        final Mono<Void> receive = session.receive()
            .map(t -> t.getPayloadAsText())
            .doOnNext(incoming::tryEmitNext)
            .doOnNext(receivedMessages::add)
            .then();

        final Mono<Void> send = session.send(outgoing.asFlux().map(session::textMessage))
            .then();

        return receive.and(send);
    }

    public OpenResponsesTestClient send(final String jsonMessage) {
        outgoing.tryEmitNext(jsonMessage);
        return this;
    }

    public OpenResponsesTestClient sendResponseCreate(
        final String model, final List<String> input
    ) {
        final String escaped = input.stream()
            .map(s -> "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
            .toList()
            .toString();
        send("{\"type\":\"response.create\",\"payload\":{\"model\":\"" + model
            + "\",\"input\":" + escaped + "}}");
        return this;
    }

    public OpenResponsesTestClient sendResponseCancel(final String responseId) {
        send("{\"type\":\"response.cancel\",\"payload\":{\"response_id\":\"" + responseId + "\"}}");
        return this;
    }

    public List<String> getMessages() {
        return List.copyOf(receivedMessages);
    }

    public String getLastMessage() {
        if (receivedMessages.isEmpty()) {
            return null;
        }
        return receivedMessages.get(receivedMessages.size() - 1);
    }

    public Flux<String> messageStream() {
        return incoming.asFlux();
    }

    public void disconnect() {
        outgoing.tryEmitComplete();
    }
}
