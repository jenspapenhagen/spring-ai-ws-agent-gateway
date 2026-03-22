package de.papenhagen.protocol;

public record ResponseCreatedPayload(
    String response_id,
    String previous_response_id,
    String status
) { }
