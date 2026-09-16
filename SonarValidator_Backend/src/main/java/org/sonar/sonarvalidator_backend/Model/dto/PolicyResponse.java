package org.sonar.sonarvalidator_backend.Model.dto;

import java.util.List;

/**
 * Agent 에게 내려보내는 정책 응답입니다.
 *
 * <p>필드 순서와 이름은 모크 브로커({@code Agent_Test/mock_API/stomp_broker.js}
 * 의 {@code samplePolicy()})와 C++ 검증 하네스가 기대하는 계약에 맞춥니다.
 * 하네스는 다음 두 가지를 확인합니다.
 * <ul>
 *   <li>본문에 {@code policy_id} 가 존재</li>
 *   <li>본문에 {@code "device_type":"VM"} 이 정확히 등장 (Jackson 기본 포맷에는
 *       콜론 뒤 공백이 없으므로 문자열 비교가 성립합니다)</li>
 * </ul>
 *
 * <h2>실제 정책 저장소가 생기면</h2>
 * 지금은 {@link org.sonar.sonarvalidator_backend.Service.PolicyRegistryService} 가
 * 문서({@code docs/Agent/*_Policy_Design.md})의 스키마에 맞춘 고정 정책을 돌려줍니다.
 * DB 나 파일 기반 저장소가 준비되면 이 DTO 의 {@code rules} 를 그대로 채우면 됩니다.
 */
public class PolicyResponse {

    /** 정책 식별자 (예: {@code pol-0001}). */
    private String policy_id;

    /** 장치 유형 ("VM", "SWITCH", "ROUTER", "FIREWALL"). */
    private String device_type;

    /** 장치 식별자 (예: {@code vm-01}). */
    private String device_id;

    /** 실제 정책 규칙 목록. 배열로 감싸 스칼라를 전달한다는 문서 규칙을 따릅니다. */
    private List<PolicyRule> rules;

    /** 정책 유효 기한 (ISO-8601). */
    private String valid_until;

    public String getPolicy_id() { return policy_id; }
    public void setPolicy_id(String policy_id) { this.policy_id = policy_id; }

    public String getDevice_type() { return device_type; }
    public void setDevice_type(String device_type) { this.device_type = device_type; }

    public String getDevice_id() { return device_id; }
    public void setDevice_id(String device_id) { this.device_id = device_id; }

    public List<PolicyRule> getRules() { return rules; }
    public void setRules(List<PolicyRule> rules) { this.rules = rules; }

    public String getValid_until() { return valid_until; }
    public void setValid_until(String valid_until) { this.valid_until = valid_until; }

    /**
     * 단일 정책 규칙. 문서의 장치별 정책 설계와 모크의 예시를 모두 표현할 수 있도록
     * 최소 필드만 두고, 나머지는 {@code extra} 로 확장합니다.
     */
    public static class PolicyRule {

        private String rule_id;
        private String action;
        private String protocol;
        private String src;
        private Integer dst_port;
        private String comment;

        public String getRule_id() { return rule_id; }
        public void setRule_id(String rule_id) { this.rule_id = rule_id; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }

        public String getSrc() { return src; }
        public void setSrc(String src) { this.src = src; }

        public Integer getDst_port() { return dst_port; }
        public void setDst_port(Integer dst_port) { this.dst_port = dst_port; }

        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
}