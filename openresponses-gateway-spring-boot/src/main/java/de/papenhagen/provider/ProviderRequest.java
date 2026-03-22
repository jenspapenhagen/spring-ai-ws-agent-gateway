package de.papenhagen.provider;

import java.util.List;

public record ProviderRequest(
    String model,
    List<String> input,
    String responseId,
    String previousResponseId
) { }
