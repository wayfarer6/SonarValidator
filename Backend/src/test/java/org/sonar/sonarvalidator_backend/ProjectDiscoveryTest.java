package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.Config.Vendors.OpenVSwitchConfigParser;
import org.sonar.sonarvalidator_backend.Model.dto.ProjectDto;
import org.sonar.sonarvalidator_backend.Model.dto.ProjectMapper;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Service.ProjectDiscoveryService;
import tools.jackson.databind.json.JsonMapper;

class ProjectDiscoveryTest {
    private NeutralDeviceConfig collected() {
        // Actual Prober envelope payload keys, including a VLAN carried only on a trunk.
        return new OpenVSwitchConfigParser().parse("switch", "OpenVSwitch", JsonMapper.builder().build().readTree("""
            {"agent":"switch", "product":"OpenVSwitch", "device_type":"SWITCH",
             "vlan_status":{"vlans":[{"vlan_id":131,"name":"VLAN131","ports":["eth1"]}]},
             "trunk_status":{"ports":[{"name":"eth0","mode":"trunk","trunk_vlans":[131,132]},
                                         {"name":"eth1","mode":"access","access_vlan":131}]}}
            """));
    }

    /**
     * 주소가 없는 L2 전용 VLAN 은 초안이 되지 않습니다.
     *
     * <p>대역을 알 수 없으므로 정책에 쓸 수 없고, 그대로 두면 편집 화면에
     * 빈 CIDR 행만 쌓입니다. (실측 Arista: VLAN1=default, VLAN99=TRANSIT)
     */
    @Test void addresslessVlansProduceNoDrafts() {
        final var drafts = ProjectDiscoveryService.drafts("switch", collected());
        assertTrue(drafts.isEmpty(),
                "VLAN131(주소 없음)·VLAN132(trunk 전용)은 대역을 알 수 없어 초안이 되지 않습니다");
    }

    @Test void namesClassesAndVlanIdentityRoundTripAndSurviveNewTelemetry() {
        final var config = collected();
        // VLAN131 에 라우팅 주소가 있어야 초안이 생깁니다.
        config.getInterfaces().get("eth1").getAddresses().add("10.10.131.1/24");
        final var draft = ProjectDiscoveryService.drafts("switch",config).getFirst();
        final var payload = new ProjectDto.SubnetPayload(draft.getId(), draft.getCidr(), "Sensitive", "업무망",
                "switch", true, 131);
        final var saved = ProjectSubnet.from(payload.toPolicySubnet());
        assertEquals(131, saved.getVlanId()); assertEquals(ZoneClass.SENSITIVE, saved.getZoneClass());
        final var project = new Project(); project.setProjectKey("test");
        project.getSubnets().add(saved);
        final var view = ProjectDiscoveryService.editingView(project, Map.of("switch",config));
        final var rows = (List<?>) view.get("subnets");
        assertEquals(1, rows.size());
        final var row = (Map<?,?>) rows.getFirst();
        assertEquals("업무망",row.get("name")); assertEquals("Sensitive",row.get("subnet_class"));
        assertEquals(131,row.get("vlan_id")); assertEquals("10.10.131.0/24",row.get("cidr"));
        assertEquals("업무망",ProjectMapper.toSubnetList(project).getFirst().get("name"));
    }

    @Test void incompleteVlanCannotBeReportedAsCompliant() {
        // 주소 없는 VLAN 은 초안이 되지 않으므로, 대역을 못 채운 서브넷을
        // 직접 만들어 "미완성은 적합할 수 없다" 계약을 지킵니다.
        final var incomplete = new org.sonar.sonarvalidator_backend.Policy.PolicySubnet();
        incomplete.setId("VLAN132-incomplete");
        incomplete.setVlanId(132);
        incomplete.setCidr("");
        final var report = new SegmentationBddEngine().validate(List.of(incomplete),List.of());
        assertFalse(report.isCompliant()); assertFalse(report.getViolations().isEmpty());
    }

    @Test void routedVlanUsesOnlyItsOwnInterfaceAddress() {
        final var config = collected();
        final var iface = config.getInterfaces().get("eth1");
        iface.getAddresses().add("10.10.131.1/24");
        final var rows = ProjectDiscoveryService.drafts("router",config);
        assertEquals(1, rows.size(), "주소 없는 VLAN132 는 초안이 되지 않습니다");
        assertEquals("10.10.131.0/24",rows.getFirst().getCidr());
        assertNull(rows.getFirst().getZoneClass());
    }
}
