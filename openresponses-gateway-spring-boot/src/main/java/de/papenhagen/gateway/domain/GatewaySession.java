package de.papenhagen.gateway.domain;

import reactor.core.Disposable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class GatewaySession {
    private final Deque<String> history = new ConcurrentLinkedDeque<>();
    private final Map<String, StoredResponse> responses = new ConcurrentHashMap<>();
    private final Deque<String> responseOrder = new ConcurrentLinkedDeque<>();
    private final Map<String, ActiveResponse> activeResponses = new ConcurrentHashMap<>();
    private volatile String lastResponseId;

    public Deque<String> history() {
        return history;
    }

    public Map<String, StoredResponse> responses() {
        return responses;
    }

    public Deque<String> responseOrder() {
        return responseOrder;
    }

    public Map<String, ActiveResponse> activeResponses() {
        return activeResponses;
    }

    public String lastResponseId() {
        return lastResponseId;
    }

    public void setLastResponseId(final String responseId) {
        this.lastResponseId = responseId;
    }

    public void clear() {
        activeResponses.values().forEach(ActiveResponse::cancel);
        activeResponses.clear();
        responses.clear();
        responseOrder.clear();
        history.clear();
    }

    public static final class ActiveResponse {
        private final String id;
        private final String previousResponseId;
        private final List<String> input;
        private final StringBuilder output = new StringBuilder();
        private final AtomicBoolean terminal = new AtomicBoolean(false);
        private volatile Disposable subscription;

        public ActiveResponse(
            final String responseId,
            final String previousId,
            final List<String> requestInput
        ) {
            this.id = responseId;
            this.previousResponseId = previousId;
            this.input = List.copyOf(new ArrayList<>(requestInput));
        }

        public void appendOutput(final String token) {
            output.append(token);
        }

        public String outputText() {
            return output.toString();
        }

        public void setSubscription(final Disposable disposableSubscription) {
            if (terminal.get()) {
                disposableSubscription.dispose();
                return;
            }
            this.subscription = disposableSubscription;
        }

        public boolean markTerminal() {
            return terminal.compareAndSet(false, true);
        }

        public boolean isTerminal() {
            return terminal.get();
        }

        public void cancel() {
            terminal.set(true);
            final Disposable local = subscription;
            if (local != null) {
                local.dispose();
            }
        }

        public StoredResponse asStoredResponse(final String status) {
            return new StoredResponse(id, previousResponseId, input, outputText(), status);
        }
    }

    /**
     * Immutable persisted response snapshot for continuation and diagnostics.
     *
     * @param id response id
     * @param previousId previous response id if present
     * @param input normalized input payload
     * @param output flattened output text
     * @param status terminal status
     */
    public record StoredResponse(
        String id,
        String previousId,
        List<String> input,
        String output,
        String status
    ) {
        public StoredResponse {
            input = List.copyOf(new ArrayList<>(input));
        }
    }
}
