package de.papenhagen.gateway.adapter.provider;

import de.papenhagen.gateway.port.ModelProviderPort;
import de.papenhagen.provider.ModelProvider;
import de.papenhagen.provider.ProviderRequest;
import reactor.core.publisher.Flux;

/**
 * Adapter that bridges the legacy {@link ModelProvider} to {@link ModelProviderPort}.
 */
public class LegacyModelProviderAdapter implements ModelProviderPort {

    private final ModelProvider delegate;

    /**
     * @param modelProvider existing provider implementation
     */
    public LegacyModelProviderAdapter(final ModelProvider modelProvider) {
        this.delegate = modelProvider;
    }

    /**
     * Delegates token streaming to the legacy provider bean.
     *
     * @param request provider request
     * @return token stream
     */
    @Override
    public Flux<String> stream(final ProviderRequest request) {
        return delegate.stream(request);
    }
}
