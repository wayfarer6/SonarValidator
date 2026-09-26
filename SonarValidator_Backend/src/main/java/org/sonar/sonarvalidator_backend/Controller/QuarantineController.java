package org.sonar.sonarvalidator_backend.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.QuarantineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 격리/해제 API 입니다.
 *
 * <h2>왜 "수동 격리" 만 제공하는가</h2>
 * <p>자동 격리는 오탐 한 번으로 정상 장비를 끊습니다. 판정 엔진(BDD)이 아무리
 * 정확해도 관측 데이터가 틀리면 결과가 틀립니다. 그래서 <b>판정은 자동,
 * 조치는 수동</b>으로 나눴습니다. 위험을 감수할지는 사람이 정합니다.
 *
 * <h2>프론트엔드 사용 흐름</h2>
 * <ol>
 *   <li>위반 목록에서 대상 Agent 를 고른다</li>
 *   <li>{@code POST /api/v1/quarantine/{agentId}} 를 호출한다</li>
 *   <li>응답의 {@code delivered} 를 확인한다
 *       <ul>
 *         <li>{@code true} — 장치가 즉시 차단됨</li>
 *         <li>{@code false} — 장치 미연결. 상태는 저장됐고 재접속 시 적용됨</li>
 *       </ul>
 *   </li>
 *   <li>해제는 {@code DELETE /api/v1/quarantine/{agentId}}</li>
 * </ol>
 *
 * <h2>⚠️ 격리는 되돌릴 수 있어야 한다</h2>
 * <p>격리 API 를 만든 이상 <b>해제 API 도 반드시 같이</b> 있어야 합니다.
 * 해제가 없으면 운영자는 DB 를 직접 고치거나 장치를 재부팅해야 하고,
 * 그러면 격리 이력이 사라져 감사를 할 수 없습니다.
 */
@RestController
@RequestMapping("/api/v1/quarantine")
public class QuarantineController {

    private static final Logger log = LoggerFactory.getLogger(QuarantineController.class);

    private final QuarantineService quarantineService;

    /**
     * @param quarantineService 격리 서비스
     */
    public QuarantineController(QuarantineService quarantineService) {
        this.quarantineService = quarantineService;
    }

    /**
     * Agent 를 격리합니다.
     *
     * <p>본문은 선택입니다. {@code {"reason": "...", "project_id": "..."} 형태로
     * 사유를 함께 보내면 이력에 남습니다.
     *
     * @param agentId  대상 Agent 식별자
     * @param body     선택 본문 ({@code reason}, {@code project_id}, {@code requested_by})
     * @return 격리 결과 ({@code delivered} 확인 필수)
     */
    @PostMapping("/{agentId}")
    public Map<String, Object> isolate(@PathVariable String agentId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        final Map<String, Object> payload = body == null ? Map.of() : body;
        final String reason = stringValue(payload, "reason");
        final String projectKey = stringValue(payload, "project_id");
        final String requestedBy = stringValue(payload, "requested_by");

        log.info("quarantine request for agent={} by={}", agentId, requestedBy);
        return quarantineService.isolate(agentId, projectKey, reason, requestedBy);
    }

    /**
     * Agent 의 격리를 해제합니다.
     *
     * <p>격리 중이 아니면 {@code released=false} 를 돌려줍니다.
     * (거짓 성공 응답을 주지 않기 위함입니다)
     *
     * @param agentId    대상 Agent 식별자
     * @param releasedBy 요청 주체 (쿼리 파라미터, 선택)
     * @return 해제 결과
     */
    @DeleteMapping("/{agentId}")
    public Map<String, Object> release(@PathVariable String agentId,
                                       @RequestParam(value = "released_by", required = false) String releasedBy) {
        log.info("quarantine release request for agent={} by={}", agentId, releasedBy);
        return quarantineService.release(agentId, releasedBy);
    }

    /**
     * 현재 격리 중인 Agent 목록을 반환합니다.
     *
     * <p>토폴로지/Agent 화면이 "빨간색" 을 칠할 때 씁니다.
     *
     * @param projectId 프로젝트 키 (없으면 전체)
     * @return 격리 중 목록 + 개수
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "project_id", required = false) String projectId) {
        final List<Map<String, Object>> active = quarantineService.listActive(projectId);
        final Set<String> ids = quarantineService.quarantinedAgentIds();

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("total", active.size());
        body.put("agent_ids", ids);
        body.put("quarantined", active);
        return body;
    }

    /**
     * 한 Agent 가 지금 격리 중인지 확인합니다.
     *
     * <p>격리 버튼의 초기 상태를 정할 때 씁니다. 전체 목록 대신 한 건만
     * 조회하면 되므로 화면이 가볍습니다.
     *
     * @param agentId Agent 식별자
     * @return 격리 여부 + 이력
     */
    @GetMapping("/{agentId}")
    public Map<String, Object> status(@PathVariable String agentId) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", agentId);
        body.put("quarantined", quarantineService.isQuarantined(agentId));
        body.put("history", quarantineService.history(agentId));
        return body;
    }

    /**
     * 맵에서 문자열 값을 꺼냅니다. (없거나 비어 있으면 null)
     *
     * @param map 본문 맵
     * @param key 키
     * @return 문자열 값 (없으면 null)
     */
    private static String stringValue(Map<String, Object> map, String key) {
        final Object value = map.get(key);
        if (value == null) {
            return null;
        }
        final String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}