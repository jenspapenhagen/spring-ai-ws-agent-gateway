package de.papenhagen.gateway.port;

import de.papenhagen.provider.ProviderRequest;
import reactor.core.publisher.Flux;

/**
 * Port for model streaming providers.
 */
public interface ModelProviderPort {
    /**
     * Streams output tokens for one provider request.
     *
     * @param request normalized request payload
     * @return token stream
     */
    Flux<String> stream(ProviderRequest request);
}
