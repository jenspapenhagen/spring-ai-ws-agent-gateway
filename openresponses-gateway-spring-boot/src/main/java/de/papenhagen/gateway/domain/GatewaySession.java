package de.papenhagen.gateway.domain;

import reactor.core.Disposable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable state container for one websocket session.
 */
public class GatewaySession {
    private final List<String> history = new CopyOnWriteArrayList<>();
    private final Map<String, StoredResponse> responses = new ConcurrentHashMap<>();
    private final Deque<String> responseOrder = new ConcurrentLinkedDeque<>();
    private final Map<String, ActiveResponse> activeResponses = new ConcurrentHashMap<>();
    private volatile String lastResponseId;

    /**
     * @return bounded textual history used for continuation
     */
    public List<String> history() {
        return history;
    }

    /**
     * @return stored terminal responses by response id
     */
    public Map<String, StoredResponse> responses() {
        return responses;
    }

    /**
     * @return insertion order of stored responses for eviction
     */
    public Deque<String> responseOrder() {
        return responseOrder;
    }

    /**
     * @return currently active in-flight responses
     */
    public Map<String, ActiveResponse> activeResponses() {
        return activeResponses;
    }

    /**
     * @return last response id seen for this session
     */
    public String lastResponseId() {
        return lastResponseId;
    }

    /**
     * @param responseId last response id produced in this session
     */
    public void setLastResponseId(final String responseId) {
        this.lastResponseId = responseId;
    }

    /**
     * Clears all session state and cancels active responses.
     */
    public void clear() {
        activeResponses.values().forEach(ActiveResponse::cancel);
        activeResponses.clear();
        responses.clear();
        responseOrder.clear();
        history.clear();
    }

    /**
     * Mutable state for one in-flight response stream.
     */
    public static final class ActiveResponse {
        private final String id;
        private final String previousResponseId;
        private final List<String> input;
        private final StringBuffer output = new StringBuffer();
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

        /**
         * @param token streamed token
         */
        public void appendOutput(final String token) {
            output.append(token);
        }

        /**
         * @return concatenated output text collected so far
         */
        public String outputText() {
            return output.toString();
        }

        /**
         * Registers provider subscription and disposes immediately if already terminal.
         *
         * @param disposableSubscription upstream disposable
         */
        public void setSubscription(final Disposable disposableSubscription) {
            this.subscription = disposableSubscription;
            if (terminal.get()) {
                disposableSubscription.dispose();
            }
        }

        /**
         * Marks this response terminal once.
         *
         * @return {@code true} if this call won terminal ownership
         */
        public boolean markTerminal() {
            return terminal.compareAndSet(false, true);
        }

        /**
         * @return whether this response is terminal
         */
        public boolean isTerminal() {
            return terminal.get();
        }

        /**
         * Cancels the upstream stream and marks terminal.
         */
        public void cancel() {
            terminal.set(true);
            final Disposable local = subscription;
            if (local != null) {
                local.dispose();
            }
        }

        /**
         * Creates an immutable snapshot of this active response.
         *
         * @param status terminal response status
         * @return stored response snapshot
         */
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
