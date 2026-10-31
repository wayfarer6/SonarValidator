package org.sonar.sonarvalidator_backend.Model.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectRule;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Policy.PolicyViolation;
import org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine;

public final class ProjectMapper {

    private ProjectMapper() {
    }

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
        // ⚠️ DB Design v2.0 — 제어평면 대역은 프로젝트마다 다를 수 있습니다.
        //   화면이 "이 프로젝트의 관리망" 을 보여주려면 응답에 실어야 합니다.
        body.put("management_prefix", project.getManagementPrefix());
        body.put("management_server_ip", project.getManagementServerIp());
        body.put("management_server_port", project.getManagementServerPort());
        body.put("created_at", project.getCreatedAt());
        body.put("updated_at", project.getUpdatedAt());
        body.put("subnet_count", project.getSubnets().size());
        body.put("rule_count", project.getRules().size());
        body.put("subnets", toSubnetList(project));
        body.put("rules", toRuleList(project));
        return body;
    }

    public static Map<String, Object> toSummary(Project project) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", project.getProjectKey());
        body.put("name", project.getName());
        body.put("category", project.getCategory());
        body.put("description", project.getDescription());
        body.put("status", project.getStatus());
        body.put("management_prefix", project.getManagementPrefix());
        body.put("management_server_ip", project.getManagementServerIp());
        body.put("management_server_port", project.getManagementServerPort());
        body.put("created_at", project.getCreatedAt());
        body.put("updated_at", project.getUpdatedAt());
        body.put("subnet_count", project.getSubnets().size());
        body.put("rule_count", project.getRules().size());
        return body;
    }

    public static List<Map<String, Object>> toSubnetList(Project project) {
        final List<Map<String, Object>> result = new ArrayList<>();
        // ⚠️ 과거에 같은 subnet_id 로 저장된 행이 남아 있어도 응답에는 한 번만 넣습니다.
        //    (자동 증가 PK 만 다른 중복 행이 화면·Rule 드롭다운에 두 번 뜨는 것을 막습니다)
        final Set<String> seenIds = new LinkedHashSet<>();
        for (final ProjectSubnet subnet : project.getSubnets()) {
            if (subnet.getSubnetId() != null && !seenIds.add(subnet.getSubnetId())) {
                continue;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", subnet.getSubnetId());
            entry.put("cidr", subnet.getCidr());
            entry.put("vlan_id", subnet.getVlanId());
            entry.put("subnet_class", subnet.getZoneClass() == null ? null : subnet.getZoneClass().label());
            entry.put("name", subnet.getName());
            entry.put("network_role", subnet.getNetworkRole());
            entry.put("agent_id", subnet.getAgentId());
            entry.put("manually_edited", subnet.isManuallyEdited());
            // 허용 목록은 화면에서 그대로 편집됩니다. 순서를 유지해 돌려줍니다.
            entry.put("allowed_peers", subnet.allowedPeerList());
            result.add(entry);
        }
        return result;
    }

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

    public static Map<String, Object> toValidationResponse(SegmentationBddEngine.Report report) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("compliant", report.isCompliant());
        body.put("model_valid", report.isModelValid());
        body.put("input_errors", new ArrayList<>(report.getInputErrors()));
        body.put("analysis_scope", report.getAnalysisScope());
        body.put("data_plane_analyzed", report.isDataPlaneAnalyzed());
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
            entry.put("evidence_type", violation.evidenceType());
            entry.put("sampled_protocol", violation.sampledProtocol());
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
