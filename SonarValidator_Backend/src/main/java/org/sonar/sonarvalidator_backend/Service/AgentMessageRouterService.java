package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 수신 봉투를 종류별로 분기해 처리합니다. (애플리케이션 계층의 유일한 진입점)
 *
 * <h2>처리 규칙</h2>
 * <table border="1">
 *   <caption>메시지 종류별 동작</caption>
 *   <tr><th>type</th><th>동작</th><th>응답</th></tr>
 *   <tr><td>{@code hello}</td><td>Agent 를 세션 레지스트리에 등록</td><td>{@code ack}</td></tr>
 *   <tr><td>{@code policy-request}</td><td>정책 생성</td><td>{@code policy-response}</td></tr>
 *   <tr><td>{@code telemetry}</td><td>최근 값 저장 (메모리)</td><td>없음</td></tr>
 *   <tr><td>{@code ack}</td><td>로그만</td><td>없음</td></tr>
 *   <tr><td>{@code error}</td><td>경고 로그</td><td>없음</td></tr>
 *   <tr><td>기타</td><td>거부</td><td>{@code error}</td></tr>
 * </table>
 *
 * <p>{@code telemetry}  {@code ack}/{@code error} 는 <b>응답을 보내지 않습니다.</b>
 * 일방향 메시지까지 응답하면 Agent 가 기대하지 않는 프레임을 받아 파싱 혼이 생깁니다.
 *
 * <h2> 상관관계 ID  직접 맞는가</h2>
 * <p>STOMP SimpleBroker 는 RECEIPT/ACK 를 지원하지 않습니다. plain WS 로 바꾸면
 * 브로커 자체가 없으므로 요청-응답 매칭은 100% 이 계층의 책임입니다.
 * Agent 는 자신이 보낸 {@code correlation_id} 와 같은 값이 응답에 있으면 그 요청의
 * 답으로 간주합니다.
 */
@Service
public class AgentMessageRouterService {

