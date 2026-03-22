package de.papenhagen.provider;

import de.papenhagen.tools.EchoTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

public class OpenAiProvider implements ModelProvider {

    private final ChatClient chatClient;
    private final EchoTools echoTools;

    public OpenAiProvider(final ChatClient.Builder builder, final EchoTools configuredEchoTools) {
        this.chatClient = builder.build();
        this.echoTools = configuredEchoTools;
    }

    /**
     * Streams model output tokens from Spring AI.
     *
     * <p>Why: the gateway standardizes protocol events externally, while this adapter keeps provider-specific
     * request shaping localized (model selection, tool wiring, prompt flattening).</p>
     */
    @Override
    public Flux<String> stream(final ProviderRequest request) {
        final String prompt = String.join("\n", request.input());
        return chatClient.prompt()
            .user(prompt)
            .options(OpenAiChatOptions.builder().model(request.model()).build())
            .tools(echoTools)
            .stream()
            .content();
    }
}
