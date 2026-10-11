package org.sonar.sonarvalidator_backend.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Service.ProjectVlanNameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 수집된 VLAN 의 <b>이름 편집</b> API 입니다.
 *
 * <h2>⚠️ 왜 별도 엔드포인트인가</h2>
 * <p>VLAN 이름을 서브넷 편집({@code PUT /api/v1/projects/{id}})에 섞으면
 * <b>대역 없는 L2 VLAN</b> 은 표현할 수 없습니다. 서브넷 표는 {@code cidr} 이
 * 필수라, 주소 없는 VLAN 의 이름을 담으려면 가짜 대역을 넣어야 하고 그러면
 * 정책 엔진이 그것을 실제 대역으로 봅니다.
 *
 * <p>그래서 이름만 다루는 좁은 API 를 둡니다. 이름 변경은 등급·대역·집행
 * 규칙을 <b>건드리지 않습니다.</b>
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectVlanController {

    private final ProjectVlanNameService names;

    /**
     * @param names VLAN 이름 서비스
     */
    public ProjectVlanController(ProjectVlanNameService names) {
        this.names = names;
    }

    /**
     * 프로젝트에 저장된 VLAN 이름을 반환합니다.
     *
     * @param projectId 프로젝트 키
     * @return {@code {project_id, total, vlans[]}}
     */
    @GetMapping("/{projectId}/vlans")
    public Map<String, Object> list(@PathVariable String projectId) {
        final List<Map<String, Object>> vlans = names.list(projectId);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("total", vlans.size());
        body.put("vlans", vlans);
        return body;
    }

    /**
     * VLAN 이름을 저장합니다. ({@code name} 이 비면 미지정으로 되돌립니다)
     *
     * @param projectId 프로젝트 키
     * @param body      {@code {agent_id, vlan_id, name}}
     * @return 저장 결과
     */
    @PutMapping("/{projectId}/vlans")
    public Map<String, Object> save(@PathVariable String projectId,
                                    @RequestBody VlanNameRequest body) {
        final String saved = names.upsert(
                projectId,
                body == null ? null : body.agentId(),
                body == null ? null : body.vlanId(),
                body == null ? null : body.name());

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("project_id", projectId);
        result.put("agent_id", body == null ? null : body.agentId());
        result.put("vlan_id", body == null ? null : body.vlanId());
        result.put("name", saved);
        result.put("saved", saved != null);
        return result;
    }

    /**
     * VLAN 이름 저장 요청입니다.
     *
     * <p>프론트엔드는 스네이크 표기({@code agent_id})를 보내므로 명시적으로
     * 매핑합니다. record 의 컴포넌트 이름이 그대로 JSON 키가 되어, 표기가
     * 어긋나면 <b>조용히 null</b> 이 들어옵니다.
     *
     * @param agentId VLAN 을 보고한 Agent
     * @param vlanId  VLAN 번호
     * @param name    이름 (비우면 미지정)
     */
    public record VlanNameRequest(
            @JsonProperty("agent_id") String agentId,
            @JsonProperty("vlan_id") Integer vlanId,
            String name) {
    }
}
