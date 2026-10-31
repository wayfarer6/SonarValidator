package org.sonar.sonarvalidator_backend.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Policy.PolicyRule;
import org.sonar.sonarvalidator_backend.Policy.PolicyViolation;
import org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.NotificationService;
import org.sonar.sonarvalidator_backend.Service.ProjectService;
import org.sonar.sonarvalidator_backend.Service.QuarantineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/policy")
public class PolicyManagement {

    private static final Logger log = LoggerFactory.getLogger(PolicyManagement.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ProjectService projectService;
    private final AgentSessionRegistry registry;

    private final QuarantineService quarantineService;

    private final NotificationService notificationService;

    private final org.sonar.sonarvalidator_backend.Service.policy.PolicyPushNotifier pushNotifier;

    public PolicyManagement(ProjectService projectService,
                            AgentSessionRegistry registry,
                            QuarantineService quarantineService,
                            NotificationService notificationService,
                            org.sonar.sonarvalidator_backend.Service.policy.PolicyPushNotifier pushNotifier) {
        this.projectService = projectService;
        this.registry = registry;
        this.quarantineService = quarantineService;
        this.notificationService = notificationService;
        this.pushNotifier = pushNotifier;
    }

    @GetMapping("/violations/{projectId}")
    public Map<String, Object> violations(@PathVariable String projectId) {
        final Project project = projectService.getByKey(projectId);
        final SegmentationBddEngine.Report report = projectService.validateStored(projectId);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("project_name", project.getName());
        body.put("compliant", report.isCompliant());
        body.put("model_valid", report.isModelValid());
        body.put("input_errors", new ArrayList<>(report.getInputErrors()));
        body.put("analysis_scope", report.getAnalysisScope());
        body.put("data_plane_analyzed", report.isDataPlaneAnalyzed());
        body.put("rule_count", report.getRuleCount());
        body.put("subnet_count", report.getSubnetCount());
        body.put("violation_count", report.getViolationCount());
        body.put("messages", new ArrayList<>(report.getMessages()));
        body.put("metrics", new LinkedHashMap<>(report.getMetrics()));

        final Map<String, Integer> bySeverity = new LinkedHashMap<>();
        bySeverity.put("CRITICAL", 0);
        bySeverity.put("MAJOR", 0);
        bySeverity.put("MINOR", 0);
        final List<Map<String, Object>> violations = new ArrayList<>();
        for (final PolicyViolation violation : report.getViolations()) {
            bySeverity.merge(violation.severity().name(), 1, Integer::sum);
            violations.add(toViolationMap(violation));
        }
        body.put("by_severity", bySeverity);
        body.put("violations", violations);

        final List<Map<String, Object>> byRule = new ArrayList<>();
        PolicyViolation.groupByRule(report.getViolations()).forEach((ruleId, items) -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rule_id", ruleId);
            entry.put("count", items.size());
            final List<String> reasons = new ArrayList<>();
            for (final PolicyViolation item : items) {
                reasons.add(item.reason());
            }
            entry.put("reasons", reasons);
            byRule.add(entry);
        });
        body.put("by_rule", byRule);
        return body;
    }

