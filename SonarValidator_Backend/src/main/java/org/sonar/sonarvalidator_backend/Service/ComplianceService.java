package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.sonar.sonarvalidator_backend.Model.entity.ComplianceChange;
import org.sonar.sonarvalidator_backend.Repository.ComplianceChangeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    private final ComplianceChangeRepository repository;

    public ComplianceService(ComplianceChangeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ComplianceChange record(String scope,
                                   String projectKey,
                                   String agentId,
                                   String type,
                                   String summary,
                                   String changedBy,
                                   String status,
                                   String detail) {
        final ComplianceChange change = new ComplianceChange();
        change.setChangeId("CHG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        change.setScope(scope == null ? "Project" : scope);
        change.setProjectKey(projectKey);
        change.setAgentId(agentId);
        change.setType(type);
        change.setSummary(summary);
        change.setChangedBy(changedBy == null || changedBy.isBlank() ? "system" : changedBy);
        change.setTimestamp(new java.util.Date());
        change.setStatus(status == null ? "Applied" : status);
        change.setDetail(detail);
        return repository.save(change);
    }

    public void recordQuietly(String scope,
                              String projectKey,
                              String agentId,
                              String type,
                              String summary,
                              String changedBy,
                              String detail) {
        try {
            record(scope, projectKey, agentId, type, summary, changedBy, "Applied", detail);
        } catch (RuntimeException ex) {
            log.warn("failed to record compliance change for project={}: {}", projectKey, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ComplianceChange> byProject(String projectKey) {
        return repository.findByProjectKeyOrderByTimestampDesc(projectKey);
    }

    @Transactional(readOnly = true)
    public List<ComplianceChange> byAgent(String agentId) {
        return repository.findByAgentIdOrderByTimestampDesc(agentId);
    }

    @Transactional(readOnly = true)
    public List<ComplianceChange> listAll() {
        return repository.findAllByOrderByTimestampDesc();
    }

    public static List<java.util.Map<String, Object>> toResponse(List<ComplianceChange> changes) {
        final List<java.util.Map<String, Object>> result = new ArrayList<>();
        for (final ComplianceChange change : changes) {
            final java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("id", change.getChangeId());
            entry.put("scope", change.getScope());
            entry.put("project_id", change.getProjectKey());
            entry.put("agent_id", change.getAgentId());
            entry.put("type", change.getType());
            entry.put("summary", change.getSummary());
            entry.put("changed_by", change.getChangedBy());
            entry.put("timestamp", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(change.getTimestamp()));
            entry.put("status", change.getStatus());
            result.add(entry);
        }
        return result;
    }
}
