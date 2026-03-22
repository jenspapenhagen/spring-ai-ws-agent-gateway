package de.papenhagen.gateway.decorator;

import de.papenhagen.gateway.port.ModelProviderPort;
import de.papenhagen.provider.ProviderRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircuitBreakerModelProviderAdapter implements ModelProviderPort {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerModelProviderAdapter.class);

    private final ModelProviderPort port;
    private final CircuitBreaker breaker;

    public CircuitBreakerModelProviderAdapter(
        final ModelProviderPort delegate,
        final CircuitBreakerRegistry registry
    ) {
        this(delegate, registry.circuitBreaker("model-provider"));
    }

    public CircuitBreakerModelProviderAdapter(
        final ModelProviderPort delegate,
        final CircuitBreaker theBreaker
    ) {
        this.port = delegate;
        this.breaker = theBreaker;
    }

    public CircuitBreaker breaker() {
        return breaker;
    }

    @Override
    public Flux<String> stream(final ProviderRequest request) {
        return Flux.defer(() -> port.stream(request)
            .transformDeferred(CircuitBreakerOperator.of(breaker))
            .doOnError(e -> log.warn("Provider call failed for responseId={}: {}",
                request.responseId(), e.getClass().getSimpleName())));
    }
}
