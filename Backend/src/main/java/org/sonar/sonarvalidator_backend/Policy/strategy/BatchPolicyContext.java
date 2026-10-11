package org.sonar.sonarvalidator_backend.Policy.strategy;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;

/**
 * 여러 연결을 <b>한 번에</b> 내려야 하는 집행 규칙의 입력입니다.
 *
 * <h2>⚠️ 왜 "연결 하나" 로는 안 되는가</h2>
 * <p>{@link PolicyBuildContext} 는 연결 <b>한 건</b>만 담습니다. 그런데 어떤
 * 장치는 규칙을 하나씩 추가할 수 없습니다.
 *
 * <ul>
 *   <li>IOS 확장 ACL 은 <b>이름으로 다시 쓰는</b> 방식입니다. 규칙 하나만
 *       지우는 문법이 없습니다({@code no ip access-list extended SONAR-CSO}
 *       는 ACL 전체를 지웁니다).</li>
 *   <li>그래서 "지금 허용된 집합 전체" 를 알아야 합니다. 운영자가 금지 연결을
 *       하나 지우면, 남은 규칙으로 ACL 을 통째로 다시 써야 그 하나가 빠집니다.</li>
 * </ul>
 *
 * <p>연결 하나만 보고 만든 규칙을 순서대로 보내면, 지운 규칙이 장치에 그대로
 * 남습니다 — 가장 위험한 실패 모드입니다(차단이 안 풀린 채 남음).
 *
 * <h2>⚠️ 정렬을 고정한다</h2>
 * <p>{@code connections} 는 호출부에서 <b>안정된 순서</b>로 넘깁니다. 같은
 * 입력이면 같은 ACL 텍스트가 나와야 "장치 상태가 바뀌었는지" 를 비교할 수
 * 있습니다. (순서가 흔들리면 매번 다시 쓰게 됩니다)
 *
 * @param subnet      이 장치에 배정된 서브넷 (해제 노드에서는 null)
 * @param vendor      텔레메트리에서 관측된 벤더 (null 이면 전략 기본값)
 * @param product     텔레메트리에서 관측된 제품명 (null 이면 전략 기본값)
 * @param connections 이 서브넷과 맺어진 모든 연결 (읽기 전용 뷰)
 */
public record BatchPolicyContext(
        ProjectSubnet subnet,
        String vendor,
        String product,
        List<PolicyBuildContext.ConnectionView> connections) {

    /**
     * 배정이 없는 장치의 <b>해제(빈 목록)</b> 입력을 만듭니다.
     *
     * <h2>⚠️ 왜 빈 목록을 명시적으로 보내야 하는가</h2>
     * <p>운영자가 프로젝트에서 장치를 빼거나 규칙을 모두 지우면, 장치에는
     * <b>이전에 넣은 차단 규칙이 남습니다.</b> 이때 정책 노드를 아예 보내지
     * 않으면 장치는 "규칙 없음" 과 "이번엔 안 보냄" 을 구분할 수 없어 차단이
     * 풀리지 않습니다. (실측: 서브넷 배정을 해제한 Switch-4 에
     * {@code dl_vlan=131, nw_dst=192.168.122.0/24} drop 이 그대로 남았습니다)
     *
     * <p>{@code acl_apply} 는 "지금 남아 있어야 하는 규칙 전체" 라는 선언이므로,
     * 빈 목록은 곧 <b>"우리 규칙을 모두 지워라"</b> 입니다. 이 노드를 만드는
     * 전략은 OVS 스위치와 라우터뿐이고(방화벽·VM 은 {@code null}),
     * 따라서 외부에서 관리하는 방화벽 규칙은 건드리지 않습니다.
     *
     * @param vendor  관측 벤더 (null 허용)
     * @param product 관측 제품명 (null 허용)
     * @return 연결이 하나도 없는 컨텍스트
     */
    public static BatchPolicyContext release(String vendor, String product) {
        return new BatchPolicyContext(null, vendor, product, List.of());
    }

    /**
     * 등급을 건너뛰는(금지) 연결만 골라 돌려줍니다.
     *
     * <p>차단 목록에는 금지 연결만 넣습니다. 허용 연결은 장치의 기본 동작
     * (permit) 이므로 굳이 규칙으로 만들지 않습니다 — 넣으면 ACL 이 커져
     * 사람이 읽을 수 없게 되고, 규칙 하나가 늘 때마다 장치 전체를 다시 쓰게
     * 됩니다.
     *
     * @return 금지 연결 목록 (원래 순서 유지)
     */
    public List<PolicyBuildContext.ConnectionView> forbiddenConnections() {
        if (connections == null) {
            return List.of();
        }
        return connections.stream().filter(PolicyBuildContext.ConnectionView::forbidden).toList();
    }

    /**
     * 이 전략이 쓸 벤더명을 정합니다.
     *
     * @param fallback 전략의 기본 벤더명
     * @return 관측 벤더가 있으면 그것, 없으면 기본값
     */
    public String vendorOr(String fallback) {
        return PolicyJson.firstNonBlank(vendor, fallback);
    }

    /**
     * 이 전략이 쓸 제품명을 정합니다.
     *
     * @param fallback 전략의 기본 제품명
     * @return 관측 제품명이 있으면 그것, 없으면 기본값
     */
    public String productOr(String fallback) {
        return PolicyJson.firstNonBlank(product, fallback);
    }
}
