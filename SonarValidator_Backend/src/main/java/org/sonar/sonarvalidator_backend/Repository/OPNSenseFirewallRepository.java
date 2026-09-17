package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entitiy.OPNSenseFirewall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * {@link OPNSenseFirewall} 엔티티의 영속성 리포지토리입니다.
 *
 * <p>{@link JpaRepository} 를 상속하면 저장/조회/삭제와 페이징이 자동으로
 * 제공됩니다. 아래 메서드들은 이름 규칙(파생 쿼리)만으로 SQL 이 생성되므로
 * 구현 코드가 필요 없습니다.
 */
@Repository
public interface OPNSenseFirewallRepository extends JpaRepository<OPNSenseFirewall, Long> {

    /**
     * Agent 식별자로 방화벽 노드를 찾습니다.
     *
     * @param agentId 봉투의 {@code agent_id}
     * @return 엔티티 (없으면 비어 있음)
     */
    Optional<OPNSenseFirewall> findByAgentId(String agentId);

    /**
     * 관리 IP 로 방화벽 노드를 찾습니다.
     *
     * @param managementIp 관리 IP
     * @return 엔티티 (없으면 비어 있음)
     */
    Optional<OPNSenseFirewall> findByManagementIp(String managementIp);

    /**
     * 이름에 특정 문자열이 포함된 노드를 찾습니다. (대소문자 무시)
     *
     * @param keyword 검색어
     * @return 일치하는 엔티티 목록
     */
    List<OPNSenseFirewall> findByNameContainingIgnoreCase(String keyword);

    /**
     * 해당 Agent 식별자가 이미 등록되어 있는지 확인합니다.
     *
     * @param agentId Agent 식별자
     * @return 존재 여부
     */
    boolean existsByAgentId(String agentId);
}
