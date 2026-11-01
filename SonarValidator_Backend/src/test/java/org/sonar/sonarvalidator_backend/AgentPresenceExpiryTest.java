package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class AgentPresenceExpiryTest {
    @Test void idleTcpSessionDoesNotKeepPoweredOffDeviceOnline() {
        var time = new AtomicLong(1000);
        var clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(call -> time.get());
        var registry = new AgentSessionRegistry(new ObjectMapper(), clock);
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);
        registry.register("node", session);
        assertEquals(1, registry.connectedCount());
        time.addAndGet(31_000);
        assertTrue(registry.connectedAgentIds().isEmpty());
        registry.register("node", session);
        assertEquals(java.util.List.of("node"), registry.connectedAgentIds());
    }

    @Test void lateDisconnectOfOldSessionDoesNotEraseNewActivity() {
        var time = new AtomicLong(1000);
        var clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(call -> time.get());
        var registry = new AgentSessionRegistry(new ObjectMapper(), clock);
        var old = mock(WebSocketSession.class);
        var current = mock(WebSocketSession.class);
        when(old.getId()).thenReturn("old");
        when(current.getId()).thenReturn("new");
        when(old.isOpen()).thenReturn(true);
        when(current.isOpen()).thenReturn(true);
        registry.register("node", old);
        time.addAndGet(29_000);
        registry.register("node", current);
        registry.unregister(old);
        time.addAndGet(2000);
        assertEquals(java.util.List.of("node"), registry.connectedAgentIds());
    }
}
