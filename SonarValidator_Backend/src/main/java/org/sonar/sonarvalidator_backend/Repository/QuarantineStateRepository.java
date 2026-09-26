package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.QuarantineState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 격리 상태 저장소입니다.
 *
 * <h2>⚠️ 파생 쿼리를 쓴 이유</h2>
 * <p>알림/로그는 필터 조합이 많아 {@code @Query} 하나로 모았지만, 격리는
 * 조회 조건이 <b>두 개뿐</b>입니다("장치별 현재 격리", "격리 중 전체").
 * 이 정도는 메서드 이름이 곧 쿼리 문서라서 파생 쿼리가 더 읽기 좋습니다.
 *
 * <h2>⚠️ "현재" 를 판단하는 기준</h2>
 * <p>{@code ReleasedAtIsNull} 이 <b>격리 중</b>을 뜻합니다. 격리 이력은 남기고
 * 해제 여부만 시각으로 표시하기 때문입니다. ({@link QuarantineState} 참고)
 */
public interface QuarantineStateRepository extends JpaRepository<QuarantineState, Long> {

    /**
     * 특정 장치의 <b>현재 격리 중인</b> 상태를 조회합니다.
     *
     * <p>한 장치는 동시에 하나만 격리 중일 수 있으므로 {@link Optional} 입니다.
     *
     * @param agentId Agent 식별자
     * @return 현재 격리 상태 (격리 중이 아니면 비어 있음)
     */
    Optional<QuarantineState> findByAgentIdAndReleasedAtIsNull(String agentId);

    /**
     * 현재 격리 중인 장치 전체를 최신순으로 조회합니다.
     *
     * <p>토폴로지 표시와 정책 푸시 대상 제외에 씁니다.
     *
     * @param pageable 페이지 제한 (목록이 무한히 커지지 않도록)
     * @return 격리 중 상태 목록
     */
    List<QuarantineState> findByReleasedAtIsNullOrderByQuarantinedAtDesc(Pageable pageable);

    /**
     * 특정 프로젝트에서 현재 격리 중인 장치를 조회합니다.
     *
     * @param projectKey 프로젝트 키
     * @param pageable   페이지 제한
     * @return 격리 중 상태 목록
     */
    List<QuarantineState> findByProjectKeyAndReleasedAtIsNullOrderByQuarantinedAtDesc(
            String projectKey, Pageable pageable);

    /**
     * 한 장치의 전체 격리 이력을 최신순으로 조회합니다.
     *
     * <p>해제된 것도 포함합니다. 감사 목적으로 필요합니다.
     *
     * @param agentId Agent 식별자
     * @param pageable 페이지 제한
     * @return 격리 이력
     */
    List<QuarantineState> findByAgentIdOrderByQuarantinedAtDesc(String agentId, Pageable pageable);
}