package org.sonar.sonarvalidator_backend.View;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectRule;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicyRule;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;

public final class ProjectView {


    private ProjectView() {
    }

    public static List<PolicySubnet> subnetsFromDevice(String agentId, NeutralDeviceConfig config) {
        final List<PolicySubnet> result = new ArrayList<>();
        if (config == null) {
            return result;
        }
        final Set<String> seen = new LinkedHashSet<>();
        int sequence = 0;

        for (final Map.Entry<String, NeutralDeviceConfig.InterfaceConfig> entry
                : config.getInterfaces().entrySet()) {
            for (final String address : entry.getValue().getAddresses()) {
                final String cidr = normalizeToCidr(address);
                if (cidr == null || !seen.add(cidr)) {
                    continue;
                }
                sequence++;
                final PolicySubnet subnet = new PolicySubnet();
                subnet.setId("Subnet-" + String.format("%04d", sequence));
                subnet.setCidr(cidr);
                subnet.setZoneClass(null);
                subnet.setName(entry.getKey());
                subnet.setAgentId(agentId);
                // 자동 수집값임을 표시합니다. (UI 에 "확인 필요" 로 노출)
                subnet.setManuallyEdited(false);
                result.add(subnet);
            }
        }
        return result;
    }

    public static List<PolicyRule> discoveredRules(String agentId, NeutralDeviceConfig config) {
        final List<PolicyRule> result = new ArrayList<>();
        if (config == null) {
            return result;
        }
        int sequence = 0;
        for (final String ruleText : config.getFirewallRules()) {
            if (ruleText == null || ruleText.isBlank()) {
                continue;
            }
            sequence++;
            final PolicyRule rule = new PolicyRule();
            rule.setId("Rule-D" + String.format("%04d", sequence));
            rule.setProtocol("any");
            rule.setOrigin(PolicyRule.Origin.DISCOVERED);
            rule.setEnabled(false);
            rule.setNote("[" + agentId + "] " + trim(ruleText, 480));
            result.add(rule);
        }
        return result;
    }

    public static Map<String, Object> forEditing(Project project, AgentMessageRouterService router) {
        final Map<String, Object> view = new LinkedHashMap<>();
        view.put("project_id", project.getProjectKey());
        view.put("name", project.getName());
        view.put("category", project.getCategory());
        view.put("description", project.getDescription());
        view.put("status", project.getStatus());

        final List<Map<String, Object>> subnets = new ArrayList<>();
        final List<Map<String, Object>> rules = new ArrayList<>();
        final boolean empty = project.getSubnets().isEmpty() && project.getRules().isEmpty();

        if (empty) {
            // 프로젝트가 비어 있으므로 수집 결과를 편집 초안으로 제시합니다.
            final Map<String, PolicySubnet> drafts = new LinkedHashMap<>();
            final Set<String> cidrs = new LinkedHashSet<>();
            final List<PolicyRule> ruleDrafts = new ArrayList<>();

            router.allConfigs().forEach((agentId, config) -> {
                for (final PolicySubnet draft : subnetsFromDevice(agentId, config)) {
                    if (!cidrs.add(draft.getCidr())) {
                        continue;
                    }
                    draft.setId("Subnet-" + String.format("%04d", drafts.size() + 1));
                    drafts.put(draft.getId(), draft);
                }
                ruleDrafts.addAll(discoveredRules(agentId, config));
            });

            drafts.values().forEach(subnet -> subnets.add(toSubnetMap(subnet)));
            ruleDrafts.forEach(rule -> rules.add(toRuleMap(rule)));
            view.put("draft", true);
            view.put("draft_note", "수집된 설정에서 만든 초안입니다. 등급은 확인 전까지 Open 이며, "
                    + "수집된 방화벽 규칙은 검토 전까지 비활성 상태입니다.");
        } else {
            project.getSubnets().forEach(subnet -> subnets.add(toSubnetMap(subnet)));
            project.getRules().forEach(rule -> rules.add(toRuleMap(rule)));
            view.put("draft", false);
        }

        view.put("subnet_count", subnets.size());
        view.put("rule_count", rules.size());
        view.put("subnets", subnets);
        view.put("rules", rules);
        return view;
    }

    public static Map<String, Object> toSubnetMap(PolicySubnet subnet) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", subnet.getId());
        entry.put("cidr", subnet.getCidr());
        entry.put("subnet_class", subnet.getZoneClass() == null ? null : subnet.getZoneClass().label());
        entry.put("name", subnet.getName());
        entry.put("agent_id", subnet.getAgentId());
        entry.put("manually_edited", subnet.isManuallyEdited());
        return entry;
    }

    public static Map<String, Object> toRuleMap(PolicyRule rule) {
        return toRuleMap(rule.getId(), rule.getSource(), rule.getDestination(),
                rule.hasPort() ? rule.getPort() : null, rule.getProtocol(),
                rule.getOrigin() == null ? "MANUAL" : rule.getOrigin().name(),
                rule.isEnabled(), rule.getNote());
    }

    public static Map<String, Object> toSubnetMap(ProjectSubnet subnet) {
        return toSubnetMap(subnet.getSubnetId(), subnet.getCidr(), subnet.getZoneClass(),
                subnet.getName(), subnet.getAgentId(), subnet.isManuallyEdited());
    }

    public static Map<String, Object> toRuleMap(ProjectRule rule) {
        return toRuleMap(rule.getRuleId(), rule.getSource(), rule.getDestination(), rule.getPort(),
                rule.getProtocol(), rule.getOrigin() == null ? "MANUAL" : rule.getOrigin().name(),
                rule.isEnabled(), rule.getNote());
    }

    private static Map<String, Object> toSubnetMap(String id, String cidr, ZoneClass zoneClass,
                                                   String name, String agentId, boolean manuallyEdited) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("cidr", cidr);
        entry.put("subnet_class", zoneClass == null ? null : zoneClass.label());
        entry.put("name", name);
        entry.put("agent_id", agentId);
        entry.put("manually_edited", manuallyEdited);
        return entry;
    }

    private static Map<String, Object> toRuleMap(String id, String source, String target, Integer port,
                                                 String protocol, String origin, boolean enabled,
                                                 String note) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("src", source);
        entry.put("dst", target);
        entry.put("port", port);
        entry.put("protocol", protocol);
        entry.put("origin", origin);
        entry.put("enabled", enabled);
        entry.put("note", note);
        return entry;
    }

    private static String normalizeToCidr(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        final String trimmed = address.trim();
        final String withPrefix = trimmed.contains("/") ? trimmed : trimmed + "/32";
        try {
            return PolicySubnet.normalizeCidr(withPrefix);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        final String compact = value.trim();
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength);
    }
}
