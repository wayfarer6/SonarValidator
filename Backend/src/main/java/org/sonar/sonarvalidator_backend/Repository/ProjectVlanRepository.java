package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.ProjectVlan;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 프로젝트별 <b>수집 VLAN 이름</b> 저장소입니다.
 *
 * <p>키는 {@code (project, agentId, vlanId)} 입니다. VLAN 번호만으로는 장비를
 * 구분할 수 없기 때문입니다 — 서로 다른 스위치의 VLAN 111 은 다른 브로드캐스트
 * 도메인이고, 운영자는 각각 다른 이름을 붙일 수 있어야 합니다.
 */
public interface ProjectVlanRepository extends JpaRepository<ProjectVlan, Long> {

    /**
     * 프로젝트의 VLAN 이름을 모두 조회합니다.
     *
     * @param projectKey 프로젝트 키
     * @return VLAN 이름 목록 (없으면 빈 목록)
     */
    List<ProjectVlan> findByProject_ProjectKey(String projectKey);

    /**
     * 특정 장비의 특정 VLAN 이름을 조회합니다.
     *
     * @param projectKey 프로젝트 키
     * @param agentId    Agent 식별자
     * @param vlanId     VLAN 번호
     * @return 저장된 이름 (없으면 비어 있음)
     */
    Optional<ProjectVlan> findByProject_ProjectKeyAndAgentIdAndVlanId(
            String projectKey, String agentId, Integer vlanId);
}
