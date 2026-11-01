package org.sonar.sonarvalidator_backend;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.sonar.sonarvalidator_backend.Model.entity.*;
import org.sonar.sonarvalidator_backend.Policy.advice.*;
import org.sonar.sonarvalidator_backend.Repository.PolicyAdviceRepository;
import org.sonar.sonarvalidator_backend.Service.*;
import org.sonar.sonarvalidator_backend.Service.policy.*;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class AdviceExecutionServiceTest {
    final PolicyAdviceRepository advices=mock(PolicyAdviceRepository.class);final ProjectService projects=mock(ProjectService.class);
    final AdviceCommandContext context=mock(AdviceCommandContext.class);final AgentTerminalBroker channel=mock(AgentTerminalBroker.class);
    final AdviceExecutionStore store=mock(AdviceExecutionStore.class);final Map<String,AdviceExecution> rows=new HashMap<>();
    final ObjectMapper json=new ObjectMapper();final WebSocketSession socket=mock(WebSocketSession.class);
    final PolicyAdvice record=new PolicyAdvice();final Project project=new Project();AdviceExecutionService service;
    final String id="11111111-1111-1111-1111-111111111111";
    @BeforeEach void setup() {
        project.setProjectKey("p1");record.setAdviceId("a1");record.setProjectKey("p1");record.setSucceeded(true);record.setStructured(true);record.setContextFingerprint("current");
        record.setCommandsJson("[{\"agent_id\":\"r1\",\"operation\":\"inspect_routes\",\"arguments\":{},\"commands\":[\"show ip route\"]}]");
        when(advices.findByAdviceId("a1")).thenReturn(Optional.of(record));when(projects.getByKey("p1")).thenReturn(project);
        when(context.snapshot(project,null)).thenReturn(new AdviceCommandContext.Snapshot("current","facts"));
        when(context.evaluate(eq(project),any())).thenReturn(new AdviceCommandContext.Evaluation(AdviceActionCatalog.compile("inspect_routes","Cisco",Map.of()),List.of()));
        when(socket.getId()).thenReturn("socket1");when(channel.actionSession("r1")).thenReturn(socket);when(channel.isCurrentAgent("r1",socket)).thenReturn(true);
        when(channel.reserveAction(anyString(),any(),anyString())).thenReturn(true);when(channel.sendAction(anyString(),any(),anyString(),anyMap())).thenReturn(true);
        when(store.find(anyString())).thenAnswer(a->rows.get(a.getArgument(0)));when(store.active()).thenAnswer(a->rows.values().stream().filter(v->v.getActiveAgentId()!=null).toList());
        when(store.create(any())).thenAnswer(a->{AdviceExecution e=a.getArgument(0);rows.put(e.getExecutionId(),e);return e;});
        doAnswer(a->{var e=rows.get(a.getArgument(0));e.setStatus("DISPATCHED");e.setSourceSessionId(a.getArgument(1));return null;}).when(store).dispatched(anyString(),anyString());
        doAnswer(a->{var e=rows.get(a.getArgument(0));e.setStatus(a.getArgument(2));e.setDetail(a.getArgument(3));e.setResultJson(a.getArgument(4));if(!e.getStatus().equals("RUNNING"))e.setActiveAgentId(null);return null;}).when(store).transition(anyString(),nullable(String.class),anyString(),anyString(),nullable(String.class));
        service=new AdviceExecutionService(advices,projects,context,new PolicyAdviceParser(),channel,store);
    }
    @Test void dispatchIsNotApplicationAndRetryIsIdempotent() {
        assertEquals("DISPATCHED",service.execute("p1","a1",0,id,"operator").get("status"));
        assertEquals("DISPATCHED",service.execute("p1","a1",0,id,"operator").get("status"));
        verify(channel,times(1)).sendAction(eq("r1"),eq(socket),eq(id),anyMap());
        service.receive("r1",socket,json.readTree("{\"type\":\"advice-command-result\",\"execution_id\":\""+id+"\",\"status\":\"SUCCEEDED\",\"verified\":true,\"output\":\"routes\"}"));
        assertEquals("SUCCEEDED",rows.get(id).getStatus());
    }
    @Test void oldOrForeignSessionCannotConfirmAndUnverifiedSuccessIsNotSuccess() {
        service.execute("p1","a1",0,id,"operator");var old=mock(WebSocketSession.class);when(old.getId()).thenReturn("old");
        var reply=json.readTree("{\"type\":\"advice-command-result\",\"execution_id\":\""+id+"\",\"status\":\"SUCCEEDED\",\"verified\":false}");
        service.receive("r1",old,reply);service.receive("other-agent",socket,reply);assertEquals("DISPATCHED",rows.get(id).getStatus());
        service.receive("r1",socket,reply);assertEquals("UNCONFIRMED",rows.get(id).getStatus());
    }
    @Test void staleContextBlocksBeforeAnyDeviceSend() {
        when(context.snapshot(project,null)).thenReturn(new AdviceCommandContext.Snapshot("changed","facts"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.execute("p1","a1",0,id,"operator")).getStatusCode().value());verify(channel,never()).sendAction(anyString(),any(),anyString(),anyMap());
    }
    @Test void disconnectAndServerRecoveryDoNotAutoReplay() {
        service.execute("p1","a1",0,id,"operator");service.disconnected("r1",socket);assertEquals("UNCONFIRMED",rows.get(id).getStatus());
        assertEquals("UNCONFIRMED",service.execute("p1","a1",0,id,"operator").get("status"));verify(channel,times(1)).sendAction(anyString(),any(),anyString(),anyMap());
    }
    @Test void requestIdentityCannotBeReusedAcrossUsersOrProjects() {
        service.execute("p1","a1",0,id,"operator");assertThrows(ResponseStatusException.class,()->service.execute("p1","a1",0,id,"other-user"));
        assertThrows(ResponseStatusException.class,()->service.execute("other-project","a1",0,id,"operator"));
    }
    @Test void failedSocketWriteIsUnconfirmedAndIsNeverRepeated() {
        when(channel.sendAction(anyString(),any(),anyString(),anyMap())).thenReturn(false);
        assertEquals("UNCONFIRMED",service.execute("p1","a1",0,id,"operator").get("status"));
        assertEquals("UNCONFIRMED",service.execute("p1","a1",0,id,"operator").get("status"));
        verify(channel,times(1)).sendAction(anyString(),any(),anyString(),anyMap());
    }
}
