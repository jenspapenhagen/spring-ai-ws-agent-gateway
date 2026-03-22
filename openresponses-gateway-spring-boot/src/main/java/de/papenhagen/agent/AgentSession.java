
package de.papenhagen.agent;

import reactor.core.Disposable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class AgentSession {
    public final List<String> history = new CopyOnWriteArrayList<>();
    public final Map<String, StoredResponse> responses = new ConcurrentHashMap<>();
    public final Deque<String> responseOrder = new ConcurrentLinkedDeque<>();
    public final Map<String, ActiveResponse> activeResponses = new ConcurrentHashMap<>();
    public volatile String lastResponseId;

    public static final class ActiveResponse {
        private final String id;
        private final String previousResponseId;
        private final List<String> input;
        // Tokens may be appended from provider callbacks while cancellation happens concurrently.
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

        public void appendOutput(final String token) {
            output.append(token);
        }

        public String outputText() {
            return output.toString();
        }

        /**
         * Registers the cancellable upstream subscription.
         *
         * <p>Why: cancellation can race with subscription establishment; if cancellation already happened,
         * dispose immediately so no extra tokens are produced.</p>
         */
        public void setSubscription(final Disposable disposableSubscription) {
            this.subscription = disposableSubscription;
            if (terminal.get()) {
                disposableSubscription.dispose();
            }
        }

        /**
         * Claims terminal ownership once.
         *
         * <p>Why: response completion, failure, and cancellation are concurrent paths that must emit at most one
         * terminal outcome.</p>
         */
        public boolean markTerminal() {
            return terminal.compareAndSet(false, true);
        }

        public boolean isTerminal() {
            return terminal.get();
        }

        /**
         * Stops provider streaming for this response.
         *
         * <p>Why: in-flight generation must stop promptly when the client cancels or the session closes.</p>
         */
        public void cancel() {
            terminal.set(true);
            final Disposable local = subscription;
            if (local != null) {
                local.dispose();
            }
        }

        /**
         * Snapshots mutable active state into immutable stored response state.
         *
         * <p>Why: stored responses are reused for continuation input and should not change after completion.</p>
         */
        public StoredResponse asStoredResponse(final String status) {
            return new StoredResponse(id, previousResponseId, input, outputText(), status);
        }
    }

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
