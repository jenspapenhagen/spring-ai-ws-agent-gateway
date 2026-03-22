package de.papenhagen.openresponses.gateway.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class OpenResponsesAssertions {

    private final ObjectMapper objectMapper;

    private OpenResponsesAssertions(final ObjectMapper theObjectMapper) {
        this.objectMapper = theObjectMapper;
    }

    public static OpenResponsesAssertions using(final ObjectMapper objectMapper) {
        return new OpenResponsesAssertions(objectMapper);
    }

    public Consumer<String> hasEventType(final String expectedType) {
        return json -> {
            final String actualType = extractField(json, "type");
            if (!expectedType.equals(actualType)) {
                throw new AssertionError(
                    "Expected event type '" + expectedType + "' but was '" + actualType + "' in: " + json);
            }
        };
    }

    public Consumer<String> hasResponseId(final String expectedResponseId) {
        return json -> {
            final String path = jsonContainsField(json, "response_id")
                ? "response_id"
                : null;
            if (path == null) {
                throw new AssertionError(
                    "No response_id field found in: " + json);
            }
            final String actual = extractAt(json, "response_id");
            if (!expectedResponseId.equals(actual)) {
                throw new AssertionError(
                    "Expected response_id '" + expectedResponseId + "' but was '" + actual + "' in: " + json);
            }
        };
    }

    public Consumer<String> hasStatus(final String expectedStatus) {
        return json -> {
            final String actual = extractAt(json, "status");
            if (!expectedStatus.equals(actual)) {
                throw new AssertionError(
                    "Expected status '" + expectedStatus + "' but was '" + actual + "' in: " + json);
            }
        };
    }

    public Consumer<String> hasOutputText(final String expectedText) {
        return json -> {
            final String actual = extractAt(json, "output_text");
            if (!expectedText.equals(actual)) {
                throw new AssertionError(
                    "Expected output_text '" + expectedText + "' but was '" + actual + "' in: " + json);
            }
        };
    }

    public Consumer<String> hasErrorCode(final String expectedCode) {
        return json -> {
            final String actual = extractAt(json, "code");
            if (!expectedCode.equals(actual)) {
                throw new AssertionError(
                    "Expected error code '" + expectedCode + "' but was '" + actual + "' in: " + json);
            }
        };
    }

    public void assertAll(final List<String> messages, final Consumer<String>... assertions) {
        for (int i = 0; i < messages.size(); i++) {
            for (final Consumer<String> assertion : assertions) {
                assertion.accept(messages.get(i));
            }
        }
    }

    public void assertFirst(final List<String> messages, final Consumer<String> assertion) {
        if (messages.isEmpty()) {
            throw new AssertionError("No messages to assert");
        }
        assertion.accept(messages.get(0));
    }

    public void assertLast(final List<String> messages, final Consumer<String> assertion) {
        if (messages.isEmpty()) {
            throw new AssertionError("No messages to assert");
        }
        assertion.accept(messages.get(messages.size() - 1));
    }

    public List<String> filterByType(final List<String> messages, final String eventType) {
        final List<String> filtered = new ArrayList<>();
        for (final String msg : messages) {
            if (eventType.equals(extractField(msg, "type"))) {
                filtered.add(msg);
            }
        }
        return filtered;
    }

    private String extractField(final String json, final String field) {
        return extractAt(json, field);
    }

    private String extractAt(final String json, final String path) {
        try {
            final JsonNode node = objectMapper.readTree(json);
            final JsonNode payload = node.has("payload") ? node.get("payload") : node;
            final JsonNode target = payload.has(path) ? payload.get(path) : node.get(path);
            return target != null && !target.isNull() ? target.asText() : null;
        } catch (Exception e) {
            throw new AssertionError("Failed to parse JSON: " + json, e);
        }
    }

    private boolean jsonContainsField(final String json, final String field) {
        try {
            final JsonNode node = objectMapper.readTree(json);
            final JsonNode payload = node.has("payload") ? node.get("payload") : node;
            return payload.has(field) || node.has(field);
        } catch (Exception e) {
            return false;
        }
    }
}
