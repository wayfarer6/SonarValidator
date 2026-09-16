package org.sonar.sonarvalidator_backend.Model.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * Agent ↔ 서버 plain WebSocket 메시지의 공통 투(envelope)입니다.
 *
 * <h2>왜 STOMP 를 쓰지 않는가</h2>
 * <p>기존 구현은 {@code @EnableWebSocketMessageBroker} + SimpleBroker + SockJS 였습니다.
 * C++ Prober 는 Boost.Beast 로 <b>raw WebSocket + JSON</b>  쓰기 때문에 STOMP 프레임을
 * 만들 수 없었고, 게다가 SimpleBroker  RECEIPT/ACK 를 구현하지 않아 요청-응답
 * 상관관계를 맞출 방법이 없었습니다. 그래서 프레임 계층을 없애고 JSON 필드 하나로
 * 상관관계를 표현합니다.
 *
 * <h2>전송 형식</h2>
 * <pre>{@code
 * {
 *   "type": "hello" | "policy-request" | "policy-response"
 *         | "telemetry" | "command" | "ack" | "error",
 *   "agent_id": "agent-20260916-...",
 *   "device_type": "VM",
 *   "correlation_id": "c-1",
 *   "payload": { }
 * }
 * }</pre>
 *
 * <p>모든 필드는 nullable 이며 파싱 실패를 막기 위해 기본값을 제공합니다.
 * 이 클래스는 순수 DTO 이므로 Jackson 이 기본 생성자 + setter 로 역직렬화합니다.
 */
public class Envelope {

    /** 메시지 종류. {@link Types} 참고. */
    private String type = Types.UNKNOWN;

    /** Agent 식별자 (`ProberConfig::GetAgentId()`). */
    private String agent_id;

    /** 장치 유형 ("SWITCH", "ROUTER", "FIREWALL", "VM"). */
    private String device_type;

    /** 요청-응답 상관관계 ID. 서버는 응답에 같은 값을 되돌려줍니다. */
    private String correlation_id;

    /** 구조화된 본문. 어떤 JSON 이든 담을 수 있도록 JsonNode 로 둡니다. */
    private JsonNode payload;

    /** 오류 메시지 (type == "error" 일 때). */
    private String error;

    /** 메시지 종류 상수 모음. */
    public static final class Types {
        public static final String HELLO = "hello";
        public static final String POLICY_REQUEST = "policy-request";
        public static final String POLICY_RESPONSE = "policy-response";
        public static final String TELEMETRY = "telemetry";
        public static final String COMMAND = "command";
        public static final String ACK = "ack";
        public static final String ERROR = "error";
        public static final String UNKNOWN = "unknown";

        private Types() {
        }
    }

    public Envelope() {
    }

    /**
     * 최소 필드만 채운 봉투를 만듭니다.
     *
     * @param type 메시지 종류
     * @return 새 봉투
     */
    public static Envelope of(String type) {
        final Envelope envelope = new Envelope();
        envelope.setType(type);
        return envelope;
    }

    /**
     * 수신 메시지에 대한 성공 응답 봉투를 만듭니다.
     *
     * @param type 응답 종류
     * @param correlationId 원 요청의 상관관계 ID
     * @param payload 응답 본문 (null 허용)
     * @return 새 봉투
     */
    public static Envelope reply(String type, String correlationId, JsonNode payload) {
        final Envelope envelope = of(type);
        envelope.setCorrelation_id(correlationId);
        envelope.setPayload(payload);
        return envelope;
    }

    /**
     * 요청 봉투의 식별 필드를 그대로 되돌려주는 응답 봉투를 만듭니다.
     *
     * <p>{@code correlation_id} 만 되돌리면 Agent 로그에 "누구에게 보내는 응답인지"가
     * 남지 않습니다. 디버깅과 다중 Agent 상황의 가독성을 위해 {@code agent_id} 와
     * {@code device_type} 도 함께 echo 합니다.
     *
     * @param type 응답 type (예: {@link Types#ACK})
     * @param request 원 요청 봉투
     * @param payload 응답 본문
     * @return 식별 필드가 채워진 응답 봉투
     */
    public static Envelope replyTo(String type, Envelope request, JsonNode payload) {
        final Envelope envelope = reply(type, request == null ? null : request.getCorrelation_id(), payload);
        if (request != null) {
            envelope.setAgent_id(request.getAgent_id());
            envelope.setDevice_type(request.getDevice_type());
        }
        return envelope;
    }

    /**
     * 오류 응답 봉투를 만듭니다.
     *
     * @param correlationId 원 요청의 상관관계 ID (모르면 null)
     * @param message 사람이 읽을 수 있는 오류 설명
     * @return 새 봉투
     */
    public static Envelope error(String correlationId, String message) {
        final Envelope envelope = of(Types.ERROR);
        envelope.setCorrelation_id(correlationId);
        envelope.setError(message);
        return envelope;
    }

    /**
     * payload 가 {@link JsonNode} 인지 확인하고 아니면 빈 객체로 바꿉니다.
     * 서버가 payload 를 그대로 순회할 때 NPE 를 막기 위한 편의 메서드입니다.
     *
     * @return payload 또는 빈 객체
     */
    public JsonNode payloadOrEmpty() {
        if (payload == null || payload.isNull()) {
            return tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        }
        return payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = (type == null || type.isBlank()) ? Types.UNKNOWN : type;
    }

    public String getAgent_id() {
        return agent_id;
    }

    public void setAgent_id(String agent_id) {
        this.agent_id = agent_id;
    }

    public String getDevice_type() {
        return device_type;
    }

    public void setDevice_type(String device_type) {
        this.device_type = device_type;
    }

    public String getCorrelation_id() {
        return correlation_id;
    }

    public void setCorrelation_id(String correlation_id) {
        this.correlation_id = correlation_id;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * 로그용 축약 표현. payload 전체를 찍지 않아 로그 폭발을 막습니다.
     *
     * @return 요약 문자열
     */
    public String summary() {
        final Map<String, Object> parts = new LinkedHashMap<>();
        parts.put("type", type);
        parts.put("agent_id", agent_id);
        parts.put("device_type", device_type);
        parts.put("correlation_id", correlation_id);
        parts.put("error", error);
        return parts.toString();
    }

    @Override
    public String toString() {
        return "Envelope" + summary();
    }
}