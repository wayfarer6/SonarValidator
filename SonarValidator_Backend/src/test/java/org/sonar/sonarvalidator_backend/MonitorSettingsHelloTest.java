package org.sonar.sonarvalidator_backend;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Service.*;
import org.sonar.sonarvalidator_backend.Service.cli.CliIngestionService;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class MonitorSettingsHelloTest {
    @Test void telemetryReceivesSavedSettingsWithoutReplacingTheManagementSession() {
        var mapper=new ObjectMapper();var registry=new AgentSessionRegistry(mapper);
        var router=new AgentMessageRouterService(registry,mock(PolicyRegistryService.class),mock(DeviceConfigService.class),
            mock(org.sonar.sonarvalidator_backend.Service.log.LogService.class),mock(NotificationService.class),mock(CliIngestionService.class));
        var settings=mock(AgentMonitorSettingsService.class);router.setMonitorSettings(settings);
        when(settings.desired("n1")).thenReturn(JsonNodeFactory.instance.objectNode().put("monitor_interval_seconds",17).put("revision",3));
        var management=session("m");registry.register("n1",management);
        var telemetry=session("t");var hello=Envelope.of(Envelope.Types.HELLO);hello.setAgent_id("n1");
        hello.setPayload(JsonNodeFactory.instance.objectNode().put("channel","telemetry"));
        var ack=router.handle(telemetry,hello);
        assertEquals(17,ack.getPayload().path("monitor_settings").path("monitor_interval_seconds").asInt());
        assertSame(telemetry,registry.telemetrySession("n1"));assertEquals(1,registry.connectedCount());
    }
    private WebSocketSession session(String id) {
        var session=mock(WebSocketSession.class);when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(new HashMap<>());when(session.isOpen()).thenReturn(true);return session;
    }
}
