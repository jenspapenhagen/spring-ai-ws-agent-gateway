package de.papenhagen.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolPayloadTest {

    @Test
    void givenCancelledPayload_whenCreated_thenExposeFields() {
        // given
        ResponseCancelledPayload payload = new ResponseCancelledPayload("resp_1", "client_requested");

        // then
        assertThat(payload.response_id()).isEqualTo("resp_1");
        assertThat(payload.reason()).isEqualTo("client_requested");
    }
}
