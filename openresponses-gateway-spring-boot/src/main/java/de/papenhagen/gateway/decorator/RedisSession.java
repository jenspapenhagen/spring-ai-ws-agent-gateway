package de.papenhagen.gateway.decorator;

import de.papenhagen.gateway.domain.GatewaySession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class RedisSession {

    private List<String> history;
    private Map<String, StoredResponseData> responses;
    private List<String> responseOrder;
    private String lastResponseId;

    public RedisSession() {
    }

    public RedisSession(final GatewaySession session) {
        this.history = new LinkedList<>(session.history());
        this.responses = new LinkedHashMap<>();
        for (final Map.Entry<String, GatewaySession.StoredResponse> entry : session.responses().entrySet()) {
            final GatewaySession.StoredResponse sr = entry.getValue();
            this.responses.put(entry.getKey(), new StoredResponseData(
                sr.id(),
                sr.previousId(),
                new ArrayList<>(sr.input()),
                sr.output(),
                sr.status()
            ));
        }
        this.responseOrder = new LinkedList<>(session.responseOrder());
        this.lastResponseId = session.lastResponseId();
    }

    public void applyTo(final GatewaySession session) {
        session.history().clear();
        session.history().addAll(this.history);
        session.responses().clear();
        if (this.responses != null) {
            for (final Map.Entry<String, StoredResponseData> entry : this.responses.entrySet()) {
                final StoredResponseData sr = entry.getValue();
                session.responses().put(entry.getKey(), new GatewaySession.StoredResponse(
                    sr.id(),
                    sr.previousId(),
                    List.copyOf(sr.input()),
                    sr.output(),
                    sr.status()
                ));
            }
        }
        session.responseOrder().clear();
        if (this.responseOrder != null) {
            session.responseOrder().addAll(this.responseOrder);
        }
        session.setLastResponseId(this.lastResponseId);
    }

    public List<String> history() {
        return history;
    }

    public void setHistory(final List<String> hist) {
        this.history = hist;
    }

    public Map<String, StoredResponseData> responses() {
        return responses;
    }

    public void setResponses(final Map<String, StoredResponseData> resp) {
        this.responses = resp;
    }

    public List<String> responseOrder() {
        return responseOrder;
    }

    public void setResponseOrder(final List<String> order) {
        this.responseOrder = order;
    }

    public String lastResponseId() {
        return lastResponseId;
    }

    public void setLastResponseId(final String id) {
        this.lastResponseId = id;
    }

    public static final class StoredResponseData {
        private String id;
        private String previousId;
        private List<String> input;
        private String output;
        private String status;

        public StoredResponseData() {
        }

        public StoredResponseData(
            final String theId,
            final String thePreviousId,
            final List<String> theInput,
            final String theOutput,
            final String theStatus
        ) {
            this.id = theId;
            this.previousId = thePreviousId;
            this.input = theInput;
            this.output = theOutput;
            this.status = theStatus;
        }

        public String id() {
            return id;
        }

        public void setId(final String theId) {
            this.id = theId;
        }

        public String previousId() {
            return previousId;
        }

        public void setPreviousId(final String thePreviousId) {
            this.previousId = thePreviousId;
        }

        public List<String> input() {
            return input;
        }

        public void setInput(final List<String> theInput) {
            this.input = theInput;
        }

        public String output() {
            return output;
        }

        public void setOutput(final String theOutput) {
            this.output = theOutput;
        }

        public String status() {
            return status;
        }

        public void setStatus(final String theStatus) {
            this.status = theStatus;
        }
    }
}
