package org.sonar.sonarvalidator_backend;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Service.ExpectedAgentService;
import org.sonar.sonarvalidator_backend.Service.ManagementServerAddress;
import org.springframework.web.server.ResponseStatusException;

class AgentDeploymentDefaultsTest {
    @Test void registeringNameInAnotherProjectCannotMoveExistingNode() {
        var repo=mock(ExpectedAgentRepository.class);var node=new ExpectedAgent();node.setAgentId("node");node.setProjectKey("lab1");
        when(repo.findByAgentId("node")).thenReturn(Optional.of(node));
        assertThrows(ResponseStatusException.class,()->new ExpectedAgentService(repo).register("node","lab2","VM","VM",null,null));
        assertEquals("lab1",node.getProjectKey());verify(repo,never()).save(any());
    }
    @Test void validServerAddressIsNormalized() {
        assertEquals("192.168.150.10",ManagementServerAddress.normalize(" 192.168.150.10 "));
        assertEquals("server.example",ManagementServerAddress.normalize("server.example"));
    }
    @Test void invalidOrInjectedServerAddressIsRejected() {
        for(String value:new String[]{"192.168.999.10","10.0.0.1;\nAGENT_NAME=other","http://server:3000","server name"})
            assertThrows(ResponseStatusException.class,()->ManagementServerAddress.normalize(value));
    }
}
