package org.sonar.sonarvalidator_backend.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.ProjectService;
import org.sonar.sonarvalidator_backend.Service.QuarantineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 네트워크 토폴로지 조회 API 입니다.
 *
 * <h2>두 가지 정보를 합쳐 줍니다</h2>
 * <ol>
 *   <li><b>수집된 실제 장치</b>: Agent 가 보고한 중립 설정
 *       ({@link NeutralDeviceConfig}) 의 인터페이스/VLAN 정보</li>
 *   <li><b>프로젝트가 정의한 서브넷</b>: 등급이 지정된 대역</li>
 * </ol>
 *
 * <p>이 둘을 합치는 것이 이 컨트롤러의 존재 이유입니다. 프론트엔드는
 * "실제로 무엇이 연결되어 있는가" 와 "무엇이 허용되어야 하는가" 를 비교해야
 * 망분리 상태를 판단할 수 있습니다.
 *
 * <h2>주소 → 등급 자동 판정</h2>
 * <p>장치 인터페이스 주소가 프로젝트 서브넷 대역에 속하면 그 등급을 붙여
 * 돌려줍니다. 덕분에 UI 가 "이 인터페이스는 Confidential 존" 처럼 색을
 * 칠할 수 있습니다. 어느 대역에도 속하지 않으면 등급은 {@code null} 입니다.
 * (추측하지 않고 비워 두는 것이 원칙입니다 — 잘못된 등급은 잘못된 경보를 만듭니다.)
 */
@RestController
@RequestMapping("/api/v1/network")
public class NetworkTopologyController {

    private static final Logger log = LoggerFactory.getLogger(NetworkTopologyController.class);

    private final ProjectService projectService;
    private final AgentMessageRouterService router;
    private final AgentSessionRegistry registry;

    /**
     * 격리 상태 통로입니다.
     *
     * <p>격리된 서브넷을 화면에서 <b>빨간색</b>으로 칠하려면 프론트가 그 사실을
     * 알아야 합니다. 별도 API 를 한 번 더 부르게 하면 두 응답이 서로 다른
     * 시점의 상태가 되어 화면이 어긋납니다. 그래서 토폴로지 응답에 함께
     * 실어 보냅니다.
     */
    private final QuarantineService quarantineService;

    /**
     * @param projectService 프로젝트 서비스
     * @param router         텔레메트리/설정 보관소
     * @param registry       Agent 세션 레지스트리
     */
    public NetworkTopologyController(ProjectService projectService,
                                     AgentMessageRouterService router,
                                     AgentSessionRegistry registry,
                                     QuarantineService quarantineService) {
        this.projectService = projectService;
        this.router = router;
        this.registry = registry;
        this.quarantineService = quarantineService;
    }

