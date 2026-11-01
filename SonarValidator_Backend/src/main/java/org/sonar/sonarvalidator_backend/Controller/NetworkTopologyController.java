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
import org.sonar.sonarvalidator_backend.Service.ObservedConnectivity;
import org.sonar.sonarvalidator_backend.Service.ProjectService;
import org.sonar.sonarvalidator_backend.Service.QuarantineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/network")
public class NetworkTopologyController {

    private static final Logger log = LoggerFactory.getLogger(NetworkTopologyController.class);

    private final ProjectService projectService;
    private final AgentMessageRouterService router;
    private final AgentSessionRegistry registry;

    private final QuarantineService quarantineService;
    private org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository expectedAgents;

    @org.springframework.beans.factory.annotation.Autowired
    public void setExpectedAgents(org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository expectedAgents) {
        this.expectedAgents = expectedAgents;
    }

    public NetworkTopologyController(ProjectService projectService,
                                     AgentMessageRouterService router,
                                     AgentSessionRegistry registry,
                                     QuarantineService quarantineService) {
        this.projectService = projectService;
        this.router = router;
        this.registry = registry;
        this.quarantineService = quarantineService;
    }

    @GetMapping("/topology/{projectId}")
    public Map<String, Object> topology(@PathVariable String projectId) {
        final Project project = projectService.getByKey(projectId);

        final Map<String, ProjectSubnet> byId = new LinkedHashMap<>();
        for (final ProjectSubnet subnet : project.getSubnets()) {
            byId.put(subnet.getSubnetId(), subnet);
        }

        final Map<String, String> idByCidr = new LinkedHashMap<>();
        for (final ProjectSubnet subnet : project.getSubnets()) {
            final String cidr = subnet.getCidr();
            if (cidr != null) {
                idByCidr.put(PolicySubnet.normalizeCidr(cidr), subnet.getSubnetId());
            }
        }

        final Set<String> assigned = new java.util.LinkedHashSet<>();
        final Map<String, Integer> intervals = new LinkedHashMap<>();
        if (expectedAgents != null) expectedAgents.findByProjectKeyOrderByCreatedAtDesc(projectId)
                .forEach(agent -> {
                    assigned.add(agent.getAgentId());
                    intervals.put(agent.getAgentId(), agent.getMonitorIntervalSeconds());
                });
        final Map<String, NeutralDeviceConfig> projectConfigs = new LinkedHashMap<>();
        router.allConfigs().forEach((agentId, config) -> {
            if (assigned.contains(agentId) || project.getSubnets().stream().anyMatch(subnet -> agentId.equalsIgnoreCase(subnet.getAgentId())))
                projectConfigs.put(agentId, config);
        });
        final List<Map<String, Object>> nodes = new ArrayList<>();
        for (final PolicySubnet subnet : project.toPolicySubnets()) {
            final String agentId = subnet.getAgentId();
            final Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", subnet.getId());
            node.put("label", subnet.getName() == null ? subnet.getId() : subnet.getName());
            node.put("cidr", subnet.getCidr());
            node.put("subnet_class", subnet.getZoneClass() == null ? null : subnet.getZoneClass().label());
            node.put("level", subnet.getZoneClass() == null ? null : subnet.getZoneClass().level());
            node.put("agent_id", agentId);
            node.put("manually_edited", subnet.isManuallyEdited());
            node.put("kind", ObservedConnectivity.KIND_SUBNET);
            // 격리 여부와 연결 여부를 함께 실어 보냅니다. 프론트는
            // quarantined 를 최우선으로 빨간색 처리합니다.
            node.putAll(quarantineService.topologyState(projectId, agentId, subnet.getCidr()));
            final var config = projectConfigs.get(agentId);
            node.put("observed_up", config == null ? null : ObservedConnectivity.isSubnetObserved(config, subnet.getCidr()));
            node.put("last_seen", agentId == null ? null : router.lastSeenOf(agentId));
            final var observedAt = agentId == null ? null : router.observedAtOf(agentId);
            node.put("observed_at", observedAt);
            node.put("config_stale", observedAt == null || observedAt.isBefore(java.time.Instant.now()
                    .minusSeconds(Math.max(120, 3L * intervals.getOrDefault(agentId, 30) + 10)))
                    || (assigned.contains(agentId) && !registry.connectedAgentIds().contains(agentId)));
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
            edges.add(edge);
        }

        for (final ObservedConnectivity.Link link
                : ObservedConnectivity.betweenSubnetsOfEachDevice(project, projectConfigs)) {
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
            edges.add(edge);
        }

        for (final var entry : projectConfigs.entrySet()) {
            final String agentId = entry.getKey();
            final var config = entry.getValue();
            final String type = config.getDeviceType();
            if (type == null || !type.equalsIgnoreCase("SWITCH")) continue;
            final var links = ObservedConnectivity.vlanAttachments(project, agentId, config);
            if (links.isEmpty()) continue;
            final String id = "device:" + agentId;
            final Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", id); node.put("label", agentId); node.put("cidr", null);
            node.put("agent_id", agentId); node.put("level", null); node.put("subnet_class", null);
            node.put("kind", "device"); node.put("connected", registry.connectedAgentIds().contains(agentId));
            node.put("observed_at", router.observedAtOf(agentId));
            node.put("config_stale", !registry.connectedAgentIds().contains(agentId));
            node.put("observed_up", links.stream().anyMatch(ObservedConnectivity.VlanAttachment::up));
            node.putAll(quarantineService.topologyState(projectId, agentId, null));
            nodes.add(node);
            for (final var link : links) {
                if (!link.up()) continue;
                final Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("source", id); edge.put("target", link.subnetId());
                edge.put("agent_id", agentId); edge.put("vlan_id", link.vlanId());
                edge.put("origin", "DISCOVERED"); edge.put("observed", true); edge.put("layer", "L2");
                edge.put("forbidden", false); edge.put("severity", "OBSERVED");
                edges.add(edge);
            }
        }

        final Set<String> exposedAgents = ObservedConnectivity.internetExposedAgents(projectConfigs);
        if (project.getSubnets().stream().anyMatch(subnet -> exposedAgents.contains(subnet.getAgentId())
                && ObservedConnectivity.isSubnetObserved(projectConfigs.get(subnet.getAgentId()), subnet.getCidr()))) {
            final Map<String, Object> internetNode = new LinkedHashMap<>();
            internetNode.put("id", ObservedConnectivity.INTERNET_NODE_ID);
            internetNode.put("label", ObservedConnectivity.INTERNET_NODE_LABEL);
            internetNode.put("cidr", null);
            // 인터넷은 공개망 성질이므로 Open 색을 씁니다.
            internetNode.put("subnet_class", ZoneClass.OPEN.label());
            internetNode.put("level", ZoneClass.OPEN.level());
            internetNode.put("agent_id", null);
            internetNode.put("manually_edited", false);
            internetNode.put("quarantined", false);
            internetNode.put("connected", true);
            internetNode.put("kind", ObservedConnectivity.KIND_INTERNET);
            nodes.add(internetNode);

            for (final ProjectSubnet subnet : project.getSubnets()) {
                final String agentId = subnet.getAgentId();
                if (agentId == null || !exposedAgents.contains(agentId)
                        || !ObservedConnectivity.isSubnetObserved(projectConfigs.get(agentId), subnet.getCidr())) {
                    continue;
                }
                final ZoneClass zone = subnet.getZoneClass();
                final boolean confidential = zone == ZoneClass.CONFIDENTIAL;
                final Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("rule_id", null);
                edge.put("source", subnet.getSubnetId());
                edge.put("target", ObservedConnectivity.INTERNET_NODE_ID);
                edge.put("source_cidr", subnet.getCidr());
                edge.put("target_cidr", null);
                edge.put("port", null);
                edge.put("protocol", null);
                edge.put("agent_id", agentId);
                // 기밀망이 인터넷으로 나가면 등급을 건너뛰는 직접 연결입니다.
                edge.put("forbidden", confidential);
                edge.put("severity", confidential ? "CRITICAL"
                        : (zone == ZoneClass.SENSITIVE ? "MAJOR" : "OBSERVED"));
                edge.put("origin", "DISCOVERED");
                edge.put("observed", true);
                edge.put("internet", true);
                edges.add(edge);
            }
        }

        final Map<String, Map<String, Object>> nodeStates = new LinkedHashMap<>();
        nodes.forEach(node -> nodeStates.put((String) node.get("id"), node));
        for (final var edge : edges) {
            final var source = nodeStates.get(edge.get("source"));
            final var target = nodeStates.get(edge.get("target"));
            edge.put("blocked", (source != null && Boolean.TRUE.equals(source.get("quarantined")))
                    || (target != null && Boolean.TRUE.equals(target.get("quarantined"))));
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
                Map.of("kind", "observed", "label", "수집된 경로·VLAN 연결", "dashed", true)));
        return body;
    }

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
