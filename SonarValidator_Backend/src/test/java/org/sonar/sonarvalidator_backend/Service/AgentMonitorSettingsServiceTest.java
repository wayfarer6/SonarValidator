package org.sonar.sonarvalidator_backend.Service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class AgentMonitorSettingsServiceTest {
    ExpectedAgentRepository repository;
    AgentSessionRegistry sessions;
    AgentMonitorSettingsService service;
    ExpectedAgent node;
    @BeforeEach void setup() {
        repository = mock(ExpectedAgentRepository.class);
        sessions = new AgentSessionRegistry(new ObjectMapper());
        service = new AgentMonitorSettingsService(repository, sessions);
        node = new ExpectedAgent(); node.setAgentId("n1"); node.setProjectKey("p1");
        when(repository.findByAgentId("n1")).thenReturn(Optional.of(node));
        when(repository.findForMonitorUpdate("n1")).thenReturn(Optional.of(node));
    }
    @Test void savesAnOfflineNodeAndSurvivesServiceRecreation() {
        service.save("n1","p1",7200,0);
        assertEquals(7200,node.getMonitorIntervalSeconds());
        assertEquals(1,node.getMonitorRevision());
        assertFalse(service.deliver("n1"));
        var fresh = new AgentMonitorSettingsService(repository,sessions);
        assertEquals(7200,fresh.status("n1","p1").get("monitor_interval_seconds"));
        assertEquals("offline",fresh.status("n1","p1").get("status"));
    }
    @Test void rejectsStaleEditorsAndOtherProjects() {
        service.save("n1","p1",5,0);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.save("n1","p1",6,0)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.save("n1","other",6,1)).getStatusCode().value());
        assertEquals(5,node.getMonitorIntervalSeconds());
    }
    @Test void rejectsOutOfRangeAndUnknownNodes() {
        for (int seconds : new int[]{0,-1,86401}) assertThrows(ResponseStatusException.class,()->service.save("n1","p1",seconds,0));
        assertThrows(ResponseStatusException.class,()->service.save("unknown","p1",30,0));
        verify(repository,never()).save(any());
    }
    @Test void sameValueCanBeRedeliveredWithoutChangingItsRevision() throws Exception {
        var telemetry = session("t1"); sessions.registerTelemetry("n1",telemetry);
        service.save("n1","p1",30,0);
        assertEquals(0,node.getMonitorRevision());
        assertTrue(service.deliver("n1"));
        verify(telemetry).sendMessage(any());
    }
    @Test void onlyTheCurrentTelemetrySessionCanConfirmApplication() {
        var management = session("m1"); var telemetry = session("t1");
        sessions.register("n1",management); sessions.registerTelemetry("n1",telemetry);
        var ack = JsonNodeFactory.instance.objectNode().put("action","monitor-interval-applied").put("revision",0).put("monitor_interval_seconds",30);
        service.recordApplied("n1",management,ack);
        assertEquals("pending",service.status("n1","p1").get("status"));
        service.recordApplied("n1",telemetry,ack);
        assertEquals("applied",service.status("n1","p1").get("status"));
        var replacement = session("t2"); sessions.registerTelemetry("n1",replacement);
        assertEquals("pending",service.status("n1","p1").get("status"));
        service.recordApplied("n1",telemetry,ack);
        assertEquals("pending",service.status("n1","p1").get("status"));
        sessions.unregister(telemetry);
        assertSame(replacement,sessions.telemetrySession("n1"));
        assertEquals(1,sessions.connectedCount());
    }
    @Test void staleAckCannotConfirmANewerSetting() {
        var telemetry=session("t1");sessions.registerTelemetry("n1",telemetry);
        service.save("n1","p1",5,0);
        service.recordApplied("n1",telemetry,JsonNodeFactory.instance.objectNode()
            .put("action","monitor-interval-applied").put("revision",0).put("monitor_interval_seconds",30));
        assertEquals("pending",service.status("n1","p1").get("status"));
    }
    @Test void rejectedPersistenceIsShownWithoutClaimingApplication() {
        var telemetry=session("t1");sessions.registerTelemetry("n1",telemetry);
        service.save("n1","p1",5,0);
        service.recordApplied("n1",telemetry,JsonNodeFactory.instance.objectNode().put("action","monitor-interval-rejected")
            .put("revision",1).put("requested_interval_seconds",5).put("monitor_interval_seconds",30));
        var status=service.status("n1","p1");
        assertEquals("error",status.get("status"));
        assertEquals(30,status.get("reported_interval_seconds"));
        assertNull(status.get("applied_revision"));
    }
    private WebSocketSession session(String id) {
        var session=mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new HashMap<>());return session;
    }
}
