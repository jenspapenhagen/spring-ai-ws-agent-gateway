package de.papenhagen.gateway.application;

import de.papenhagen.protocol.ClientEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ClientEventParserTest {

    @Test
    void givenEnvelopePayload_whenParse_thenKeepTypeAndPayload() {
        // given
        ClientEventParser parser = new ClientEventParser(new ObjectMapper(), "gpt-4.1-mini");
        String raw = "{\"type\":\"response.cancel\",\"payload\":{\"response_id\":\"resp_1\"}}";

        // when
        ClientEvent event = parser.parse(raw).block();

        // then
        assertThat(event.type()).isEqualTo("response.cancel");
        assertThat(event.payload().path("response_id").asText()).isEqualTo("resp_1");
    }

    @Test
    void givenPayloadWithoutType_whenParse_thenDefaultToResponseCreate() {
        // given
        ClientEventParser parser = new ClientEventParser(new ObjectMapper(), "gpt-4.1-mini");
        String raw = "{\"input\":[\"hello\"]}";

        // when
        ClientEvent event = parser.parse(raw).block();

        // then
        assertThat(event.type()).isEqualTo("response.create");
        assertThat(event.payload().path("input").get(0).asText()).isEqualTo("hello");
    }

    @Test
    void givenPlainText_whenParse_thenFallbackToImplicitCreatePayload() {
        // given
        ClientEventParser parser = new ClientEventParser(new ObjectMapper(), "gpt-4.1-mini");

        // when
        ClientEvent event = parser.parse("hello world").block();

        // then
        assertThat(event.type()).isEqualTo("response.create");
        assertThat(event.payload().path("model").asText()).isEqualTo("gpt-4.1-mini");
        assertThat(event.payload().path("input").get(0).asText()).isEqualTo("hello world");
    }
}
