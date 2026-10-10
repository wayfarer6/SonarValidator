package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.dto.ProjectMapper;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.sonar.sonarvalidator_backend.Service.ProjectSubnetAutoSyncService;

/**
 * 수집된 텔레메트리 → 프로젝트 서브넷 자동 반영 규칙을 검증합니다.
 *
 * <p>여기서 지키려는 계약은 세 가지입니다.
 * <ol>
 *   <li>장치가 붙으면 대역이 <b>자동으로</b> 생긴다 (운영자가 아무것도 안 해도)</li>
 *   <li>관리망과 주소 없는 VLAN 은 <b>들어오지 않는다</b></li>
 *   <li>운영자가 손댄 것(수동 편집·다른 장치)은 <b>건드리지 않는다</b></li>
 * </ol>
 */
class ProjectSubnetAutoSyncServiceTest {

    private ProjectRepository projects;
    private ExpectedAgentRepository expected;
    private ProjectSubnetAutoSyncService service;
    private Project project;

    @BeforeEach
    void setUp() {
        projects = mock(ProjectRepository.class);
        expected = mock(ExpectedAgentRepository.class);
        service = new ProjectSubnetAutoSyncService(projects, expected);
        project = new Project();
        project.setProjectKey("PRJ-1");
        project.setName("RVI-Network");
        // 이 랩의 관리망: 서버 10.20.0.3 → 10.20.0.0/24 가 관리망으로 도출됩니다.
        project.setManagementServerIp("10.20.0.3");
        when(projects.findByProjectKey("PRJ-1")).thenReturn(Optional.of(project));
    }

    /** 프로젝트에 에이전트를 등록합니다. */
    private void expectAgentInProject(String agentId) {
        final ExpectedAgent agent = new ExpectedAgent();
        agent.setAgentId(agentId);
        agent.setProjectKey("PRJ-1");
        when(expected.findByAgentId(agentId)).thenReturn(Optional.of(agent));
    }

    /**
     * Arista 스위치 형태의 수집 결과를 만듭니다.
     *
     * <p>실측한 랩 장비를 그대로 반영했습니다 — SVI 는 {@code Vlan8}/{@code Vlan9}
     * 이고, 관리 인터페이스는 {@code Management1}, VLAN 멤버는
     * {@code Et2}/{@code Et3} 입니다. (SVI 이름과 멤버 이름이 다릅니다)
     */
    private static NeutralDeviceConfig aristaConfig() {
        final NeutralDeviceConfig config = new NeutralDeviceConfig();
        config.setHostname("Arista-Switch");
        config.setFormat("ARISTA_vEOS");

        vlan(config, 8, "VLAN8", "Et2");
        vlan(config, 9, "VLAN9", "Et3");
        vlan(config, 99, "TRANSIT");

        address(config, "Vlan8", "10.0.8.1/24");
        address(config, "Vlan9", "10.0.9.1/24");
        address(config, "Ethernet1", "172.18.10.2/24");
        // 관리 인터페이스 — 자동 반영에서 빠져야 합니다.
        address(config, "Management1", "10.20.0.4/24");
        address(config, "lo", "127.0.0.1/8");
        return config;
    }

    private static void vlan(NeutralDeviceConfig config, int id, String name, String... members) {
        final var vlan = config.vlanOrCreate(id);
        vlan.setName(name);
        vlan.setStatus("active");
        vlan.getMembers().addAll(List.of(members));
    }

    private static void address(NeutralDeviceConfig config, String name, String... addresses) {
        final var iface = config.interfaceOrCreate(name);
        iface.getAddresses().addAll(List.of(addresses));
    }

    /** 프로젝트에 저장된 서브넷을 CIDR 로 찾습니다. */
    private ProjectSubnet subnetOf(String cidr) {
        for (final ProjectSubnet subnet : project.getSubnets()) {
            if (cidr.equals(subnet.getCidr())) {
                return subnet;
            }
        }
        return null;
    }

    @Test
    @DisplayName("수집된 대역이 자동으로 프로젝트 서브넷이 된다 (등급 Open)")
    void createsSubnetsFromCollectedTelemetry() {        expectAgentInProject("Arista-Switch");
        assertTrue(service.syncForAgent("Arista-Switch", aristaConfig()));

        // ⚠️ VLAN 으로 라우팅되는 장비는 **VLAN 대역만** 씁니다.
        //    물리 포트 대역(Ethernet1=172.18.10.0/24)까지 만들면 같은 망이
        //    인터페이스 이름으로 목록에 중복됩니다.
        assertEquals(2, project.getSubnets().size(), "VLAN8 · VLAN9 (VLAN 기반만)");
        assertNull(subnetOf("172.18.10.0/24"), "VL​AN 장비의 물리 포트 대역은 제외됩니다");
        final ProjectSubnet vlan8 = subnetOf("10.0.8.0/24");
        assertNotNull(vlan8, "VLAN8 SVI 대역이 생겨야 합니다");
        assertEquals(ZoneClass.OPEN, vlan8.getZoneClass(), "등급은 안전한 쪽(Open)으로 채웁니다");
        assertEquals("Arista-Switch", vlan8.getAgentId());
        assertEquals(8, vlan8.getVlanId());
        assertFalse(vlan8.isManuallyEdited(), "자동 생성분으로 표시되어야 합니다");

        verify(projects).save(project);
    }

