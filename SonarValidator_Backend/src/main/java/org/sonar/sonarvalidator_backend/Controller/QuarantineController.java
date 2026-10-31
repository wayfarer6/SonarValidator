package org.sonar.sonarvalidator_backend.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.QuarantineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quarantine")
public class QuarantineController {

    private static final Logger log = LoggerFactory.getLogger(QuarantineController.class);

    private final QuarantineService quarantineService;

    public QuarantineController(QuarantineService quarantineService) {
        this.quarantineService = quarantineService;
    }

    @PostMapping("/{agentId}")
    public Map<String, Object> isolate(@PathVariable String agentId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        final Map<String, Object> payload = body == null ? Map.of() : body;
        final String reason = stringValue(payload, "reason");
        final String projectKey = stringValue(payload, "project_id");
        final String requestedBy = stringValue(payload, "requested_by");
        final String targetCidr = stringValue(payload, "target_cidr");
        final Integer nodeId = integerValue(payload, "node_id");

        log.info("quarantine request for agent={} node={} target={} by={}",
                agentId, nodeId, targetCidr, requestedBy);
        return quarantineService.isolate(agentId, nodeId, projectKey, reason, requestedBy, targetCidr);
    }

    @PostMapping("/by-node")
    public Map<String, Object> isolateByNode(
            @RequestParam("node_id") Integer nodeId,
            @RequestParam(value = "project_id", required = false) String projectId,
            @RequestParam(value = "target_cidr", required = false) String targetCidr,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "requested_by", required = false) String requestedBy) {

        log.info("quarantine request for node={} target={} by={}", nodeId, targetCidr, requestedBy);
        return quarantineService.isolateByNode(nodeId, projectId, reason, requestedBy, targetCidr);
    }

    @DeleteMapping("/{agentId}")
    public Map<String, Object> release(@PathVariable String agentId,
                                       @RequestParam(value = "node_id", required = false) Integer nodeId,
                           @RequestParam(value = "released_by", required = false) String releasedBy,
                           @RequestParam(value = "target_cidr", required = false) String targetCidr) {
        log.info("quarantine release request for agent={} node={} target={} by={}",
            agentId, nodeId, targetCidr, releasedBy);
        return quarantineService.release(agentId, nodeId, releasedBy, targetCidr);
    }

    @DeleteMapping("/by-node")
    public Map<String, Object> releaseByNode(
            @RequestParam("node_id") Integer nodeId,
            @RequestParam(value = "released_by", required = false) String releasedBy) {
        log.info("quarantine release request for node={} by={}", nodeId, releasedBy);
        return quarantineService.release(null, nodeId, releasedBy);
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "project_id", required = false) String projectId) {
        final List<Map<String, Object>> active = quarantineService.listActive(projectId);
        final Set<String> ids = quarantineService.quarantinedAgentIds();
        final Set<Integer> nodeIds = quarantineService.quarantinedNodeIds();

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("total", active.size());
        body.put("agent_ids", ids);
        // ⚠️ DB Design v1.5 — Agent 없는 장비(방화벽)를 화면이 표시하려면
        //   노드 번호도 함께 알아야 합니다.
        body.put("node_ids", nodeIds);
        body.put("quarantined", active);
        return body;
    }

    @GetMapping("/{agentId}")
    public Map<String, Object> status(@PathVariable String agentId,
                                      @RequestParam(value = "node_id", required = false) Integer nodeId) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", agentId);
        body.put("node_id", nodeId);
        body.put("quarantined", quarantineService.isQuarantined(agentId, nodeId));
        body.put("history", quarantineService.history(agentId));
        return body;
    }

    private static String stringValue(Map<String, Object> map, String key) {
        final Object value = map.get(key);
        if (value == null) {
            return null;
        }
        final String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static Integer integerValue(Map<String, Object> map, String key) {
        final Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
