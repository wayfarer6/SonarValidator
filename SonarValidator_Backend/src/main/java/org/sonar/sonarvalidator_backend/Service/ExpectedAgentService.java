package org.sonar.sonarvalidator_backend.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExpectedAgentService {

    private static final Logger log = LoggerFactory.getLogger(ExpectedAgentService.class);

    private final ExpectedAgentRepository repository;

    public ExpectedAgentService(ExpectedAgentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ExpectedAgent register(String agentId,
                                  String projectKey,
                                  String deviceType,
                                  String nodeType,
                                  String expectedIp,
                                  String note) {
        final String id = (agentId == null) ? "" : agentId.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("agent_id is required");
        }

        final Date now = new Date();
        final ExpectedAgent entity = repository.findByAgentId(id).orElseGet(ExpectedAgent::new);

        if (entity.getProjectKey() != null && !entity.getProjectKey().equals(projectKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 프로젝트에 등록된 Agent 이름입니다. 별도 이름을 사용하세요.");
        }
        entity.setAgentId(id);
        entity.setProjectKey(projectKey);
        entity.setDeviceType(resolveDeviceType(deviceType, id));
        entity.setNodeType((nodeType == null || nodeType.isBlank()) ? "VM" : nodeType);
        entity.setExpectedIp(expectedIp);
        if (note != null) {
            entity.setNote(note);
        }
        entity.setStatus("Deployed");
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);

        final ExpectedAgent saved = repository.save(entity);
        log.info("장비 등록: 노드={} 프로젝트={} 종류={}", id, projectKey, saved.getDeviceType());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ExpectedAgent> list(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            return repository.findAllByOrderByCreatedAtDesc();
        }
        return repository.findByProjectKeyOrderByCreatedAtDesc(projectKey);
    }

    @Transactional
    public void delete(String agentId) {
        repository.findByAgentId(agentId).ifPresent(entity -> {
            repository.delete(entity);
            log.info("장비 등록 해제: 노드={}", agentId);
        });
    }

    @Transactional
    public void deleteFromProject(String agentId, String projectKey) {
        final ExpectedAgent entity = repository.findByAgentId(agentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Agent is not registered in this project"));
        if (!projectKey.equals(entity.getProjectKey())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Agent is not registered in this project");
        }
        repository.delete(entity);
        log.info("장비 등록 해제: 노드={} 프로젝트={}", agentId, projectKey);
    }

    @Transactional
    public void deleteByProject(String projectKey) {
        repository.deleteByProjectKey(projectKey);
    }

    public static Map<String, Map<String, Object>> overview(Collection<ExpectedAgent> expected,
                                                            Collection<String> connectedIds,
                                                            Collection<String> telemetryIds) {
        final Set<String> connected = lowerCaseSet(connectedIds);
        final Set<String> seen = lowerCaseSet(telemetryIds);

        final Map<String, Map<String, Object>> result = new java.util.LinkedHashMap<>();

        for (final ExpectedAgent item : expected) {
            final Map<String, Object> entry = new java.util.LinkedHashMap<>();
            final String key = lower(item.getAgentId());
            entry.put("agent_id", item.getAgentId());
            entry.put("project_id", item.getProjectKey());
            entry.put("device_type", item.getDeviceType());
            entry.put("node_type", item.getNodeType());
            entry.put("expected_ip", item.getExpectedIp());
            entry.put("monitor_interval_seconds", item.getMonitorIntervalSeconds());
            entry.put("registered_at", item.getCreatedAt());
            entry.put("connected", connected.contains(key));
            entry.put("telemetry_seen", seen.contains(key));
            entry.put("expected", true);
            entry.put("state", stateOf(connected.contains(key), seen.contains(key), true));
            applyApiManaged(entry, item.getAgentId());
            result.put(key, entry);
        }

        final Map<String, String> canonical = new LinkedHashMap<>();
        if (connectedIds != null) {
            for (final String value : connectedIds) {
                if (value != null && !value.isBlank()) {
                    canonical.putIfAbsent(lower(value), value);
                }
            }
        }
        if (telemetryIds != null) {
            for (final String value : telemetryIds) {
                if (value != null && !value.isBlank()) {
                    canonical.putIfAbsent(lower(value), value);
                }
            }
        }

        final Set<String> unexpected = new LinkedHashSet<>(connected);
        unexpected.addAll(seen);
        unexpected.removeAll(result.keySet());
        for (final String key : unexpected) {
            final String agentId = canonical.getOrDefault(key, key);
            final Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("project_id", null);
            // 유형을 비워 두면 화면에 "—" 로만 보여 운영자가 판단할 수 없습니다.
            // 식별자에서 추론합니다. (inferFromDeviceId 가 이름의 끝도 봅니다)
            entry.put("device_type", resolveDeviceType(null, agentId));
            entry.put("node_type", null);
            entry.put("expected_ip", null);
            entry.put("registered_at", null);
            entry.put("connected", connected.contains(key));
            entry.put("telemetry_seen", seen.contains(key));
            entry.put("expected", false);
            entry.put("state", stateOf(connected.contains(key), seen.contains(key), false));
            applyApiManaged(entry, agentId);
            result.put(key, entry);
        }

        return result;
    }

    public static String stateOf(boolean connected, boolean seen, boolean expected) {
        if (connected) {
            return "connected";
        }
        if (seen) {
            return "telemetry-only";
        }
        return expected ? "silent" : "unregistered";
    }

    private static String resolveDeviceType(String deviceType, String agentId) {
        final DeviceType parsed = DeviceType.fromString(deviceType);
        if (parsed != null) {
            return parsed.name();
        }
        final DeviceType inferred = DeviceType.inferFromDeviceId(agentId);
        return (inferred == null) ? DeviceType.VM.name() : inferred.name();
    }

    private static Set<String> lowerCaseSet(Collection<String> values) {
        final Set<String> set = new LinkedHashSet<>();
        if (values != null) {
            for (final String value : values) {
                if (value != null && !value.isBlank()) {
                    set.add(lower(value));
                }
            }
        }
        return set;
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static Map<String, Object> toResponse(List<ExpectedAgent> expected,
                                                 Collection<String> connectedIds,
                                                 Collection<String> telemetryIds) {
        final Map<String, Map<String, Object>> overview = overview(expected, connectedIds, telemetryIds);
        int connected = 0;
        int silent = 0;
        for (final Map<String, Object> entry : overview.values()) {
            if (Boolean.TRUE.equals(entry.get("connected"))) {
                connected++;
            } else if ("silent".equals(entry.get("state"))) {
                silent++;
            }
        }

        final Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("total", overview.size());
        body.put("expected_total", expected.size());
        body.put("connected", connected);
        body.put("silent", silent);
        body.put("agents", new ArrayList<>(overview.values()));
        return body;
    }

    private static void applyApiManaged(Map<String, Object> entry, String agentId) {
        final String key = agentId == null
                ? ""
                : agentId.toLowerCase(java.util.Locale.ROOT);
        final String deviceType = String.valueOf(entry.get("device_type"));
        final boolean managed = key.contains("opnsense")
                || key.contains("firewall")
                || "FIREWALL".equalsIgnoreCase(deviceType);
        entry.put("api_managed", managed);
    }
}
