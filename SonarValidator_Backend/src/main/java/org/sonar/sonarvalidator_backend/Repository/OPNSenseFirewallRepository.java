package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.OPNSenseFirewall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * {@link OPNSenseFirewall} 엔티티의 영속성 리포지토리입니다.
 *
 * <p>{@link JpaRepository} 를 상속하면 저장/조회/삭제와 페이징이 자동으로
 * 제공됩니다. 아래 메서드들은 이름 규칙(파생 쿼리)만으로 SQL 이 생성되므로
 * 구현 코드가 필요 없습니다.
 *
 * <p>식별자 타입은 {@code Integer} 입니다. 엔티티의 PK 가 대리 키가 아니라
 * {@code configuration.node_id} 를 그대로 쓰는 {@code node_id} 이기 때문입니다.
 *
 * <h2>⚠️ DB Design v1.5 — agent_id 로 찾지 않는다</h2>
 * <p>OPNsense 는 REST API 로 직접 연결되어 <b>Agent 가 없을 수 있습니다.</b>
 * 그래서 {@code findByAgentId} 를 제거하고, 노드 정본
 * ({@code configuration})을 경유하는 조회로 바꿨습니다. Agent 식별자로
 * 찾아야 하면 {@link ConfigurationRepository#findByAgentId(String)} 로
 * 노드를 먼저 찾고 {@code findById(nodeId)} 를 쓰세요.
 */
@Repository
public interface OPNSenseFirewallRepository extends JpaRepository<OPNSenseFirewall, Integer> {

    /**
     * 관리 IP 로 방화벽 노드를 찾습니다.
     *
     * <p>Agent 가 없는 장비에서 사실상 유일한 조회 키입니다.
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
     * 해당 관리 IP 가 이미 등록되어 있는지 확인합니다.
     *
     * @param managementIp 관리 IP
     * @return 존재 여부
     */
    boolean existsByManagementIp(String managementIp);
}
