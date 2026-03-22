package de.papenhagen.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EchoToolsTest {

    @Test
    void givenInput_whenEcho_thenReturnEchoPrefixedText() {
        // given
        EchoTools tools = new EchoTools();

        // when
        String output = tools.echo("abc");

        // then
        assertThat(output).isEqualTo("echo:abc");
    }
}
