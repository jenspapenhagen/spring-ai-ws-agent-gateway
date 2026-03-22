package de.papenhagen.gateway.domain;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GatewaySessionTest {

    @Test
    void givenSessionWithState_whenClear_thenCancelActiveAndRemoveAllState() {
        // given
        GatewaySession session = new GatewaySession();
        session.history().add("hello");
        session.responses().put("r1", new GatewaySession.StoredResponse("r1", null, List.of("in"), "out", "completed"));
        session.responseOrder().addLast("r1");
        GatewaySession.ActiveResponse active = new GatewaySession.ActiveResponse("r2", null, List.of("in2"));
        session.activeResponses().put("r2", active);

        // when
        session.clear();

        // then
        assertThat(active.isTerminal()).isTrue();
        assertThat(session.history()).isEmpty();
        assertThat(session.responses()).isEmpty();
        assertThat(session.responseOrder()).isEmpty();
        assertThat(session.activeResponses()).isEmpty();
    }

    @Test
    void givenAlreadyTerminalActiveResponse_whenSetSubscription_thenDisposeImmediately() {
        // given
        GatewaySession.ActiveResponse active = new GatewaySession.ActiveResponse("r1", null, List.of("in"));
        Disposable disposable = mock(Disposable.class);
        active.cancel();

        // when
        active.setSubscription(disposable);

        // then
        verify(disposable).dispose();
    }
}
