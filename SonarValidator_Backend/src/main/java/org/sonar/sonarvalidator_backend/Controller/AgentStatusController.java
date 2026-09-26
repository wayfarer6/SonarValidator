package org.sonar.sonarvalidator_backend.Controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.AgentTelemetryStore;
import org.sonar.sonarvalidator_backend.Service.DeviceConfigService;
import org.sonar.sonarvalidator_backend.Service.ExpectedAgentService;
import org.sonar.sonarvalidator_backend.Service.QuarantineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Agent 상태 조회 및 서버→Agent 푸시용 REST 엔드포인트입니다.
 *
 * <h2>왜 REST 도 필요한가</h2>
 * <p>WebSocket 은 <b>Agent→서버</b> 름과 <b>서버→Agent</b> 푸시를 담당합니다.
 * 그러나 "지금 몇 대 붙어 있나", "이 Agent 의 최근 텔레메트리는?" 같은 질문은
 * 웹 UI/운영자 관점의 <b>요청-응답</b>이므로 HTTP 가 자연스럽습니다.
 * 두 인터페이스는 같은 서비스({@link AgentSessionRegistry},
 * {@link AgentMessageRouterService})를 공유하므로 상태가 단일합니다.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentStatusController {

    private final AgentSessionRegistry registry;
    private final AgentMessageRouterService router;
    private final DeviceConfigService deviceConfigService;
    private final ExpectedAgentService expectedAgentService;

    /**
     * 격리 상태 통로입니다.
     *
     * <p>Agent 화면은 "이 장치가 격리 중인가" 를 알아야 격리/해제 버튼 중
     * 무엇을 보여줄지 정할 수 있습니다. 별도 API 를 부르지 않아도 되게
     * 목록 응답에 함께 실어 보냅니다.
     */
    private final QuarantineService quarantineService;

    /**
     * 원본·중립 설정·수신 시각 저장소입니다.
     *
     * <p>유령 Agent 정리(제거)에 씁니다. 제거 경로가 없으면 목록이 무한
     * 누적되는 문제를 해결하기 위해 추가했습니다.
     */
    private final AgentTelemetryStore telemetryStore;

    public AgentStatusController(AgentSessionRegistry registry,
                                 AgentMessageRouterService router,
                                 DeviceConfigService deviceConfigService,
                                 ExpectedAgentService expectedAgentService,
                                 QuarantineService quarantineService,
                                 AgentTelemetryStore telemetryStore) {
        this.registry = registry;
        this.router = router;
        this.deviceConfigService = deviceConfigService;
        this.expectedAgentService = expectedAgentService;
        this.quarantineService = quarantineService;
        this.telemetryStore = telemetryStore;
    }

    /**
     * 연결된 Agent 목록과 요약 상태를 반환합니다.
     *
     * @return {@code {"connected": n, "agents": [...]}}
     */
    @GetMapping
    public Map<String, Object> list() {
        final java.util.Set<String> quarantined = quarantineService.quarantinedAgentIds();
        final List<Map<String, Object>> agents = new ArrayList<>();
        for (String agentId : registry.connectedAgentIds()) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("last_seen", router.lastSeenOf(agentId));
            entry.put("has_telemetry", router.lastTelemetryOf(agentId) != null);
            entry.put("quarantined", isQuarantined(quarantined, agentId));
            agents.add(entry);
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected", registry.connectedCount());
        body.put("total_policy_requests", router.policyRequestCount());
        body.put("server_time", Instant.now().toString());
        body.put("quarantined_count", quarantined.size());
        body.put("quarantined", quarantined);
        body.put("agents", agents);
        return body;
    }

    /**
     * Agent 식별자가 격리 집합에 있는지 <b>대소문자 무시</b>로 확인합니다.
     *
     * <p>Agent 식별자는 운영자가 적어 넣는 값이라 {@code VDI-1} / {@code vdi-1}
     * 이 섞입니다. 정확히 비교하면 격리된 장치가 "정상" 으로 보여 버립니다.
     *
     * @param quarantined 격리 중인 식별자 집합
     * @param agentId     확인할 식별자
     * @return 격리 중이면 {@code true}
     */
    private static boolean isQuarantined(java.util.Set<String> quarantined, String agentId) {
        if (agentId == null) {
            return false;
        }
        return quarantined.stream().anyMatch(id -> id != null && id.equalsIgnoreCase(agentId));
    }

    /**
     * 배포 예정 + 실제 관측을 합친 <b>통합 장치 현황</b>을 반환합니다.
     *
     * <h2>{@link #list()} 와의 차이</h2>
     * <p>{@code list()} 는 "지금 붙어 있는 Agent" 만 봅니다. 운영 화면은 그보다
     * 넓은 시야가 필요합니다 — <i>배포했는데 아직 안 붙은 장치</i>가 보여야
     * 배포 실패를 알아챌 수 있습니다. 그래서 이 엔드포인트는
     * 예정({@code expected_agent}) ∪ 연결(WebSocket) ∪ 텔레메트리를 합쳐
     * {@code state} 로 구분해 돌려줍니다.
     *
     * <p>기존 응답 키({@code connected} / {@code agents})는 그대로 유지합니다.
     * 화면이 두 형태를 모두 소화할 필요가 없도록 하기 위함입니다.
     *
     * @param projectId 프로젝트 키 (없으면 전체)
     * @return 통합 현황 + 기존 요약 키
     */
    @GetMapping("/overview")
    public Map<String, Object> overview(
            @RequestParam(name = "project_id", required = false) String projectId) {
        final java.util.Set<String> observed = new java.util.TreeSet<>();
        for (final String agentId : router.allConfigs().keySet()) {
            if (agentId != null) {
                observed.add(agentId);
            }
        }
        for (final String agentId : router.allTelemetryAgentIds()) {
            if (agentId != null) {
                observed.add(agentId);
            }
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected", registry.connectedCount());
        body.put("total_policy_requests", router.policyRequestCount());
        body.put("server_time", Instant.now().toString());
        body.put("agents", new ArrayList<>(registry.connectedAgentIds()));
        final java.util.Set<String> quarantined = quarantineService.quarantinedAgentIds();
        body.put("quarantined_count", quarantined.size());
        body.put("quarantined", quarantined);
        body.putAll(ExpectedAgentService.toResponse(
                expectedAgentService.list(projectId), registry.connectedAgentIds(), observed));
        return body;
    }

    /**
     * 특정 Agent 의 최근 텔레메트리를 반환합니다.
     *
     * @param agentId Agent 식별자
     * @return 텔레메트리 JSON, 없으면 404
     */
    @GetMapping("/{agentId}/telemetry")
    public JsonNode telemetry(@PathVariable String agentId) {
        final JsonNode telemetry = router.lastTelemetryOf(agentId);
        if (telemetry == null) {
            throw new AgentNotFoundException(agentId);
        }
        return telemetry;
    }

    /**
     * 특정 Agent 의 <b>중립 설정</b>을 반환합니다.
     *
     * <p>텔레메트리 원본({@code /telemetry})은 벤더 고유 표현을 그대로 담고 있습니다.
     * 이 엔드포인트는 그것을 벤더 중립 구조(인터페이스/VLAN/트렌크/라우팅/규칙)로
     * 변환한 결과를 돌려줍니다. 분석·비교 UI 는 이쪽을 쓰면 벤더를 몰라도 됩니다.
     *
     * @param agentId Agent 식별자
     * @return 중립 설정, 없으면 404
     */
    @GetMapping("/{agentId}/config")
    public NeutralDeviceConfig config(@PathVariable String agentId) {
        final NeutralDeviceConfig config = router.lastConfigOf(agentId);
        if (config == null) {
            throw new AgentNotFoundException(agentId);
        }
        return config;
    }

    /**
     * 등록된 벤더 파서 목록과, 현재 변환된 설정의 요약을 돌려줍니다.
     *
     * <p>운영자가 "어떤 장비가 어떤 형식으로 해석됐는지" 한 번에 확인하는 용도입니다.
     *
     * @return 형식 목록 + Agent 별 요약
     */
    @GetMapping("/configs")
    public Map<String, Object> configs() {
        final List<Map<String, Object>> summaries = new ArrayList<>();
        router.allConfigs().forEach((agentId, config) -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("format", config.getFormat());
            entry.put("vendor", config.getVendor());
            entry.put("product", config.getProduct());
            entry.put("interfaces", config.getInterfaces().size());
            entry.put("routes", config.getRoutes().size());
            entry.put("vlans", config.getVlans().size());
            entry.put("arp_entries", config.getArpEntries().size());
            entry.put("firewall_rules", config.getFirewallRules().size());
            entry.put("warnings", List.copyOf(config.getWarnings()));
            summaries.add(entry);
        });

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("supported_formats", deviceConfigService.supportedFormats());
        body.put("parsed_devices", summaries.size());
        body.put("devices", summaries);
        return body;
    }

    /**
     * 특정 Agent 에게 서버 지시(푸시)를 보냅니다.
     *
     * <p>본문은 두 가지 형태를 모두 받습니다.
     * <ul>
     *   <li>본문 자체가 payload 인 경우: {@code {"monitor_interval":60}}</li>
     *   <li>봉투를 그대로 준 경우: {@code {"type":"command","payload":{...}}}</li>
     * </ul>
     *
     * <p>어느 쪽이든 전송되는 봉투의 type 은 항상 {@code command} 입니다.
     * (운영자가 임의 type 을 밀어 넣어 Agent 상태를 깨뜨리지 못하게 합니다.)
     *
     * @param agentId 대상 Agent 식별자
     * @param body 봉투 또는 payload
     * @return 전송 결과
     */
    @PostMapping("/{agentId}/push")
    public Map<String, Object> push(@PathVariable String agentId,
                                    @RequestBody JsonNode body) {
        final Envelope envelope = toCommandEnvelope(body);
        envelope.setAgent_id(agentId);

        final boolean delivered = registry.sendTo(agentId, envelope);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_id", agentId);
        result.put("delivered", delivered);
        result.put("reason", delivered ? null : "agent not connected");
        return result;
    }

    /**
     * 연결된 모든 Agent 에게 지시를 브로드캐스트합니다.
     *
     * @param body 봉투 또는 payload
     * @return 전송 성공 수
     */
    @PostMapping("/broadcast")
    public Map<String, Object> broadcast(@RequestBody JsonNode body) {
        final Envelope envelope = toCommandEnvelope(body);
        final int sent = registry.broadcast(envelope);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", sent);
        result.put("connected", registry.connectedCount());
        return result;
    }

    /**
     * Agent 하나의 수집 이력을 제거합니다. (유령 정리)
     *
     * <h2>왜 필요한가</h2>
     * <p>한 번이라도 텔레메트리를 보낸 Agent 는 저장소에 영구히 남아
     * {@code overview} 목록을 계속 차지합니다. 랩에서 프로버를 여러 번
     * 기동하면 그때마다 새 이름이 생겨 <b>프로세스 1대인데 목록이 10건</b>이
     * 되는 일이 있었습니다.
     *
     * <p>연결 중인 Agent 는 제거하지 않습니다. 살아 있는 세션을 지우면
     * 다음 텔레메트리(30초 뒤)에 다시 나타나 <b>깜빡임</b>을 만듭니다.
     *
     * @param agentId 대상 Agent 식별자
     * @return 제거 결과 ({@code removed}, {@code connected}, {@code reason})
     */
    @DeleteMapping("/{agentId}/telemetry")
    public ResponseEntity<Map<String, Object>> removeTelemetry(@PathVariable String agentId) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_id", agentId);

        if (registry.connectedAgentIds().contains(agentId)) {
            result.put("removed", false);
            result.put("connected", true);
            result.put("reason", "Agent is connected; disconnecting it first would be needed");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }

        final boolean removed = telemetryStore.remove(agentId);
        result.put("removed", removed);
        result.put("connected", false);
        result.put("reason", removed ? null : "no telemetry for this agent");
        return removed
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 오래된 수신 이력을 일괄 정리합니다.
     *
     * <p>{@code older_than_hours} 를 주면 그 시간보다 오래 수신이 없는
     * Agent 를 제거합니다. 생략하면 기본 24시간을 씁니다.
     *
     * @param olderThanHours 기준 시간 (기본 24)
     * @return 제거 결과 ({@code removed}, {@code remaining})
     */
    @DeleteMapping("/stale")
    public Map<String, Object> pruneStale(
            @RequestParam(name = "older_than_hours", required = false) Double olderThanHours) {
        final double hours = (olderThanHours == null || olderThanHours <= 0) ? DEFAULT_STALE_HOURS
                : olderThanHours;
        final java.time.Instant cutoff = java.time.Instant.now()
                .minusMillis((long) (hours * 3_600_000L));

        final int removed = telemetryStore.pruneUnseenSince(cutoff);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("removed", removed);
        result.put("older_than_hours", hours);
        result.put("cutoff", cutoff.toString());
        result.put("remaining_telemetry", telemetryStore.allTelemetryAgentIds().size());
        return result;
    }

    /** 유령 정리의 기본 기준 시간(시간 단위). */
    private static final double DEFAULT_STALE_HOURS = 24;

    /**
     * REST 본문을 서버→Agent 푸시용 {@code command} 봉투로 변환합니다.
     *
     * <p>"envelope" 키가 있으면 그것을 payload 로, 없으면 본문 전체를 payload 로
     * 취급합니다. 덕분에 {@code {"monitor_interval":60}} 같은 짧은 호출도,
     * 완전한 봉투도 모두 동작합니다.
     *
     * @param body REST 본문
     * @return type 이 {@code command} 인 봉투
     */
    private Envelope toCommandEnvelope(JsonNode body) {
        final Envelope envelope = Envelope.of(Envelope.Types.COMMAND);

        if (body == null || body.isNull() || body.isMissingNode()) {
            return envelope;
        }

        // 봉투를 그대로 보낸 경우: 기존 correlation_id/payload 를 존중합니다.
        final JsonNode explicitPayload = body.path("payload");
        if (explicitPayload.isObject()) {
            envelope.setPayload(explicitPayload);
            final JsonNode correlationId = body.path("correlation_id");
            if (correlationId.isString()) {
                envelope.setCorrelation_id(correlationId.asString());
            }
            return envelope;
        }

        // 짧은 호출: 본문 전체를 payload 로 사용합니다.
        envelope.setPayload(body);
        return envelope;
    }

    /**
     * Agent 가 없을 때 404 를 내기 위한 예외입니다.
     * {@link org.springframework.web.bind.annotation.ResponseStatus} 대신
     * {@code @ResponseStatus} 를 여 별도 들러 없이 동작하게 합니다.
     */
    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class AgentNotFoundException extends RuntimeException {
        /**
         * @param agentId 찾지 못한 Agent 식별자
         */
        public AgentNotFoundException(String agentId) {
            super("no telemetry for agent: " + agentId);
        }
    }
}