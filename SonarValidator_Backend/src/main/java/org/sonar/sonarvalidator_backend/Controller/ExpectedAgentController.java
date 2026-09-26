package org.sonar.sonarvalidator_backend.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.ExpectedAgentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/**
 * 배포 예정 Agent 목록과 <b>기대 대비 실제</b> 현황을 제공합니다.
 *
 * <h2>배포 화면이 이 API 를 필요로 하는 이유</h2>
 * <p>배포 버튼을 누른 직후에는 서버에 아무 흔적이 남지 않습니다. 그래서 화면을
 * 새로 고치면 "Agent 0대" 로 돌아가고, 운영자는 배포가 실패했는지 아직
 * 기동 중인지 알 수 없습니다. 배포 <b>결정</b> 을 서버에 남기면 그 사이를
 * 화면이 이어 줄 수 있습니다.
 *
 * <h2>{@code connected} 와 {@code total} 의 차이</h2>
 * <p>{@code total} 은 서버가 아는 모든 장치(예정 + 실제 관측)이고,
 * {@code connected} 는 그중 <b>지금 WebSocket 이 살아 있는</b> 수입니다.
 * 이 둘을 함께 보여줘야 "배포는 됐는데 통신이 안 되는" 상태가 드러납니다.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class ExpectedAgentController {

    private final ExpectedAgentService service;
    private final AgentSessionRegistry registry;
    private final AgentMessageRouterService router;

    /**
     * @param service  배포 예정 서비스
     * @param registry WebSocket 세션 레지스트리
     * @param router   텔레메트리/설정 조회용 라우터
     */
    public ExpectedAgentController(ExpectedAgentService service,
                                   AgentSessionRegistry registry,
                                   AgentMessageRouterService router) {
        this.service = service;
        this.registry = registry;
        this.router = router;
    }

    /**
     * 배포 예정 Agent 를 등록합니다. (같은 식별자는 갱신)
     *
     * <p>본문 예시:
     * <pre>
     * {
     *   "agent_id": "VDI-1-agent",
     *   "project_id": "poc-dai-pbl",
     *   "device_type": "VM",
     *   "node_type": "VM",
     *   "expected_ip": "10.10.131.11"
     * }
     * </pre>
     *
     * @param body 요청 본문
     * @return 등록/갱신 결과
     */
    @PostMapping("/expected")
    public Map<String, Object> register(@RequestBody JsonNode body) {
        final String agentId = text(body, "agent_id", text(body, "agentId", null));
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agent_id is required");
        }

        final ExpectedAgent saved = service.register(
                agentId,
                text(body, "project_id", text(body, "projectKey", null)),
                text(body, "device_type", text(body, "deviceType", null)),
                text(body, "node_type", text(body, "nodeType", null)),
                text(body, "expected_ip", text(body, "expectedIp", null)),
                text(body, "note", null));

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_id", saved.getAgentId());
        result.put("project_id", saved.getProjectKey());
        result.put("device_type", saved.getDeviceType());
        result.put("node_type", saved.getNodeType());
        result.put("status", saved.getStatus());
        result.put("registered", true);
        return result;
    }

    /**
     * 배포 예정 + 실제 관측을 합친 장치 현황을 반환합니다.
     *
     * @param projectId 프로젝트 키 (없으면 전체)
     * @return 통합 현황
     */
    @GetMapping("/expected")
    public Map<String, Object> list(@RequestParam(name = "project_id", required = false) String projectId) {
        final List<ExpectedAgent> expected = service.list(projectId);
        return ExpectedAgentService.toResponse(expected, registry.connectedAgentIds(), observedAgentIds());
    }

    /**
     * 배포 예정 항목을 삭제합니다. (연결된 세션은 건드리지 않음)
     *
     * @param agentId Agent 식별자
     * @return 삭제 결과
     */
    @DeleteMapping("/expected/{agentId}")
    public Map<String, Object> delete(@PathVariable String agentId) {
        service.delete(agentId);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_id", agentId);
        result.put("removed", true);
        return result;
    }

    /**
     * 텔레메트리를 보낸 적 있는 식별자 집합을 모읍니다.
     *
     * <p>기준을 두 갈래로 잡습니다.
     * <ul>
     *   <li>{@code allConfigs()} — 설정 변환까지 성공한 Agent</li>
     *   <li>{@code lastTelemetryOf()} — 원본만 도착한 Agent (파싱 실패 포함)</li>
     * </ul>
     * 두 번째를 빼면 "텔레메트리는 오는데 목록에는 없는" 장치가 생겨,
     * 운영자는 서버가 장치를 못 본다고 오해합니다.
     *
     * @return 관측된 식별자 (정렬)
     */
    private Set<String> observedAgentIds() {
        final Set<String> ids = new TreeSet<>();
        for (final String agentId : router.allConfigs().keySet()) {
            if (agentId != null) {
                ids.add(agentId);
            }
        }
        for (final String agentId : registry.connectedAgentIds()) {
            if (agentId != null && router.lastTelemetryOf(agentId) != null) {
                ids.add(agentId);
            }
        }
        return ids;
    }

    /**
     * 본문에서 문자열 값을 꺼냅니다. (snake_case 우선, camelCase 대안)
     *
     * @param body    본문
     * @param key     키
     * @param fallback 없을 때 값
     * @return 문자열 또는 fallback
     */
    private static String text(JsonNode body, String key, String fallback) {
        if (body == null) {
            return fallback;
        }
        final JsonNode node = body.path(key);
        if (node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        final String value = node.isString() ? node.asString() : node.toString();
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * 응답 조립에 쓰는 편의 메서드입니다. (테스트에서 직접 만들 때 사용)
     *
     * @param expected 배포 예정 목록
     * @param connectedIds 연결 식별자
     * @return 통합 현황
     */
    public static Map<String, Object> buildResponse(List<ExpectedAgent> expected, Set<String> connectedIds) {
        return ExpectedAgentService.toResponse(expected, new ArrayList<>(connectedIds), List.of());
    }
}