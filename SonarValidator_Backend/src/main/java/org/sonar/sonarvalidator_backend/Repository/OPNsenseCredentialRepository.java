package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.OPNsenseCredential;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * OPNsense 접속 정보 저장소입니다.
 *
 * <p>조회 키는 {@code agentId} 입니다. 화면에서 장치를 고르면 그 장치의
 * 설정을 읽는 흐름이므로 자연 키가 맞습니다.
 */
public interface OPNsenseCredentialRepository extends JpaRepository<OPNsenseCredential, Long> {

    /**
     * Agent 식별자로 조회합니다.
     *
     * @param agentId Agent 식별자
     * @return 접속 정보 (없으면 비어 있음)
     */
    Optional<OPNsenseCredential> findByAgentId(String agentId);

    /**
     * 등록 여부를 확인합니다.
     *
     * @param agentId Agent 식별자
     * @return 존재하면 {@code true}
     */
    boolean existsByAgentId(String agentId);

    /**
     * 등록된 모든 접속 정보를 조회합니다.
     *
     * @return 접속 정보 목록
     */
    List<OPNsenseCredential> findAllByOrderByIdAsc();

    /**
     * 설정이 완료된(키와 시크릿이 모두 있는) 항목만 조회합니다.
     *
     * @return 사용 가능한 접속 정보 목록
     */
    List<OPNsenseCredential> findByApiKeyIsNotNullAndSecretIsNotNull();
}