    @Test
    @DisplayName("관리망과 주소 없는 VLAN 은 서브넷이 되지 않는다")
    void skipsManagementNetworkAndAddresslessVlans() {
        expectAgentInProject("Arista-Switch");

        service.syncForAgent("Arista-Switch", aristaConfig());

        assertNull(subnetOf("10.20.0.0/24"), "관리망(Management1)은 정책 대상이 아닙니다");
        assertNull(subnetOf("127.0.0.0/8"), "루프백은 대역으로 만들지 않습니다");
        // VLAN 99(TRANSIT)는 SVI 주소가 없어 대역을 알 수 없습니다.
        final boolean hasEmptyCidr = project.getSubnets().stream()
                .anyMatch(subnet -> subnet.getCidr() == null || subnet.getCidr().isBlank());
        assertFalse(hasEmptyCidr, "주소 없는 VLAN 을 빈 대역으로 넣으면 정책이 깨집니다");
    }

    @Test
    @DisplayName("VLAN 이 없는 라우터는 인터페이스 대역을 그대로 쓴다")
    void keepsInterfaceSubnetsWhenDeviceHasNoVlans() {
        expectAgentInProject("Cisco-Router");
        final NeutralDeviceConfig router = new NeutralDeviceConfig();
        router.setHostname("Cisco-Router");
        address(router, "GigabitEthernet1", "192.168.122.254/32");
        address(router, "GigabitEthernet2", "172.128.0.1/32");
        address(router, "lo", "127.0.0.1/8");

        assertTrue(service.syncForAgent("Cisco-Router", router));

        // 라우터는 VLAN 이 없으므로 인터페이스 대역이 유일한 정보입니다.
        assertNotNull(subnetOf("192.168.122.254/32"), "인터페이스 대역이 반영되어야 합니다");
        assertNotNull(subnetOf("172.128.0.1/32"));
        assertNull(subnetOf("127.0.0.0/8"), "루프백은 대역으로 만들지 않습니다");
    }

    @Test
    @DisplayName("운영자가 등급을 바꾼 대역이 중복 행으로 늘어나지 않는다")
    void doesNotDuplicateOperatorEditedSubnet() {
        expectAgentInProject("Arista-Switch");
        service.syncForAgent("Arista-Switch", aristaConfig());

        // 운영자가 VLAN8 을 손댑니다. (manually_edited = true)
        final ProjectSubnet vlan8 = subnetOf("10.0.8.0/24");
        vlan8.setManuallyEdited(true);
        vlan8.setZoneClass(ZoneClass.SENSITIVE);

        // 다음 수집 주기 — 같은 대역이 "보존 대상"과 "초안" 양쪽으로 계산됩니다.
        service.syncForAgent("Arista-Switch", aristaConfig());

        final long vlan8Rows = project.getSubnets().stream()
                .filter(subnet -> "10.0.8.0/24".equals(subnet.getCidr()))
                .count();
        assertEquals(1, vlan8Rows,
                "같은 subnet_id 가 두 번 저장되면 대역 드롭다운에 CIDR 이 두 번 뜹니다");
        assertEquals(ZoneClass.SENSITIVE, subnetOf("10.0.8.0/24").getZoneClass(),
                "운영자가 고른 등급이 남아야 합니다");
    }

    @Test
    @DisplayName("replacePolicy 는 같은 식별자를 한 번만 저장한다")
    void replacePolicyIgnoresDuplicateIdentities() {
        final PolicySubnet first = PolicySubnet.of("VLAN8-dup", "10.0.8.0/24", ZoneClass.SENSITIVE);
        final PolicySubnet second = PolicySubnet.of("VLAN8-dup", "10.0.8.0/24", ZoneClass.OPEN);

        project.replacePolicy(new ArrayList<>(List.of(first, second)), null);

        assertEquals(1, project.getSubnets().size(), "자동 증가 PK 만 다른 중복 행을 만들지 않습니다");
        assertEquals(ZoneClass.SENSITIVE, project.getSubnets().getFirst().getZoneClass(),
                "먼저 온 항목이 남습니다");
    }

    @Test
    @DisplayName("응답 목록은 과거 중복 행이 있어도 대역을 한 번만 낸다")
    void responseListDeduplicatesLegacyRows() {
        final PolicySubnet saved = PolicySubnet.of("VLAN8-legacy", "10.0.8.0/24", ZoneClass.OPEN);
        project.replacePolicy(new ArrayList<>(List.of(saved)), null);
        // PK 만 다른 중복 행이 DB 에 남아 있는 상황을 흉내냅니다.
        project.getSubnets().add(ProjectSubnet.from(saved));

        final var rows = ProjectMapper.toSubnetList(project);

        assertEquals(1, rows.size(), "화면·규칙 드롭다운에 같은 대역이 두 번 뜨면 안 됩니다");
    }

