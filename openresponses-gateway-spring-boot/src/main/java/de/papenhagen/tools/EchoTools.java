package de.papenhagen.tools;

import org.springframework.ai.tool.annotation.Tool;

public class EchoTools {

    @Tool(name = "echo", description = "Echoes back the provided input text")
    public String echo(final String input) {
        return "echo:" + input;
    }
}
