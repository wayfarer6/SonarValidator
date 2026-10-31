package org.sonar.sonarvalidator_backend.Controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.AgentTelemetryStore;
import org.sonar.sonarvalidator_backend.Service.DeviceConfigService;
import org.sonar.sonarvalidator_backend.Service.ExpectedAgentService;
import org.sonar.sonarvalidator_backend.Service.QuarantineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentStatusController {

    private final AgentSessionRegistry registry;
    private final AgentMessageRouterService router;
    private final DeviceConfigService deviceConfigService;
    private final ExpectedAgentService expectedAgentService;

    private final QuarantineService quarantineService;

    private final AgentTelemetryStore telemetryStore;

    public AgentStatusController(AgentSessionRegistry registry,
                                 AgentMessageRouterService router,
                                 DeviceConfigService deviceConfigService,
                                 ExpectedAgentService expectedAgentService,
                                 QuarantineService quarantineService,
                                 AgentTelemetryStore telemetryStore) {
        this.registry = registry;
        this.router = router;
        this.deviceConfigService = deviceConfigService;
        this.expectedAgentService = expectedAgentService;
        this.quarantineService = quarantineService;
        this.telemetryStore = telemetryStore;
    }

    @GetMapping
    public Map<String, Object> list() {
        final java.util.Set<String> quarantined = quarantineService.quarantinedAgentIds();
        final List<Map<String, Object>> agents = new ArrayList<>();
        for (String agentId : registry.connectedAgentIds()) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("last_seen", router.lastSeenOf(agentId));
            entry.put("has_telemetry", router.lastTelemetryOf(agentId) != null);
            entry.put("quarantined", isQuarantined(quarantined, agentId));
            agents.add(entry);
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected", registry.connectedCount());
        body.put("total_policy_requests", router.policyRequestCount());
        body.put("server_time", Instant.now().toString());
        body.put("quarantined_count", quarantined.size());
        body.put("quarantined", quarantined);
        body.put("agents", agents);
        return body;
    }

    private static boolean isQuarantined(java.util.Set<String> quarantined, String agentId) {
        if (agentId == null) {
            return false;
        }
        return quarantined.stream().anyMatch(id -> id != null && id.equalsIgnoreCase(agentId));
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(
            @RequestParam(name = "project_id", required = false) String projectId) {
        final java.util.Set<String> observed = new java.util.TreeSet<>();
        for (final String agentId : router.allConfigs().keySet()) {
            if (agentId != null) {
                observed.add(agentId);
            }
        }
        for (final String agentId : router.allTelemetryAgentIds()) {
            if (agentId != null) {
                observed.add(agentId);
            }
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected", registry.connectedCount());
        body.put("total_policy_requests", router.policyRequestCount());
        body.put("server_time", Instant.now().toString());
        body.put("agents", new ArrayList<>(registry.connectedAgentIds()));
        final java.util.Set<String> quarantined = quarantineService.quarantinedAgentIds();
        body.put("quarantined_count", quarantined.size());
        body.put("quarantined", quarantined);
        body.putAll(ExpectedAgentService.toResponse(
                expectedAgentService.list(projectId), registry.connectedAgentIds(), observed));
        return body;
    }

    @GetMapping("/{agentId}/telemetry")
    public JsonNode telemetry(@PathVariable String agentId) {
        final JsonNode telemetry = router.lastTelemetryOf(agentId);
        if (telemetry == null) {
            throw new AgentNotFoundException(agentId);
        }
        return telemetry;
    }

    @GetMapping("/{agentId}/config")
    public NeutralDeviceConfig config(@PathVariable String agentId) {
        final NeutralDeviceConfig config = router.lastConfigOf(agentId);
        if (config == null) {
            throw new AgentNotFoundException(agentId);
        }
        return config;
    }

    @GetMapping("/configs")
    public Map<String, Object> configs() {
        final List<Map<String, Object>> summaries = new ArrayList<>();
        router.allConfigs().forEach((agentId, config) -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("format", config.getFormat());
            entry.put("vendor", config.getVendor());
            entry.put("product", config.getProduct());
            entry.put("interfaces", config.getInterfaces().size());
            entry.put("routes", config.getRoutes().size());
            entry.put("vlans", config.getVlans().size());
            entry.put("arp_entries", config.getArpEntries().size());
            entry.put("firewall_rules", config.getFirewallRules().size());
            entry.put("warnings", List.copyOf(config.getWarnings()));
            summaries.add(entry);
        });

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("supported_formats", deviceConfigService.supportedFormats());
        body.put("parsed_devices", summaries.size());
        body.put("devices", summaries);
        return body;
    }

    @PostMapping("/{agentId}/push")
    public Map<String, Object> push(@PathVariable String agentId,
                                    @RequestBody JsonNode body) {
        final Envelope envelope = toCommandEnvelope(body);
        envelope.setAgent_id(agentId);

        final boolean delivered = registry.sendTo(agentId, envelope);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_id", agentId);
        result.put("delivered", delivered);
        result.put("reason", delivered ? null : "agent not connected");
        return result;
    }

    @PostMapping("/broadcast")
    public Map<String, Object> broadcast(@RequestBody JsonNode body) {
        final Envelope envelope = toCommandEnvelope(body);
        final int sent = registry.broadcast(envelope);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", sent);
        result.put("connected", registry.connectedCount());
        return result;
    }

    @DeleteMapping("/{agentId}/telemetry")
    public ResponseEntity<Map<String, Object>> removeTelemetry(@PathVariable String agentId) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_id", agentId);

        if (registry.connectedAgentIds().contains(agentId)) {
            result.put("removed", false);
            result.put("connected", true);
            result.put("reason", "Agent is connected; disconnecting it first would be needed");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }

        final boolean removed = telemetryStore.remove(agentId);
        result.put("removed", removed);
        result.put("connected", false);
        result.put("reason", removed ? null : "no telemetry for this agent");
        return removed
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    @DeleteMapping("/stale")
    public Map<String, Object> pruneStale(
            @RequestParam(name = "older_than_hours", required = false) Double olderThanHours) {
        final double hours = (olderThanHours == null || olderThanHours <= 0) ? DEFAULT_STALE_HOURS
                : olderThanHours;
        final java.time.Instant cutoff = java.time.Instant.now()
                .minusMillis((long) (hours * 3_600_000L));

        final int removed = telemetryStore.pruneUnseenSince(cutoff);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("removed", removed);
        result.put("older_than_hours", hours);
        result.put("cutoff", cutoff.toString());
        result.put("remaining_telemetry", telemetryStore.allTelemetryAgentIds().size());
        return result;
    }

    private static final double DEFAULT_STALE_HOURS = 24;

    private Envelope toCommandEnvelope(JsonNode body) {
        final Envelope envelope = Envelope.of(Envelope.Types.COMMAND);

        if (body == null || body.isNull() || body.isMissingNode()) {
            return envelope;
        }

        // 봉투를 그대로 보낸 경우: 기존 correlation_id/payload 를 존중합니다.
        final JsonNode explicitPayload = body.path("payload");
        if (explicitPayload.isObject()) {
            envelope.setPayload(explicitPayload);
            final JsonNode correlationId = body.path("correlation_id");
            if (correlationId.isString()) {
                envelope.setCorrelation_id(correlationId.asString());
            }
            return envelope;
        }

        // 짧은 호출: 본문 전체를 payload 로 사용합니다.
        envelope.setPayload(body);
        return envelope;
    }

    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class AgentNotFoundException extends RuntimeException {
        public AgentNotFoundException(String agentId) {
            super("no telemetry for agent: " + agentId);
        }
    }
}
