package org.sonar.sonarvalidator_backend.Policy.strategy;

import org.sonar.sonarvalidator_backend.Model.DeviceType;

/**
 * 장치 유형 하나의 <b>정책 생성 규칙</b>을 담는 전략입니다.
 *
 * <h2>⚠️ 왜 switch 문을 없애는가</h2>
 * <p>이전에는 {@code PolicyRegistryService} 안에 유형별 {@code switch} 가
 * <b>네 곳</b>({@code defaultRule}, {@code declarationRule},
 * {@code enforcementRule}, {@code defaultVendor}/{@code defaultProduct})에
 * 흩어져 있었습니다. 그래서 "VM 정책을 고친다" 는 작업이
 * <b>서로 멀리 떨어진 네 곳</b>을 동시에 고치는 일이 되었고, 하나를 빠뜨리면
 * 폴백 경로와 실제 경로가 다른 정책을 만들었습니다.
 *
 * <p>유형별로 클래스를 나누면 그 유형에 관한 모든 지식이 <b>한 파일</b>에
 * 모입니다. 새 장치를 추가하는 일은 클래스 하나를 더하는 일이 되고,
 * 기존 유형의 코드는 건드리지 않습니다. (개방-폐쇄 원칙)
 *
 * <h2>계약</h2>
 * <ul>
 *   <li>{@link #supports(DeviceType)} — 이 전략이 담당하는 유형</li>
 *   <li>{@link #defaultRule()} — 배정된 서브넷이 없을 때의 최소 선언</li>
 *   <li>{@link #declarationRule(PolicyBuildContext)} — "이 장치는 어느 대역인가"</li>
 *   <li>{@link #enforcementRule(PolicyBuildContext)} — "무엇을 허용/차단하는가"</li>
 * </ul>
 *
 * <h2>⚠️ 예외를 던지지 않는다</h2>
 * <p>정책 생성 실패는 Agent 의 요청 자체를 막으면 안 됩니다. 에이전트는 응답이
 * 없으면 재시도만 반복하며 <b>텔레메트리도 멈춥니다.</b> 그래서 적용할 수 없는
 * 경우 {@code null} 을 돌려주고, 호출자가 폴백을 씁니다.
 */
public interface DevicePolicyStrategy {

    /**
     * 이 전략이 담당하는 장치 유형인지 확인합니다.
     *
     * @param type 장치 유형 (null 이면 false)
     * @return 담당하면 true
     */
    boolean supports(DeviceType type);

    /**
     * 이 유형의 문서상 기본 벤더명을 돌려줍니다.
     *
     * <p>정책 JSON 의 {@code vendor} 필드에 들어갑니다. 텔레메트리에서 실제
     * 벤더가 관측되면 그 값이 우선합니다.
     *
     * @return 벤더명 (예: {@code "Canonical"}, {@code "Linux"})
     */
    String defaultVendor();

    /**
     * 이 유형의 문서상 기본 제품/모델명을 돌려줍니다.
     *
     * @return 제품명 (예: {@code "Ubuntu Linux"}, {@code "nftables"})
     */
    String defaultProduct();

    /**
     * 배정된 서브넷이 없을 때 쓰는 <b>최소 선언</b> 규칙을 만듭니다.
     *
     * <p>{@code docs/Agent/*_Policy_Design.md} 의 스키마 형태를 따릅니다.
     * 실제 허용/차단 결정은 담기지 않습니다 — 정책이 아니라 "이 장치가
     * 어떤 종류인가" 를 알리는 선언입니다.
     *
     * @return 선언 규칙 노드
     */
    tools.jackson.databind.node.ObjectNode defaultRule();

    /**
     * "이 장치는 어느 대역인가" 를 장치가 이해하는 형태로 만듭니다.
     *
     * @param context 서브넷·벤더·관측 제품 정보
     * @return 선언 규칙 노드
     */
    tools.jackson.databind.node.ObjectNode declarationRule(PolicyBuildContext context);

    /**
     * 연결 한 건을 이 유형의 장치 명령으로 옮깁니다.
     *
     * <p>ACL 을 실제 커맨드로 옮길 수 없는 유형은 {@code null} 을 돌려줍니다.
     * 해석하지 못하는 필드를 넣어 보내면 장치마다 다르게 무시되어
     * "적용된 것처럼 보이는" 상태가 되기 때문입니다.
     *
     * @param context 연결 정보 (한 건)
     * @return 규칙 노드 (지원하지 않으면 null)
     */
    tools.jackson.databind.node.ObjectNode enforcementRule(PolicyBuildContext context);
}