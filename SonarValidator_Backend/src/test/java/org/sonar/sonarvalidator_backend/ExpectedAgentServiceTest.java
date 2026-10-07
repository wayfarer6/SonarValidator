package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Service.ExpectedAgentService;
import org.springframework.web.server.ResponseStatusException;

class ExpectedAgentServiceTest {

    private ExpectedAgentRepository repository;
    private ExpectedAgentService service;

    @BeforeEach
    void setUp() {
        repository = mock(ExpectedAgentRepository.class);
        service = new ExpectedAgentService(repository);
    }

    @Test
    void deleteFromProjectRemovesExpectedAgentWhenProjectMatches() {
        final ExpectedAgent agent = expectedAgent("project-a");
        when(repository.findByAgentId("agent-1")).thenReturn(Optional.of(agent));

        service.deleteFromProject("agent-1", "project-a");

        verify(repository).delete(agent);
    }

    @Test
    void deleteFromProjectDoesNotRemoveAgentFromAnotherProject() {
        final ExpectedAgent agent = expectedAgent("project-a");
        when(repository.findByAgentId("agent-1")).thenReturn(Optional.of(agent));

        final ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteFromProject("agent-1", "project-b"));

        assertEquals(404, exception.getStatusCode().value());
        verify(repository, never()).delete(agent);
    }

    private static ExpectedAgent expectedAgent(String projectKey) {
        final ExpectedAgent agent = new ExpectedAgent();
        agent.setAgentId("agent-1");
        agent.setProjectKey(projectKey);
        return agent;
    }
}
