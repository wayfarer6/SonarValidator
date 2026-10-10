package org.sonar.sonarvalidator_backend;

import static org.mockito.Mockito.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Config.TerminalAgentWebSocketHandler;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Service.AgentTerminalBroker;
import org.springframework.web.socket.*;
import tools.jackson.databind.ObjectMapper;

class TerminalAgentWebSocketHandlerTest {
    private static final String KEY = "test-key-".repeat(8);

    @Test
    void sameConfiguredKeyAuthenticatesRegisteredAgent() throws Exception {
        final var repository = mock(ExpectedAgentRepository.class);
        final var broker = mock(AgentTerminalBroker.class);
        final var session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        final var agent = new ExpectedAgent();
        agent.setProjectKey("rvi-test");
        when(repository.findByAgentId("Cisco-Router")).thenReturn(Optional.of(agent));
        final var mapper = new ObjectMapper();
        final var handler = new TerminalAgentWebSocketHandler(mapper, repository, broker, "  " + KEY + "\n");
        handler.handleMessage(session, new TextMessage(mapper.writeValueAsString(
                Map.of("type", "terminal-hello", "agent_id", "Cisco-Router", "secret", KEY))));
        verify(broker).registerAgent("Cisco-Router", session);
        verify(session, never()).close(any());
    }

    @Test
    void missingOrMismatchedKeyCannotAuthenticate() throws Exception {
        for (String configured : new String[]{"", "different-".repeat(8)}) {
            final var repository = mock(ExpectedAgentRepository.class);
            final var broker = mock(AgentTerminalBroker.class);
            final var session = mock(WebSocketSession.class);
            final var mapper = new ObjectMapper();
            final var handler = new TerminalAgentWebSocketHandler(mapper, repository, broker, configured);
            handler.handleMessage(session, new TextMessage(mapper.writeValueAsString(
                    Map.of("type", "terminal-hello", "agent_id", "Cisco-Router", "secret", KEY))));
            verify(session).close(any(CloseStatus.class));
            verifyNoInteractions(repository, broker);
        }
    }
}
