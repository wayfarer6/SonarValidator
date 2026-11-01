package org.sonar.sonarvalidator_backend;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Controller.AdviceExecutionController;
import org.sonar.sonarvalidator_backend.Service.policy.AdviceExecutionService;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class AdviceExecutionAuthorizationTest {
    @Test void viewerCannotDispatchAndConfirmationIsRequired() {
        var service=mock(AdviceExecutionService.class);var controller=new AdviceExecutionController(service);var mapper=new ObjectMapper();
        var body=mapper.readTree("{\"proposal_index\":0,\"request_id\":\"11111111-1111-1111-1111-111111111111\",\"confirmed\":true}");
        assertEquals(403,assertThrows(ResponseStatusException.class,()->controller.execute("p","a",body,new TestingAuthenticationToken("v","","ROLE_VIEWER"))).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->controller.execute("p","a",mapper.readTree("{\"confirmed\":false}"),new TestingAuthenticationToken("o","","ROLE_OPERATOR"))).getStatusCode().value());verifyNoInteractions(service);
    }
}
