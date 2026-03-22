package de.papenhagen.protocol;

public record ResponseCancelledPayload(
    String response_id,
    String reason
) { }