    @GetMapping("/forbidden-pairs/{projectId}")
    public Map<String, Object> forbiddenPairs(@PathVariable String projectId) {
        final Project project = projectService.getByKey(projectId);
        final List<Map<String, Object>> pairs = new ArrayList<>();
        for (final String[] pair : projectService.forbiddenPairs(project.toPolicySubnets())) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("src", pair[0]);
            entry.put("dst", pair[1]);
            pairs.add(entry);
        }
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("total", pairs.size());
        body.put("pairs", pairs);
        body.put("rule", "등급 차이가 2 이상인 서브넷끼리는 직접 연결할 수 없습니다. "
                + "(Confidential <-> Open 금지)");
        return body;
    }

    @PostMapping("/push/{projectId}")
    public Map<String, Object> push(@PathVariable String projectId,
                                    @RequestParam(value = "force", defaultValue = "false") boolean force) {
        final Project project = projectService.getByKey(projectId);
        final SegmentationBddEngine.Report report = projectService.validateStored(projectId);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);

        if (!report.isModelValid()) {
            body.put("pushed", false);
            body.put("reason", "invalid policy model; force cannot bypass input errors");
            body.put("model_valid", false);
            body.put("input_errors", new ArrayList<>(report.getInputErrors()));
            return body;
        }

        if (!report.isCompliant() && !force) {
            body.put("pushed", false);
            body.put("reason", "policy validation failed; fix violations or pass force=true");
            body.put("violation_count", report.getViolationCount());
            body.put("violated_rule_ids", new ArrayList<>(report.getViolatedRuleIds()));
            log.warn("policy push rejected for project={} violations={}",
                    projectId, report.getViolationCount());




            notificationService.notifyQuietly(
                    "POLICY",
                    "critical",
                    "정책 푸시 거부: " + project.getName(),
                    "망분리 위반 " + report.getViolationCount()
                            + "건으로 푸시가 차단되었습니다. 위반을 수정하거나 강제 전송하세요.",
                    projectId,
                    null,
                    "system",
                    "/project/editor/" + projectId,
                    "policy-push-rejected:" + projectId + ":" + report.getViolationCount());
            return body;
        }

        final List<PolicyRule> rules = project.toPolicyRules();
        final List<Map<String, Object>> deliveries = new ArrayList<>();

        // 격리된 장치는 한 번만 조회해 집합으로 둡니다. (서브넷 수만큼 DB 를 치지 않기)
        final var quarantined = quarantineService.quarantinedAgentIds();
        final List<String> skipped = new ArrayList<>();
        int sent = 0;

        for (final var subnet : project.getSubnets()) {
            final String agentId = subnet.getAgentId();
            if (agentId == null || agentId.isBlank()) {
                continue;
            }



            if (quarantined.contains(agentId)) {
                skipped.add(agentId);
                log.info("policy push skipped quarantined agent={} project={}", agentId, projectId);
                continue;
            }
            final ObjectNode payload = JSON.objectNode();
            payload.put("project_id", projectId);
            payload.put("subnet_id", subnet.getSubnetId());
            payload.put("cidr", subnet.getCidr());
            payload.put("subnet_class", subnet.getZoneClass() == null
                    ? null
                    : subnet.getZoneClass().label());

            final var ruleNodes = payload.putArray("rules");
            for (final PolicyRule rule : rules) {
                if (!rule.isEnabled() || !subnet.getSubnetId().equals(rule.getSource())) {
                    continue;
                }
                final ObjectNode ruleNode = ruleNodes.addObject();
                ruleNode.put("rule_id", rule.getId());
                ruleNode.put("destination", rule.getDestination());
                ruleNode.put("protocol", rule.getProtocol());
                if (rule.hasPort() && rule.getPort() != PacketVariables.ANY_PORT) {
                    ruleNode.put("port", rule.getPort());
                }
            }

            final Envelope envelope = Envelope.of(Envelope.Types.COMMAND);
            envelope.setAgent_id(agentId);
            envelope.setPayload(payload);
            final boolean delivered = registry.sendTo(agentId, envelope);
            if (delivered) {
                sent++;
            }

            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("subnet_id", subnet.getSubnetId());
            entry.put("rule_count", ruleNodes.size());
            entry.put("delivered", delivered);
            deliveries.add(entry);
        }

        body.put("pushed", true);
        body.put("forced", force && !report.isCompliant());
        body.put("delivered", sent);
        body.put("targets", deliveries.size());
        body.put("skipped_quarantined", skipped);
        body.put("deliveries", deliveries);
        log.info("policy pushed for project={} delivered={}/{} skipped_quarantined={}",
                projectId, sent, deliveries.size(), skipped.size());

        final org.sonar.sonarvalidator_backend.Service.policy.PushOutcomeStrategy.PushOutcome outcome =
                new org.sonar.sonarvalidator_backend.Service.policy.PushOutcomeStrategy.PushOutcome(
                        projectId,
                        project.getName(),
                        deliveries.size(),
                        sent,
                        skipped,
                        report.getViolationCount(),
                        force && !report.isCompliant());


        // 응답만 보고 판단할 수 있어야 합니다.
        body.put("outcome", pushNotifier.nameOf(outcome));

        for (final var notice : pushNotifier.classify(outcome)) {
            notificationService.notifyQuietly(
                    notice.category().value(),
                    notice.severity().value(),
                    notice.title(),
                    notice.message(),
                    projectId,
                    notice.agentId(),
                    notice.source(),
                    notice.link(),
                    notice.dedupeKey());
        }
        return body;
    }

    private Map<String, Object> toViolationMap(PolicyViolation violation) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("rule_id", violation.ruleId());
        entry.put("src_subnet", violation.sourceSubnetId());
        entry.put("dst_subnet", violation.targetSubnetId());
        entry.put("src_class", violation.sourceZone() == null ? null : violation.sourceZone().label());
        entry.put("dst_class", violation.targetZone() == null ? null : violation.targetZone().label());
        entry.put("reason", violation.reason());
        entry.put("severity", violation.severity().name());
        entry.put("evidence_type", violation.evidenceType());
        entry.put("sampled_protocol", violation.sampledProtocol());
        entry.put("sampled_packet", violation.sampledPacket());
        entry.put("sampled_src_ip", violation.sampledSourceIp());
        entry.put("sampled_dst_ip", violation.sampledTargetIp());
        entry.put("sampled_port", violation.sampledPort() == PacketVariables.ANY_PORT
                ? null
                : violation.sampledPort());
        return entry;
    }
}
