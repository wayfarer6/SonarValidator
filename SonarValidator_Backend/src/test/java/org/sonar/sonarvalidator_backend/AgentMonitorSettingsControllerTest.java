package org.sonar.sonarvalidator_backend;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Controller.AgentMonitorSettingsController;
import org.sonar.sonarvalidator_backend.Service.AgentMonitorSettingsService;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class AgentMonitorSettingsControllerTest {
    @Test void rejectsMalformedAndFractionalNumbersBeforeSaving() {
        var service=mock(AgentMonitorSettingsService.class);var controller=new AgentMonitorSettingsController(service);
        var json=new ObjectMapper();
        for (String seconds:new String[]{"0","-1","1.5","86401","\"30\"","true","18446744073709551617"}) {
            var body=json.readTree("{\"monitor_interval_seconds\":"+seconds+",\"revision\":0}");
            assertEquals(400,assertThrows(ResponseStatusException.class,()->controller.update("n1","p1",body)).getStatusCode().value());
        }
        verifyNoInteractions(service);
    }
    @Test void returnsDeliverySeparatelyFromConfirmedApplication() {
        var service=mock(AgentMonitorSettingsService.class);var controller=new AgentMonitorSettingsController(service);
        when(service.deliver("n1")).thenReturn(true);
        when(service.status("n1","p1")).thenReturn(new LinkedHashMap<>(Map.of("status","pending")));
        var result=controller.update("n1","p1",new ObjectMapper().readTree("{\"monitor_interval_seconds\":5,\"revision\":0}"));
        assertEquals(true,result.get("delivered"));assertEquals("pending",result.get("status"));
        verify(service).save("n1","p1",5,0);
    }
}
