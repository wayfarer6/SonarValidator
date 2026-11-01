package org.sonar.sonarvalidator_backend;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.sonar.sonarvalidator_backend.Model.entity.*;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Policy.*;
import org.sonar.sonarvalidator_backend.Policy.advice.*;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Service.*;
import org.sonar.sonarvalidator_backend.Service.policy.*;

class AdviceCommandContextTest {
    final ExpectedAgentRepository expected=mock(ExpectedAgentRepository.class);
    final AgentTelemetryStore telemetry=new AgentTelemetryStore();final AgentTerminalBroker channel=mock(AgentTerminalBroker.class);
    final Project project=new Project();AdviceCommandContext context;
    @BeforeEach void setup() {
        project.setProjectKey("p1");project.setManagementPrefix("192.168.144.0/20");project.setManagementServerIp("192.168.148.180");
        var agent=new ExpectedAgent();agent.setAgentId("r1");agent.setProjectKey("p1");agent.setExpectedIp("192.168.148.181");
        when(expected.findByAgentId("r1")).thenReturn(Optional.of(agent));when(expected.findByProjectKeyOrderByCreatedAtDesc("p1")).thenReturn(List.of(agent));
        when(channel.supportsAction(anyString(),anyString())).thenReturn(true);
        var config=new NeutralDeviceConfig();config.setProduct("Linux nftables");telemetry.putConfig("r1",config);
        var subnet=new PolicySubnet();subnet.setId("data");subnet.setCidr("10.90.0.0/24");subnet.setAgentId("r1");subnet.setZoneClass(ZoneClass.SENSITIVE);project.replacePolicy(List.of(subnet),null);
        context=new AdviceCommandContext(expected,telemetry,channel);
    }
    AdviceCommandProposal proposal(String source,String target){return new AdviceCommandProposal("r1","block","reason","block_forward_ipv4",Map.of("source",source,"destination",target,"protocol","tcp","port",443),List.of());}
    @Test void sourceMustBelongToTheSelectedNodeAndManagementIsProtected() {
        assertTrue(context.evaluate(project,proposal("10.90.0.0/24","10.91.0.0/24")).executable());
        assertFalse(context.evaluate(project,proposal("10.80.0.0/24","10.91.0.0/24")).executable());
        assertFalse(context.evaluate(project,proposal("10.90.0.0/24","192.168.148.0/24")).executable());
    }
    @Test void foreignProjectAndUnknownProductCannotExecute() {
        var foreign=new ExpectedAgent();foreign.setProjectKey("other");when(expected.findByAgentId("r1")).thenReturn(Optional.of(foreign));
        assertFalse(context.evaluate(project,proposal("10.90.0.0/24","10.91.0.0/24")).executable());
    }
    @Test void contextFingerprintChangesWithPolicyButNotHeartbeat() {
        var original=context.snapshot(project,"r1").fingerprint();telemetry.touch("r1");assertEquals(original,context.snapshot(project,"r1").fingerprint());
        project.getSubnets().getFirst().setCidr("10.92.0.0/24");assertNotEquals(original,context.snapshot(project,"r1").fingerprint());
    }
    @Test void offlineOrLegacyProberKeepsTheCommandsVisibleButNotExecutable() {
        when(channel.supportsAction(anyString(),anyString())).thenReturn(false);
        var result=context.evaluate(project,proposal("10.90.0.0/24","10.91.0.0/24"));assertNotNull(result.plan());assertFalse(result.executable());
    }
}
