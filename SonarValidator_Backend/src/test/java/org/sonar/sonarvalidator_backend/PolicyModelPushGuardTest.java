package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Controller.PolicyManagement;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Policy.*;
import org.sonar.sonarvalidator_backend.Service.*;
import org.sonar.sonarvalidator_backend.Service.policy.PolicyPushNotifier;

class PolicyModelPushGuardTest {
    @Test
    void forceCannotSendAnInvalidModelToAnyAgent() {
        var projects = mock(ProjectService.class);
        var sessions = mock(AgentSessionRegistry.class);
        var quarantine = mock(QuarantineService.class);
        var notifications = mock(NotificationService.class);
        var notifier = mock(PolicyPushNotifier.class);
        var invalid = new SegmentationBddEngine().validate(
                List.of(PolicySubnet.of("C", "broken-cidr", ZoneClass.CONFIDENTIAL)), List.of());
        when(projects.getByKey("test")).thenReturn(mock(Project.class));
        when(projects.validateStored("test")).thenReturn(invalid);
        var controller = new PolicyManagement(projects, sessions, quarantine, notifications, notifier);
        for (boolean force : List.of(false, true)) {
            assertThat(controller.push("test", force)).containsEntry("pushed", false)
                    .containsEntry("model_valid", false);
        }
        verifyNoInteractions(sessions, quarantine, notifications, notifier);
    }
}
