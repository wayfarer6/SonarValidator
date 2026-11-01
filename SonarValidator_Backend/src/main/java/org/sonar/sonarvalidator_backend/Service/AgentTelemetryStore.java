package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

@Service
public class AgentTelemetryStore {

    private final Map<String, JsonNode> lastTelemetry = new ConcurrentHashMap<>();

    private record AcceptedSnapshot(JsonNode payload, NeutralDeviceConfig config, boolean offline, Instant observedAt) {}

    private final Map<String, AcceptedSnapshot> accepted = new ConcurrentHashMap<>();

    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    //  기록

    public void touch(String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            lastSeen.put(agentId, Instant.now());
        }
    }

    public void putTelemetry(String agentId, JsonNode payload) {
        if (agentId == null) {
            return;
        }
        lastTelemetry.put(agentId, payload);
    }

    public void putConfig(String agentId, NeutralDeviceConfig config) {
        if (agentId == null) {
            return;
        }
        accepted.compute(agentId, (id, previous) -> new AcceptedSnapshot(
                previous == null ? null : previous.payload(), config, false, Instant.now()));
    }

    public void putOfflineConfig(String agentId, NeutralDeviceConfig config) {
        if (agentId == null) return;
        accepted.compute(agentId, (id, previous) -> new AcceptedSnapshot(
                previous == null ? null : previous.payload(), config, true, Instant.now()));
    }

    public void putAcceptedSnapshot(String agentId, JsonNode payload, NeutralDeviceConfig config, boolean offline) {
        accepted.put(agentId, new AcceptedSnapshot(payload.deepCopy(), config, offline, Instant.now()));
        putTelemetry(agentId, payload);
    }

    public Instant observedAtOf(String agentId) {
        final var snapshot = accepted.get(agentId);
        return snapshot == null ? null : snapshot.observedAt();
    }

    public JsonNode acceptedPayloadOf(String agentId) {
        final var snapshot = accepted.get(agentId);
        return snapshot == null ? null : snapshot.payload();
    }

    //  조회

    public JsonNode telemetryOf(String agentId) {
        return lastTelemetry.get(agentId);
    }

    public NeutralDeviceConfig configOf(String agentId) {
        final var snapshot = accepted.get(agentId);
        return snapshot == null ? null : snapshot.config();
    }

    public Map<String, NeutralDeviceConfig> allConfigs() {
        return accepted.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getValue().config()));
    }

    public Set<String> allTelemetryAgentIds() {
        return Set.copyOf(lastTelemetry.keySet());
    }

    public Instant lastSeenOf(String agentId) {
        return lastSeen.get(agentId);
    }

    public boolean isOfflineOrigin(String agentId) {
        final var snapshot = accepted.get(agentId);
        return snapshot != null && snapshot.offline();
    }


    public boolean remove(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return false;
        }
        final boolean telemetry = lastTelemetry.remove(agentId) != null;
        final boolean config = accepted.remove(agentId) != null;
        final boolean seen = lastSeen.remove(agentId) != null;
        return telemetry || config || seen;
    }

    public int pruneUnseenSince(Instant cutoff) {
        if (cutoff == null) {
            return 0;
        }
        int removed = 0;
        for (final Map.Entry<String, Instant> entry : lastSeen.entrySet()) {
            final String agentId = entry.getKey();
            final Instant seen = entry.getValue();
            if (seen != null && seen.isBefore(cutoff) && remove(agentId)) {
                removed++;
            }
        }
        return removed;
    }

    public String summary() {
        return "telemetry=" + lastTelemetry.size()
                + " config=" + accepted.size()
                + " offline=" + accepted.values().stream().filter(AcceptedSnapshot::offline).count();
    }
}
