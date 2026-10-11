package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.Configuration;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 노드 정본 저장소입니다. ({@code configuration} 테이블)
 *
 * <h2>⚠️ 왜 이 저장소가 필요했나 — 노드 정본이 없었다</h2>
 * <p>{@code device_log} 와 {@code opnsense_firewall} 이 {@code node_id} 로
 * 노드를 참조하는데도, {@code configuration} 테이블에 <b>쓰는 코드가 없었습니다.</b>
 * 설정은 {@code AgentTelemetryStore} 의 인메모리 맵에만 남고 재기동하면 사라졌습니다.
 * 그래서 노드 번호가 DB 에 존재하지 않아 외래키를 걸 대상 자체가 없었습니다.
 *
 * <h2>⚠️ upsert 기준은 자연키 agent_id</h2>
 * <p>같은 장비가 텔레메트리를 다시 보낼 때마다 새 행을 만들면 "같은 장비가
 * 여러 행" 이 되어 {@code node_id} 참조가 갈라집니다(boot 재접속마다 번호 변경).
 * 그래서 {@code agent_id} 로 먼저 찾고, 있으면 그 행을 갱신합니다.
 *
 * <p>Agent 없이 REST API 로만 관리되는 장비(OPNsense)는 {@code agent_id} 가
 * 비어 있을 수 있으므로 값은 null 을 허용합니다.
 */
@Repository
public interface ConfigurationRepository extends JpaRepository<Configuration, Integer> {

    /**
     * Agent 식별자로 노드 정본을 찾습니다.
     *
     * @param agentId 장비(Agent) 식별자
     * @return 노드 (없으면 비어 있음)
     */
    Optional<Configuration> findByAgentId(String agentId);

    /**
     * 호스트명으로 노드 정본을 찾습니다.
     *
     * <p>자연키 이전에 만들어진 행(agent_id 가 비어 있는 행)과의 호환 경로입니다.
     *
     * @param hostname 호스트명
     * @return 노드 (없으면 비어 있음)
     */
    Optional<Configuration> findBy_hostname(String hostname);

    /**
     * 장치 유형별 노드를 조회합니다.
     *
     * @param deviceType 장치 유형
     * @return 노드 목록
     */
    List<Configuration> findByDeviceType(DeviceType deviceType);

    /**
     * 해당 Agent 의 노드가 이미 있는지 확인합니다.
     *
     * @param agentId 장비(Agent) 식별자
     * @return 존재 여부
     */
    boolean existsByAgentId(String agentId);
}