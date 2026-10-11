package org.sonar.sonarvalidator_backend.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectVlan;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.sonar.sonarvalidator_backend.Repository.ProjectVlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집된 VLAN 에 <b>운영자 이름</b>을 붙이고 조회하는 서비스입니다.
 *
 * <h2>⚠️ 왜 이름이 필요한가</h2>
 * <p>장비가 알려주는 VLAN 정보에는 이름다운 이름이 없습니다. Open vSwitch 는
 * {@code tag: 131} 같은 숫자만 주므로 프로버가 {@code VLAN131} 로 <b>기계적</b>
 * 으로 만듭니다. "ATICS 통신망", "감시장비망" 같은 실제 업무 이름은 운영자만
 * 알 수 있습니다.
 *
 * <p>그런데 이 이름을 {@link org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet}
 * 에 저장할 수 없습니다 — 그 표는 {@code cidr} 이 필수라, 주소 없는 L2 VLAN 은
 * 넣을 자리가 없습니다(가짜 대역을 넣으면 정책 대상이 오염됩니다).
 * 그래서 {@link ProjectVlan} 이 그 자리를 담당합니다.
 *
 * <h2>이름이 정책을 바꾸지 않는다</h2>
 * <p>이 서비스는 <b>표시 이름만</b> 다룹니다. 등급(CSO)·대역·허용 목록은
 * 서브넷/규칙이 소유하므로, 이름을 바꿔도 집행 규칙은 변하지 않습니다.
 */
@Service
public class ProjectVlanNameService {

    private static final Logger log = LoggerFactory.getLogger(ProjectVlanNameService.class);

    /** 이름 최대 길이입니다. (엔티티 컬럼과 일치) */
    private static final int MAX_NAME_LENGTH = 200;

    private final ProjectRepository projects;
    private final ProjectVlanRepository vlans;

    /**
     * @param projects 프로젝트 저장소
     * @param vlans    VLAN 이름 저장소
     */
    public ProjectVlanNameService(ProjectRepository projects, ProjectVlanRepository vlans) {
        this.projects = projects;
        this.vlans = vlans;
    }

    /**
     * 프로젝트의 VLAN 이름을 {@code "agent|vlan"} 키로 돌려줍니다.
     *
     * <p>화면이 수집 결과(장비 텔레메트리)와 저장된 이름을 <b>한 번에</b>
     * 합칠 수 있게 하려고 맵으로 줍니다. 목록을 돌며 매번 조회하면 VLAN 수만큼
     * DB 를 치게 됩니다.
     *
     * @param projectKey 프로젝트 키
     * @return 이름 맵 (없으면 빈 맵)
     */
    @Transactional(readOnly = true)
    public Map<String, String> namesOf(String projectKey) {
        final Map<String, String> names = new LinkedHashMap<>();
        if (projectKey == null || projectKey.isBlank()) {
            return names;
        }
        for (final ProjectVlan vlan : vlans.findByProject_ProjectKey(projectKey)) {
            if (vlan.getName() == null || vlan.getName().isBlank()) {
                continue;
            }
            names.put(key(vlan.getAgentId(), vlan.getVlanId()), vlan.getName());
        }
        return names;
    }

    /**
     * 프로젝트의 VLAN 이름 목록을 반환합니다. (API 응답용)
     *
     * @param projectKey 프로젝트 키
     * @return {@code {agent_id, vlan_id, name}} 목록
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String projectKey) {
        return vlans.findByProject_ProjectKey(projectKey).stream()
                .map(vlan -> {
                    final Map<String, Object> row = new LinkedHashMap<>();
                    row.put("agent_id", vlan.getAgentId());
                    row.put("vlan_id", vlan.getVlanId());
                    row.put("name", vlan.getName());
                    return row;
                })
                .toList();
    }

    /**
     * VLAN 이름을 저장합니다. (같은 키는 갱신)
     *
     * <h2>⚠️ 빈 이름은 삭제입니다</h2>
     * <p>운영자가 이름을 지우면 "미지정" 으로 돌아가야 합니다. 행을 빈 문자열로
     * 남기면 화면이 빈 칸을 이름으로 표시하고, 나중에 자동 이름을 덮어씁니다.
     *
     * @param projectKey 프로젝트 키
     * @param agentId    Agent 식별자
     * @param vlanId     VLAN 번호
     * @param name       이름 (null/빈 값이면 삭제)
     * @return 저장 후 이름 (삭제했으면 null)
     */
    @Transactional
    public String upsert(String projectKey, String agentId, Integer vlanId, String name) {
        if (projectKey == null || projectKey.isBlank()) {
            throw new IllegalArgumentException("project_key is required");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agent_id is required");
        }
        if (vlanId == null) {
            throw new IllegalArgumentException("vlan_id is required");
        }

        final String trimmed = (name == null) ? "" : name.trim();
        final Optional<ProjectVlan> existing =
                vlans.findByProject_ProjectKeyAndAgentIdAndVlanId(projectKey, agentId, vlanId);

        if (trimmed.isEmpty()) {
            existing.ifPresent(vlans::delete);
            log.info("vlan name cleared: project={} agent={} vlan={}", projectKey, agentId, vlanId);
            return null;
        }

        final String value = trimmed.length() > MAX_NAME_LENGTH
                ? trimmed.substring(0, MAX_NAME_LENGTH)
                : trimmed;
        if (existing.isPresent()) {
            existing.get().setName(value);
            vlans.save(existing.get());
        } else {
            final Project project = projects.findByProjectKey(projectKey)
                    .orElseThrow(() -> new IllegalArgumentException("project not found: " + projectKey));
            vlans.save(new ProjectVlan(project, agentId, vlanId, value));
        }
        log.info("vlan name saved: project={} agent={} vlan={} name={}",
                projectKey, agentId, vlanId, value);
        return value;
    }

    /**
     * 이름 맵의 키를 만듭니다.
     *
     * <p>Agent 식별자는 대소문자가 섞여 보고됩니다({@code Switch-3}). 저장과
     * 조회가 같은 표기를 쓰도록 소문자로 맞춥니다.
     *
     * @param agentId Agent 식별자
     * @param vlanId  VLAN 번호
     * @return 조회 키
     */
    public static String key(String agentId, Integer vlanId) {
        return (agentId == null ? "" : agentId.toLowerCase(Locale.ROOT)) + "|" + vlanId;
    }
}
