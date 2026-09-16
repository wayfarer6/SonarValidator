package org.sonar.sonarvalidator_backend.Model.dto;

/**
 * Agent 가 보내는 정책 요청 본문입니다.
 *
 * <p>C++ 하네스({@code Agent_Test/agent_comm_test.cpp})와 모크 브로커가
 * {@code {"device_id":"vm-01"}} 형태로 보내므로 필드명을 그대로 맞춥니다.
 *
 * <p>JSON 필드명이 snake_case 이므로 자바 필드명도 snake_case 로 두어
 * 별도 {@code @JsonProperty} 없이 Jackson 기본 명명 규칙으로 왕복시킵니다.
 */
public class PolicyRequest {

    /** 대상 장치 식별자 (예: {@code vm-01}). */
    private String device_id;

    /** Agent 식별자 (선택). */
    private String agent_id;

    public String getDevice_id() { return device_id; }
    public void setDevice_id(String device_id) { this.device_id = device_id; }

    public String getAgent_id() { return agent_id; }
    public void setAgent_id(String agent_id) { this.agent_id = agent_id; }

    @Override
    public String toString() {
        return "PolicyRequest{device_id=" + device_id + ", agent_id=" + agent_id + '}';
    }
}