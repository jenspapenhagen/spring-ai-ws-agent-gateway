package de.papenhagen.agent;

import de.papenhagen.gateway.application.ResponseLifecycleService;
import de.papenhagen.protocol.ServerEvent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceFacadeTest {

    @Test
    void givenLifecycleService_whenHandle_thenDelegateCallAndReturnStream() {
        // given
        ResponseLifecycleService lifecycleService = mock(ResponseLifecycleService.class);
        AgentService agentService = new AgentService(lifecycleService);
        ServerEvent event = new ServerEvent("response.created", null);
        when(lifecycleService.handle("s1", "msg")).thenReturn(Flux.just(event));

        // when
        List<ServerEvent> events = agentService.handle("s1", "msg").collectList().block();

        // then
        verify(lifecycleService).handle("s1", "msg");
        assertThat(events).containsExactly(event);
    }

    @Test
    void givenLifecycleService_whenCloseSession_thenDelegateCall() {
        // given
        ResponseLifecycleService lifecycleService = mock(ResponseLifecycleService.class);
        AgentService agentService = new AgentService(lifecycleService);

        // when
        agentService.closeSession("s1");

        // then
        verify(lifecycleService).closeSession("s1");
    }
}
