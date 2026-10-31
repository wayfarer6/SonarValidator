package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.dto.ProjectDto;
import org.sonar.sonarvalidator_backend.Model.dto.ProjectMapper;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import tools.jackson.databind.json.JsonMapper;

class SubnetNetworkRoleTest {
    @Test
    void keepsNetworkRoleSeparateFromCsoAcrossApiAndEntityMapping() {
        var payload = JsonMapper.builder().build().readValue("""
            {"id":"S1","cidr":"10.0.0.0/24","network_role":"TRANSIT",
             "subnet_class":"Sensitive","agent_id":"R1","manually_edited":true}
            """, ProjectDto.SubnetPayload.class);
        var entity = ProjectSubnet.from(payload.toPolicySubnet());
        var project = new Project();
        project.getSubnets().add(entity);
        var result = ProjectMapper.toSubnetList(project).getFirst();
        assertEquals("TRANSIT", result.get("network_role"));
        assertEquals("Sensitive", result.get("subnet_class"));
        assertEquals("TRANSIT", entity.toPolicySubnet().getNetworkRole());
    }

    @Test
    void rejectsUnknownRoleInsteadOfStoringAFalseClassification() {
        var payload = new ProjectDto.SubnetPayload("S1", "10.0.0.0/24", null, "test", "R1", true, null, null, "ANYTHING");
        assertThrows(IllegalArgumentException.class, payload::toPolicySubnet);
    }
}
