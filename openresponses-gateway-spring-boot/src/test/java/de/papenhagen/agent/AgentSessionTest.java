package de.papenhagen.agent;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentSessionTest {

    @Test
    void givenSessionWithState_whenMutatingThenClearing_thenStateIsManagedCorrectly() {
        // given
        AgentSession session = new AgentSession();
        session.lastResponseId = "r_last";
        session.history.add("hello");
        session.responses.put("r1", new AgentSession.StoredResponse("r1", null, List.of("in"), "out", "completed"));
        session.responseOrder.addLast("r1");
        AgentSession.ActiveResponse active = new AgentSession.ActiveResponse("r2", "r1", List.of("in2"));
        session.activeResponses.put("r2", active);

        // when
        session.activeResponses.values().forEach(AgentSession.ActiveResponse::cancel);
        session.activeResponses.clear();
        session.responses.clear();
        session.responseOrder.clear();
        session.history.clear();

        // then
        assertThat(session.lastResponseId).isEqualTo("r_last");
        assertThat(active.isTerminal()).isTrue();
        assertThat(session.history).isEmpty();
        assertThat(session.responses).isEmpty();
        assertThat(session.responseOrder).isEmpty();
        assertThat(session.activeResponses).isEmpty();
    }

    @Test
    void givenActiveResponse_whenCancelledBeforeSubscription_thenSetSubscriptionDisposesImmediately() {
        // given
        AgentSession.ActiveResponse active = new AgentSession.ActiveResponse("r1", null, List.of("in"));
        Disposable disposable = mock(Disposable.class);
        active.cancel();

        // when
        active.setSubscription(disposable);

        // then
        verify(disposable).dispose();
    }

    @Test
    void givenActiveResponse_whenAppendingAndSnapshotting_thenStoredResponseContainsOutput() {
        // given
        AgentSession.ActiveResponse active = new AgentSession.ActiveResponse("r1", "r0", List.of("in"));

        // when
        active.appendOutput("hello");
        active.appendOutput(" world");
        AgentSession.StoredResponse stored = active.asStoredResponse("completed");

        // then
        assertThat(active.markTerminal()).isTrue();
        assertThat(active.markTerminal()).isFalse();
        assertThat(stored.id()).isEqualTo("r1");
        assertThat(stored.previousId()).isEqualTo("r0");
        assertThat(stored.input()).containsExactly("in");
        assertThat(stored.output()).isEqualTo("hello world");
        assertThat(stored.status()).isEqualTo("completed");
    }
}
