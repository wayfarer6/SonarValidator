package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.PolicyAdvice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 정책 조언 기록 저장소입니다.
 *
 * <p>조회는 화면이 요구하는 세 축(전체 / 프로젝트 / 규칙)으로 제공하고
 * 항상 최신순입니다. 조언은 로그 분석과 마찬가지로 건수가 적어 페이지
 * 제한은 {@link Pageable} 로 호출측이 정합니다.
 */
public interface PolicyAdviceRepository extends JpaRepository<PolicyAdvice, Long> {

    /**
     * 조언 식별자로 조회합니다.
     *
     * @param adviceId 조언 식별자 (예: {@code PADV-1A2B3C4D})
     * @return 조언 (없으면 빈 값)
     */
    Optional<PolicyAdvice> findByAdviceId(String adviceId);

    /**
     * 전체 조언 이력을 최신순 조회합니다.
     *
     * @param pageable 페이지 크기
     * @return 조언 이력
     */
    List<PolicyAdvice> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 프로젝트의 조언 이력을 최신순 조회합니다.
     *
     * @param projectKey 프로젝트 키
     * @param pageable   페이지 크기
     * @return 조언 이력
     */
    List<PolicyAdvice> findByProjectKeyOrderByCreatedAtDesc(String projectKey, Pageable pageable);

    /**
     * 특정 위반 규칙의 조언 이력을 최신순 조회합니다.
     *
     * <p>위반 카드에서 "이 위반에 대해 전에 무엇이라고 조언했나" 를 보여줄 때
     * 씁니다. AI 를 다시 부르지 않아도 되므로 비용을 아낍니다.
     *
     * @param projectKey 프로젝트 키
     * @param ruleId     규칙 식별자
     * @param pageable   페이지 크기
     * @return 조언 이력
     */
    List<PolicyAdvice> findByProjectKeyAndRuleIdOrderByCreatedAtDesc(String projectKey,
                                                                     String ruleId,
                                                                     Pageable pageable);
}