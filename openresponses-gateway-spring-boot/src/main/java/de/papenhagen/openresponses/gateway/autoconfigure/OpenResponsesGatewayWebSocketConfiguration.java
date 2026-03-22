package de.papenhagen.openresponses.gateway.autoconfigure;

import java.util.Map;
import de.papenhagen.ws.OpenResponsesHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

public class OpenResponsesGatewayWebSocketConfiguration {

    /**
     * Registers the gateway endpoint path.
     *
     * <p>Why: endpoint path is property-driven so starter consumers can align with existing gateway routing
     * conventions without code changes.</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "openResponsesHandlerMapping")
    public HandlerMapping openResponsesHandlerMapping(
        final OpenResponsesHandler handler,
        final OpenResponsesGatewayProperties properties
    ) {
        return new SimpleUrlHandlerMapping(Map.of(properties.getPath(), handler), 1);
    }

    /**
     * Supplies the default adapter required by Spring WebFlux WebSocket handling.
     *
     * <p>Why: declaring this conditionally avoids clashing with host applications that already customize the
     * adapter behavior.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
