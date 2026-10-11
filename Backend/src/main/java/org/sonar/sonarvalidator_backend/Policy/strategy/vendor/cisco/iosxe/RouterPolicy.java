package org.sonar.sonarvalidator_backend.Policy.strategy.vendor.cisco.iosxe;

import java.util.List;

import org.sonar.sonarvalidator_backend.Config.SiteProperties;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Policy.strategy.BatchPolicyContext;
import org.sonar.sonarvalidator_backend.Policy.strategy.DevicePolicy;
import org.sonar.sonarvalidator_backend.Policy.strategy.PolicyBuildContext;
import org.sonar.sonarvalidator_backend.Policy.strategy.PolicyJson;
import org.springframework.stereotype.Component;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * FRR / Cisco 라우터의 정책 전략입니다.
 *
 * <h2>이 전략이 아는 것</h2>
 * <ul>
 *   <li>폴백: 인터페이스 on (문서 {@code Router_Policy_Design.md})</li>
 *   <li>선언: 정적 경로 형태의 대역/마스크</li>
 *   <li>집행: 등급을 건너뛰는 연결의 <b>ACL 차단</b> (확장 ACL 이름 {@code SONAR-CSO})</li>
 * </ul>
 *
 * <h2>⚠️ 라우터만 네트워크 주소를 쓴다</h2>
 * <p>다른 유형은 인터페이스에 <b>호스트 주소</b>를 넣어야 하지만, 라우터의
 * 선언은 <b>목적지 대역</b>을 광고하는 것입니다. 그래서 여기서는
 * {@link PolicyJson#splitCidr} 의 네트워크 주소를 그대로 씁니다.
 * 이 구분을 놓치면 "10.20.111.10/24 대역으로 가는 경로" 같은 잘못된 광고가
 * 만들어집니다.
 *
 * <h2>⚠️ 집행이 필요해진 이유</h2>
 * <p>예전에는 "라우터는 경로 제공자이고, 차단은 방화벽과 스위치가 한다" 고
 * 봤습니다. 그런데 랩 실측에서 <b>라우터가 인터넷 기본 경로를 들고 있어</b>
 * 기밀망이 인터넷으로 나갈 수 있었습니다(수집된 라우팅 테이블로 확인).
 * 즉 등급을 건너뛰는 연결이 라우터를 그대로 통과합니다. 그래서 라우터에도
 * ACL 을 내려보냅니다.
 *
 * <p>Cisco 는 {@code ip access-list extended} + 인터페이스 바인딩,
 * FRR 은 <b>코드만</b> 구현합니다 (실제 vty 가 없어 검증하지 못했습니다).
 */
@Component
public class RouterPolicy implements DevicePolicy {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /**
     * 기본 인터페이스 이름을 담은 사이트 설정입니다.
     *
     * <h2>⚠️ 왜 상수가 아니라 설정인가</h2>
     * <p>인터페이스 이름은 <b>배포·모델마다 다릅니다</b> —
     * {@code GigabitEthernet0/0/1}, {@code Ethernet1}, {@code ge-0/0/0}.
     * 상수로 두면 모델을 추가할 때마다 이 클래스를 고쳐야 합니다.
     */
    private final SiteProperties site;

    /**
     * @param site 사이트 설정 (기본 인터페이스 이름)
     */
    public RouterPolicy(SiteProperties site) {
        this.site = site;
    }

    @Override
    public boolean supports(DeviceType type) {
        return type == DeviceType.ROUTER;
    }

    @Override
    public String defaultVendor() {
        return "Cisco";
    }

    @Override
    public String defaultProduct() {
        return "IOS XE";
    }

    @Override
    public ObjectNode defaultRule() {
        final ObjectNode rule = JSON.objectNode();
        rule.putArray("vendor").add("Cisco");
        rule.putArray("product").add("IOS XE");
        rule.putArray("model").add("Cisco ISR");
        rule.putArray("command").add("on");
        rule.putArray("interface").add(site.getRouterDefaults().getDefaultInterface());
        return rule;
    }

    @Override
    public ObjectNode declarationRule(PolicyBuildContext context) {
        final ObjectNode rule = baseRule(context);
        rule.putArray("command").add("create");
        rule.putArray("protocol").add("static");

        // ⚠️ 여기서는 네트워크 주소(대역)를 씁니다. 목적지를 광고하는 것이므로
        //    호스트 주소로 바꾸면 안 됩니다. (다른 유형과 반대)
        final String[] parts = PolicyJson.splitCidr(context.subnet().getCidr());
        if (parts != null) {
            rule.putArray("destination_prefix").add(parts[0]);
            final String mask = PolicyJson.maskOf(parts[1]);
            if (mask != null) {
                rule.putArray("subnet_mask").add(mask);
            }
        }
        return rule;
    }

    @Override
    public ObjectNode enforcementRule(PolicyBuildContext context) {
        final PolicyBuildContext.ConnectionView connection = context.connection();
        if (connection == null) {
            return null;
        }

        // 등급을 건너뛰는 연결만 차단합니다. 허용 연결은 장치 기본 동작
        // (permit) 이므로 굳이 규칙으로 만들지 않습니다 — 넣으면 ACL 이 커져
        // 사람이 읽을 수 없게 되고, 규칙 하나가 늘 때마다 장치 전체를 다시
        // 쓰게 됩니다.
        if (!connection.forbidden()) {
            return null;
        }

        final ObjectNode entry = aclEntry(connection);
        if (entry == null) {
            // ACL 구문을 만들 수 없으면 규칙을 보내지 않습니다.
            // 보내면 장치가 "적용된 것처럼" 삼킬 수 있습니다.
            return null;
        }

        /*
         * 연결 하나를 단독으로 적용하는 경로입니다.
         *
         * ⚠️ 실제 차단은 acl_apply (batchEnforcementRule) 가 담당합니다.
         *    IOS 확장 ACL 은 규칙을 하나씩 추가/삭제할 수 없고 이름으로
         *    통째로 다시 써야 하기 때문입니다. 그래서 이 노드는
         *    "이 연결이 금지다" 라는 기록(로그·미리보기) 역할입니다.
         */
        final ObjectNode rule = baseRule(context);
        rule.putArray("command").add("create");
        rule.putArray("rule_id").add(connection.ruleId());
        rule.putArray("reason").add(connection.reason());

        final ObjectNode target = rule.putObject("rule_target");
        target.putArray("acl_name").add(aclName());
        target.putArray("acl_scope").add("router");

        final ObjectNode match = rule.putObject("match_criteria");
        match.setAll(entry);
        rule.putArray("action").add("deny");
        return rule;
    }

    /**
     * 이 라우터가 지금 차단해야 하는 연결을 <b>한 번에</b> 내려보냅니다.
     *
     * <h2>⚠️ 왜 "연결 하나" 로는 안 되는가</h2>
     * <p>IOS 확장 ACL 은 규칙 하나만 지우는 문법이 없습니다.
     * {@code no ip access-list extended SONAR-CSO} 는 ACL 을 <b>통째로</b>
     * 지웁니다. 그래서 "지금 남아 있어야 하는 규칙 전체" 를 받아 매번 다시
     * 씁니다. 연결 하나만 보고 만든 규칙을 차례로 보내면 <b>운영자가 지운
     * 규칙이 장치에 그대로 남습니다</b> — 차단이 안 풀린 채 남는, 가장 위험한
     * 실패입니다.
     *
     * <p>반대로 완전히 비우는 경우(<b>차단할 연결 0건</b>)에도 노드를 만듭니다.
     * "빈 목록" 과 "보내지 않음" 은 다릅니다 — 운영자가 마지막 금지 연결을
     * 지운 순간을 표현하려면 빈 목록을 보내야 합니다.
     *
     * @param context 서브넷·벤더·이 서브넷의 모든 연결
     * @return 일괄 집행 노드
     */
    @Override
    public ObjectNode batchEnforcementRule(BatchPolicyContext context) {
        final List<PolicyBuildContext.ConnectionView> forbidden = context.forbiddenConnections();

        final ObjectNode rule = JSON.objectNode();
        rule.putArray("vendor").add(context.vendorOr(defaultVendor()));
        rule.putArray("product").add(context.productOr(defaultProduct()));
        rule.putArray("model").add(defaultProduct());
        rule.putArray("subnet_id").add(
                PolicyJson.firstNonBlank(context.subnet().getSubnetId(), "subnet"));
        // 이 정책이 보호하는 대역입니다.
        //
        // ⚠️ Prober 는 이 값으로 <b>ACL 을 걸 인터페이스를 장치에서 직접 찾습니다</b>.
        //    없으면 아래 applied_interface 힌트만 남는데, 그 힌트는 배포 기본값이라
        //    8000v 처럼 `GigabitEthernet1..4` 를 쓰는 장치에서는 존재하지 않습니다.
        //    그러면 ACL 이 만들어지기만 하고 어디에도 걸리지 않아 <b>차단이
        //    전혀 동작하지 않습니다.</b> (RVI 실장비에서 실측한 실패)
        if (context.subnet().getCidr() != null) {
            rule.putArray("subnet_cidr").add(context.subnet().getCidr());
        }
        // ⚠️ "apply" 여야 합니다. "create" 로 보내면 Prober 의 라우터 적용기가
        //    생성 분기(ospf/logging)로 들어가 아무것도 하지 않고 false 를 돌려줍니다.
        rule.putArray("command").add("apply");

        final ObjectNode target = rule.putObject("rule_target");
        target.putArray("acl_name").add(aclName());
        target.putArray("acl_scope").add("router");
        // ACL 을 걸 인터페이스입니다. 라우터는 어느 포트가 안쪽인지 서버에
        // 알려주지 않으므로(그럴 표준 필드가 없습니다) 사이트 설정을 따릅니다.
        // ⚠️ 이 인터페이스가 틀리면 엉뚱한 곳이 막힙니다 — 배포마다 확인해야 합니다.
        target.putArray("applied_interface").add(site.getRouterDefaults().getDefaultInterface());

        // 이전 규칙을 모두 지우고 다시 씁니다. 지운 규칙이 남지 않게 하는
        // 유일한 방법입니다.
        rule.put("replace", true);

        final ArrayNode entries = rule.putArray("acl_rules");
        for (final PolicyBuildContext.ConnectionView connection : forbidden) {
            final ObjectNode criteria = aclEntry(connection);
            if (criteria == null) {
                continue;
            }
            final ObjectNode entry = entries.addObject();
            entry.set("match_criteria", criteria);
            entry.putArray("action").add("deny");
            if (connection.reason() != null) {
                entry.putArray("reason").add(connection.reason());
            }
        }
        return rule;
    }

    /**
     * 연결 한 건을 확장 ACL 의 조건부(주소 4종 + 프로토콜)로 옮깁니다.
     *
     * <h2>⚠️ 와일드카드 마스크이지 서브넷 마스크가 아니다</h2>
     * <p>IOS 확장 ACL 은 {@code 192.168.10.0 0.0.0.255} 처럼 <b>반전
     * 마스크</b>를 씁니다({@code /24} → {@code 0.0.0.255}). 서브넷 마스크를
     * 그대로 넣으면 {@code /24} 가 {@code 255.255.255.0} 이 되어 매칭되는
     * 주소가 사실상 없어집니다 — <b>차단이 조용히 사라집니다</b>.
     *
     * <h2>⚠️ 포트를 쓰지 않는 이유</h2>
     * <p>라우터는 IP 포워딩 장치입니다. 막으려는 것은 대역 간 연결이므로
     * 프로토콜을 {@code ip} 로 두고 모든 포트를 막습니다. {@code tcp 80} 처럼
     * 좁히면 나머지 포트로 샙니다.
     *
     * @param connection 연결 (금지 연결)
     * @return 조건 노드 (구문을 만들 수 없으면 null)
     */
    private static ObjectNode aclEntry(PolicyBuildContext.ConnectionView connection) {
        if (connection.sourceCidr() == null || connection.destinationCidr() == null) {
            return null;
        }

        final String[] source = PolicyJson.splitCidr(connection.sourceCidr());
        final String[] destination = PolicyJson.splitCidr(connection.destinationCidr());
        if (source == null || destination == null) {
            return null;
        }
        final String sourceWildcard = PolicyJson.wildcardOf(source[1]);
        final String destinationWildcard = PolicyJson.wildcardOf(destination[1]);
        if (sourceWildcard == null || destinationWildcard == null) {
            return null;
        }

        final ObjectNode match = JSON.objectNode();
        // 네트워크 주소(대역)를 씁니다 — 라우터의 차단은 대역 단위입니다.
        match.putArray("ip_saddr").add(source[0]);
        match.putArray("ip_saddr_wildcard").add(sourceWildcard);
        match.putArray("ip_daddr").add(destination[0]);
        match.putArray("ip_daddr_wildcard").add(destinationWildcard);
        match.putArray("protocol").add("ip");
        return match;
    }

    /**
     * 이 랩에서 쓰는 라우터 ACL 이름입니다.
     *
     * <p>장치에서 같은 이름으로 찾아 지울 수 있어야 하므로 상수로 둡니다.
     *
     * @return ACL 이름
     */
    public static String aclName() {
        return "SONAR-CSO";
    }

    /**
     * 공통 머리말을 채웁니다.
     *
     * @param context 빌드 컨텍스트
     * @return 머리말이 채워진 노드
     */
    private ObjectNode baseRule(PolicyBuildContext context) {
        final ObjectNode rule = JSON.objectNode();
        rule.putArray("vendor").add(context.vendorOr(defaultVendor()));
        rule.putArray("product").add(context.productOr(defaultProduct()));
        rule.putArray("model").add(defaultProduct());
        rule.putArray("subnet_id").add(
                PolicyJson.firstNonBlank(context.subnet().getSubnetId(), "subnet"));
        return rule;
    }
}