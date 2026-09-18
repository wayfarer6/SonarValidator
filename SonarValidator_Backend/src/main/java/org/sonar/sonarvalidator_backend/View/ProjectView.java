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

/**
 * 프로젝트의 <b>자동 수집 설정</b>을 프로젝트 편집기 입력 형태로 변환합니다.
 *
 * <h2>역할</h2>
 * <p>Agent 가 올린 텔레메트리에는 서브넷 등급도, 연결 규칙도 없습니다.
 * 그대로는 편집기에 띄울 수 없으므로 <b>수집 결과를 편집 가능한 초안</b> 으로
 * 바꿔 주는 계층이 필요합니다. 이 클래스가 그 변환을 담당합니다.
 *
 * <pre>
 *   NeutralDeviceConfig (벤더 중립 설정)
 *        │  ProjectView.fromDiscovered(...)
 *        ▼
 *   편집기 입력 (서브넷 초안 + 규칙 초안)
 * </pre>
 *
 * <h2>등급을 추측하지 않는 원칙</h2>
 * <p>주소 대역만 보고 등급(Confidential/Sensitive/Open)을 정하는 것은
 * 위험합니다. 잘못된 등급은 잘못된 경보를 만들고, 운영자가 경보를 무시하게
 * 됩니다. 그래서 이 클래스는 등급을 <b>Open 으로 두고</b>
 * {@code manually_edited=false} 로 표시해 "아직 사람이 확인하지 않았다" 를
 * 드러냅니다. 등급 결정은 항상 사람이 합니다.
 */
public final class ProjectView {

    /** 초안 서브넷의 기본 등급. 사람이 확인하기 전까지의 값입니다. */
    private static final ZoneClass DEFAULT_ZONE = ZoneClass.OPEN;

    private ProjectView() {
    }

    /**
     * 수집된 장치 설정에서 편집기용 서브넷 초안을 만듭니다.
     *
     * <p>장치의 인터페이스 주소를 CIDR 로 정규화하고 중복을 제거합니다.
     * 주소가 없거나 파싱할 수 없는 인터페이스는 건너뜁니다.
     *
     * @param agentId 장치 식별자
     * @param config  중립 설정
     * @return 서브넷 초안 목록
     */
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
                subnet.setZoneClass(DEFAULT_ZONE);
                subnet.setName(entry.getKey());
                subnet.setAgentId(agentId);
                // 자동 수집값임을 표시합니다. (UI 에 "확인 필요" 로 노출)
                subnet.setManuallyEdited(false);
                result.add(subnet);
            }
        }
        return result;
    }

    /**
     * 수집된 방화벽 규칙에서 편집기용 규칙 초안을 만듭니다.
     *
     * <p>방화벽 규칙 문자열은 벤더마다 형태가 달라 완전 파싱이 어렵습니다.
     * 여기서는 <b>사람이 검토할 출발점</b> 만 제공합니다. 파싱에 실패한 줄도
     * 버리지 않고 원문을 메모에 남겨 편집기에서 보이게 합니다.
     * (조용히 사라지면 망분리 누락을 못 찾습니다.)
     *
     * <p>초안은 {@code enabled=false} 입니다. 서브넷 등급을 확인하지 않은 상태로
     * 위반을 보고하면 오탐이 쏟아져 검증 자체가 무의미해지기 때문입니다.
     *
     * @param agentId 장치 식별자
     * @param config  중립 설정
     * @return 규칙 초안 목록
     */
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

    /**
     * 프로젝트 + 수집 설정을 하나의 편집기 뷰로 묶습니다.
     *
     * <p>프로젝트에 이미 서브넷이 있으면 그것을 <b>우선</b>하고, 비어 있을 때만
     * 수집 결과로 초안을 만듭니다. 운영자가 고친 등급을 자동 수집이 덮어쓰면
     * 편집 기능의 의미가 없어지기 때문입니다.
     *
     * <p>초안은 저장 전 값이므로 {@link PolicySubnet} /
     * {@link PolicyRule} 도메인 객체로 만들어 변환합니다. 아직 저장되지 않은
     * 값이 영속 컨텍스트에 섞이지 않게 하기 위함입니다.
     *
     * @param project 프로젝트
     * @param router  수집 설정 보관소
     * @return 편집기 뷰
     */
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

    /**
     * 도메인 서브넷을 편집기 맵으로 바꿉니다.
     *
     * @param subnet 서브넷
     * @return 편집기 맵
     */
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

    /**
     * 도메인 규칙을 편집기 맵으로 바꿉니다.
     *
     * @param rule 규칙
     * @return 편집기 맵
     */
    public static Map<String, Object> toRuleMap(PolicyRule rule) {
        return toRuleMap(rule.getId(), rule.getSource(), rule.getDestination(),
                rule.hasPort() ? rule.getPort() : null, rule.getProtocol(),
                rule.getOrigin() == null ? "MANUAL" : rule.getOrigin().name(),
                rule.isEnabled(), rule.getNote());
    }

    /**
     * 서브넷 엔티티를 편집기 맵으로 바꿉니다.
     *
     * @param subnet 서브넷 엔티티
     * @return 편집기 맵
     */
    public static Map<String, Object> toSubnetMap(ProjectSubnet subnet) {
        return toSubnetMap(subnet.getSubnetId(), subnet.getCidr(), subnet.getZoneClass(),
                subnet.getName(), subnet.getAgentId(), subnet.isManuallyEdited());
    }

    /**
     * 규칙 엔티티를 편집기 맵으로 바꿉니다.
     *
     * @param rule 규칙 엔티티
     * @return 편집기 맵
     */
    public static Map<String, Object> toRuleMap(ProjectRule rule) {
        return toRuleMap(rule.getRuleId(), rule.getSource(), rule.getDestination(), rule.getPort(),
                rule.getProtocol(), rule.getOrigin() == null ? "MANUAL" : rule.getOrigin().name(),
                rule.isEnabled(), rule.getNote());
    }

    /**
     * 서브넷 맵을 만듭니다. 두 표현(도메인/엔티티)의 공통 조립부입니다.
     *
     * @param id             식별자
     * @param cidr           대역
     * @param zoneClass      등급
     * @param name           표시 이름
     * @param agentId        출처 장치
     * @param manuallyEdited 수동 편집 여부
     * @return 편집기 맵
     */
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

    /**
     * 규칙 맵을 만듭니다. 두 표현(도메인/엔티티)의 공통 조립부입니다.
     *
     * @param id        식별자
     * @param source    출발 서브넷
     * @param target    도착 서브넷
     * @param port      포트 (미지정이면 null)
     * @param protocol  프로토콜
     * @param origin    출처
     * @param enabled   검증 포함 여부
     * @param note      메모
     * @return 편집기 맵
     */
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

    /**
     * {@code 10.10.131.1/24} 형태를 네트워크 주소 기준 CIDR 로 정규화합니다.
     *
     * @param address 주소 (CIDR 또는 단일 IP)
     * @return 정규화된 CIDR, 파싱 불가면 {@code null}
     */
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

    /** 문자열을 최대 길이로 자릅니다. */
    private static String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        final String compact = value.trim();
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength);
    }
}
