package org.sonar.sonarvalidator_backend;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.sonar.sonarvalidator_backend.Model.entity.AdviceExecution;
import org.sonar.sonarvalidator_backend.Repository.AdviceExecutionRepository;
import org.sonar.sonarvalidator_backend.Service.policy.AdviceExecutionStore;
import jakarta.persistence.EntityManager;

@DataJpaTest
class AdviceExecutionStoreTest {
    @Autowired AdviceExecutionRepository repository;@Autowired EntityManager entities;
    AdviceExecution row(){var r=new AdviceExecution();r.setExecutionId(UUID.randomUUID().toString());r.setProjectKey("p1");r.setAdviceId("a1");r.setAgentId("r1");r.setActiveAgentId("r1");r.setOperation("inspect_routes");r.setStatus("PENDING");r.setRequestedBy("operator");r.setPlanJson("{}");r.setCreatedAt(Instant.now());r.setExpiresAt(Instant.now().plusSeconds(120));return r;}
    @Test void activeNodeIsUnique() {repository.saveAndFlush(row());assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->repository.saveAndFlush(row()));}
    @Test void terminalResultReleasesNodeAndSurvivesEntityReload() {
        var service=new AdviceExecutionStore(repository);var r=service.create(row());service.dispatched(r.getExecutionId(),"socket1");
        service.transition(r.getExecutionId(),"wrong-socket","SUCCEEDED","forged",null);entities.flush();entities.clear();assertEquals("DISPATCHED",repository.findById(r.getExecutionId()).orElseThrow().getStatus());
        service.transition(r.getExecutionId(),"socket1","SUCCEEDED","verified","{\"verified\":true}");entities.flush();entities.clear();
        var saved=repository.findById(r.getExecutionId()).orElseThrow();assertEquals("SUCCEEDED",saved.getStatus());assertNull(saved.getActiveAgentId());assertNotNull(saved.getFinishedAt());
        assertNotNull(service.create(row()));
    }
}
