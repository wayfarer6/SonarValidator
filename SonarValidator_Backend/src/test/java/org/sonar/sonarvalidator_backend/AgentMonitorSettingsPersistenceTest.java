package org.sonar.sonarvalidator_backend;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Service.AgentMonitorSettingsService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest
class AgentMonitorSettingsPersistenceTest {
    @Autowired ExpectedAgentRepository repository;
    @Autowired EntityManager entities;
    @Test void intervalAndRevisionRemainAfterReloadingTheEntity() {
        var node=new ExpectedAgent();node.setAgentId("settings-persist");node.setProjectKey("p1");
        repository.saveAndFlush(node);entities.clear();
        var sessions=new AgentSessionRegistry(new ObjectMapper());
        var service=new AgentMonitorSettingsService(repository,sessions);
        service.save("settings-persist","p1",123,0);entities.flush();entities.clear();
        var fresh=repository.findByAgentId("settings-persist").orElseThrow();
        assertEquals(123,fresh.getMonitorIntervalSeconds());assertEquals(1,fresh.getMonitorRevision());
    }
}
