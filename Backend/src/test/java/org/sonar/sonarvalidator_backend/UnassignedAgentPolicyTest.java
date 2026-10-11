package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Service.PolicyRegistryService;

class UnassignedAgentPolicyTest {
    @ParameterizedTest
    @EnumSource(DeviceType.class)
    void externalEnforcementKeepsCollectionPolicyReadOnly(DeviceType type) {
        var repository = org.sonar.sonarvalidator_backend.support.StubRepository.of(
                org.sonar.sonarvalidator_backend.Repository.ProjectRepository.class,
                (method, args) -> { throw new AssertionError("Must not compile project commands in external mode"); });
        var service = new PolicyRegistryService(repository);
        service.setAutomaticEnforcementEnabled(false);
        var policy = service.forAgent("assigned", type, "assigned", null, null);
        assertEquals("external-enforcement", policy.path("summary").path("source").asText());
        assertEquals(0, policy.path("policies").size());
    }

    @ParameterizedTest
    @EnumSource(DeviceType.class)
    void registeringAnUnassignedAgentDoesNotConfigureItsNetwork(DeviceType type) {
        var policy = new PolicyRegistryService().forAgent("unassigned", type, "unassigned", null, null);
        assertEquals("unassigned", policy.path("device_id").asText());
        assertEquals("default", policy.path("summary").path("source").asText());
        assertTrue(policy.path("policies").isArray());
        assertEquals(0, policy.path("policies").size(), "Collection must not apply example device rules");
    }
}
