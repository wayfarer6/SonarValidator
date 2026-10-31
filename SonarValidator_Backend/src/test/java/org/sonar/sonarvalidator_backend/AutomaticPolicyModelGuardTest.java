package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Policy.*;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.sonar.sonarvalidator_backend.Service.PolicyRegistryService;

class AutomaticPolicyModelGuardTest {
    @Test
    void invalidModelProducesNoProjectCommandsForAutomaticRequests() {
        for (String cidr : List.of("broken-network", "10.0.0.0/24/garbage", "10.0.0.0/24")) {
            var project = new Project();
            project.setProjectKey("test");
            var subnet = PolicySubnet.of("S", cidr, cidr.equals("10.0.0.0/24") ? null : ZoneClass.SENSITIVE);
            subnet.setAgentId("agent");
            project.replacePolicy(List.of(subnet), List.of());
            var repository = mock(ProjectRepository.class);
            when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(project));
            var policy = new PolicyRegistryService(repository).forAgent("agent", DeviceType.ROUTER, "agent", null, null);
            assertThat(policy.path("model_valid").asBoolean()).isFalse();
            assertThat(policy.path("summary").path("source").asString()).isEqualTo("invalid-project-model");
            assertThat(policy.path("policies").size()).isZero();
            assertThat(policy.has("acl_apply")).isFalse();
            assertThat(policy.path("input_errors").size()).isGreaterThan(0);
        }
    }
}
