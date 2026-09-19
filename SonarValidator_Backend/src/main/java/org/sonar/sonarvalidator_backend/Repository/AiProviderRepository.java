package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 공급자 설정 저장소입니다.
 *
 * <p>기본 공급자 조회가 필요한 이유: 분석 요청에 공급자를 지정하지 않으면
 * {@code is_default=true} 인 것을 자동으로 씁니다.
 */
public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {

    /**
     * 사용 가능한 공급자를 이름순으로 조회합니다. (화면 목록용)
     *
     * @return 공급자 목록
     */
    List<AiProvider> findAllByOrderByNameAsc();

    /**
     * 기본 공급자를 조회합니다.
     *
     * <p>{@code List} 로 받는 이유: 기본 플래그가 둘 이상 켜져 있을 수 있습니다.
     * (동시 요청으로) 그때 예외가 나면 분석 전체가 실패하므로, 첫 건을 씁니다.
     *
     * @return 기본 공급자 후보 (없으면 빈 목록)
     */
    List<AiProvider> findByIsDefaultTrue();

    /**
     * 사용 가능한(enabled) 공급자만 조회합니다.
     *
     * @return 사용 가능한 공급자
     */
    List<AiProvider> findByEnabledTrueOrderByNameAsc();

    /**
     * 이름으로 조회합니다. (중복 이름 방지)
     *
     * @param name 공급자 이름
     * @return 공급자 (없으면 빈 값)
     */
    Optional<AiProvider> findByName(String name);
}
