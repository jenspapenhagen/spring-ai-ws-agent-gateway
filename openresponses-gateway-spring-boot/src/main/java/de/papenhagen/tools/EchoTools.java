package de.papenhagen.tools;

import org.springframework.ai.tool.annotation.Tool;

/**
 * Demo placeholder tool. Override the {@code EchoTools} bean in your application
 * to register real tool implementations.
 */
public class EchoTools {

    @Tool(name = "echo", description = "Echoes back the provided input text")
    public String echo(final String input) {
        return "echo:" + input;
    }
}
