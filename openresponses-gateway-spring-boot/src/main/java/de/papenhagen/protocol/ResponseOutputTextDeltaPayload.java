package de.papenhagen.protocol;

public record ResponseOutputTextDeltaPayload(
    String response_id,
    String delta
) { }
