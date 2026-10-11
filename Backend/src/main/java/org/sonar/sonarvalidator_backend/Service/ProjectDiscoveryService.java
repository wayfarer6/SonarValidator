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
        final Set<String> management = ProjectSubnetAutoSyncService.managementNetworks(project);
        final List<Map<String, Object>> rows = new ArrayList<>(ProjectMapper.toSubnetList(project));
        final Set<String> keys = new LinkedHashSet<>();
        final Set<Object> savedIds = new LinkedHashSet<>();
        rows.forEach(row -> savedIds.add(row.get("id")));
        rows.forEach(row -> keys.add(key((String) row.get("agent_id"), (Integer) row.get("vlan_id"), (String) row.get("cidr"))));
        final int savedCount = rows.size();
        configs.forEach((agent, config) -> {
            for (final PolicySubnet subnet : drafts(agent, config)) {
                // ⚠️ 관리망은 자동 동기화와 **같은 규칙**으로 걸러야 합니다.
                //    아니면 관리 인터페이스 대역이 매 주기 목록에서 나타났다
                //    사라지며 저장이 반복됩니다(added/changed 가 계속 잡힘).
                if (ProjectSubnetAutoSyncService.isManagement(
                        subnet.getCidr(), management)) {
                    continue;
                }
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
        // ⚠️ SVI 이름으로도 VLAN 을 찾습니다.
        //    실측한 Arista vEOS 는 VLAN 8 의 SVI 를 `Vlan8` 이라는 이름의
        //    인터페이스로 둡니다. 그런데 `vlan 8` 의 멤버에는 `Vlan8` 이
        //    들어 있지 않아(Et2, Cpu 만), 이름을 보지 않으면 SVI 주소가
        //    <b>VLAN 과 연결되지 않은 채</b> 별도 대역으로 생깁니다.
        //    그러면 화면에서 "VLAN8 대역" 이 어느 VLAN 인지 알 수 없습니다.
        config.getInterfaces().keySet().forEach(name -> {
            final Integer byName = vlanIdFromInterfaceName(name);
            if (byName != null) vlans.add(byName);
        });
        final Set<String> vlanInterfaces = new LinkedHashSet<>();
        for (final int vlan : vlans) {
            final var info = config.getVlans().get(vlan);
            final Set<String> cidrs = new LinkedHashSet<>();
            config.getInterfaces().forEach((name, iface) -> {
                if (Integer.valueOf(vlan).equals(iface.getAccessVlan())
                        || Integer.valueOf(vlan).equals(vlanIdFromInterfaceName(name))
                        || (info != null && info.getMembers().contains(name))) {
                    vlanInterfaces.add(name);
                    iface.getAddresses().forEach(address -> { final String cidr = network(address); if (cidr != null) cidrs.add(cidr); });
                }
            });
            if (cidrs.isEmpty()) {
                // 주소가 없는 L2 전용 VLAN 은 대역을 알 수 없습니다. 그대로 두면
                // 편집 화면에 빈 CIDR 행으로 쌓여(실측: VLAN1=default, VLAN99=TRANSIT)
                // 어느 것이 실제 정책 대상인지 구분되지 않습니다. 초안을 만들지 않습니다.
                continue;
            }
            final String name = info != null && info.getName() != null && !info.getName().isBlank()
                    ? info.getName() : "VLAN" + vlan;
            cidrs.forEach(cidr -> result.add(draft(agent, vlan, cidr, name)));
        }
        // ⚠️ VLAN 으로 라우팅되는 장비는 **VLAN 대역만** 씁니다.
        //
        //    스위치의 물리 포트 대역(Ethernet1, Management1)까지 초안으로 만들면
        //    같은 망이 인터페이스 이름으로 한 번 더 생겨 목록이 중복됩니다.
        //    (실측 Arista: VLAN8/VLAN9 가 정상인데 Ethernet1=172.18.10.0/24,
        //     Management1=10.20.0.0/24 까지 별도 대역으로 올라왔습니다.)
        //
        //    반대로 VLAN 이 하나도 없는 장비(라우터)는 인터페이스 대역이
        //    유일한 정보이므로 그대로 만듭니다.
        if (!vlans.isEmpty())
        {
            return result;
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

    /**
     * 인터페이스 이름에서 VLAN 번호를 얻습니다. (예: {@code Vlan8} → 8)
     *
     * <p>Aruba/ArubaOS, Cisco, Arista 모두 SVI 를 {@code Vlan<id>} 로
     * 만드는 관례를 따릅니다. 대소문자는 구분하지 않습니다.
     *
     * @param name 인터페이스 이름
     * @return VLAN 번호, 이름이 SVI 형태가 아니면 {@code null}
     */
    private static Integer vlanIdFromInterfaceName(String name) {
        if (name == null || name.length() <= 4) return null;
        if (!name.regionMatches(true, 0, "vlan", 0, 4)) return null;
        final String digits = name.substring(4);
        for (int i = 0; i < digits.length(); ++i) {
            if (!Character.isDigit(digits.charAt(i))) return null;
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
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
