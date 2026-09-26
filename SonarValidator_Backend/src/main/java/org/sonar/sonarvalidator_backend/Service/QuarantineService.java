package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Model.entity.QuarantineState;
import org.sonar.sonarvalidator_backend.Repository.QuarantineStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Agent 격리(isolation)와 해제를 담당합니다.
 *
 * <h2>격리 명령이 지나는 길</h2>
 * <pre>
 *   운영자 클릭 (프론트)
 *     → POST /api/v1/quarantine/{agentId}
 *       → QuarantineService.isolate()
 *         ├─ DB 에 QuarantineState 기록        (판단 근거 남기기)
 *         ├─ WebSocket 으로 command 봉투 전송  (장치에게 실제로 시키기)
 *         ├─ ComplianceService 이력 기록       (무엇이 바뀌었나)
 *         └─ NotificationService 알림 기록     (운영자에게 알리기)
 * </pre>
 *
 * <h2>⚠️ 순서가 중요하다: DB 먼저, 전송 나중</h2>
 * <p>전송을 먼저 하면, 전송은 성공했는데 DB 저장이 실패하는 순간
 * <b>장치는 격리됐는데 서버는 모르는 상태</b>가 됩니다. 그러면 해제 버튼이
 * 뜨지 않아 운영자가 장치를 되살릴 방법이 없습니다.
 *
 * <p>반대로 DB 를 먼저 하면, 전송이 실패해도 <b>"격리하려 했으나 전달 실패"</b>
 * 라는 사실이 남습니다. 운영자가 재시도할 수 있고, 위험한 방향(장치는 살아
 * 있는데 서버는 격리됐다고 믿는 것)이 아닙니다.
 *
 * <h2>⚠️ 자동 격리는 하지 않는다</h2>
 * <p>오탐 한 번으로 정상 장비를 끊으면 서비스가 마비됩니다. 격리는 반드시
 * 사람이 누릅니다. 이 서비스는 <b>요청받은 격리를 수행</b>할 뿐,
 * 스스로 판단해 격리하지 않습니다.
 *
 * <h2>⚠️ 연결이 끊긴 장치도 격리할 수 있다</h2>
 * <p>명령은 전달되지 않지만(사실을 응답에 남깁니다) 상태는 DB 에 남습니다.
 * 그래서 그 장치가 다시 접속해 정책을 요청하면
 * {@link #isQuarantined(String)} 가 {@code true} 라서 차단 정책을 받습니다.
 */
@Service
public class QuarantineService {

    private static final Logger log = LoggerFactory.getLogger(QuarantineService.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /** 격리 이력 조회 시 한 번에 가져올 최대 건수입니다. */
    private static final int HISTORY_LIMIT = 200;

    /**
     * ⚠️ {@code "quarantine"} / {@code "release"} 문자열은 Prober 의
     * {@code envelope.hpp} 에 있는 상수와 <b>정확히 같아야</b> 합니다.
     * 한 글자만 달라도 Agent 는 알 수 없는 명령으로 무시합니다.
     */
    private static final String ACTION_QUARANTINE = "quarantine";
    private static final String ACTION_RELEASE = "release";

    private final QuarantineStateRepository repository;
    private final AgentSessionRegistry registry;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;

    /**
     * 장치 유형 판별기입니다. (방화벽 격리 제외 판단)
     *
     * <p><b>선택 의존</b>입니다. 격리 서비스 단위 테스트가 이 판별기 없이
     * 돌아야 하기 때문입니다(저장소 목이 필요). 없으면 <b>식별자 추론만</b>으로
     * 유형을 판단하므로 {@code GNS3.Firewall} 같은 이름도 제대로 걸러집니다.
     */
    private DeviceTypeResolver deviceTypeResolver;

    /**
     * 보낸 격리/해제 명령의 <b>실제 적용 결과</b>를 기억합니다.
     *
     * <h2>왜 필요한가</h2>
     * <p>{@link AgentSessionRegistry#sendTo} 가 {@code true} 를 돌려주는 것은
     * "소켓에 써 넣었다" 는 뜻일 뿐입니다. 장치가 인터페이스를 실제로 내렸는지는
     * <b>알 수 없습니다.</b> Agent 는 적용 결과를 {@code ack} 봉투로 돌려주고,
     * 그때 격리가 비로소 완결됩니다.
     *
     * <p>그래서 운영자 응답에는 {@code delivered}(보냈나)와
     * {@code applied}(적용됐나)를 구분해 실어 보냅니다. 두 값이 다르면
     * "장치는 살아 있고 서버는 격리됐다고 믿는" 위험한 상태를 화면에서
     * 즉시 알아챌 수 있습니다.
     */
    private final Map<String, Map<String, Object>> lastAck = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * @param repository          격리 상태 저장소
     * @param registry            Agent 세션 레지스트리 (명령 전송)
     * @param complianceService   변경 이력 서비스
     * @param notificationService 알림 서비스
     */
    public QuarantineService(QuarantineStateRepository repository,
                             AgentSessionRegistry registry,
                             ComplianceService complianceService,
                             NotificationService notificationService) {
        this.repository = repository;
        this.registry = registry;
        this.complianceService = complianceService;
        this.notificationService = notificationService;
    }

    /**
     * 격리 상태 조회 통로를 주입합니다.
     *
     * @param deviceTypeResolver 장치 유형 판별기 (테스트에서는 생략 가능)
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDeviceTypeResolver(DeviceTypeResolver deviceTypeResolver) {
        this.deviceTypeResolver = deviceTypeResolver;
    }

    // ------------------------------------------------------------------
    //  격리 / 해제
    // ------------------------------------------------------------------

    /**
     * Agent 를 격리합니다.
     *
     * <p>이미 격리 중이면 <b>명령만 다시 보냅니다.</b> 새 행을 만들지 않는
     * 이유는, 격리 중에 재시도하는 것은 "그때 명령이 안 갔을 수도 있으니
     * 다시 보내자" 는 뜻이지 새로운 사건이 아니기 때문입니다.
     *
     * @param agentId     격리할 Agent 식별자
     * @param projectKey  프로젝트 키 (없으면 null)
     * @param reason      격리 사유 (운영자 메모)
     * @param requestedBy 요청 주체
     * @return 격리 결과
     */
    @Transactional
    public Map<String, Object> isolate(String agentId,
                                       String projectKey,
                                       String reason,
                                       String requestedBy) {
        final String operator = requestedBy == null || requestedBy.isBlank() ? "operator" : requestedBy;

        // ⚠️ 방화벽은 격리 대상이 아닙니다.
        //   랩의 방화벽은 eth1 트렁크로 VLAN 131/132/133 을 동시에 들고 있어,
        //   인터페이스를 내리면 격리하려던 한 대가 아니라 <b>무관한 존 전체</b>가
        //   끊깁니다. 그래서 여기서 <b>거부</b>하고, 그 사실을 응답에 남깁니다.
        //
        //   조용히 무시하지 않는 이유: 운영자는 버튼을 눌렀다고 믿습니다.
        //   "격리했습니다" 라고 거짓 응답하면 뚫린 망을 방치하게 됩니다.
        final DeviceType deviceType = resolveDeviceType(agentId);
        if (DeviceTypeResolver.exclusionReason(deviceType) != null) {
            final String why = DeviceTypeResolver.exclusionReason(deviceType);
            log.warn("rejected isolation of {} ({}) — not isolatable", agentId, deviceType);
            return excludedResponse(agentId, projectKey, deviceType, why);
        }

        final Optional<QuarantineState> active = repository.findByAgentIdAndReleasedAtIsNull(agentId);
        final boolean retry = active.isPresent();

        final QuarantineState state;
        if (retry) {
            // 이미 격리 중 — 새 사건이 아니므로 상태는 그대로 두고 명령만 재전송합니다.
            state = active.get();
            if (reason != null && !reason.isBlank()) {
                state.setReason(reason);
            }
            log.info("agent {} already quarantined; re-sending command", agentId);
        } else {
            final QuarantineState created = new QuarantineState();
            created.setAgentId(agentId);
            created.setProjectKey(projectKey);
            created.setReason(reason);
            created.setRequestedBy(operator);
            created.setQuarantinedAt(new Date());
            created.setCommandDelivered(false);
            state = repository.save(created);
            log.warn("quarantining agent={} project={} by={} reason={}",
                    agentId, projectKey, operator, reason);
        }

        // DB 를 먼저 남긴 뒤에 명령을 보냅니다. (위 "순서가 중요하다" 참고)
        final boolean delivered = sendCommand(agentId, ACTION_QUARANTINE, projectKey, state.getReason());
        if (delivered != state.isCommandDelivered()) {
            state.setCommandDelivered(delivered);
            repository.save(state);
        }

        // 격리는 보안 사건입니다. 이력과 알림 <b>양쪽</b>에 남깁니다.
        // 이력은 "무엇이 바뀌었나"(감사), 알림은 "지금 조치가 필요하다"(경고)입니다.
        complianceService.recordQuietly(
                "Agent",
                projectKey,
                agentId,
                retry ? "QuarantineRetry" : "Quarantine",
                "Agent " + agentId + " 격리"
                        + (state.getReason() == null ? "" : " — " + state.getReason())
                        + (delivered ? " (명령 전달됨)" : " (⚠️ 명령 미전달 — 장치 미연결)"),
                operator,
                "{\"agent_id\":\"" + agentId + "\",\"delivered\":" + delivered + "}");

        notificationService.notifyQuietly(
                "SECURITY",
                delivered ? "critical" : "warning",
                "Agent 격리: " + agentId,
                delivered
                        ? "위반 장치 " + agentId + " 를 격리했습니다. 해당 장치의 트래픽이 차단됩니다."
                        : "위반 장치 " + agentId + " 를 격리하려 했으나 명령이 전달되지 않았습니다. "
                                + "(장치 미연결) 재접속 시 차단 정책이 적용됩니다.",
                projectKey,
                agentId,
                "quarantine",
                "/agent",
                "quarantine:" + agentId);

        return toResponse(state, delivered, retry);
    }

    /**
     * Agent 의 격리를 해제합니다.
     *
     * <p>격리 중이 아니면 <b>아무것도 하지 않고</b> 그 사실을 알려줍니다.
     * "해제했습니다" 라고 거짓 응답하면 운영자는 장치가 풀렸다고 믿습니다.
     *
     * @param agentId    해제할 Agent 식별자
     * @param releasedBy 요청 주체
     * @return 해제 결과
     */
    @Transactional
    public Map<String, Object> release(String agentId, String releasedBy) {
        final String operator = releasedBy == null || releasedBy.isBlank() ? "operator" : releasedBy;

        final Optional<QuarantineState> active = repository.findByAgentIdAndReleasedAtIsNull(agentId);
        if (active.isEmpty()) {
            final Map<String, Object> body = new LinkedHashMap<>();
            body.put("agent_id", agentId);
            body.put("released", false);
            body.put("reason", "agent is not quarantined");
            return body;
        }

        final QuarantineState state = active.get();
        state.setReleasedAt(new Date());
        state.setReleasedBy(operator);
        repository.save(state);

        // 해제 명령을 보내 장치가 인터페이스를 다시 올리게 합니다.
        final boolean delivered = sendCommand(agentId, ACTION_RELEASE, state.getProjectKey(), null);

        log.info("released agent={} by={} delivered={}", agentId, operator, delivered);

        complianceService.recordQuietly(
                "Agent",
                state.getProjectKey(),
                agentId,
                "QuarantineRelease",
                "Agent " + agentId + " 격리 해제"
                        + (delivered ? " (명령 전달됨)" : " (⚠️ 명령 미전달 — 장치 미연결)"),
                operator,
                "{\"agent_id\":\"" + agentId + "\",\"delivered\":" + delivered + "}");

        notificationService.notifyQuietly(
                "SECURITY",
                delivered ? "info" : "warning",
                "Agent 격리 해제: " + agentId,
                delivered
                        ? "장치 " + agentId + " 의 격리를 해제했습니다. 정상 정책이 다시 적용됩니다."
                        : "장치 " + agentId + " 의 격리를 해제했으나 명령이 전달되지 않았습니다. "
                                + "장치가 연결되면 정상 정책이 적용됩니다.",
                state.getProjectKey(),
                agentId,
                "quarantine",
                "/agent",
                "quarantine-release:" + agentId);

        // 해제는 같은 키로 알림을 합치지 않습니다. 격리/해제가 반복되면
        // 합쳐져서 "지금 격리 중인가" 를 알 수 없게 되기 때문입니다.
        return withReleasedFlag(toResponse(state, delivered, false), true);
    }

    /**
     * 해제 응답에 {@code released} 를 명시합니다.
     *
     * <h2>⚠️ 왜 필요한가</h2>
     * <p>{@link #toResponse} 는 격리 응답용이라 {@code released} 를 넣지
     * 않습니다. 그러면 해제 <b>성공</b> 응답에도 이 키가 없고,
     * 클라이언트는 {@code result.released} 가 {@code undefined} 인 것을 보고
     * <b>"격리 중이 아니었다"</b> 로 해석합니다. 실제로는 성공했는데 화면이
     * 정반대 메시지를 띄우는 셈입니다. (최종 E2E 에서 실제로 재현)
     *
     * <p>실패 경로만 {@code released:false} 를 넣고 성공 경로는 빠뜨리면,
     * "키가 없다 = 실패" 라는 규칙을 클라이언트가 알 수 없습니다.
     * <b>두 경로 모두 명시</b>해야 합니다.
     *
     * @param body     격리 응답 본문
     * @param released 해제가 실제로 일어났는지
     * @return {@code released} 가 포함된 본문
     */
    private static Map<String, Object> withReleasedFlag(Map<String, Object> body, boolean released) {
        body.put("released", released);
        return body;
    }

    /**
     * 격리 대상이 아니라는 응답을 만듭니다.
     *
     * <h2>⚠️ 행을 만들지 않는다</h2>
     * <p>거부된 요청에 대해 격리 행을 남기면 이후 목록/이력에 "격리 중" 인 것처럼
     * 보입니다. 거부는 <b>상태 변화가 없습니다.</b> 그래서 DB/명령/알림 어느 것도
     * 건드리지 않고 응답만 만듭니다.
     *
     * <p>대신 <b>이력에는 남깁니다.</b> "왜 방화벽이 격리 안 되나" 를 운영자가
     * 나중에 물을 수 있고, 시도 자체는 감사 대상입니다.
     *
     * @param agentId    Agent 식별자
     * @param projectKey 프로젝트 키
     * @param type       장치 유형
     * @param why        거부 사유
     * @return 운영자 응답
     */
    private Map<String, Object> excludedResponse(String agentId,
                                                 String projectKey,
                                                 DeviceType type,
                                                 String why) {
        complianceService.recordQuietly(
                "Agent",
                projectKey,
                agentId,
                "QuarantineRejected",
                "Agent " + agentId + " 격리 거부 — " + why,
                "system",
                "{\"agent_id\":\"" + agentId + "\",\"device_type\":\""
                        + DeviceTypeResolver.nameOf(type) + "\"}");

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", agentId);
        body.put("project_id", projectKey);
        body.put("device_type", DeviceTypeResolver.nameOf(type));
        body.put("quarantined", false);
        body.put("delivered", false);
        body.put("applied", false);
        body.put("rejected", true);
        body.put("reason", why);
        body.put("hint", "프로젝트 규칙에서 해당 연결만 차단하세요.");
        return withReleasedFlag(body, false);
    }

    /**
     * Agent 의 장치 유형을 판별합니다.
     *
     * @param agentId Agent 식별자
     * @return 장치 유형 (절대 null 이 아님)
     */
    private DeviceType resolveDeviceType(String agentId) {
        if (deviceTypeResolver != null) {
            return deviceTypeResolver.resolve(agentId, null);
        }
        // 판별기가 없으면(단위 테스트) 식별자 관례만으로 판단합니다.
        // GNS3.Firewall / DMZ-Firewall 처럼 끝 토큰이 FIREWALL 이면 걸러집니다.
        return DeviceTypeResolver.resolveWithoutRepository(agentId, null);
    }

    /**
     * Agent 에게 격리/해제 명령을 보냅니다.
     *
     * @param agentId    대상 Agent
     * @param action     {@code "quarantine"} 또는 {@code "release"}
     * @param projectKey 프로젝트 키 (Agent 가 로그에 남김)
     * @param reason     사유 (격리일 때만)
     * @return 전달 성공 여부 (미연결이면 {@code false})
     */
    private boolean sendCommand(String agentId, String action, String projectKey, String reason) {
        final ObjectNode payload = JSON.objectNode();
        payload.put("action", action);
        payload.put("agent_id", agentId);
        if (projectKey != null) {
            payload.put("project_id", projectKey);
        }
        if (reason != null && !reason.isBlank()) {
            payload.put("reason", reason);
        }
        payload.put("issued_at", Instant.now().toString());

        final Envelope envelope = Envelope.of(Envelope.Types.COMMAND);
        envelope.setAgent_id(agentId);
        envelope.setPayload(payload);

        final boolean delivered = registry.sendTo(agentId, envelope);
        if (!delivered) {
            log.warn("quarantine {} command NOT delivered to agent {} (not connected)", action, agentId);
        }
        return delivered;
    }

    // ------------------------------------------------------------------
    //  조회
    // ------------------------------------------------------------------

    /**
     * 특정 Agent 가 <b>현재</b> 격리 중인지 확인합니다.
     *
     * <p>정책 푸시와 토폴로지 표시에서 호출합니다.
     *
     * @param agentId Agent 식별자
     * @return 격리 중이면 {@code true}
     */
    @Transactional(readOnly = true)
    public boolean isQuarantined(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return false;
        }
        return repository.findByAgentIdAndReleasedAtIsNull(agentId).isPresent();
    }

    /**
     * 현재 격리 중인 Agent 식별자 집합입니다.
     *
     * <p>정책 푸시에서 대상 제외에 씁니다. 매 서브넷마다 DB 를 치지 않도록
     * 한 번에 집합으로 받습니다.
     *
     * @return 격리 중인 Agent 식별자 (소문자 아님 — 원본 그대로)
     */
    @Transactional(readOnly = true)
    public Set<String> quarantinedAgentIds() {
        final List<QuarantineState> rows = repository.findByReleasedAtIsNullOrderByQuarantinedAtDesc(
                PageRequest.of(0, HISTORY_LIMIT));
        final Set<String> ids = new LinkedHashSet<>();
        for (final QuarantineState row : rows) {
            ids.add(row.getAgentId());
        }
        return ids;
    }

    /**
     * 현재 격리 중인 상태 목록을 반환합니다.
     *
     * @param projectKey 프로젝트 키 (없으면 전체)
     * @return 격리 상태 목록 (화면/API 용 맵)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listActive(String projectKey) {
        final List<QuarantineState> rows = (projectKey == null || projectKey.isBlank())
                ? repository.findByReleasedAtIsNullOrderByQuarantinedAtDesc(PageRequest.of(0, HISTORY_LIMIT))
                : repository.findByProjectKeyAndReleasedAtIsNullOrderByQuarantinedAtDesc(
                        projectKey, PageRequest.of(0, HISTORY_LIMIT));

        final List<Map<String, Object>> result = new ArrayList<>();
        for (final QuarantineState row : rows) {
            result.add(toSummary(row));
        }
        return result;
    }

    /**
     * 한 Agent 의 격리 이력을 반환합니다. (해제된 것 포함)
     *
     * @param agentId Agent 식별자
     * @return 격리 이력
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(String agentId) {
        final List<QuarantineState> rows = repository.findByAgentIdOrderByQuarantinedAtDesc(
                agentId, PageRequest.of(0, HISTORY_LIMIT));
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final QuarantineState row : rows) {
            result.add(toSummary(row));
        }
        return result;
    }

    /**
     * 격리 상태를 응답 맵으로 변환합니다.
     *
     * @param state     저장된 상태
     * @param delivered 이번 요청에서 명령이 전달됐는지
     * @param retry     이미 격리 중이었는지
     * @return API 응답
     */
    private Map<String, Object> toResponse(QuarantineState state, boolean delivered, boolean retry) {
        final Map<String, Object> body = toSummary(state);
        body.put("delivered", delivered);
        body.put("retry", retry);
        if (!delivered) {
            // 운영자가 "왜 반영이 안 되지" 를 묻지 않도록 이유를 문장으로 남깁니다.
            body.put("warning", "장치가 연결되어 있지 않아 명령이 전달되지 않았습니다. "
                    + "장치가 재접속하면 차단 정책이 자동 적용됩니다.");
        }

        // Agent 가 ack 로 알려온 실제 적용 결과입니다.
        // (null 이면 아직 ack 가 오지 않았거나 구버전 Agent 입니다.)
        final Map<String, Object> ack = lastAck.get(state.getAgentId());
        body.put("applied", ack == null ? null : ack.get("ok"));
        if (ack != null) {
            body.put("applied_detail", ack.get("detail"));
            body.put("available", ack.get("available"));
            body.put("blocked", ack.get("blocked"));
            body.put("preserved", ack.get("preserved"));
        }
        body.put("connected", registry.connectedAgentIds().contains(state.getAgentId()));
        return body;
    }

    /**
     * 격리 상태를 요약 맵으로 변환합니다.
     *
     * @param row 저장된 상태
     * @return 요약 맵
     */
    private Map<String, Object> toSummary(QuarantineState row) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("agent_id", row.getAgentId());
        entry.put("project_id", row.getProjectKey());
        entry.put("reason", row.getReason());
        entry.put("requested_by", row.getRequestedBy());
        entry.put("command_delivered", row.isCommandDelivered());
        entry.put("quarantined_at", row.getQuarantinedAt() == null ? null : row.getQuarantinedAt().toInstant().toString());
        entry.put("released_at", row.getReleasedAt() == null ? null : row.getReleasedAt().toInstant().toString());
        entry.put("released_by", row.getReleasedBy());
        entry.put("active", row.isActive());
        return entry;
    }

    /**
     * Agent 가 보낸 격리/해제 명령의 <b>적용 결과</b>(ack)를 기록합니다.
     *
     * <h2>왜 서버가 ack 를 해석해야 하는가</h2>
     * <p>{@code sendTo} 가 {@code true} 인 것은 소켓에 써 넣었다는 뜻일 뿐,
     * 장치가 인터페이스를 정말 내렸는지는 알 수 없습니다. Agent 가 ack 로
     * 돌려준 결과를 여기서 받아 두어야 운영자 화면이
     * "명령 보냄" 과 "실제 차단됨" 을 구분해 보여줄 수 있습니다.
     *
     * <p>⚠️ ack 를 받지 못했다고 격리를 실패로 처리하지 않습니다. 구버전
     * Agent 는 ack 를 보내지 않으며, 그렇다고 격리 자체가 안 된 것은
     * 아니기 때문입니다. {@code applied} 를 {@code null} 로 두어
     * "모름" 과 "실패" 를 구분합니다.
     *
     * @param agentId Agent 식별자
     * @param payload ack 의 payload (action/ok/affected/preserved/detail)
     */
    public void recordAck(String agentId, JsonNode payload) {
        if (agentId == null || agentId.isBlank() || payload == null || !payload.isObject()) {
            return;
        }

        final String action = text(payload, "action");
        // 격리/해제가 아닌 ack (예: 정책 적용 보고)는 여기서 다루지 않습니다.
        if (action == null || !(action.equals(ACTION_QUARANTINE) || action.equals(ACTION_RELEASE))) {
            return;
        }

        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("action", action);
        record.put("ok", payload.has("ok") && payload.get("ok").isBoolean()
                ? payload.get("ok").asBoolean() : null);
        record.put("detail", text(payload, "detail"));
        record.put("available", strings(payload, "affected"));
        record.put("blocked", strings(payload, "affected"));
        record.put("preserved", strings(payload, "preserved"));
        record.put("received_at", Instant.now().toString());
        lastAck.put(agentId, record);

        final Object ok = record.get("ok");
        if (Boolean.TRUE.equals(ok)) {
            log.info("quarantine ack agent={} action={} affected={} preserved={}",
                    agentId, action, record.get("affected"), record.get("preserved"));
        } else {
            // 적용 실패는 경고로 남깁니다. 운영자가 콘솔로 들어가야 할 수 있습니다.
            log.warn("quarantine ack FAILED agent={} action={} detail={}",
                    agentId, action, record.get("detail"));
            notificationService.notifyQuietly(
                    "SECURITY",
                    "critical",
                    "Agent 격리 적용 실패: " + agentId,
                    "장치 " + agentId + " 에 격리 명령을 보냈으나 적용에 실패했습니다. "
                            + "(" + record.get("detail") + ") 콘솔에서 직접 확인하세요.",
                    null,
                    agentId,
                    "quarantine",
                    "/agent",
                    "quarantine-ack-failed:" + agentId);
        }
    }

    /**
     * 저장된 최근 명령 적용 결과를 조회합니다. (없으면 {@code null})
     *
     * @param agentId Agent 식별자
     * @return 최근 ack 결과
     */
    public Map<String, Object> lastAck(String agentId) {
        return lastAck.get(agentId);
    }

    private static String text(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        return (value != null && value.isTextual()) ? value.asText() : null;
    }

    private static List<String> strings(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        for (final JsonNode item : value) {
            if (item.isTextual()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    /**
     * 배포 예정 목록에서 격리 대상을 찾을 때 쓰는 헬퍼입니다.
     *
     * <p>{@link ExpectedAgent} 는 프로젝트 키를 문자열로 갖고, 격리는 그 값을
     * 그대로 저장합니다. 여기서는 편의를 위해 문자열 비교만 제공합니다.
     *
     * @param expected 배포 예정 정보
     * @param agentId  실제 연결된 Agent 식별자
     * @return 같은 장치로 보이면 {@code true} (대소문자 무시)
     */
    public static boolean sameAgent(ExpectedAgent expected, String agentId) {
        if (expected == null || expected.getAgentId() == null || agentId == null) {
            return false;
        }
        return expected.getAgentId().equalsIgnoreCase(agentId);
    }
}