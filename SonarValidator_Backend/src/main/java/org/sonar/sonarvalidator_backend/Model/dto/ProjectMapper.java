package org.sonar.sonarvalidator_backend.Model.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectRule;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Policy.PolicyViolation;
import org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine;

/**
 * 프로젝트를 프론트엔드가 바로 쓸 수 있는 JSON 형태로 바꿉니다.
 *
 * <h2>왜 변환 계층을 따로 두는가</h2>
 * <p>엔티티를 그대로 반환하면 (1) 지연 로딩 프록시가 순환 참조를 만들고,
 * (2) 내부 컬럼명이 API 계약이 되어 스키마 변경이 곧 API 파괴가 됩니다.
 * 변환을 한 곳에 모아 두면 노출 필드를 통제할 수 있습니다.
 *
 * <p>모든 키는 snake_case 이고, 등급은 프론트엔드와 같은
 * {@code Confidential}/{@code Sensitive}/{@code Open} 표기를 씁니다.
 */
public final class ProjectMapper {

    private ProjectMapper() {
    }

    /**
     * 프로젝트 1건을 API 응답 맵으로 바꿉니다.
     *
     * @param project 프로젝트 (null 이면 null 반환)
     * @return 응답 맵
     */
    public static Map<String, Object> toResponse(Project project) {
        if (project == null) {
            return null;
        }
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", project.getProjectKey());
        body.put("name", project.getName());
        body.put("category", project.getCategory());
        body.put("description", project.getDescription());
        body.put("status", project.getStatus());
        body.put("created_at", project.getCreatedAt());
        body.put("updated_at", project.getUpdatedAt());
        body.put("subnet_count", project.getSubnets().size());
        body.put("rule_count", project.getRules().size());
        body.put("subnets", toSubnetList(project));
        body.put("rules", toRuleList(project));
        return body;
    }

    /**
     * 프로젝트 1건을 목록용 요약으로 바꿉니다. (서브넷/규칙 본문 제외)
     *
     * @param project 프로젝트
     * @return 요약 맵
     */
    public static Map<String, Object> toSummary(Project project) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", project.getProjectKey());
        body.put("name", project.getName());
        body.put("category", project.getCategory());
        body.put("description", project.getDescription());
        body.put("status", project.getStatus());
        body.put("created_at", project.getCreatedAt());
        body.put("updated_at", project.getUpdatedAt());
        body.put("subnet_count", project.getSubnets().size());
        body.put("rule_count", project.getRules().size());
        return body;
    }

    /**
     * 서브넷 목록을 응답 형태로 바꿉니다.
     *
     * @param project 프로젝트
     * @return 서브넷 맵 목록
     */
    public static List<Map<String, Object>> toSubnetList(Project project) {
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final ProjectSubnet subnet : project.getSubnets()) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", subnet.getSubnetId());
            entry.put("cidr", subnet.getCidr());
            entry.put("subnet_class", subnet.getZoneClass() == null ? null : subnet.getZoneClass().label());
            entry.put("name", subnet.getName());
            entry.put("agent_id", subnet.getAgentId());
            entry.put("manually_edited", subnet.isManuallyEdited());
            result.add(entry);
        }
        return result;
    }

    /**
     * 규칙 목록을 응답 형태로 바꿉니다.
     *
     * @param project 프로젝트
     * @return 규칙 맵 목록
     */
    public static List<Map<String, Object>> toRuleList(Project project) {
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final ProjectRule rule : project.getRules()) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", rule.getRuleId());
            entry.put("src", rule.getSource());
            entry.put("dst", rule.getDestination());
            entry.put("port", rule.getPort());
            entry.put("protocol", rule.getProtocol());
            entry.put("origin", rule.getOrigin() == null ? "MANUAL" : rule.getOrigin().name());
            entry.put("enabled", rule.isEnabled());
            entry.put("note", rule.getNote());
            result.add(entry);
        }
        return result;
    }

    /**
     * 검증 보고서를 API 응답 맵으로 바꿉니다.
     *
     * <p>{@code violations} 는 UI 가 표로 그릴 수 있게 평평한 목록으로 두고,
     * {@code violated_rule_ids} 는 규칙 행을 강조하는 데 씁니다.
     *
     * @param report 검증 보고서
     * @return 응답 맵
     */
    public static Map<String, Object> toValidationResponse(SegmentationBddEngine.Report report) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("compliant", report.isCompliant());
        body.put("rule_count", report.getRuleCount());
        body.put("subnet_count", report.getSubnetCount());
        body.put("violation_count", report.getViolationCount());
        body.put("messages", new ArrayList<>(report.getMessages()));
        body.put("violated_rule_ids", new ArrayList<>(report.getViolatedRuleIds()));
        body.put("metrics", new LinkedHashMap<>(report.getMetrics()));

        final List<Map<String, Object>> violations = new ArrayList<>();
        for (final PolicyViolation violation : report.getViolations()) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rule_id", violation.ruleId());
            entry.put("src_subnet", violation.sourceSubnetId());
            entry.put("dst_subnet", violation.targetSubnetId());
            entry.put("src_class", violation.sourceZone() == null ? null : violation.sourceZone().label());
            entry.put("dst_class", violation.targetZone() == null ? null : violation.targetZone().label());
            entry.put("reason", violation.reason());
            entry.put("severity", violation.severity().name());
            entry.put("sampled_packet", violation.sampledPacket());
            entry.put("sampled_src_ip", violation.sampledSourceIp());
            entry.put("sampled_dst_ip", violation.sampledTargetIp());
            entry.put("sampled_port", violation.sampledPort() == PacketVariables.ANY_PORT
                    ? null
                    : violation.sampledPort());
            violations.add(entry);
        }
        body.put("violations", violations);
        return body;
    }
}
