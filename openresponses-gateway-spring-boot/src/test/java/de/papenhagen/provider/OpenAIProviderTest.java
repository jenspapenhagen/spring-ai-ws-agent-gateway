package de.papenhagen.provider;

import de.papenhagen.tools.EchoTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAIProviderTest {

    @Test
    void givenProviderRequest_whenStream_thenUseSpringAiToolsAndReturnTokenFlux() {
        // given
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        EchoTools tools = new EchoTools();

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("hello\nworld")).thenReturn(requestSpec);
        when(requestSpec.options(org.mockito.ArgumentMatchers.any(OpenAiChatOptions.class))).thenReturn(requestSpec);
        when(requestSpec.tools(tools)).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("a", "b"));

        OpenAiProvider provider = new OpenAiProvider(builder, tools);
        ProviderRequest request = new ProviderRequest("gpt-4.1-mini", List.of("hello", "world"), "r1", null);

        // when
        List<String> output = provider.stream(request).collectList().block();

        // then
        assertThat(output).containsExactly("a", "b");
        verify(requestSpec).tools(tools);
    }
}
