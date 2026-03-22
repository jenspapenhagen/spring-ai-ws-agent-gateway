
package de.papenhagen.provider;

import reactor.core.publisher.Flux;

public interface ModelProvider {
    Flux<String> stream(ProviderRequest request);
}
