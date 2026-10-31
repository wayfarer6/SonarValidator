package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.dto.ProjectDto;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.sonar.sonarvalidator_backend.Service.ProjectService;
import org.springframework.web.server.ResponseStatusException;

class BddApiInputTest {
    @Test
    void draftValidationPreservesNullEntriesAsErrors() {
        var service = new ProjectService(mock(ProjectRepository.class), null, null);
        var report = service.validate(new ProjectDto.ValidateRequest(
                Arrays.asList((ProjectDto.SubnetPayload) null), Arrays.asList((ProjectDto.RulePayload) null)));
        assertThat(report.isModelValid()).isFalse();
        assertThat(report.isCompliant()).isFalse();
        assertThat(report.getInputErrors()).hasSize(2);
    }

    @Test
    void saveCannotSilentlyDiscardDuplicateOrNullSubnets() {
        var repository = mock(ProjectRepository.class);
        when(repository.findByProjectKey("test")).thenReturn(Optional.of(new Project()));
        var service = new ProjectService(repository, null, null);
        var subnet = new ProjectDto.SubnetPayload("same", "10.0.0.0/24", "Sensitive", null, null, true);
        for (var subnets : List.of(List.of(subnet, subnet), Arrays.asList((ProjectDto.SubnetPayload) null))) {
            var request = new ProjectDto.UpdateRequest(null, null, null, null, null, null, null, subnets, null);
            assertThatThrownBy(() -> service.update("test", request)).isInstanceOfSatisfying(
                    ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
        }
        verify(repository, never()).save(any());
    }
}
