package org.sonar.sonarvalidator_backend.Service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;

class AgentTerminalBrokerTest {

    @Test
    void replacesClosedBrowserSessionAndRejectsSecondOpenSession() {
        final AgentTerminalBroker broker = new AgentTerminalBroker(new ObjectMapper());
        final WebSocketSession agent = session(true);
        final WebSocketSession firstBrowser = session(true);
        final WebSocketSession closedBrowser = session(false);
        final WebSocketSession replacementBrowser = session(true);

        assertTrue(broker.registerAgent("test-vm", agent));
        assertTrue(broker.openBrowser("test-vm", firstBrowser));
        assertFalse(broker.openBrowser("test-vm", replacementBrowser));
        broker.browserClosed("test-vm", firstBrowser);

        assertTrue(broker.openBrowser("test-vm", closedBrowser));
        broker.rejectBrowser("test-vm", closedBrowser);
        assertTrue(broker.openBrowser("test-vm", replacementBrowser));
    }

    private WebSocketSession session(boolean open) {
        final WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(open);
        when(session.getId()).thenReturn(open ? "open" : "closed");
        return session;
    }
}