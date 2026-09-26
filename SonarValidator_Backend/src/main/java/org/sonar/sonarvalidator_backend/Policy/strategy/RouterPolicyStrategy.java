package org.sonar.sonarvalidator_backend.Policy.strategy;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * FRR / Cisco 라우터의 정책 전략입니다.
 *
 * <h2>이 전략이 아는 것</h2>
 * <ul>
 *   <li>폴백: 인터페이스 on (문서 {@code Router_Policy_Design.md})</li>
 *   <li>선언: 정적 경로 형태의 대역/마스크</li>
 *   <li>집행: 없음 — 라우터 ACL 은 아직 다루지 않습니다</li>
 * </ul>
 *
 * <h2>⚠️ 라우터만 네트워크 주소를 쓴다</h2>
 * <p>다른 유형은 인터페이스에 <b>호스트 주소</b>를 넣어야 하지만, 라우터의
 * 선언은 <b>목적지 대역</b>을 광고하는 것입니다. 그래서 여기서는
 * {@link PolicyJson#splitCidr} 의 네트워크 주소를 그대로 씁니다.
 * 이 구분을 놓치면 "10.20.111.10/24 대역으로 가는 경로" 같은 잘못된 광고가
 * 만들어집니다.
 *
 * <h2>⚠️ 집행 규칙이 없는 이유</h2>
 * <p>라우터에 ACL 을 넣으려면 FRR 의 route-map / access-list 를 생성해야 하고,
 * 그것은 기존 라우팅 정책과 충돌할 위험이 큽니다. 랩에서 라우터는 <b>경로
 * 제공자</b>이고, 차단은 방화벽과 스위치가 담당합니다. 의도(intents)는
 * 공통으로 전달되므로 화면은 "이 연결이 금지" 를 알고 있습니다.
 *
 * <p>나중에 라우터 ACL 이 필요해지면 이 클래스에 {@code enforcementRule}
 * 구현을 더하면 됩니다 — 다른 유형은 건드리지 않습니다.
 */
@Component
public class RouterPolicyStrategy implements DevicePolicyStrategy {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

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
        rule.putArray("interface").add("GigabitEthernet0/0/1");
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
        // 라우터 ACL 은 아직 다루지 않습니다. (클래스 주석 참고)
        return null;
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