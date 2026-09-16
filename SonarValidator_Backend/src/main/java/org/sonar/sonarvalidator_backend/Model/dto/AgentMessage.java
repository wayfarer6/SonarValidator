package org.sonar.sonarvalidator_backend.Model.dto;

import java.util.Map;

/**
 * Agent ↔ 서버 STOMP 메시지의 공통 봉투입니다.
 *
 * <h2>설계 배경</h2>
 * 기존 {@code AgentMessage} 는 {@code sender}/{@code content} 두 필드뿐이라
 * "누가, 어떤 종류의, 어떤 상관관계 ID 로" 보냈는지 표현할 수 없었습니다.
 * 서버가 정책 응답을 돌려주려면 최소한 상관관계 ID 가 필요하므로 필드를 확장했습니다.
 *
 * <h2>필드</h2>
 * <ul>
 *   <li>{@code agent_id} — Agent 식별자 ({@code ProberConfig::GetAgentId()})</li>
 *   <li>{@code device_type} — SWITCH / ROUTER / FIREWALL / VM</li>
 *   <li>{@code message_type} — {@code "text"}, {@code "policy-request"},
 *       {@code "system-info"}, {@code "telemetry"} 등</li>
 *   <li>{@code correlation_id} — 요청-응답을 맞추기 위한 ID (SimpleBroker 가
 *       RECEIPT 를 지원하지 않으므로 애플리케이션 레벨로 직접 맞니다)</li>
 *   <li>{@code sender} / {@code content} — 기존 필드 (하위 호환 유지)</li>
 *   <li>{@code payload} — 구조화된 본문이 필요할 때 사용하는 임의 JSON</li>
 * </ul>
 */
public class AgentMessage {

    private String sender;
    private String content;

    private String agent_id;
    private String device_type;
    private String message_type;
    private String correlation_id;
    private Map<String, Object> payload;

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAgent_id() { return agent_id; }
    public void setAgent_id(String agent_id) { this.agent_id = agent_id; }

    public String getDevice_type() { return device_type; }
    public void setDevice_type(String device_type) { this.device_type = device_type; }

    public String getMessage_type() { return message_type; }
    public void setMessage_type(String message_type) { this.message_type = message_type; }

    public String getCorrelation_id() { return correlation_id; }
    public void setCorrelation_id(String correlation_id) { this.correlation_id = correlation_id; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    @Override
    public String toString() {
        return "AgentMessage{agent_id=" + agent_id
                + ", device_type=" + device_type
                + ", message_type=" + message_type
                + ", correlation_id=" + correlation_id
                + ", sender=" + sender
                + ", content=" + content + '}';
    }
}
