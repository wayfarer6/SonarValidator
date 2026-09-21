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

/**
 * 정책 관리 화면이 쓰는 조회/적용 API 입니다.
 *
 * <h2>이 컨트롤러가 생긴 배경</h2>
 * <p>기존 {@code PolicyManagement} 는
 * {@code public boolean isPolicyViolated(String device_id)} 만 있는 스텁이었고
 * 판정 로직이 없었습니다. 이제 판정은 {@link SegmentationBddEngine} 이 하므로,
 * 이 클래스는 <b>화면이 필요로 하는 형태로 결과를 가공</b> 하는 역할만 합니다.
 *
 * <h2>제공하는 화면 기능</h2>
 * <ol>
 *   <li><b>위반 현황</b>: 프로젝트별 위반 목록 + 반례 패킷
 *       ({@code GET /api/v1/policy/violations/{projectId}})</li>
 *   <li><b>금지 조합 안내</b>: 등급을 건너뛰는 쌍 목록</li>
 *   <li><b>정책 푸시</b>: 검증을 통과한 정책만 장치로 내려보냄</li>
 * </ol>
 *
 * <h2>푸시가 검증을 통과해야 하는 이유</h2>
 * <p>위반 정책을 장치에 밀어 넣으면 실제로 망분리가 깨집니다. 그래서
 * {@link #push} 는 위반이 하나라도 있으면 <b>거부</b>하고, 어떤 규칙 때문인지
 * 함께 돌려줍니다. ({@code force=true} 로 우회할 수 있지만 경고 로그가 남습니다.)
 */
@RestController
@RequestMapping("/api/v1/policy")
public class PolicyManagement {

