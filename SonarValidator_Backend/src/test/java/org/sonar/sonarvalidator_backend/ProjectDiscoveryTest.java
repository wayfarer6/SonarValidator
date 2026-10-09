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

    @Test void preservesL2VlansAndUnclassifiedStateWithoutInventingNetworks() {
        final var drafts = ProjectDiscoveryService.drafts("switch", collected());
        assertEquals(2, drafts.size());
        assertEquals(List.of(131,132), drafts.stream().map(s -> s.getVlanId()).toList());
        assertTrue(drafts.stream().allMatch(s -> s.getCidr().isEmpty() && s.getZoneClass() == null));
        assertEquals("VLAN131", drafts.getFirst().getName());
        assertEquals("VLAN132", drafts.getLast().getName());
        assertEquals(drafts.getFirst().getId(), ProjectDiscoveryService.drafts("switch",collected()).getFirst().getId());
    }

    @Test void namesClassesAndVlanIdentityRoundTripAndSurviveNewTelemetry() {
        final var draft = ProjectDiscoveryService.drafts("switch",collected()).getFirst();
        final var payload = new ProjectDto.SubnetPayload(draft.getId(), "", "Sensitive", "업무망",
                "switch", true, 131);
        final var saved = ProjectSubnet.from(payload.toPolicySubnet());
        assertEquals(131, saved.getVlanId()); assertEquals(ZoneClass.SENSITIVE, saved.getZoneClass());
        final var project = new Project(); project.setProjectKey("test");
        project.getSubnets().add(saved);
        final var view = ProjectDiscoveryService.editingView(project, Map.of("switch",collected()));
        final var rows = (List<?>) view.get("subnets");
        assertEquals(2, rows.size());
        final var row = (Map<?,?>) rows.getFirst();
        assertEquals("업무망",row.get("name")); assertEquals("Sensitive",row.get("subnet_class"));
        assertEquals(131,row.get("vlan_id")); assertEquals("",row.get("cidr"));
        assertEquals("업무망",ProjectMapper.toSubnetList(project).getFirst().get("name"));
    }

    @Test void incompleteVlanCannotBeReportedAsCompliant() {
        final var draft = ProjectDiscoveryService.drafts("switch",collected());
        final var report = new SegmentationBddEngine().validate(draft,List.of());
        assertFalse(report.isCompliant()); assertFalse(report.getViolations().isEmpty());
    }

    @Test void routedVlanUsesOnlyItsOwnInterfaceAddress() {
        final var config = collected();
        final var iface = config.getInterfaces().get("eth1");
        iface.getAddresses().add("10.10.131.1/24");
        final var rows = ProjectDiscoveryService.drafts("router",config);
        assertEquals("10.10.131.0/24",rows.getFirst().getCidr());
        assertEquals("",rows.getLast().getCidr());
        assertNull(rows.getFirst().getZoneClass());
    }
}
