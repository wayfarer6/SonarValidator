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

/**
 * 변경 이력의 기록과 조회를 담당합니다.
 *
 * <h2>기록 지점</h2>
 * <p>이 서비스는 이력을 <b>직접 만들지 않습니다.</b> 프로젝트 수정/정책 푸시처럼
 * "실제로 무언가 바뀌는" 지점에서 호출해 남깁니다. 이렇게 두면 이력이
 * 실제 변경과 어긋날 수 없습니다.
 *
 * <p>기록 실패가 본 작업을 막아서는 안 되므로, 호출측은 예외를 흡수하고
 * 경고 로그만 남깁니다. (감사 로그 때문에 정책 적용이 실패하는 것이 더 나쁩니다.)
 */
@Service
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    private final ComplianceChangeRepository repository;

    /**
     * @param repository 변경 이력 저장소
     */
    public ComplianceService(ComplianceChangeRepository repository) {
        this.repository = repository;
    }

    /**
     * 변경 이력 한 건을 기록합니다.
     *
     * @param scope      변경 범위 ({@code Project} / {@code Agent})
     * @param projectKey 프로젝트 키
     * @param agentId    장치 식별자 (없으면 null)
     * @param type       변경 유형
     * @param summary    요약
     * @param changedBy  변경 주체
     * @param status     적용 상태
     * @param detail     상세 JSON (선택)
     * @return 저장된 이력
     */
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
        change.setTimestamp(Instant.now().toString());
        change.setStatus(status == null ? "Applied" : status);
        change.setDetail(detail);
        return repository.save(change);
    }

    /**
     * 변경 이력을 기록하되 예외를 흡수합니다.
     *
     * <p>정책 저장/푸시 경로에서 호출합니다. 이력 기록이 실패해도 본 작업은
     * 성공해야 하므로 예외를 삼키고 경고만 남깁니다.
     *
     * @param scope      변경 범위
     * @param projectKey 프로젝트 키
     * @param agentId    장치 식별자
     * @param type       변경 유형
     * @param summary    요약
     * @param changedBy  변경 주체
     * @param detail     상세 JSON
     */
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

    /**
     * 프로젝트의 변경 이력을 최신순으로 조회합니다.
     *
     * @param projectKey 프로젝트 키
     * @return 변경 이력
     */
    @Transactional(readOnly = true)
    public List<ComplianceChange> byProject(String projectKey) {
        return repository.findByProjectKeyOrderByTimestampDesc(projectKey);
    }

    /**
     * 장치의 변경 이력을 최신순으로 조회합니다.
     *
     * @param agentId 장치 식별자
     * @return 변경 이력
     */
    @Transactional(readOnly = true)
    public List<ComplianceChange> byAgent(String agentId) {
        return repository.findByAgentIdOrderByTimestampDesc(agentId);
    }

    /**
     * 전체 변경 이력을 최신순으로 조회합니다.
     *
     * @return 변경 이력
     */
    @Transactional(readOnly = true)
    public List<ComplianceChange> listAll() {
        return repository.findAllByOrderByTimestampDesc();
    }

    /**
     * 변경 이력을 응답 맵으로 바꿉니다.
     *
     * @param changes 변경 이력 목록
     * @return 응답 맵 목록
     */
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
            entry.put("timestamp", change.getTimestamp());
            entry.put("status", change.getStatus());
            result.add(entry);
        }
        return result;
    }
}