    @Test
    @DisplayName("프로젝트에 없는 Agent 는 반영하지 않는다")
    void ignoresAgentWithoutProject() {
        when(expected.findByAgentId("Arista-Switch")).thenReturn(Optional.empty());

        assertFalse(service.syncForAgent("Arista-Switch", aristaConfig()));
        assertTrue(project.getSubnets().isEmpty());
        verify(projects, never()).save(project);
    }

    @Test
    @DisplayName("같은 수집이 반복되면 저장하지 않는다 (30초 주기 쓰기 방지)")
    void doesNotWriteWhenNothingChanged() {
        expectAgentInProject("Arista-Switch");

        assertTrue(service.syncForAgent("Arista-Switch", aristaConfig()));
        assertFalse(service.syncForAgent("Arista-Switch", aristaConfig()), "두 번째는 변화 없음");

        verify(projects).save(project);
    }

    @Test
    @DisplayName("운영자가 지정한 등급은 재수집해도 유지된다")
    void preservesZoneClassChosenByOperator() {
        expectAgentInProject("Arista-Switch");
        service.syncForAgent("Arista-Switch", aristaConfig());

        // 운영자가 VLAN9 를 기밀망으로 올립니다.
        final ProjectSubnet vlan9 = subnetOf("10.0.9.0/24");
        vlan9.setZoneClass(ZoneClass.CONFIDENTIAL);

        service.syncForAgent("Arista-Switch", aristaConfig());

        assertEquals(ZoneClass.CONFIDENTIAL, subnetOf("10.0.9.0/24").getZoneClass(),
                "다시 수집했다고 등급을 Open 으로 되돌리면 안 됩니다");
    }

    @Test
    @DisplayName("수동 편집한 서브넷은 수집에서 사라져도 지우지 않는다")
    void keepsManuallyEditedSubnet() {
        expectAgentInProject("Arista-Switch");
        service.syncForAgent("Arista-Switch", aristaConfig());

        final ProjectSubnet vlan9 = subnetOf("10.0.9.0/24");
        vlan9.setManuallyEdited(true);

        // 장치에서 VLAN9 SVI 가 사라졌습니다.
        final NeutralDeviceConfig shrunk = aristaConfig();
        shrunk.getInterfaces().remove("Vlan9");
        shrunk.getVlans().remove(9);
        service.syncForAgent("Arista-Switch", shrunk);

        assertNotNull(subnetOf("10.0.9.0/24"), "운영자가 손댄 대역은 보존되어야 합니다");
    }

    @Test
    @DisplayName("수집에서 사라진 자동 생성 대역은 함께 지운다")
    void removesAutoSubnetNoLongerCollected() {
        expectAgentInProject("Arista-Switch");
        service.syncForAgent("Arista-Switch", aristaConfig());
        assertNotNull(subnetOf("10.0.9.0/24"));

        final NeutralDeviceConfig shrunk = aristaConfig();
        shrunk.getInterfaces().remove("Vlan9");
        shrunk.getVlans().remove(9);
        assertTrue(service.syncForAgent("Arista-Switch", shrunk));

        assertNull(subnetOf("10.0.9.0/24"),
                "없는 대역이 정책에 남으면 유령 위반 판정이 계속 납니다");
        assertNotNull(subnetOf("10.0.8.0/24"), "다른 대역은 유지");
    }

    @Test
    @DisplayName("다른 장치가 만든 서브넷은 건드리지 않는다")
    void doesNotTouchSubnetsOfOtherAgents() {
        expectAgentInProject("Arista-Switch");
        // 다른 장치가 만든 서브넷을 미리 넣어 둡니다.
        final PolicySubnet foreign = PolicySubnet.of("Subnet-0009", "192.168.50.0/24", ZoneClass.SENSITIVE);
        foreign.setAgentId("Cisco-Router");
        project.replacePolicy(new ArrayList<>(List.of(foreign)), null);

        service.syncForAgent("Arista-Switch", aristaConfig());

        final ProjectSubnet kept = subnetOf("192.168.50.0/24");
        assertNotNull(kept, "다른 장치 대역은 소유자가 아니므로 유지");
        assertEquals("Cisco-Router", kept.getAgentId());
        assertEquals(ZoneClass.SENSITIVE, kept.getZoneClass());
    }

    @Test
    @DisplayName("수집 설정이 비어 있어도 예외를 던지지 않는다 (수신 경로 보호)")
    void neverThrowsOnBadInput() {
        // 프로젝트 조회가 터져도 텔레메트리 수신이 막히면 안 됩니다.
        when(projects.findByProjectKey("PRJ-1")).thenThrow(new IllegalStateException("db down"));
        expectAgentInProject("Arista-Switch");

        assertFalse(service.syncForAgent("Arista-Switch", aristaConfig()));
        assertFalse(service.syncForAgent(null, aristaConfig()));
        assertFalse(service.syncForAgent("Arista-Switch", null));
    }
}
