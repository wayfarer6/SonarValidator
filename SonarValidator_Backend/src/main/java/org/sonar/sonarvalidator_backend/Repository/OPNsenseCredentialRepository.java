package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.OPNsenseCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * OPNsense 접속 정보 저장소입니다.
 *
 * <p>Credentials are uniquely associated with canonical
 * {@code configuration.node_id}; external Agent identifiers are resolved
 * through {@code ConfigurationRepository}.
 */
public interface OPNsenseCredentialRepository extends JpaRepository<OPNsenseCredential, Long> {

    @Query("select c from OPNsenseCredential c where c.node.node_id = :nodeId")
    Optional<OPNsenseCredential> findByNodeId(@Param("nodeId") Integer nodeId);

    @Query("select (count(c) > 0) from OPNsenseCredential c where c.node.node_id = :nodeId")
    boolean existsByNodeId(@Param("nodeId") Integer nodeId);

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
