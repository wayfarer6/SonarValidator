package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.LogAnalysis;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 로그 분석 결과 저장소입니다.
 *
 * <p>조회는 화면이 요구하는 세 축(전체 / 프로젝트 / 장비)으로 제공하고,
 * 항상 최신순입니다. 분석 결과는 로그와 달리 건수가 적어
 * 페이지 제한은 {@link Pageable} 로 호출측이 정합니다.
 */
public interface LogAnalysisRepository extends JpaRepository<LogAnalysis, Long> {

    /**
     * 분석 식별자로 조회합니다.
     *
     * @param analysisId 분석 식별자 (예: {@code AIA-1A2B3C4D})
     * @return 분석 (없으면 빈 값)
     */
    Optional<LogAnalysis> findByAnalysisId(String analysisId);

    /**
     * 전체 분석 이력을 최신순 조회합니다.
     *
     * @param pageable 페이지 크기
     * @return 분석 이력
     */
    List<LogAnalysis> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 프로젝트의 분석 이력을 최신순 조회합니다.
     *
     * @param projectKey 프로젝트 키
     * @param pageable   페이지 크기
     * @return 분석 이력
     */
    List<LogAnalysis> findByProjectKeyOrderByCreatedAtDesc(String projectKey, Pageable pageable);

    /**
     * 장비의 분석 이력을 최신순 조회합니다.
     *
     * @param agentId  장비 식별자
     * @param pageable 페이지 크기
     * @return 분석 이력
     */
    List<LogAnalysis> findByAgentIdOrderByCreatedAtDesc(String agentId, Pageable pageable);

    /**
     * 프로젝트 + 장비 조합으로 조회합니다.
     *
     * @param projectKey 프로젝트 키
     * @param agentId    장비 식별자
     * @param pageable   페이지 크기
     * @return 분석 이력
     */
    List<LogAnalysis> findByProjectKeyAndAgentIdOrderByCreatedAtDesc(String projectKey,
                                                                     String agentId,
                                                                     Pageable pageable);
}
