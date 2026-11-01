package org.sonar.sonarvalidator_backend.Config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class AgentWebSocketHandlerTest {
    @Test
    void nullJsonIsRejectedWithoutInvokingTheRouter() {
        assertRejected("null");
    }

    @Test
    void malformedInputDoesNotEchoPayloadOrParserDiagnostics() {
        assertRejected("{\"password\":\"test-sensitive-payload\", invalid}");
    }

    private void assertRejected(String payload) {
        var router = mock(AgentMessageRouterService.class);
        var registry = mock(AgentSessionRegistry.class);
        var socket = mock(WebSocketSession.class);
        var handler = new AgentWebSocketHandler(new ObjectMapper(), router, registry);
        assertDoesNotThrow(() -> handler.handleTextMessage(socket, new TextMessage(payload)));
        var response = ArgumentCaptor.forClass(Envelope.class);
        verify(registry).send(eq(socket), response.capture());
        assertEquals("invalid JSON envelope", response.getValue().getError());
        verifyNoInteractions(router);
    }
}
