package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Service.*;
import org.sonar.sonarvalidator_backend.Service.telemetry.*;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class TelemetryObserverTest {
    @Test
    void oneFailedObserverDoesNotSuppressLaterObservers() {
        var order = new ArrayList<Integer>();
        TelemetryObserver first = event -> { order.add(1); throw new IllegalStateException("expected failure"); };
        TelemetryObserver second = event -> { assertThat(event.agentId()).isEqualTo("node"); order.add(2); };
        new TelemetryEventPublisher(List.of(first, second)).publish(
                new TelemetrySnapshot("node", new NeutralDeviceConfig(), TelemetrySnapshot.Source.LIVE, Instant.now()));
        assertThat(order).containsExactly(1, 2);
    }
    @Test
    void liveAndOfflineTelemetryUseTheSameExtensionPoint() {
        var parser = mock(DeviceConfigService.class);
        var config = new NeutralDeviceConfig(); config.setFormat("custom");
        config.interfaceOrCreate("eth0").getAddresses().add("10.0.0.1/24");
        when(parser.parse(eq("node"), eq("custom"), any(JsonNode.class))).thenReturn(config);
        var router = new AgentMessageRouterService(mock(AgentSessionRegistry.class), mock(PolicyRegistryService.class),
                parser, null, null, null);
        var received = new ArrayList<TelemetrySnapshot>();
        router.setTelemetryEvents(new TelemetryEventPublisher(List.of(received::add)));
        var payload = JsonNodeFactory.instance.objectNode().put("product", "custom");
        router.acceptOfflineTelemetry("node", "custom", payload);
        var envelope = JsonMapper.builder().build().readValue(
                "{\"type\":\"telemetry\",\"agent_id\":\"node\",\"payload\":{\"product\":\"custom\"}}", Envelope.class);
        var session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new java.util.HashMap<>());
        router.handle(session, envelope);
        assertThat(received).extracting(TelemetrySnapshot::source)
                .containsExactly(TelemetrySnapshot.Source.OFFLINE, TelemetrySnapshot.Source.LIVE);
        assertThat(router.allConfigs()).containsEntry("node", config);
    }
}
