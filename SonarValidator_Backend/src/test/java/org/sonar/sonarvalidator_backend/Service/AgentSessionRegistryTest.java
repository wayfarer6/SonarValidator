package org.sonar.sonarvalidator_backend.Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class AgentSessionRegistryTest {
    @Test
    @Timeout(5)
    void replyAndPushShareOneSessionLock() throws Exception {
        var registry = new AgentSessionRegistry(new ObjectMapper());
        var session = session("one");
        registry.register("agent", session);
        var entered = new AtomicBoolean();
        var release = new AtomicBoolean();
        var sends = new AtomicInteger();
        var successes = new AtomicInteger();
        doAnswer(call -> {
            if (sends.incrementAndGet() == 1) {
                entered.set(true);
                await(release::get);
            }
            return null;
        }).when(session).sendMessage(any());
        var reply = new Thread(() -> {
            if (registry.send(session, Envelope.error(null, "reply"))) successes.incrementAndGet();
        });
        var push = new Thread(() -> {
            if (registry.sendTo("agent", Envelope.error(null, "push"))) successes.incrementAndGet();
        });
        try {
            reply.start();
            await(entered::get);
            push.start();
            await(() -> push.getState() == Thread.State.BLOCKED || sends.get() > 1);
            assertEquals(Thread.State.BLOCKED, push.getState());
            assertEquals(1, sends.get());
        } finally {
            release.set(true);
            reply.join(2000);
            push.join(2000);
        }
        assertFalse(reply.isAlive());
        assertFalse(push.isAlive());
        assertEquals(2, successes.get());
    }

    @Test
    void closingOldConnectionKeepsReplacement() {
        var registry = new AgentSessionRegistry(new ObjectMapper());
        var old = session("old");
        var replacement = session("new");
        registry.register("agent", old);
        registry.register("agent", replacement);
        registry.unregister(old);
        assertTrue(registry.sendTo("agent", Envelope.error(null, "still connected")));
        assertEquals(1, registry.connectedCount());
    }

    @Test
    void replyAndPushUseTheExistingSendDecorator() throws Exception {
        var registry = new AgentSessionRegistry(new ObjectMapper());
        var original = session("raw");
        var decorated = session("decorated");
        original.getAttributes().put("wrapped", decorated);
        registry.register("agent", original);
        assertTrue(registry.send(original, Envelope.error(null, "reply")));
        assertTrue(registry.sendTo("agent", Envelope.error(null, "push")));
        verify(original, never()).sendMessage(any());
        verify(decorated, times(2)).sendMessage(any());
    }

    private static WebSocketSession session(String id) {
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        return session;
    }

    private static void await(BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!done.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(done.getAsBoolean(), "worker did not reach the expected state");
    }
}
