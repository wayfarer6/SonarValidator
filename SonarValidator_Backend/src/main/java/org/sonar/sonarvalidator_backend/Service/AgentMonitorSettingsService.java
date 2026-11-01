package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AgentMonitorSettingsService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentMonitorSettingsService.class);
    public static final int DEFAULT_SECONDS = 30;
    public static final int MAX_SECONDS = 86400;
    private final ExpectedAgentRepository repository;
    private final AgentSessionRegistry sessions;
    private record Applied(String sessionId, int seconds, long revision, Instant at, String error) {}
    private final Map<String, Applied> applied = new ConcurrentHashMap<>();

    public AgentMonitorSettingsService(ExpectedAgentRepository repository, AgentSessionRegistry sessions) {
        this.repository = repository;
        this.sessions = sessions;
    }

    @Transactional
    public void save(String agentId, String projectId, int seconds, long expectedRevision) {
        if (seconds < 1 || seconds > MAX_SECONDS || expectedRevision < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수집 주기는 1~86400초 범위의 정수여야 합니다.");
        final ExpectedAgent node = repository.findForMonitorUpdate(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "등록된 노드가 없습니다."));
        requireProject(node, projectId);
        if (node.getMonitorRevision() != expectedRevision)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 화면에서 설정이 변경됐습니다. 다시 불러오세요.");
        if (node.getMonitorIntervalSeconds() != seconds) {
            node.setMonitorIntervalSeconds(seconds);
            node.setMonitorRevision(node.getMonitorRevision() + 1);
            node.setUpdatedAt(new Date());
            repository.save(node);
            log.info("수집 주기 저장: 노드={} 주기={}초", agentId, seconds);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(String agentId, String projectId) {
        final ExpectedAgent node = requireNode(agentId, projectId);
        final WebSocketSession session = sessions.telemetrySession(agentId);
        final Applied report = applied.get(agentId);
        final boolean current = session != null && report != null && session.getId().equals(report.sessionId());
        final boolean matches = current && report.error() == null && report.revision() == node.getMonitorRevision()
                && report.seconds() == node.getMonitorIntervalSeconds();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_id", agentId);
        result.put("monitor_interval_seconds", node.getMonitorIntervalSeconds());
        result.put("revision", node.getMonitorRevision());
        result.put("collector_connected", session != null);
        result.put("reported_interval_seconds", current ? report.seconds() : null);
        result.put("applied_revision", current && report.error() == null ? report.revision() : null);
        result.put("applied_at", current && report.error() == null ? report.at().toString() : null);
        final boolean failed = current && report.revision() == node.getMonitorRevision() && report.error() != null;
        result.put("status", matches ? "applied" : failed ? "error" : session == null ? "offline" : "pending");
        result.put("error", failed ? report.error() : null);
        return result;
    }

    @Transactional(readOnly = true)
    public ObjectNode desired(String agentId) {
        return repository.findByAgentId(agentId).map(node -> {
            final ObjectNode body = JsonNodeFactory.instance.objectNode();
            body.put("monitor_interval_seconds", node.getMonitorIntervalSeconds());
            body.put("revision", node.getMonitorRevision());
            return body;
        }).orElse(null);
    }

    public boolean deliver(String agentId) {
        final ObjectNode desired = desired(agentId);
        if (desired == null) return false;
        desired.put("action", "set-monitor-interval");
        final Envelope command = Envelope.of(Envelope.Types.COMMAND);
        command.setAgent_id(agentId);
        command.setPayload(desired);
        return sessions.sendToTelemetry(agentId, command);
    }

    public void recordApplied(String agentId, WebSocketSession source, JsonNode body) {
        final String action = body.path("action").asString("");
        final boolean rejected = "monitor-interval-rejected".equals(action);
        if ((!"monitor-interval-applied".equals(action) && !rejected) || source != sessions.telemetrySession(agentId)) return;
        final JsonNode seconds = body.path("monitor_interval_seconds");
        final JsonNode revision = body.path("revision");
        if (!seconds.isIntegralNumber() || !seconds.canConvertToInt() ||
                !revision.isIntegralNumber() || !revision.canConvertToLong() ||
                seconds.asInt() < 1 || seconds.asInt() > MAX_SECONDS || revision.asLong() < 0) return;
        final ExpectedAgent node = repository.findByAgentId(agentId).orElse(null);
        if (node == null || node.getMonitorRevision() != revision.asLong()) return;
        if (rejected) {
            if (body.path("requested_interval_seconds").asInt(-1) != node.getMonitorIntervalSeconds()) return;
        } else if (node.getMonitorIntervalSeconds() != seconds.asInt()) return;
        applied.put(agentId, new Applied(source.getId(), seconds.asInt(), revision.asLong(), Instant.now(),
                rejected ? "Prober가 수집 주기를 저장하지 못했습니다. Prober 로그와 저장소 상태를 확인하세요." : null));
    }

    private ExpectedAgent requireNode(String agentId, String projectId) {
        final ExpectedAgent node = repository.findByAgentId(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "등록된 노드가 없습니다."));
        requireProject(node, projectId);
        return node;
    }

    private static void requireProject(ExpectedAgent node, String projectId) {
        if (projectId == null || !projectId.equals(node.getProjectKey()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이 프로젝트의 노드가 아닙니다.");
    }
}
