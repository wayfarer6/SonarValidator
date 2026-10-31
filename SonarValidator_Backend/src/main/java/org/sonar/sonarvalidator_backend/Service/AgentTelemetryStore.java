package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

@Service
public class AgentTelemetryStore {

    private final Map<String, JsonNode> lastTelemetry = new ConcurrentHashMap<>();

    private final Map<String, NeutralDeviceConfig> lastConfig = new ConcurrentHashMap<>();

    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    private final Set<String> offlineOrigins = ConcurrentHashMap.newKeySet();

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
        lastConfig.put(agentId, config);
        offlineOrigins.remove(agentId);
    }

    public void putOfflineConfig(String agentId, NeutralDeviceConfig config) {
        if (agentId == null) {
            return;
        }
        lastConfig.put(agentId, config);
        offlineOrigins.add(agentId);
    }

    //  조회

    public JsonNode telemetryOf(String agentId) {
        return lastTelemetry.get(agentId);
    }

    public NeutralDeviceConfig configOf(String agentId) {
        return lastConfig.get(agentId);
    }

    public Map<String, NeutralDeviceConfig> allConfigs() {
        return Map.copyOf(lastConfig);
    }

    public Set<String> allTelemetryAgentIds() {
        return Set.copyOf(lastTelemetry.keySet());
    }

    public Instant lastSeenOf(String agentId) {
        return lastSeen.get(agentId);
    }

    public boolean isOfflineOrigin(String agentId) {
        return offlineOrigins.contains(agentId);
    }


    public boolean remove(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return false;
        }
        final boolean telemetry = lastTelemetry.remove(agentId) != null;
        final boolean config = lastConfig.remove(agentId) != null;
        final boolean seen = lastSeen.remove(agentId) != null;
        final boolean offline = offlineOrigins.remove(agentId);
        return telemetry || config || seen || offline;
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
                + " config=" + lastConfig.size()
                + " offline=" + offlineOrigins.size();
    }
}
