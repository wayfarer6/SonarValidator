package org.sonar.sonarvalidator_backend.Service.policy;

import java.time.Instant;
import java.util.*;
import org.sonar.sonarvalidator_backend.Model.entity.AdviceExecution;
import org.sonar.sonarvalidator_backend.Policy.advice.*;
import org.sonar.sonarvalidator_backend.Repository.PolicyAdviceRepository;
import org.sonar.sonarvalidator_backend.Service.AgentTerminalBroker;
import org.sonar.sonarvalidator_backend.Service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.*;

@Service
public class AdviceExecutionService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdviceExecutionService.class);
    private final PolicyAdviceRepository advices;
    private final ProjectService projects;
    private final AdviceCommandContext context;
    private final PolicyAdviceParser parser;
    private final AgentTerminalBroker channel;
    private final AdviceExecutionStore store;
    private final ObjectMapper mapper=new ObjectMapper();
    public AdviceExecutionService(PolicyAdviceRepository advices, ProjectService projects, AdviceCommandContext context,
            PolicyAdviceParser parser, AgentTerminalBroker channel, AdviceExecutionStore store) {
        this.advices=advices;this.projects=projects;this.context=context;this.parser=parser;this.channel=channel;this.store=store;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        for(var row:store.active()) finish(row,"UNCONFIRMED","서버가 재시작돼 완료를 확인하지 못했습니다. 장비를 재조회한 뒤 판단하세요.",null);
    }
    public Map<String,Object> execute(String project, String adviceId, int index, String requestId, String user) {
        expire();
        try { if(!UUID.fromString(requestId).toString().equals(requestId)) throw new IllegalArgumentException(); }
        catch(RuntimeException e) { throw error(HttpStatus.BAD_REQUEST,"유효한 request_id가 필요합니다."); }
        var previous=store.find(requestId);
        if(previous!=null) return sameRequest(previous,project,adviceId,index,user);
        var record=advices.findByAdviceId(adviceId).filter(a->project.equals(a.getProjectKey())).orElseThrow(()->error(HttpStatus.NOT_FOUND,"조언을 찾을 수 없습니다."));
        var p=projects.getByKey(project);
        if(!Boolean.TRUE.equals(record.getSucceeded()) || !Boolean.TRUE.equals(record.getStructured()) || record.getContextFingerprint()==null)
            throw error(HttpStatus.CONFLICT,"실행 가능한 새 조언을 생성하세요.");
        if(!record.getContextFingerprint().equals(context.snapshot(p,record.getTargetAgent()).fingerprint()))
            throw error(HttpStatus.CONFLICT,"관측 또는 프로젝트가 달라졌습니다. 새 조언을 생성하세요.");
        List<AdviceCommandProposal> proposals=parser.readCommands(mapper.readTree(record.getCommandsJson()==null?"[]":record.getCommandsJson()));
        if(index<0 || index>=proposals.size()) throw error(HttpStatus.BAD_REQUEST,"명령 제안 번호가 올바르지 않습니다.");
        var proposal=proposals.get(index);
        if (record.getTargetAgent()!=null && !record.getTargetAgent().equals(proposal.agentId())) throw error(HttpStatus.CONFLICT,"선택한 분석 노드와 다른 대상입니다.");
        var evaluation=context.evaluate(p,proposal);
        if(!evaluation.executable()) throw error(HttpStatus.CONFLICT,String.join(" ",evaluation.reasons()));
        var plan=evaluation.plan();WebSocketSession session=channel.actionSession(proposal.agentId());
        if(session==null) throw error(HttpStatus.CONFLICT,"Prober 인증 채널이 연결되지 않았습니다.");
        var row=new AdviceExecution();row.setExecutionId(requestId);row.setProjectKey(project);row.setAdviceId(adviceId);
        row.setProposalIndex(index);row.setAgentId(proposal.agentId());row.setActiveAgentId(proposal.agentId());
        row.setOperation(plan.operation());row.setStatus("PENDING");row.setRequestedBy(user);row.setCreatedAt(Instant.now());row.setExpiresAt(Instant.now().plusSeconds(120));
        Map<String,Object> wire=new LinkedHashMap<>();wire.put("type","advice-command");wire.put("execution_id",requestId);
        wire.put("agent_id",proposal.agentId());wire.put("operation",plan.operation());wire.put("arguments",plan.arguments());
        wire.put("expires_at",row.getExpiresAt().getEpochSecond());
        wire.put("management_prefix",p.getManagementPrefix()==null?"":p.getManagementPrefix());
        wire.put("management_server",p.getManagementServerIp()==null?"":p.getManagementServerIp());
        row.setPlanJson(mapper.writeValueAsString(Map.of("wire",wire,"commands",plan.commands(),"verification",plan.verification(),"changes_configuration",plan.changesConfiguration())));
        try { store.create(row); }
        catch(DataIntegrityViolationException e) {
            previous=store.find(requestId);
            if(previous!=null)return sameRequest(previous,project,adviceId,index,user);
            throw error(HttpStatus.CONFLICT,"이 노드에서 다른 명령이 실행 중입니다.");
        }
        if(!channel.reserveAction(proposal.agentId(),session,requestId)) finish(row,"REJECTED","터미널을 닫거나 현재 명령이 끝난 뒤 실행하세요.",null);
        else {
            store.dispatched(requestId,session.getId());
            log.info("장비 명령 전송 요청: 노드={} 작업={} id={}", proposal.agentId(), plan.operation(), requestId);
            if(!channel.sendAction(proposal.agentId(),session,requestId,wire)) finish(row,"UNCONFIRMED","전송 중 연결이 끊겨 수신 여부를 확인하지 못했습니다. 자동 재전송하지 않습니다.",null);
        }
        return view(store.find(requestId));
    }
    private Map<String,Object> sameRequest(AdviceExecution previous,String project,String advice,int index,String user) {
        if(!project.equals(previous.getProjectKey()) || !advice.equals(previous.getAdviceId()) || index!=previous.getProposalIndex() || !user.equals(previous.getRequestedBy()))
            throw error(HttpStatus.CONFLICT,"같은 request_id를 다른 요청에 사용할 수 없습니다.");
        return view(previous);
    }
    public void receive(String agent, WebSocketSession session, JsonNode message) {
        String id=message.path("execution_id").asString("");var row=store.find(id);
        if(row==null || !agent.equals(row.getAgentId()) || !session.getId().equals(row.getSourceSessionId()) || !channel.isCurrentAgent(agent,session)) return;
        String type=message.path("type").asString("");
        if(type.equals("advice-command-started")) store.transition(id,session.getId(),"RUNNING","Prober가 명령을 실행 중입니다.",null);
        else if(type.equals("advice-command-result")) {
            String state=message.path("status").asString("");
            if(!Set.of("SUCCEEDED","FAILED","REJECTED","UNCONFIRMED").contains(state)) return;
            if(state.equals("SUCCEEDED") && (!message.path("verified").isBoolean() || !message.path("verified").asBoolean())) state="UNCONFIRMED";
            String detail=bounded(message.path("detail").asString(""),1800);
            Map<String,Object> result=Map.of("status",state,"verified",message.path("verified").asBoolean(false),
                    "changed",message.path("changed").asBoolean(false),"output",bounded(message.path("output").asString(""),18000),"detail",detail);
            finish(row,state,detail,mapper.writeValueAsString(result));
        }
    }
    public void disconnected(String agent, WebSocketSession session) {
        for(var row:store.active()) if(agent.equals(row.getAgentId()) && session.getId().equals(row.getSourceSessionId()))
            finish(row,"UNCONFIRMED","Prober 연결이 끊겨 적용 여부를 확인하지 못했습니다. 자동 재실행하지 않습니다.",null);
    }
    public Map<String,Object> get(String project,String advice,String id) {
        expire();var row=store.find(id);
        if(row==null || !project.equals(row.getProjectKey()) || !advice.equals(row.getAdviceId())) throw error(HttpStatus.NOT_FOUND,"실행 이력을 찾을 수 없습니다.");
        return view(row);
    }
    public List<Map<String,Object>> history(String project,String advice) { expire();return store.history(project,advice).stream().limit(50).map(this::view).toList(); }
    private void expire() { for(var row:store.active()) if(row.getExpiresAt().isBefore(Instant.now())) finish(row,"UNCONFIRMED","제한 시간 안에 결과를 받지 못했습니다. 장비 상태를 먼저 확인하세요.",null); }
    private void finish(AdviceExecution row,String state,String detail,String result) {
        if (row.getFinishedAt() == null) log.info("장비 명령 결과: 노드={} 상태={} id={}", row.getAgentId(), state, row.getExecutionId());
        store.transition(row.getExecutionId(),null,state,detail,result);channel.releaseAction(row.getAgentId(),row.getExecutionId());
    }
    private Map<String,Object> view(AdviceExecution row) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("execution_id",row.getExecutionId());result.put("project_id",row.getProjectKey());result.put("advice_id",row.getAdviceId());
        result.put("agent_id",row.getAgentId());result.put("proposal_index",row.getProposalIndex());result.put("operation",row.getOperation());
        result.put("status",row.getStatus());result.put("detail",row.getDetail());result.put("requested_by",row.getRequestedBy());
        result.put("created_at",row.getCreatedAt().toString());result.put("finished_at",row.getFinishedAt()==null?null:row.getFinishedAt().toString());
        result.put("plan",mapper.readTree(row.getPlanJson()));result.put("result",row.getResultJson()==null?null:mapper.readTree(row.getResultJson()));return result;
    }
    private static String bounded(String s,int max) { return s.substring(0,Math.min(max,s.length())); }
    private static ResponseStatusException error(HttpStatus status,String detail) { return new ResponseStatusException(status,detail); }
}
