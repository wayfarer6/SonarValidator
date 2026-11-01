package org.sonar.sonarvalidator_backend;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.entity.*;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Policy.*;
import org.sonar.sonarvalidator_backend.Policy.advice.*;
import org.sonar.sonarvalidator_backend.Repository.*;
import org.sonar.sonarvalidator_backend.Service.*;
import org.sonar.sonarvalidator_backend.Service.ai.*;
import org.sonar.sonarvalidator_backend.Service.policy.*;
import tools.jackson.databind.ObjectMapper;

class AdviceProviderPipelineTest {
    @Test void mockHttpProviderFeedsActualPromptParserPersistenceAndEligibility() throws Exception {
        var json=new ObjectMapper();var request=new AtomicReference<String>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String answer="{\"risk_level\":\"MEDIUM\",\"summary\":\"[Mock] 설정 경고를 확인하세요\",\"root_cause\":\"수집 경고\",\"command_proposals\":[{\"agent_id\":\"r1\",\"title\":\"경로 확인\",\"reason\":\"원인 확인\",\"operation\":\"inspect_routes\",\"arguments\":{},\"commands\":[\"show ip route\"]}]}";
        server.createContext("/v1/chat/completions",exchange->{request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] bytes=json.writeValueAsBytes(Map.of("choices",List.of(Map.of("message",Map.of("content",answer)))));exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try {
            var projects=mock(ProjectService.class);var project=new Project();project.setProjectKey("p1");project.setName("Mock lab");when(projects.getByKey("p1")).thenReturn(project);var report=new SegmentationBddEngine.Report();report.setCompliant(false);when(projects.validateStored("p1")).thenReturn(report);
            var expected=mock(ExpectedAgentRepository.class);var node=new ExpectedAgent();node.setAgentId("r1");node.setProjectKey("p1");when(expected.findByAgentId("r1")).thenReturn(Optional.of(node));when(expected.findByProjectKeyOrderByCreatedAtDesc("p1")).thenReturn(List.of(node));
            var telemetry=new AgentTelemetryStore();var config=new NeutralDeviceConfig();config.setProduct("Cisco IOS XE");config.getWarnings().add("mock collection warning");telemetry.putConfig("r1",config);var channel=mock(AgentTerminalBroker.class);when(channel.supportsAction("r1","inspect_routes")).thenReturn(true);
            var providers=mock(AiProviderService.class);var provider=new AiProvider();provider.setName("Mock HTTP");provider.setModel("mock-json");when(providers.resolveOrEmpty(null)).thenReturn(Optional.of(provider));when(providers.toConnection(provider)).thenReturn(new OpenAiCompatibleClient.Connection("http://127.0.0.1:"+server.getAddress().getPort()+"/v1",null,"mock-json","none",10,1000,0.0,false));
            var repository=mock(PolicyAdviceRepository.class);when(repository.save(any())).thenAnswer(a->a.getArgument(0));var service=new ViolationAdvisorService(projects,providers,new OpenAiCompatibleClient(),new PolicyAdviceParser(),repository);service.setCommandContext(new AdviceCommandContext(expected,telemetry,channel));
            var result=service.advise("p1",null,null,null,null,"설정 오류를 진단해 주세요","operator","r1");
            assertEquals(true,result.get("succeeded"));var commands=(List<?>)result.get("command_proposals");assertEquals(1,commands.size());assertEquals(true,((Map<?,?>)commands.getFirst()).get("executable"));
            assertTrue(request.get().contains("command_proposals"));assertTrue(request.get().contains("mock collection warning"));assertTrue(request.get().contains("POLICY_HEADER_SPACE")||request.get().contains("헤더 공간"));
        } finally {server.stop(0);}
    }
}
