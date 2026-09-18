package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.entity.ComplianceChange;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 변경 이력 저장소입니다.
 *
 * <p>조회는 화면이 요구하는 두 축(프로젝트 / 장치)으로만 제공합니다.
 * 정렬은 항상 최신순입니다.
 */
public interface ComplianceChangeRepository extends JpaRepository<ComplianceChange, Long> {

    /**
     * 프로젝트의 변경 이력을 최신순으로 조회합니다.
     *
     * @param projectKey 프로젝트 키
     * @return 변경 이력
     */
    List<ComplianceChange> findByProjectKeyOrderByTimestampDesc(String projectKey);

    /**
     * 장치의 변경 이력을 최신순으로 조회합니다.
     *
     * @param agentId 장치 식별자
     * @return 변경 이력
     */
    List<ComplianceChange> findByAgentIdOrderByTimestampDesc(String agentId);

    /**
     * 전체 변경 이력을 최신순으로 조회합니다.
     *
     * @return 변경 이력
     */
    List<ComplianceChange> findAllByOrderByTimestampDesc();

    /**
     * 프로젝트 + 장치 조합으로 조회합니다.
     *
     * @param projectKey 프로젝트 키
     * @param agentId    장치 식별자
     * @return 변경 이력
     */
    List<ComplianceChange> findByProjectKeyAndAgentIdOrderByTimestampDesc(String projectKey, String agentId);
}
