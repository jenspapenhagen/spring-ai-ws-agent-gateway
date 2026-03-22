package de.papenhagen.protocol;

public record ResponseCompletedPayload(
    String response_id,
    String status,
    String output_text
) { }
