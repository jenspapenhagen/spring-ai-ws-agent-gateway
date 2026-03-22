package de.papenhagen.protocol;

import tools.jackson.databind.JsonNode;

public record ClientEvent(String type, JsonNode payload) { }
