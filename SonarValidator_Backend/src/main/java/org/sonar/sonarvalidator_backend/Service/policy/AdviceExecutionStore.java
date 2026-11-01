package org.sonar.sonarvalidator_backend.Service.policy;

import java.time.Instant;
import java.util.List;
import org.sonar.sonarvalidator_backend.Model.entity.AdviceExecution;
import org.sonar.sonarvalidator_backend.Repository.AdviceExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdviceExecutionStore {
    private final AdviceExecutionRepository repository;
    public AdviceExecutionStore(AdviceExecutionRepository repository) { this.repository=repository; }
    @Transactional public AdviceExecution create(AdviceExecution row) { return repository.saveAndFlush(row); }
    @Transactional public void transition(String id, String sessionId, String state, String detail, String result) {
        var row=repository.findForUpdate(id).orElse(null);
        if(row==null || row.getActiveAgentId()==null || (sessionId!=null && !sessionId.equals(row.getSourceSessionId()))) return;
        if (state.equals("RUNNING") && !row.getStatus().equals("DISPATCHED")) return;
        row.setStatus(state); row.setDetail(detail);
        if(result!=null) row.setResultJson(result);
        if(!state.equals("RUNNING") && !state.equals("DISPATCHED")) {
            row.setActiveAgentId(null); row.setFinishedAt(Instant.now());
        }
        repository.save(row);
    }
    @Transactional public void dispatched(String id, String sessionId) {
        var row=repository.findForUpdate(id).orElseThrow();
        if(!row.getStatus().equals("PENDING")) return;
        row.setStatus("DISPATCHED");row.setSourceSessionId(sessionId);row.setDispatchedAt(Instant.now());
        repository.save(row);
    }
    @Transactional(readOnly=true) public List<AdviceExecution> active() { return repository.findByActiveAgentIdIsNotNull(); }
    @Transactional(readOnly=true) public List<AdviceExecution> recentErrors(String project) {
        return repository.findTop12ByProjectKeyAndStatusInOrderByCreatedAtDesc(project,List.of("FAILED","REJECTED","UNCONFIRMED"));
    }
    @Transactional(readOnly=true) public AdviceExecution find(String id) { return repository.findById(id).orElse(null); }
    @Transactional(readOnly=true) public List<AdviceExecution> history(String project,String advice) { return repository.findByProjectKeyAndAdviceIdOrderByCreatedAtDesc(project,advice); }
}
