package de.papenhagen.provider;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;
import java.util.List;

public class OpenAiProvider implements ModelProvider {

    private final ChatClient chatClient;
    private final List<Object> tools;

    public OpenAiProvider(final ChatClient.Builder builder, final List<Object> configuredTools) {
        this.chatClient = builder.build();
        this.tools = List.copyOf(configuredTools);
    }

    @Override
    public Flux<String> stream(final ProviderRequest request) {
        final String prompt = String.join("\n", request.input());
        final ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
            .user(prompt)
            .options(OpenAiChatOptions.builder().model(request.model()).build());
        if (tools != null && !tools.isEmpty()) {
            return promptSpec
                .tools(tools.toArray())
                .stream()
                .content();
        }
        return promptSpec.stream().content();
    }
}
