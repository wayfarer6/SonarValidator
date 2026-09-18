package org.sonar.sonarvalidator_backend.Model.dto;

import java.util.List;

import org.sonar.sonarvalidator_backend.Policy.PolicyRule;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 프로젝트 REST API 의 요청/응답 본문 모음입니다.
 *
 * <h2>별도 클래스로 묶은 이유</h2>
 * <p>프로젝트 생성/수정/검증이 <b>같은 필드 집합</b>을 공유하기 때문입니다.
 * 필드가 흩어지면 프론트엔드와 계약이 어긋나기 쉬워 한 곳에서 정의합니다.
 *
 * <h2>⚠️ 필드 이름을 명시적으로 매핑한 이유 (중요)</h2>
 * <p>프로젝트의 JSON 관례는 <b>snake_case</b> 입니다
 * ({@code project_id}, {@code subnet_class}, {@code agent_id} …).
 * 그런데 이 클래스는 {@code record} 라서 컴포넌트 이름이 그대로 JSON 키가 됩니다.
 * 즉 {@code subnetClass} 라고 쓰면 JSON 키도 {@code subnetClass} 가 되어,
 * 프론트엔드가 보내는 {@code subnet_class} 를 <b>조용히 못 읽습니다</b>.
 *
 * <p>증상이 특히 위험한 이유: 값이 {@code null} 로 들어오면
 * {@link ZoneClass#fromString(String)} 이 {@code null} 을 돌려주고, 서비스가
 * 기본값 {@code OPEN} 으로 채웁니다. 그래서 <b>예외 없이</b> 모든 서브넷이
 * Open 등급이 되고, 검증은 "위반 없음" 이라고 잘못 보고합니다.
 * (실제로 E2E 검증에서 이 문제를 발견했습니다)
 *
 * <p>그래서 모든 snake_case 필드에 {@link JsonProperty} 를 명시했습니다.
 * 전역 {@code SNAKE_CASE} 전략을 쓰는 방법도 있지만, 그러면 Map 기반 응답과
 * 다른 DTO 의 키까지 함께 바뀌어 영향 범위가 커집니다.
 *
 * <p>{@code ProjectDtoSerializationTest} 가 이 계약을 왕복 검증합니다.
 */
public final class ProjectDto {

    private ProjectDto() {
    }

    /**
     * 서브넷 한 건입니다.
     *
     * <p>{@code manuallyEdited} 를 <b>래퍼 타입</b> 으로 둔 이유: primitive
     * {@code boolean} 이면 JSON 에서 필드를 생략했을 때
     * {@code Cannot map null into type boolean} 로 역직렬화가 실패합니다.
     * API 계약상 이 필드는 선택이어야 하므로 래퍼를 쓰고, 변환 시 기본값을
     * 정합니다.
     *
     * @param id             서브넷 식별자 (예: {@code Subnet-0004})
     * @param cidr           CIDR 대역
     * @param subnetClass    보안 등급 (프론트엔드와 동일한 표기: Confidential/Sensitive/Open)
     * @param name           표시용 이름
     * @param agentId        자동 수집 출처 장치
     * @param manuallyEdited 수동 편집 여부 (생략하면 false)
     */
    public record SubnetPayload(
            String id,
            String cidr,
            @JsonProperty("subnet_class") String subnetClass,
            String name,
            @JsonProperty("agent_id") String agentId,
            @JsonProperty("manually_edited") Boolean manuallyEdited) {

        /**
         * 도메인 객체로 변환합니다.
         *
         * @return 검증용 서브넷
         */
        public PolicySubnet toPolicySubnet() {
            final PolicySubnet subnet = new PolicySubnet();
            subnet.setId(id);
            subnet.setCidr(cidr);
            subnet.setZoneClass(ZoneClass.fromString(subnetClass));
            subnet.setName(name);
            subnet.setAgentId(agentId);
            subnet.setManuallyEdited(manuallyEdited != null && manuallyEdited);
            return subnet;
        }
    }

    /**
     * 연결 규칙 한 건입니다.
     *
     * @param id            규칙 식별자 (예: {@code Rule-0001})
     * @param src           출발 서브넷 식별자
     * @param dst           도착 서브넷 식별자
     * @param port          허용 포트 (null 이면 미지정)
     * @param protocol      프로토콜
     * @param origin        규칙 출처 ({@code MANUAL} / {@code DISCOVERED})
     * @param enabled       검증 포함 여부
     * @param note          비활성 사유 메모
     */
    public record RulePayload(
            String id,
            String src,
            String dst,
            Integer port,
            String protocol,
            String origin,
            Boolean enabled,
            String note) {

        /**
         * 도메인 객체로 변환합니다.
         *
         * @return 검증용 규칙
         */
        public PolicyRule toPolicyRule() {
            final PolicyRule rule = new PolicyRule();
            rule.setId(id);
            rule.setSource(src);
            rule.setDestination(dst);
            if (port != null) {
                rule.setPort(port);
            }
            if (protocol != null && !protocol.isBlank()) {
                rule.setProtocol(protocol);
            }
            if (origin != null && "DISCOVERED".equalsIgnoreCase(origin.trim())) {
                rule.setOrigin(PolicyRule.Origin.DISCOVERED);
            } else {
                rule.setOrigin(PolicyRule.Origin.MANUAL);
            }
            rule.setEnabled(enabled == null || enabled);
            rule.setNote(note);
            return rule;
        }
    }

    /**
     * 프로젝트 생성 요청입니다.
     *
     * @param projectId   외부 키 (없으면 서버가 생성)
     * @param name        표시 이름
     * @param category    분류
     * @param description 설명
     * @param status      진행 상태
     */
    public record CreateRequest(
            @JsonProperty("project_id") String projectId,
            String name,
            String category,
            String description,
            String status) {
    }

    /**
     * 프로젝트 수정 요청입니다. null 인 필드는 "변경 없음" 으로 해석합니다.
     *
     * <p>{@code subnets} / {@code rules} 를 주면 기존 목록을 <b>전부 교체</b> 합니다.
     * 부분 수정(한 행만 바꾸기)을 지원하지 않는 대신 계약이 단순해지고,
     * 편집기가 화면 전체 상태를 한 번에 보내는 흐름과 맞아떨어집니다.
     *
     * @param name        표시 이름
     * @param category    분류
     * @param description 설명
     * @param status      진행 상태
     * @param subnets     서브넷 목록 (null 이면 유지)
     * @param rules       규칙 목록 (null 이면 유지)
     */
    public record UpdateRequest(
            String name,
            String category,
            String description,
            String status,
            List<SubnetPayload> subnets,
            List<RulePayload> rules) {
    }

    /**
     * 검증만 수행하는 요청입니다. 저장하지 않습니다.
     *
     * @param subnets 서브넷 목록
     * @param rules   규칙 목록
     */
    public record ValidateRequest(
            List<SubnetPayload> subnets,
            List<RulePayload> rules) {
    }
}