    private static final Logger log = LoggerFactory.getLogger(AgentMessageRouterService.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /** Agent 별 최근 텔레메트리 (DB 도입 전까지의 임시 저장소). */
    private final Map<String, JsonNode> lastTelemetry = new ConcurrentHashMap<>();

    /** Agent 별 마지막 수신 시각. */
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    /** 정책 요청 처리 건수 (모니터링용). */
    private final AtomicLong policyRequestCount = new AtomicLong();

    private final AgentSessionRegistry registry;
    private final PolicyRegistryService policyRegistry;

    public AgentMessageRouterService(AgentSessionRegistry registry,
                                     PolicyRegistryService policyRegistry) {
        this.registry = registry;
        this.policyRegistry = policyRegistry;
    }

    /**
     * 수신 봉투를 처리합니다.
     *
     * @param session 메시지를 보낸 세션
     * @param envelope 파싱된 봉투
     * @return Agent 에게 돌려줄 응답 (응답이 없으면 {@code null})
     */
    public Envelope handle(WebSocketSession session, Envelope envelope) {
        final String agentId = resolveAgentId(session, envelope);
        if (agentId != null) {
            lastSeen.put(agentId, Instant.now());
        }
        log.debug("recv {} from agent={}", envelope.summary(), agentId);

        return switch (envelope.getType()) {
            case Envelope.Types.HELLO -> onHello(session, envelope, agentId);
            case Envelope.Types.POLICY_REQUEST -> onPolicyRequest(session, envelope, agentId);
            case Envelope.Types.TELEMETRY -> onTelemetry(envelope, agentId);
            // Agent 가 우리 푸시(command)에 대한 확인을 보낼 때 사용합니다.
            case Envelope.Types.ACK -> onAck(envelope, agentId);
            case Envelope.Types.ERROR -> onError(envelope, agentId);
            default -> Envelope.error(envelope.getCorrelation_id(),
                    "unsupported message type: " + envelope.getType());
        };
    }

    /**
     * {@code hello} 처리: Agent 를 등록하고 ack 를 돌려니다.
     *
     * <p>Agent 의 첫 메시지가 hello 가 아니어도(예: 구버전 Agent 가 바로 telemetry 를
     * 보내도) 동작하도록, 등록은 hello 뿐 아니라 모든 메시지에서 지연 등록됩니다.
     *
     * @param session 세션
     * @param envelope 요청 봉투
     * @param agentId 해석된 Agent 식별자
     * @return ack 봉투
     */
    private Envelope onHello(WebSocketSession session, Envelope envelope, String agentId) {
        registry.register(agentId, session);

        final ObjectNode payload = JSON.objectNode();
        payload.put("agent_id", agentId == null ? "" : agentId);
        payload.put("server_time", Instant.now().toString());
        payload.put("connected_agents", registry.connectedCount());
        return Envelope.replyTo(Envelope.Types.ACK, envelope, payload);
    }

    /**
     * {@code policy-request} 처리: 장치 유형/식별자를 해석해 정책을 돌려줍니다.
     *
     * <p>장치 유형 결정 순서:
     * <ol>
     *   <li>투의 {@code device_type} (문자열 → {@link DeviceType#fromString})</li>
     *   <li>payload 의 {@code device_id} 접두사 ({@link DeviceType#inferFromDeviceId})</li>
     *   <li>둘 다 없으면 {@link DeviceType#VM}</li>
     * </ol>
     *
     * @param session 세션
     * @param envelope 요청 봉투
     * @param agentId 해석된 Agent 식별자
     * @return policy-response 봉투
     */
    private Envelope onPolicyRequest(WebSocketSession session, Envelope envelope, String agentId) {
        registry.register(agentId, session);

        final JsonNode payload = envelope.payloadOrEmpty();
        final String deviceId = text(payload, "device_id",
                text(payload, "agent_id", agentId));

        DeviceType deviceType = DeviceType.fromString(envelope.getDevice_type());
        if (deviceType == null) {
            deviceType = DeviceType.fromString(text(payload, "device_type", null));
        }
        if (deviceType == null) {
            deviceType = DeviceType.inferFromDeviceId(deviceId);
        }

        final ObjectNode policy = policyRegistry.forDevice(deviceType, deviceId);
        policyRequestCount.incrementAndGet();
        log.info("policy-request from agent={} device={} type={} -> {}",
                agentId, deviceId, deviceType, policy.path("policy_id").asString("?"));

        return Envelope.replyTo(Envelope.Types.POLICY_RESPONSE, envelope, policy);
    }

    /**
     * {@code telemetry} 처리: 최근 값만 저장하고 응답하지 않습니다.
     *
     * @param envelope 수신 봉투
     * @param agentId 해석된 Agent 식별자
     * @return 항상 {@code null} (일방향)
     */
    private Envelope onTelemetry(Envelope envelope, String agentId) {
        if (agentId == null) {
            return null;
        }
        lastTelemetry.put(agentId, envelope.payloadOrEmpty());
        log.info("telemetry from agent={} keys={}", agentId, envelope.payloadOrEmpty().size());
        return null;
    }

    /**
     * Agent 가 보낸 {@code ack} 를 기록합니다.
     *
     * @param envelope 수신 봉투
     * @param agentId 해석된 Agent 식별자
     * @return 항상 {@code null}
     */
    private Envelope onAck(Envelope envelope, String agentId) {
        log.info("ack from agent={} correlation={}", agentId, envelope.getCorrelation_id());
        return null;
    }

    /**
     * Agent 가 보고한 오류를 경고로 기록합니다.
     *
     * @param envelope 수신 봉투
     * @param agentId 해석된 Agent 식별자
     * @return 항상 {@code null}
     */
    private Envelope onError(Envelope envelope, String agentId) {
        log.warn("agent={} reported error: {}", agentId, envelope.getError());
        return null;
    }

    /**
     * 특정 Agent 의 최근 텔레메트리를 조회합니다. (REST/프론트엔드 연동 지점)
     *
     * @param agentId Agent 식별자
     * @return 최근 텔레메트리 (없으면 {@code null})
     */
    public JsonNode lastTelemetryOf(String agentId) {
        return lastTelemetry.get(agentId);
    }

    /**
     * Agent 의 마지막 수신 시각입니다. (헬스 체크 지점)
     *
     * @param agentId Agent 식별자
     * @return 마지막 수신 시각 (없으면 {@code null})
     */
    public Instant lastSeenOf(String agentId) {
        return lastSeen.get(agentId);
    }

    /**
     * 누적 정책 요청 건수입니다.
     *
     * @return 처리한 policy-request 수
     */
    public long policyRequestCount() {
        return policyRequestCount.get();
    }

    /**
     * Agent 식별자를 결정합니다.
     *
     * <p>봉투에 {@code agent_id} 가 없으면 세션 속성({@code agent_id})을,
     * 그것도 없으면 {@code "session:<id>"} 를 니다.
     *
     * @param session 세션
     * @param envelope 수신 봉투
     * @return Agent 식별자 (항상 non-null)
     */
    private String resolveAgentId(WebSocketSession session, Envelope envelope) {
        if (envelope.getAgent_id() != null && !envelope.getAgent_id().isBlank()) {
            return envelope.getAgent_id();
        }
        final Object fromAttributes = session.getAttributes().get("agent_id");
        if (fromAttributes instanceof String attr && !attr.isBlank()) {
            return attr;
        }
        return "session:" + session.getId();
    }

    /**
     * JSON 노드에서 문자열 필드를 안전하게 꺼니다.
     *
     * @param node 대상 노드
     * @param field 필드명
     * @param fallback 값이 없을 때 사용할 문자열
     * @return 필드 값 또는 fallback
     */
    private String text(JsonNode node, String field, String fallback) {
        final JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        final String text = value.asString(null);
        return (text == null || text.isBlank()) ? fallback : text;
    }
}