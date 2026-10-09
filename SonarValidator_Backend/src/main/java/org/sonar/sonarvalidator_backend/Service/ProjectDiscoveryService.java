package org.sonar.sonarvalidator_backend.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.dto.ProjectMapper;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only editing view: collected VLANs are drafts, never automatic policy writes. */
@Service
public class ProjectDiscoveryService {
    private final ProjectService projects;
    private final ExpectedAgentRepository expected;
    private final AgentMessageRouterService router;

    public ProjectDiscoveryService(ProjectService projects, ExpectedAgentRepository expected,
                                   AgentMessageRouterService router) {
        this.projects = projects;
        this.expected = expected;
        this.router = router;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> forEditing(String projectId) {
        final Map<String, NeutralDeviceConfig> configs = new LinkedHashMap<>();
        final var all = router.allConfigs();
        expected.findByProjectKeyOrderByCreatedAtDesc(projectId).forEach(agent -> {
            if (all.containsKey(agent.getAgentId())) configs.put(agent.getAgentId(), all.get(agent.getAgentId()));
        });
        return editingView(projects.getByKey(projectId), configs);
    }

    public static Map<String, Object> editingView(Project project, Map<String, NeutralDeviceConfig> configs) {
        final Map<String, Object> view = ProjectMapper.toResponse(project);
        final List<Map<String, Object>> rows = new ArrayList<>(ProjectMapper.toSubnetList(project));
        final Set<String> keys = new LinkedHashSet<>();
        final Set<Object> savedIds = new LinkedHashSet<>();
        rows.forEach(row -> savedIds.add(row.get("id")));
        rows.forEach(row -> keys.add(key((String) row.get("agent_id"), (Integer) row.get("vlan_id"), (String) row.get("cidr"))));
        final int savedCount = rows.size();
        configs.forEach((agent, config) -> {
            for (final PolicySubnet subnet : drafts(agent, config)) {
                if (savedIds.contains(subnet.getId())) continue;
                if (subnet.getVlanId() != null && rows.stream().anyMatch(row ->
                        agent.equals(row.get("agent_id")) && subnet.getVlanId().equals(row.get("vlan_id"))
                        && Boolean.TRUE.equals(row.get("manually_edited")))) continue;
                if (!keys.add(key(agent, subnet.getVlanId(), subnet.getCidr()))) continue;
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", subnet.getId()); row.put("vlan_id", subnet.getVlanId());
                row.put("cidr", subnet.getCidr()); row.put("name", subnet.getName());
                row.put("agent_id", agent); row.put("subnet_class", null); row.put("manually_edited", false);
                rows.add(row);
            }
        });
        view.put("subnets", rows);
        view.put("subnet_count", rows.size());
        view.put("draft", rows.size() > savedCount);
        if (rows.size() > savedCount) view.put("draft_note",
                "장비에서 수집한 VLAN/서브넷 초안입니다. 이름은 자동 생성되며 CSO는 미분류입니다. 이름·IP 대역·CSO를 확인하고 저장하세요.");
        return view;
    }

    private static String key(String agent, Integer vlan, String cidr) {
        // A VLAN number alone does not prove that two devices share a broadcast domain.
        return vlan == null ? "cidr:" + cidr : agent + ":vlan:" + vlan + ":" + cidr;
    }

    public static List<PolicySubnet> drafts(String agent, NeutralDeviceConfig config) {
        final List<PolicySubnet> result = new ArrayList<>();
        final Set<Integer> vlans = new java.util.TreeSet<>(config.getVlans().keySet());
        config.getInterfaces().values().forEach(iface -> {
            if (iface.getAccessVlan() != null) vlans.add(iface.getAccessVlan());
            vlans.addAll(iface.getTrunkVlans());
        });
        final Set<String> vlanInterfaces = new LinkedHashSet<>();
        for (final int vlan : vlans) {
            final var info = config.getVlans().get(vlan);
            final Set<String> cidrs = new LinkedHashSet<>();
            config.getInterfaces().forEach((name, iface) -> {
                if (Integer.valueOf(vlan).equals(iface.getAccessVlan())
                        || (info != null && info.getMembers().contains(name))) {
                    vlanInterfaces.add(name);
                    iface.getAddresses().forEach(address -> { final String cidr = network(address); if (cidr != null) cidrs.add(cidr); });
                }
            });
            if (cidrs.isEmpty()) cidrs.add("");
            final String name = info != null && info.getName() != null && !info.getName().isBlank()
                    ? info.getName() : "VLAN" + vlan;
            cidrs.forEach(cidr -> result.add(draft(agent, vlan, cidr, name)));
        }
        config.getInterfaces().forEach((name, iface) -> {
            if ("lo".equalsIgnoreCase(name) || vlanInterfaces.contains(name)) return;
            iface.getAddresses().forEach(address -> {
                final String cidr = network(address);
                if (cidr != null) result.add(draft(agent, null, cidr, agent + " / " + name));
            });
        });
        return result;
    }

    private static PolicySubnet draft(String agent, Integer vlan, String cidr, String name) {
        final PolicySubnet subnet = new PolicySubnet();
        subnet.setId((vlan == null ? "Subnet-" : "VLAN" + vlan + "-")
                + UUID.nameUUIDFromBytes(key(agent, vlan, cidr).getBytes(StandardCharsets.UTF_8)));
        subnet.setAgentId(agent); subnet.setVlanId(vlan); subnet.setCidr(cidr); subnet.setName(name);
        return subnet;
    }

    private static String network(String address) {
        if (address == null || address.contains(":") || address.startsWith("127.")) return null;
        try {
            PacketVariables.parseCidr(address);
            return PolicySubnet.normalizeCidr(address);
        } catch (RuntimeException ex) { return null; }
    }
}
