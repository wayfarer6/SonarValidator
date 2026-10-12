package org.sonar.sonarvalidator_backend.Controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.ExpectedAgentService;
import org.sonar.sonarvalidator_backend.Service.ObservedConnectivity;
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
    private final ExpectedAgentService expectedAgentService;

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
                                     ExpectedAgentService expectedAgentService,
                                     QuarantineService quarantineService) {
        this.projectService = projectService;
        this.router = router;
        this.registry = registry;
        this.expectedAgentService = expectedAgentService;
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
        final Map<String, NeutralDeviceConfig> configs = router.allConfigs();

        final Map<String, ProjectSubnet> byId = new LinkedHashMap<>();
        for (final ProjectSubnet subnet : project.getSubnets()) {
            byId.put(subnet.getSubnetId(), subnet);
        }

        /*
         * ⚠️ 간선은 **노드 id** 를 참조해야 합니다.
         *
         * 예전에는 규칙의 CIDR(`10.0.8.0/24`)을 그대로 넣었습니다.
         * 그런데 노드는 `Subnet-0001` 로 선언됩니다. 그래서 Mermaid 가
         * "없는 노드" 를 자동 생성해 **`10_0_8_0_24` 가 별도 노드로 떠 있었고**,
         * 서브넷 노드에는 간선이 하나도 안 붙었습니다.
         *
         * 또 아래 등급 조회(`byId.get(...)`)가 CIDR 로 조회되어 **항상 null** 이 되고
         * `forbidden` 이 항상 false 였습니다 — 금지 연결이 강조되지 않았습니다.
         * 두 증상의 원인이 같습니다.
         */
        final Map<String, String> idByCidr = new LinkedHashMap<>();
        for (final ProjectSubnet subnet : project.getSubnets()) {
            final String cidr = subnet.getCidr();
            if (cidr != null) {
                idByCidr.put(PolicySubnet.normalizeCidr(cidr), subnet.getSubnetId());
            }
        }

        final Set<String> quarantinedAgents = quarantineService.quarantinedNodeAgentIds();
        // ⚠️ DB Design v1.5 — Agent 없는 장비(방화벽)는 agent_id 가 없으므로
        //   노드 번호로 격리 여부를 판단해야 합니다.
        final Set<Integer> quarantinedNodes = quarantineService.quarantinedNodeIds();

        final List<Map<String, Object>> nodes = new ArrayList<>();
        for (final PolicySubnet subnet : project.toPolicySubnets()) {
            final String agentId = subnet.getAgentId();
            // ⚠️ Agent 식별자는 운영자가 적어 넣은 값이라 대소문자가 섞입니다.
            //    (VDI-1 / vdi-1) 그래서 관대하게 비교합니다.
                final boolean quarantined = agentId != null && (
                    quarantinedAgents.stream().anyMatch(id -> id != null && id.equalsIgnoreCase(agentId))
                    || quarantineService.isSubnetQuarantined(agentId, subnet.getCidr()));
            final Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", subnet.getId());
            node.put("label", subnet.getName() == null ? subnet.getId() : subnet.getName());
            node.put("cidr", subnet.getCidr());
            node.put("vlan_id", subnet.getVlanId());
            node.put("subnet_class", subnet.getZoneClass() == null ? null : subnet.getZoneClass().label());
            node.put("level", subnet.getZoneClass() == null ? null : subnet.getZoneClass().level());
            node.put("agent_id", agentId);
            node.put("manually_edited", subnet.isManuallyEdited());
            node.put("kind", ObservedConnectivity.KIND_SUBNET);
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
            // 규칙은 CIDR 로 서브넷을 가리킵니다. 노드 id 로 옮겨 적어야
            // Mermaid 가 서브넷 노드에 간선을 붙입니다.
            final String sourceId = idByCidr.getOrDefault(
                    PolicySubnet.normalizeCidr(rule.getSource()), rule.getSource());
            final String targetId = idByCidr.getOrDefault(
                    PolicySubnet.normalizeCidr(rule.getDestination()), rule.getDestination());

            final Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("rule_id", rule.getId());
            edge.put("source", sourceId);
            edge.put("target", targetId);
            // CIDR 원문도 남깁니다 — 화면이 필요할 때 라벨로 쓸 수 있게 합니다.
            edge.put("source_cidr", rule.getSource());
            edge.put("target_cidr", rule.getDestination());
            edge.put("port", rule.hasPort() ? rule.getPort() : null);
            edge.put("protocol", rule.getProtocol());

            // 간선의 위험도: 등급을 건너뛰면 forbidden
            // ⚠️ 이제 source/target 이 노드 id 이므로 조회가 성공합니다.
            final ProjectSubnet source = byId.get(sourceId);
            final ProjectSubnet target = byId.get(targetId);
            final ZoneClass sourceZone = source == null ? null : source.getZoneClass();
            final ZoneClass targetZone = target == null ? null : target.getZoneClass();
            final boolean forbidden = ZoneClass.forbidsDirectConnection(sourceZone, targetZone);
            edge.put("forbidden", forbidden);
            edge.put("severity", forbidden ? "CRITICAL" : (rule.hasPort() ? "OK" : "MAJOR"));
            edge.put("origin", "MANUAL");
            edge.put("observed", false);
            edge.put("edge_type", "policy");
            edges.add(edge);
        }

        /*
         * --- 2) 라우팅 테이블에서 읽은 실제 연결 -------------------------
         *
         * 규칙만 그리면 "설정된 정책" 만 보이고, 장치가 실제로 VLAN 사이를
         * 라우팅하고 있어도 화면에는 선이 없습니다. 그래서 수집된 라우팅
         * 정보(L3 존재)로 만든 간선을 함께 실어 보냅니다.
         *
         * 저장하지 않습니다 — 라우팅 테이블은 살아 있는 텔레메트리이고, 장치가
         * 바뀌면 화면에서도 함께 사라져야 합니다.
         */
        for (final ObservedConnectivity.Link link
                : ObservedConnectivity.betweenSubnetsOfEachDevice(project, configs)) {
            final Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("rule_id", null);
            edge.put("source", link.sourceId());
            edge.put("target", link.targetId());
            edge.put("source_cidr", link.sourceCidr());
            edge.put("target_cidr", link.targetCidr());
            edge.put("port", null);
            edge.put("protocol", null);
            edge.put("agent_id", link.agentId());
            edge.put("forbidden", link.forbidden());
            // OBSERVED: 규칙 위반이 아니라 관측된 연결(존을 건너뛰면 CRITICAL 로 승격)
            edge.put("severity", link.forbidden() ? "CRITICAL" : "OBSERVED");
            edge.put("origin", "DISCOVERED");
            edge.put("observed", true);
            edge.put("edge_type", "routed_subnets");
            edges.add(edge);
        }

        /*
         * --- 3) 장비와 기본 경로의 실제 다음 홉 ---------------------------
         *
         * VLAN/서브넷을 기본 경로가 있다는 이유만으로 Internet에 직접 붙이면
         * Firewall/C4I Router/Gateway Router를 모두 건너뛴 것처럼 보입니다.
         * 그래서 프로젝트의 대역은 먼저 실제 VLAN 스위치/장비에 붙이고, 기본 경로
         * nextHop 주소의 장비를 찾아 장비 간 홉을 만듭니다. 내부 장비를 찾을 수
         * 없는 마지막 홉만 Internet에 연결합니다.
         *
         * 예: VLAN131 → Firewall → C4I Router → Gateway Router → Internet
         */
        final Set<String> projectAgents = new LinkedHashSet<>();
        for (final ExpectedAgent expected : expectedAgentService.list(projectId)) {
            if (expected.getAgentId() != null && !expected.getAgentId().isBlank()) {
                projectAgents.add(expected.getAgentId());
            }
        }
        for (final ProjectSubnet subnet : project.getSubnets()) {
            if (subnet.getAgentId() != null && !subnet.getAgentId().isBlank()) {
                projectAgents.add(subnet.getAgentId());
            }
        }

        // 장비 노드는 Physical View의 라우팅 경유점을 나타냅니다.
        for (final String agentId : projectAgents) {
            final NeutralDeviceConfig config = findConfig(configs, agentId);
            if (config == null) {
                continue;
            }
            final Map<String, Object> deviceNode = new LinkedHashMap<>();
            deviceNode.put("id", deviceNodeId(agentId));
            deviceNode.put("label", config.getHostname() == null || config.getHostname().isBlank()
                    ? agentId : config.getHostname());
            deviceNode.put("cidr", null);
            deviceNode.put("vlan_id", null);
            deviceNode.put("subnet_class", null);
            deviceNode.put("level", null);
            deviceNode.put("agent_id", agentId);
            deviceNode.put("manually_edited", false);
            deviceNode.put("kind", ObservedConnectivity.KIND_DEVICE);
            deviceNode.put("device_type", config.getDeviceType());
            deviceNode.put("quarantined", containsIgnoreCase(quarantinedAgents, agentId));
            deviceNode.put("connected", containsIgnoreCase(registry.connectedAgentIds(), agentId));
            nodes.add(deviceNode);
        }

        final List<ObservedConnectivity.PhysicalLink> projectPhysicalLinks = new ArrayList<>();
        for (final ObservedConnectivity.PhysicalLink link
                : ObservedConnectivity.physicalLinks(project, configs)) {
            final String sourceAgent = canonicalAgent(projectAgents, link.sourceAgentId());
            final String targetAgent = canonicalAgent(projectAgents, link.targetAgentId());
            if (sourceAgent == null || targetAgent == null) {
                continue;
            }
            final ObservedConnectivity.PhysicalLink projectLink =
                    new ObservedConnectivity.PhysicalLink(
                            sourceAgent, link.sourceInterface(),
                            targetAgent, link.targetInterface());
            projectPhysicalLinks.add(projectLink);

            final Map<String, Object> edge = discoveredEdge(
                    deviceNodeId(sourceAgent), deviceNodeId(targetAgent), sourceAgent);
            edge.put("edge_type", "physical_link");
            edge.put("source_interface", link.sourceInterface());
            edge.put("target_interface", link.targetInterface());
            edges.add(edge);
        }

        // VLAN은 그 VLAN을 실제 보유한 스위치에 먼저 붙입니다. 스위치 수집 정보가
        // 없을 때만 L3 인터페이스를 가진 장비에 붙여 연결선이 끊기지 않게 합니다.
        for (final ProjectSubnet subnet : project.getSubnets()) {
            final String agentId = canonicalAgent(projectAgents, subnet.getAgentId());
            if (agentId == null || findConfig(configs, agentId) == null) {
                continue;
            }
            boolean attachedToSwitch = false;
            if (subnet.getVlanId() != null) {
                for (final String switchAgent : projectAgents) {
                    final NeutralDeviceConfig switchConfig = findConfig(configs, switchAgent);
                    if (switchConfig == null || !isSwitch(switchAgent, switchConfig)
                            || !switchConfig.getVlans().containsKey(subnet.getVlanId())) {
                        continue;
                    }
                    final Map<String, Object> vlanEdge = discoveredEdge(
                            subnet.getSubnetId(), deviceNodeId(switchAgent), switchAgent);
                    vlanEdge.put("source_cidr", subnet.getCidr());
                    vlanEdge.put("edge_type", "vlan_membership");
                    edges.add(vlanEdge);
                    attachedToSwitch = true;

                    // 일부 L2 스위치는 ARP 테이블을 보고하지 않습니다. 그래도 같은
                    // VLAN이 스위치와 L3 게이트웨이 양쪽에서 확인되면 그 업링크는
                    // 끊기면 안 됩니다. MAC 기반 링크가 없을 때만 보완선을 만듭니다.
                    if (!switchAgent.equalsIgnoreCase(agentId)
                            && !hasPhysicalPath(switchAgent, agentId, projectPhysicalLinks)) {
                        projectPhysicalLinks.add(new ObservedConnectivity.PhysicalLink(
                                switchAgent, null, agentId, null));
                        final Map<String, Object> uplinkEdge = discoveredEdge(
                                deviceNodeId(switchAgent), deviceNodeId(agentId), switchAgent);
                        uplinkEdge.put("edge_type", "physical_link");
                        uplinkEdge.put("evidence", "vlan_membership");
                        edges.add(uplinkEdge);
                    }
                }
            }
            if (attachedToSwitch) {
                continue;
            }
            final Map<String, Object> edge = discoveredEdge(
                    subnet.getSubnetId(), deviceNodeId(agentId), agentId);
            edge.put("source_cidr", subnet.getCidr());
            edge.put("edge_type", "attachment");
            edges.add(edge);
        }

        final List<ObservedConnectivity.DefaultRouteHop> projectRouteHops = new ArrayList<>();
        boolean hasInternetEgress = false;
        for (final ObservedConnectivity.DefaultRouteHop hop
                : ObservedConnectivity.defaultRouteHops(configs)) {
            final String sourceAgent = canonicalAgent(projectAgents, hop.sourceAgentId());
            if (sourceAgent == null) {
                continue;
            }
            final String targetAgent = canonicalAgent(projectAgents, hop.targetAgentId());
            // VM/서버의 관리용 기본 경로가 외부 NAT를 직접 가리키더라도 업무망의
            // 인터넷 출구로 그리지 않습니다. 토폴로지 경계는 라우터/방화벽이
            // 실제로 외부로 내보내는 마지막 홉만 나타냅니다.
            if (targetAgent == null
                    && !isInternetBoundaryDevice(
                            sourceAgent, findConfig(configs, sourceAgent), hop.nextHop())) {
                continue;
            }
            projectRouteHops.add(new ObservedConnectivity.DefaultRouteHop(
                    sourceAgent, hop.nextHop(), hop.nextHopInterface(),
                    targetAgent, hop.targetInterface()));
            if (targetAgent == null) {
                hasInternetEgress = true;
            }
        }

        if (hasInternetEgress) {
            final Map<String, Object> internetNode = new LinkedHashMap<>();
            internetNode.put("id", ObservedConnectivity.INTERNET_NODE_ID);
            internetNode.put("label", ObservedConnectivity.INTERNET_NODE_LABEL);
            internetNode.put("cidr", null);
            internetNode.put("vlan_id", null);
            // 인터넷은 공개망 성질이므로 Open 색을 씁니다.
            internetNode.put("subnet_class", ZoneClass.OPEN.label());
            internetNode.put("level", ZoneClass.OPEN.level());
            internetNode.put("agent_id", null);
            internetNode.put("manually_edited", false);
            internetNode.put("quarantined", false);
            internetNode.put("connected", true);
            internetNode.put("kind", ObservedConnectivity.KIND_INTERNET);
            nodes.add(internetNode);
        }

        for (final ObservedConnectivity.DefaultRouteHop hop : projectRouteHops) {
            if (hop.targetAgentId() != null
                    && hasPhysicalPath(hop.sourceAgentId(), hop.targetAgentId(),
                            projectPhysicalLinks)) {
                // 같은 두 장비 사이의 실제 스위치 경로가 이미 선으로 이어져 있으면
                // 직결처럼 보이는 nextHop 지름길 선은 만들지 않습니다.
                continue;
            }
            final String target = hop.targetAgentId() == null
                    ? ObservedConnectivity.INTERNET_NODE_ID
                    : deviceNodeId(hop.targetAgentId());
            final Map<String, Object> edge = discoveredEdge(
                    deviceNodeId(hop.sourceAgentId()), target, hop.sourceAgentId());
            edge.put("next_hop", hop.nextHop());
            edge.put("next_hop_interface", hop.nextHopInterface());
            edge.put("target_interface", hop.targetInterface());
            if (hop.targetAgentId() == null) {
                edge.put("edge_type", "internet_egress");
                edge.put("internet", true);
            } else {
                edge.put("edge_type", "default_route");
            }
            edges.add(edge);
        }

        final Set<String> internetReachable =
                ObservedConnectivity.internetReachableAgents(projectRouteHops);
        for (final Map<String, Object> node : nodes) {
            if (!ObservedConnectivity.KIND_SUBNET.equals(node.get("kind"))) {
                continue;
            }
            final String agentId = canonicalAgent(projectAgents, (String) node.get("agent_id"));
            final ProjectSubnet subnet = byId.get((String) node.get("id"));
            node.put("internet_reachable",
                    subnet != null && subnet.getVlanId() != null
                            && agentId != null
                            && containsIgnoreCase(internetReachable, agentId)
                            && ObservedConnectivity.internetAllowed(
                                    agentId, findConfig(configs, agentId),
                                    subnet == null ? null : subnet.getZoneClass()));
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
        // 화면이 "실선=정책 규칙 / 점선=장치가 실제로 라우팅하는 연결" 을
        // 구분해 그릴 수 있도록 범례도 함께 싣습니다.
        body.put("edge_legend", List.of(
                Map.of("kind", "policy", "label", "정책 규칙", "dashed", false),
                Map.of("kind", "physical_link", "label", "장비 간 물리 연결", "dashed", false),
                Map.of("kind", "vlan_membership", "label", "VLAN 연결", "dashed", false),
                Map.of("kind", "default_route", "label", "기본 경로 다음 홉", "dashed", true),
                Map.of("kind", "internet_egress", "label", "외부 게이트웨이", "dashed", true)));
        return body;
    }

    private static Map<String, Object> discoveredEdge(String source, String target, String agentId) {
        final Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("rule_id", null);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("source_cidr", null);
        edge.put("target_cidr", null);
        edge.put("port", null);
        edge.put("protocol", null);
        edge.put("agent_id", agentId);
        edge.put("forbidden", false);
        edge.put("severity", "OBSERVED");
        edge.put("origin", "DISCOVERED");
        edge.put("observed", true);
        return edge;
    }

    private static String deviceNodeId(String agentId) {
        return "device:" + agentId;
    }

    private static NeutralDeviceConfig findConfig(Map<String, NeutralDeviceConfig> configs,
                                                  String agentId) {
        if (configs == null || agentId == null) {
            return null;
        }
        final NeutralDeviceConfig exact = configs.get(agentId);
        if (exact != null) {
            return exact;
        }
        for (final Map.Entry<String, NeutralDeviceConfig> entry : configs.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(agentId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String canonicalAgent(Set<String> projectAgents, String candidate) {
        if (candidate == null) {
            return null;
        }
        for (final String agentId : projectAgents) {
            if (agentId.equalsIgnoreCase(candidate)) {
                return agentId;
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(Collection<String> values, String candidate) {
        if (candidate == null) {
            return false;
        }
        for (final String value : values) {
            if (value != null && value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSwitch(String agentId, NeutralDeviceConfig config) {
        final DeviceType reported = DeviceType.fromString(
                config == null ? null : config.getDeviceType());
        final DeviceType resolved = reported != null
                ? reported
                : DeviceType.inferFromDeviceId(agentId);
        return resolved == DeviceType.SWITCH;
    }

    private static boolean hasPhysicalPath(
            String sourceAgent, String targetAgent,
            List<ObservedConnectivity.PhysicalLink> links) {
        if (sourceAgent == null || targetAgent == null) {
            return false;
        }
        final Set<String> visited = new LinkedHashSet<>();
        final List<String> pending = new ArrayList<>();
        pending.add(sourceAgent);
        while (!pending.isEmpty()) {
            final String current = pending.removeFirst();
            if (!visited.add(current.toLowerCase())) {
                continue;
            }
            if (current.equalsIgnoreCase(targetAgent)) {
                return true;
            }
            for (final ObservedConnectivity.PhysicalLink link : links) {
                if (link.sourceAgentId().equalsIgnoreCase(current)
                        && !containsIgnoreCase(visited, link.targetAgentId())) {
                    pending.add(link.targetAgentId());
                } else if (link.targetAgentId().equalsIgnoreCase(current)
                        && !containsIgnoreCase(visited, link.sourceAgentId())) {
                    pending.add(link.sourceAgentId());
                }
            }
        }
        return false;
    }

    private static boolean isInternetBoundaryDevice(String agentId,
                                                    NeutralDeviceConfig config,
                                                    String nextHop) {
        final DeviceType reported = DeviceType.fromString(
                config == null ? null : config.getDeviceType());
        final DeviceType resolved = reported != null
                ? reported
                : DeviceType.inferFromDeviceId(agentId);
        if (resolved != DeviceType.ROUTER && resolved != DeviceType.FIREWALL) {
            return false;
        }

        // 수집이 잠시 끊긴 내부 nextHop을 곧바로 Internet으로 오판하지 않습니다.
        // 명시적인 경계 역할 이름이거나 공인 nextHop인 경우에만 외부 출구입니다.
        final String role = ((agentId == null ? "" : agentId) + " "
                + (config == null || config.getHostname() == null ? "" : config.getHostname()))
                .toLowerCase();
        return role.contains("gateway") || role.contains("border")
                || role.contains("edge-router") || isPublicIpv4(nextHop);
    }

    private static boolean isPublicIpv4(String address) {
        if (address == null) {
            return false;
        }
        final String[] parts = address.trim().split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            final int first = Integer.parseInt(parts[0]);
            final int second = Integer.parseInt(parts[1]);
            for (final String part : parts) {
                final int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) {
                    return false;
                }
            }
            return first != 10
                    && !(first == 172 && second >= 16 && second <= 31)
                    && !(first == 192 && second == 168)
                    && first != 127
                    && !(first == 169 && second == 254)
                    && first > 0 && first < 224;
        } catch (NumberFormatException ex) {
            return false;
        }
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
