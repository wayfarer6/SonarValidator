package org.sonar.sonarvalidator_backend.Policy.strategy;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Open vSwitch 스위치의 정책 전략입니다.
 *
 * <h2>이 전략이 아는 것</h2>
 * <ul>
 *   <li>폴백: Arista vEOS 포트 enable (문서 {@code Switch_Policy_Design.md})</li>
 *   <li>선언: 관리 주소 + 넷마스크</li>
 *   <li>집행: <b>OVS ACL 플로우</b> (출발/도착 대역 기준 drop)</li>
 * </ul>
 *
 * <h2>⚠️ 왜 스위치인데 L3 주소로 막는가</h2>
 * <p>Open vSwitch 는 VLAN 태그만으로는 <b>다른 VLAN 사이의 이동을 막지
 * 못합니다.</b> 태그는 "어느 VLAN 소속인가" 를 표시할 뿐이고, 라우터가
 * 두 VLAN 을 모두 들고 있으면 라우팅으로 넘어갑니다. 실제로 랩의
 * Survillance-Network-Router 는 VLAN 111/112 를 <b>동시에</b> 가지므로
 * 스위치의 tag 설정만으로는 존 간 격리가 되지 않습니다.
 *
 * <p>그래서 스위치에도 출발/도착 대역 기준 drop 플로우를 넣습니다.
 * 방화벽 규칙과 같은 판정을 스위치 계층에서 <b>한 번 더</b> 거는
 * 심층 방어입니다.
 *
 * <h2>⚠️ 업링크(trunk) 포트에 넣는다</h2>
 * <p>access 포트(tag 111 등)에 넣으면 그 VLAN 안에서만 매칭되어 존 간 이동을
 * 보지 못합니다. 랩 관례대로 업링크를 {@link #UPLINK_PORT} 로 둡니다.
 *
 * <h2>⚠️ 차단 우선순위를 허용보다 높게</h2>
 * <p>같은 5-튜플에 두 규칙이 걸렸을 때 <b>차단이 이겨야</b> 합니다.
 * 허용이 이기면 등급 건너뛰기가 조용히 통과합니다. 이 우선순위는 C++ 의
 * {@code ApplyOpenVSwitchPolicy} 가 계산하지만, 여기서도 의미를 남겨 둡니다.
 */
@Component
public class SwitchPolicyStrategy implements DevicePolicyStrategy {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /**
     * OVS ACL 플로우를 넣을 업링크(trunk) 포트 이름입니다.
     *
     * <p>랩의 모든 스위치가 업링크를 {@code eth0} 으로 씁니다
     * (Switch-1 = eth0 trunk[111,112], eth1/eth2 = access).
     */
    public static final String UPLINK_PORT = "eth0";

    /**
     * OVS 브리지 이름입니다.
     *
     * <p>랩의 모든 스위치가 {@code br0} 하나를 씁니다.
     */
    public static final String BRIDGE_NAME = "br0";

    @Override
    public boolean supports(DeviceType type) {
        return type == DeviceType.SWITCH;
    }

    @Override
    public String defaultVendor() {
        return "Arista";
    }

    @Override
    public String defaultProduct() {
        return "Arista vEOS";
    }

    @Override
    public ObjectNode defaultRule() {
        final ObjectNode rule = JSON.objectNode();
        rule.putArray("vendor").add("Arista");
        rule.putArray("product").add("Arista vEOS");
        rule.putArray("model").add("Arista vEOS");
        rule.putArray("command").add("on");
        rule.putArray("enable").add("true");
        rule.putArray("port").add("Ethernet 1");
        return rule;
    }

    @Override
    public ObjectNode declarationRule(PolicyBuildContext context) {
        final ObjectNode rule = baseRule(context);

        // OVS 는 관리 주소를 직접 들고 있지 않습니다(브리지/포트가 담당).
        // 그래도 관리 주소를 선언해 두면 운영자가 "어느 대역인가" 를 확인할 수 있고,
        // 텔레메트리에서 주소가 비어 있을 때 대조 기준이 됩니다.
        rule.putArray("command").add("create");

        final String[] parts = PolicyJson.splitCidr(context.subnet().getCidr());
        if (parts != null) {
            // ⚠️ 스위치 관리 주소도 <b>호스트 주소</b>입니다.
            //   네트워크 주소(.0)를 넣으면 `ip addr add 10.20.111.0/24` 가
            //   되어 대역 판정이 깨집니다.
            final String host = PolicyJson.hostCidrOf(context.subnet().getCidr());
            if (host != null) {
                rule.putArray("ip_address").add(host);
            }
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
        if (connection == null
                || connection.sourceCidr() == null
                || connection.destinationCidr() == null) {
            return null;
        }

        final ObjectNode rule = baseRule(context);
        rule.putArray("command").add("create");
        rule.putArray("rule_id").add(connection.ruleId());
        rule.putArray("reason").add(connection.reason());

        // C++ ApplyOpenVSwitchPolicy 의 acl_name 경로가 읽는 키들입니다.
        // (acl_name + source_subnet + destination_subnet + action + applied_interface)
        rule.putArray("acl_name").add("acl-" + connection.ruleId());
        rule.putArray("source_subnet").add(connection.sourceCidr());
        rule.putArray("destination_subnet").add(connection.destinationCidr());
        rule.putArray("applied_interface").add(UPLINK_PORT);
        rule.putArray("bridge_name").add(BRIDGE_NAME);

        // C++ 는 action=="deny" 만 drop 으로 보고 나머지는 normal(통과)입니다.
        rule.putArray("action").add(connection.forbidden() ? "deny" : "accept");

        // 이 스위치가 속한 등급을 함께 남깁니다. 운영자가 "이 규칙이 어느 존
        // 사이에 걸렸나" 를 확인할 때 필요합니다.
        if (context.subnet().getZoneClass() != null) {
            rule.putArray("zone_class").add(context.subnet().getZoneClass().label());
        }
        rule.putArray("subnet_cidr").add(context.subnet().getCidr());
        return rule;
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