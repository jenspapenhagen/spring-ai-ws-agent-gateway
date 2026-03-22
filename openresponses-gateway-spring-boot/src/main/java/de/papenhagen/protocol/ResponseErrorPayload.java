package de.papenhagen.protocol;

public record ResponseErrorPayload(
    String response_id,
    String code,
    String message,
    boolean retryable
) { }
