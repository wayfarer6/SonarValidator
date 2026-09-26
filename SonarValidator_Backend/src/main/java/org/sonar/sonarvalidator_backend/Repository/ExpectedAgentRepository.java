package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 배포 예정 Agent 저장소입니다.
 *
 * <p>조회 키는 {@code agentId} 입니다. Agent 가 스스로 보고하는 값이 그쪽이기
 * 때문입니다. (숫자 PK 는 Agent 가 알 수 없습니다)
 */
public interface ExpectedAgentRepository extends JpaRepository<ExpectedAgent, Long> {

    /**
     * Agent 식별자로 조회합니다.
     *
     * @param agentId Agent 식별자
     * @return 배포 예정 정보 (없으면 비어 있음)
     */
    Optional<ExpectedAgent> findByAgentId(String agentId);

    /**
     * 프로젝트별 배포 예정 목록을 최신순으로 조회합니다.
     *
     * @param projectKey 프로젝트 키
     * @return 배포 예정 목록
     */
    List<ExpectedAgent> findByProjectKeyOrderByCreatedAtDesc(String projectKey);

    /**
     * 전체 배포 예정 목록을 최신순으로 조회합니다.
     *
     * @return 배포 예정 목록
     */
    List<ExpectedAgent> findAllByOrderByCreatedAtDesc();

    /**
     * 존재 여부를 확인합니다. (중복 등록 방지)
     *
     * @param agentId Agent 식별자
     * @return 존재하면 {@code true}
     */
    boolean existsByAgentId(String agentId);

    /**
     * 프로젝트에 속한 배포 예정 Agent 를 모두 지웁니다.
     *
     * <p>프로젝트 삭제 시 남은 행이 "유령 장치" 로 목록에 뜨는 것을 막습니다.
     *
     * @param projectKey 프로젝트 키
     */
    void deleteByProjectKey(String projectKey);
}