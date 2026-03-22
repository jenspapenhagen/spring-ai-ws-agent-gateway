package de.papenhagen.gateway.adapter.session;

import de.papenhagen.gateway.domain.GatewaySession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryGatewaySessionRepositoryTest {

    @Test
    void givenSameSessionId_whenGetOrCreate_thenReturnSameSessionInstance() {
        // given
        InMemoryGatewaySessionRepository repository = new InMemoryGatewaySessionRepository();

        // when
        GatewaySession first = repository.getOrCreate("s1");
        GatewaySession second = repository.getOrCreate("s1");

        // then
        assertThat(second).isSameAs(first);
    }

    @Test
    void givenRemovedSession_whenGetOrCreateAgain_thenReturnNewSessionInstance() {
        // given
        InMemoryGatewaySessionRepository repository = new InMemoryGatewaySessionRepository();
        GatewaySession first = repository.getOrCreate("s1");

        // when
        GatewaySession removed = repository.remove("s1");
        GatewaySession recreated = repository.getOrCreate("s1");

        // then
        assertThat(removed).isSameAs(first);
        assertThat(recreated).isNotSameAs(first);
    }
}
