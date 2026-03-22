
package de.papenhagen.protocol;

import java.util.List;

public record ResponseCreate(
    String model,
    List<String> input,
    String previous_response_id
) { }