    /**
     * 프로젝트 관점의 토폴로지를 반환합니다.
     *
     * <p>서브넷을 노드로, 연결 규칙을 간선으로 표현합니다. 프론트엔드의
     * Mermaid 토폴로지가 이 구조를 그대로 그릴 수 있게 평평한 형태로 둡니다.
     *
     * @param projectId 프로젝트 키
     * @return 노드/간선/등급 범례
     */
    @GetMapping("/topology/{projectId}")
    public Map<String, Object> topology(@PathVariable String projectId) {
        final Project project = projectService.getByKey(projectId);

        final Map<String, ProjectSubnet> byId = new LinkedHashMap<>();
        for (final ProjectSubnet subnet : project.getSubnets()) {
            byId.put(subnet.getSubnetId(), subnet);
        }

        final Set<String> quarantinedAgents = quarantineService.quarantinedAgentIds();

        final List<Map<String, Object>> nodes = new ArrayList<>();
        for (final PolicySubnet subnet : project.toPolicySubnets()) {
            final String agentId = subnet.getAgentId();
            // ⚠️ Agent 식별자는 운영자가 적어 넣은 값이라 대소문자가 섞입니다.
            //    (VDI-1 / vdi-1) 그래서 관대하게 비교합니다.
            final boolean quarantined = agentId != null && quarantinedAgents.stream()
                    .anyMatch(id -> id != null && id.equalsIgnoreCase(agentId));
            final Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", subnet.getId());
            node.put("label", subnet.getName() == null ? subnet.getId() : subnet.getName());
            node.put("cidr", subnet.getCidr());
            node.put("subnet_class", subnet.getZoneClass() == null ? null : subnet.getZoneClass().label());
            node.put("level", subnet.getZoneClass() == null ? null : subnet.getZoneClass().level());
            node.put("agent_id", agentId);
            node.put("manually_edited", subnet.isManuallyEdited());
            // 격리 여부와 연결 여부를 함께 실어 보냅니다. 프론트는
            // quarantined 를 최우선으로 빨간색 처리합니다.
            node.put("quarantined", quarantined);
            node.put("connected", agentId != null && registry.connectedAgentIds().contains(agentId));
            nodes.add(node);
        }

        final List<Map<String, Object>> edges = new ArrayList<>();
        for (final var rule : project.toPolicyRules()) {
            if (!rule.isEnabled()) {
                continue;
            }
            final Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("rule_id", rule.getId());
            edge.put("source", rule.getSource());
            edge.put("target", rule.getDestination());
            edge.put("port", rule.hasPort() ? rule.getPort() : null);
            edge.put("protocol", rule.getProtocol());

            // 간선의 위험도: 등급을 건너뛰면 forbidden
            final ProjectSubnet source = byId.get(rule.getSource());
            final ProjectSubnet target = byId.get(rule.getDestination());
            final ZoneClass sourceZone = source == null ? null : source.getZoneClass();
            final ZoneClass targetZone = target == null ? null : target.getZoneClass();
            final boolean forbidden = ZoneClass.forbidsDirectConnection(sourceZone, targetZone);
            edge.put("forbidden", forbidden);
            edge.put("severity", forbidden ? "CRITICAL" : (rule.hasPort() ? "OK" : "MAJOR"));
            edges.add(edge);
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("project_name", project.getName());
        body.put("nodes", nodes);
        body.put("edges", edges);
        body.put("legend", List.of(
                Map.of("label", "Open", "level", 1, "color", "#16a34a"),
                Map.of("label", "Sensitive", "level", 2, "color", "#9333ea"),
                Map.of("label", "Confidential", "level", 3, "color", "#dc2626")));
        return body;
    }

    /**
     * 수집된 실제 장치 정보를 프로젝트 서브넷 등급과 결합해 반환합니다.
     *
     * @param projectId 프로젝트 키
     * @return 장치 목록 (인터페이스에 등급을 붙임)
     */
    @GetMapping("/discovered/{projectId}")
    public Map<String, Object> discovered(@PathVariable String projectId) {
        final Project project = projectService.getByKey(projectId);
        final Map<String, ZoneClass> cidrIndex = PolicySubnet.cidrIndex(project.toPolicySubnets());

        final List<Map<String, Object>> devices = new ArrayList<>();
        router.allConfigs().forEach((agentId, config) -> devices.add(toDeviceMap(agentId, config, cidrIndex)));

        // 연결됐지만 아직 텔레메트리가 없는 장치도 목록에 넣습니다.
        for (final String agentId : registry.connectedAgentIds()) {
            final boolean present = devices.stream()
                    .anyMatch(device -> agentId.equals(device.get("agent_id")));
            if (!present) {
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("agent_id", agentId);
                entry.put("hostname", agentId);
                entry.put("format", null);
                entry.put("product", null);
                entry.put("interfaces", List.of());
                entry.put("vlans", List.of());
                entry.put("discovered", false);
                devices.add(entry);
            }
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("device_count", devices.size());
        body.put("devices", devices);
        return body;
    }

    /**
     * 전체 장치의 수집 현황을 반환합니다. (프로젝트 무관)
     *
     * <p>{@code /network} 화면의 "탐지된 노드" 카드가 쓰는 요약입니다.
     *
     * @return 형식별/장치별 요약
     */
    @GetMapping("/discovered")
    public Map<String, Object> discoveredAll() {
        final Map<String, Integer> byFormat = new LinkedHashMap<>();
        final List<Map<String, Object>> devices = new ArrayList<>();

        router.allConfigs().forEach((agentId, config) -> {
            final String format = config.getFormat() == null ? "unknown" : config.getFormat();
            byFormat.merge(format, 1, Integer::sum);
            devices.add(toDeviceMap(agentId, config, Map.of()));
        });

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected_agents", registry.connectedCount());
        body.put("parsed_devices", devices.size());
        body.put("by_format", byFormat);
        body.put("devices", devices);
        body.put("server_time", java.time.Instant.now().toString());
        return body;
    }

    /**
     * 중립 설정 한 건을 화면용 장치 맵으로 바꿉니다.
     *
     * @param agentId   장치 식별자
     * @param config    중립 설정
     * @param cidrIndex 정규화 CIDR → 등급 색인
     * @return 장치 맵
     */
    private Map<String, Object> toDeviceMap(String agentId,
                                            NeutralDeviceConfig config,
                                            Map<String, ZoneClass> cidrIndex) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("agent_id", agentId);
        entry.put("hostname", config.getHostname());
        entry.put("format", config.getFormat());
        entry.put("vendor", config.getVendor());
        entry.put("product", config.getProduct());
        entry.put("device_type", config.getDeviceType());
        entry.put("last_seen", router.lastSeenOf(agentId));
        entry.put("discovered", true);

        final List<Map<String, Object>> interfaces = new ArrayList<>();
        config.getInterfaces().forEach((name, iface) -> {
            final Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("addresses", List.copyOf(iface.getAddresses()));
            item.put("access_vlan", iface.getAccessVlan());
            item.put("trunk_vlans", List.copyOf(iface.getTrunkVlans()));
            item.put("mode", iface.getMode());
            item.put("parent", iface.getParent());
            item.put("admin_state", iface.getAdminState());
            item.put("oper_state", iface.getOperState());
            item.put("mac_address", iface.getMacAddress());

            // 주소가 프로젝트 서브넷에 속하면 등급을 붙입니다.
            String matchedClass = null;
            for (final String address : iface.getAddresses()) {
                final ZoneClass zone = cidrIndex.get(PolicySubnet.normalizeCidr(address));
                if (zone != null) {
                    matchedClass = zone.label();
                    break;
                }
            }
            item.put("subnet_class", matchedClass);
            interfaces.add(item);
        });
        entry.put("interfaces", interfaces);

        final List<Map<String, Object>> vlans = new ArrayList<>();
        config.getVlans().forEach((vlanId, vlan) -> {
            final Map<String, Object> item = new LinkedHashMap<>();
            item.put("vlan_id", vlanId);
            item.put("name", vlan.getName());
            item.put("status", vlan.getStatus());
            item.put("members", List.copyOf(vlan.getMembers()));
            vlans.add(item);
        });
        entry.put("vlans", vlans);
        entry.put("route_count", config.getRoutes().size());
        entry.put("firewall_rule_count", config.getFirewallRules().size());
        entry.put("warnings", List.copyOf(config.getWarnings()));
        return entry;
    }
}
