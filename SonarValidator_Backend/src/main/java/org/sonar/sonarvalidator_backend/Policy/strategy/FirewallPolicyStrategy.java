package org.sonar.sonarvalidator_backend.Policy.strategy;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * nftables 방화벽의 정책 전략입니다.
 *
 * <h2>이 전략이 아는 것</h2>
 * <ul>
 *   <li>폴백: {@code filter} 테이블 생성</li>
 *   <li>선언: 별도 {@code sonar} 테이블 + input/forward 체인 (기본 accept)</li>
 *   <li>집행: 연결별 {@code rule_target + match_criteria + action}</li>
 * </ul>
 *
 * <h2>⚠️ 기본 {@code filter} 테이블을 건드리지 않는다</h2>
 * <p>기존 방화벽 설정을 덮어쓰면 랩 전체가 끊길 수 있습니다. 그래서 별도
 * {@code sonar} 테이블을 만들고, 그 안에서만 규칙을 다룹니다.
 *
 * <h2>⚠️ 기본 정책을 {@code drop} 으로 두지 않는다</h2>
 * <p>체인 기본값을 {@code accept} 로 두고 금지 연결만 명시적으로 {@code drop}
 * 합니다. 기본을 {@code drop} 으로 두면 정책이 비어 있는 순간(예: 프로젝트에
 * 규칙이 하나도 없을 때) <b>모든 트래픽이 끊깁니다.</b>
 *
 * <h2>⚠️ 이 장치는 격리 대상이 아니다</h2>
 * <p>방화벽은 {@code eth1} 트렁크에 여러 VLAN 을 <b>동시에</b> 들고 있습니다
 * (랩 기준 131/132/133). 인터페이스를 내리는 격리를 적용하면 위반한 서브넷만
 * 끊는 것이 아니라 <b>무관한 존 전체가 함께 죽습니다.</b> 그래서 격리는
 * 상위 계층(격리 서비스)에서 방화벽을 제외합니다.
 * 이 전략은 정책만 다룹니다.
 */
@Component
public class FirewallPolicyStrategy implements DevicePolicyStrategy {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /**
     * 방화벽 정책이 쓰는 nftables 테이블 이름입니다.
     *
     * <p>기본 {@code filter} 를 건드리지 않기 위한 별도 이름입니다.
     */
    public static final String FIREWALL_TABLE = "sonar";

    @Override
    public boolean supports(DeviceType type) {
        return type == DeviceType.FIREWALL;
    }

    @Override
    public String defaultVendor() {
        return "Linux";
    }

    @Override
    public String defaultProduct() {
        return "nftables";
    }

    @Override
    public ObjectNode defaultRule() {
        final ObjectNode rule = JSON.objectNode();
        rule.putArray("vendor").add("Linux");
        rule.putArray("product").add("nftables");
        rule.putArray("model").add("Linux Netfilter");
        rule.putArray("command").add("create");
        rule.putArray("table_family").add("inet");
        rule.putArray("table_name").add("filter");

        final ArrayNode chains = rule.putArray("chains");
        chains.add(chain("input", "drop"));
        chains.add(chain("forward", "drop"));
        return rule;
    }

    @Override
    public ObjectNode declarationRule(PolicyBuildContext context) {
        final ObjectNode rule = baseRule(context);

        rule.putArray("command").add("create");
        rule.putArray("table_family").add("inet");
        rule.putArray("table_name").add(FIREWALL_TABLE);

        final ArrayNode chains = rule.putArray("chains");
        chains.add(chain("input", "accept"));
        chains.add(chain("forward", "accept"));
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

        final ObjectNode target = rule.putObject("rule_target");
        target.putArray("table_family").add("inet");
        target.putArray("table_name").add(FIREWALL_TABLE);
        target.putArray("chain_name").add("forward");

        final ObjectNode match = rule.putObject("match_criteria");
        match.putArray("ip_saddr").add(connection.sourceCidr());
        match.putArray("ip_daddr").add(connection.destinationCidr());
        match.putArray("protocol").add(connection.protocol());

        // C++ 의 nftables 경로는 규칙별 verdict 를 그대로 씁니다.
        rule.putArray("action").add(connection.forbidden() ? "drop" : "accept");
        return rule;
    }

    /**
     * nftables 체인 1개를 만듭니다.
     *
     * @param hook          hook 이름이자 chain 이름 (input/forward)
     * @param defaultPolicy 기본 verdict (drop/accept)
     * @return 체인 노드
     */
    private ObjectNode chain(String hook, String defaultPolicy) {
        final ObjectNode chain = JSON.objectNode();
        chain.putArray("chain_name").add(hook);
        chain.putArray("hook").add(hook);
        chain.putArray("priority").add("0");
        chain.putArray("policy").add(defaultPolicy);
        return chain;
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