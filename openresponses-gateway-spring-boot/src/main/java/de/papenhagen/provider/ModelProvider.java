
package de.papenhagen.provider;

import de.papenhagen.gateway.port.ModelProviderPort;
import reactor.core.publisher.Flux;

public interface ModelProvider extends ModelProviderPort {
    Flux<String> stream(ProviderRequest request);
}
