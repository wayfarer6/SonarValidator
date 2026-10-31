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

@Component
public class RouterPolicy implements DevicePolicy {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final SiteProperties site;

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

        if (!connection.forbidden()) {
            return null;
        }

        final ObjectNode entry = aclEntry(connection);
        if (entry == null) {
            // ACL 구문을 만들 수 없으면 규칙을 보내지 않습니다.
            // 보내면 장치가 "적용된 것처럼" 삼킬 수 있습니다.
            return null;
        }

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

    @Override
    public ObjectNode batchEnforcementRule(BatchPolicyContext context) {
        final List<PolicyBuildContext.ConnectionView> forbidden = context.forbiddenConnections();

        final ObjectNode rule = JSON.objectNode();
        rule.putArray("vendor").add(context.vendorOr(defaultVendor()));
        rule.putArray("product").add(context.productOr(defaultProduct()));
        rule.putArray("model").add(defaultProduct());
        rule.putArray("subnet_id").add(
                PolicyJson.firstNonBlank(context.subnet().getSubnetId(), "subnet"));
        if (context.subnet().getCidr() != null) {
            rule.putArray("subnet_cidr").add(context.subnet().getCidr());
        }
        // ⚠️ "apply" 여야 합니다. "create" 로 보내면 Prober 의 라우터 적용기가
        //    생성 분기(ospf/logging)로 들어가 아무것도 하지 않고 false 를 돌려줍니다.
        rule.putArray("command").add("apply");

        final ObjectNode target = rule.putObject("rule_target");
        target.putArray("acl_name").add(aclName());
        target.putArray("acl_scope").add("router");



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

    public static String aclName() {
        return "SONAR-CSO";
    }

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
