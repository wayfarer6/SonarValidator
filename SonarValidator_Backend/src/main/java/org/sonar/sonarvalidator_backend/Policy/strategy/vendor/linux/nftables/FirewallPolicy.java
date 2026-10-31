package org.sonar.sonarvalidator_backend.Policy.strategy.vendor.linux.nftables;

import org.sonar.sonarvalidator_backend.Config.SiteProperties;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Policy.strategy.DevicePolicy;
import org.sonar.sonarvalidator_backend.Policy.strategy.PolicyBuildContext;
import org.sonar.sonarvalidator_backend.Policy.strategy.PolicyJson;
import org.springframework.stereotype.Component;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Component
public class FirewallPolicy implements DevicePolicy {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final SiteProperties site;

    public FirewallPolicy(SiteProperties site) {
        this.site = site;
    }

    private String firewallTable() {
        return site.getFirewallDefaults().getTableName();
    }

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
        rule.putArray("table_name").add(firewallTable());

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
        target.putArray("table_name").add(firewallTable());
        target.putArray("chain_name").add("forward");

        final ObjectNode match = rule.putObject("match_criteria");
        match.putArray("ip_saddr").add(connection.sourceCidr());
        match.putArray("ip_daddr").add(connection.destinationCidr());
        match.putArray("protocol").add(connection.protocol());

        // C++ 의 nftables 경로는 규칙별 verdict 를 그대로 씁니다.
        rule.putArray("action").add(connection.forbidden() ? "drop" : "accept");
        return rule;
    }

    private ObjectNode chain(String hook, String defaultPolicy) {
        final ObjectNode chain = JSON.objectNode();
        chain.putArray("chain_name").add(hook);
        chain.putArray("hook").add(hook);
        chain.putArray("priority").add("0");
        chain.putArray("policy").add(defaultPolicy);
        return chain;
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