    private static final Logger log = LoggerFactory.getLogger(PolicyManagement.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ProjectService projectService;
    private final AgentSessionRegistry registry;

    /**
     * 알림 기록기입니다.
     *
     * <p>푸시는 "지금 장치에 반영했는가" 를 답하는 작업입니다. 성공/실패 모두
     * 운영자가 나중에 확인할 가치가 있으므로 알림으로 남깁니다.
     * 특히 <b>푸시 거부와 전달 0대</b>는 화면을 닫으면 사라지므로 반드시 기록합니다.
     */
    private final NotificationService notificationService;

    /**
     * @param projectService      프로젝트 서비스 (검증 엔진 접근)
     * @param registry            Agent 세션 레지스트리 (푸시 대상)
     * @param notificationService 알림 서비스
     */
    public PolicyManagement(ProjectService projectService,
                            AgentSessionRegistry registry,
                            NotificationService notificationService) {
        this.projectService = projectService;
        this.registry = registry;
        this.notificationService = notificationService;
    }

    /**
     * 프로젝트의 정책 위반 현황을 반환합니다.
     *
     * <p>응답에는 위반 목록, 규칙별 그룹, 반례 패킷, BDD 진단 지표가 들어갑니다.
     * 프론트엔드는 {@code by_severity} 로 요약 카드를, {@code violations} 로
     * 표를 그립니다.
     *
     * @param projectId 프로젝트 키
     * @return 위반 현황
     */
    @GetMapping("/violations/{projectId}")
    public Map<String, Object> violations(@PathVariable String projectId) {
        final Project project = projectService.getByKey(projectId);
        final SegmentationBddEngine.Report report = projectService.validateStored(projectId);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("project_name", project.getName());
        body.put("compliant", report.isCompliant());
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

    /**
     * 프로젝트의 금지 서브넷 쌍 목록을 반환합니다.
     *
     * <p>편집기가 규칙을 만들기 <b>전에</b> "이 조합은 애초에 안 됩니다" 를
     * 안내하는 정적 정보입니다.
     *
     * @param projectId 프로젝트 키
     * @return 금지 쌍 + 등급 설명
     */
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

    /**
     * 검증을 통과한 정책을 소속 장치로 푸시합니다.
     *
     * <p>서브넷을 출발지로 하는 활성 규칙을 모아 장치별 {@code command} 봉투로
     * 만들어 보냅니다. 어떤 장치에 몇 건이 갔는지 응답에 담아 추적이 가능합니다.
     *
     * @param projectId 프로젝트 키
     * @param force     {@code true} 면 위반이 있어도 강제 전송
     * @return 전송 결과
     */
    @PostMapping("/push/{projectId}")
    public Map<String, Object> push(@PathVariable String projectId,
                                    @RequestParam(value = "force", defaultValue = "false") boolean force) {
        final Project project = projectService.getByKey(projectId);
        final SegmentationBddEngine.Report report = projectService.validateStored(projectId);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);

        if (!report.isCompliant() && !force) {
            body.put("pushed", false);
            body.put("reason", "policy validation failed; fix violations or pass force=true");
            body.put("violation_count", report.getViolationCount());
            body.put("violated_rule_ids", new ArrayList<>(report.getViolatedRuleIds()));
            log.warn("policy push rejected for project={} violations={}",
                    projectId, report.getViolationCount());

            // 거부는 운영자가 즉시 알아야 하는 사건입니다. 그리고 같은 위반이
            // 반복되면 합쳐야 목록이 의미를 유지합니다. 그래서 dedupeKey 를
            // 프로젝트+위반건수로 둡니다. (건수가 바뀌면 다른 문제로 봅니다)
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
        int sent = 0;

        for (final var subnet : project.getSubnets()) {
            final String agentId = subnet.getAgentId();
            if (agentId == null || agentId.isBlank()) {
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
        body.put("deliveries", deliveries);
        log.info("policy pushed for project={} delivered={}/{}",
                projectId, sent, deliveries.size());

        // 전달 결과를 세 갈래로 나눠 남깁니다. "푸시 완료" 라고 뭉뚱그리면
        // 대상이 하나도 없었던 경우까지 성공으로 보여 운영자가 오해합니다.
        //   (A) 대상 없음   — 서브넷에 agent_id 가 없어 보낼 곳이 없음
        //   (B) 전달 실패   — 대상은 있는데 0대 전송 (프로버가 꺼져 있음)
        //   (C) 전달 성공   — 일부 또는 전부 전송
        if (deliveries.isEmpty()) {
            notificationService.notifyQuietly(
                    "POLICY",
                    "warning",
                    "정책 푸시 대상 없음: " + project.getName(),
                    "서브넷에 연결된 Agent 가 없어 아무 장치에도 전달되지 않았습니다. "
                            + "Agent 배포 후 서브넷에 매핑하세요.",
                    projectId,
                    null,
                    "system",
                    "/project/editor/" + projectId,
                    "policy-push-no-target:" + projectId);
        } else if (sent == 0) {
            notificationService.notifyQuietly(
                    "POLICY",
                    "warning",
                    "정책 푸시 실패: " + project.getName(),
                    "대상 " + deliveries.size() + "대 중 0대에 전달되었습니다. "
                            + "장치의 프로버가 연결되어 있는지 확인하세요.",
                    projectId,
                    null,
                    "system",
                    "/project/editor/" + projectId,
                    "policy-push-none-delivered:" + projectId);
        } else {
            // 강제 전송은 위반 상태로 내려간 것이므로 심각도를 올립니다.
            // (성공으로만 보이면 "위반을 고쳤다" 는 오해를 낳습니다)
            final boolean forced = force && !report.isCompliant();
            notificationService.notifyQuietly(
                    "POLICY",
                    forced ? "warning" : "info",
                    "정책 푸시 " + (forced ? "강제 완료: " : "완료: ") + project.getName(),
                    "대상 " + deliveries.size() + "대 중 " + sent + "대에 전달되었습니다."
                            + (forced ? " (망분리 위반이 남은 상태로 강제 전송됨)" : ""),
                    projectId,
                    null,
                    "system",
                    "/project/editor/" + projectId,
                    null);
        }
        return body;
    }

    /**
     * 위반 한 건을 응답 맵으로 바꿉니다.
     *
     * @param violation 위반
     * @return 응답 맵
     */
    private Map<String, Object> toViolationMap(PolicyViolation violation) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("rule_id", violation.ruleId());
        entry.put("src_subnet", violation.sourceSubnetId());
        entry.put("dst_subnet", violation.targetSubnetId());
        entry.put("src_class", violation.sourceZone() == null ? null : violation.sourceZone().label());
        entry.put("dst_class", violation.targetZone() == null ? null : violation.targetZone().label());
        entry.put("reason", violation.reason());
        entry.put("severity", violation.severity().name());
        entry.put("sampled_packet", violation.sampledPacket());
        entry.put("sampled_port", violation.sampledPort() == PacketVariables.ANY_PORT
                ? null
                : violation.sampledPort());
        return entry;
    }
}
