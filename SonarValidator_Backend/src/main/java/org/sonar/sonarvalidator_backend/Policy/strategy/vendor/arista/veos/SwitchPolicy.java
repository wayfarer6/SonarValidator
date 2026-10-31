package org.sonar.sonarvalidator_backend.Policy.strategy.vendor.arista.veos;

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
public class SwitchPolicy implements DevicePolicy {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    public static final String COOKIE = "0x534f4e41";

    private final SiteProperties site;

    public SwitchPolicy(SiteProperties site) {
        this.site = site;
    }

    private String uplinkPort() {
        return site.getSwitchDefaults().getUplinkPort();
    }

    private String bridgeName() {
        return site.getSwitchDefaults().getBridgeName();
    }

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
        // ⚠️ 포트 이름은 제품·배포마다 다릅니다(Arista="Ethernet 1", OVS="eth1").
        //    상수로 박지 않고 설정에서 읽습니다.
        rule.putArray("port").add(site.getSwitchDefaults().getDefaultPort());
        return rule;
    }

    @Override
    public ObjectNode declarationRule(PolicyBuildContext context) {
        final ObjectNode rule = baseRule(context);




        rule.putArray("command").add("create");

        final String[] parts = PolicyJson.splitCidr(context.subnet().getCidr());
        if (parts != null) {



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
        rule.putArray("applied_interface").add(uplinkPort());
        rule.putArray("bridge_name").add(bridgeName());

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

    @Override
    public ObjectNode batchEnforcementRule(BatchPolicyContext context) {
        final String product = context.productOr(defaultProduct());
        if (!isOpenVSwitch(product)) {
            return null;
        }

        final List<PolicyBuildContext.ConnectionView> forbidden = context.forbiddenConnections();

        final ObjectNode rule = JSON.objectNode();
        rule.putArray("vendor").add(context.vendorOr(defaultVendor()));
        rule.putArray("product").add(product);
        rule.putArray("model").add(defaultProduct());
        rule.putArray("subnet_id").add(
                PolicyJson.firstNonBlank(context.subnet().getSubnetId(), "subnet"));
        // "create" 가 아니라 "apply" 입니다 — 목록 전체를 주고 장치가 맞추게
        // 하는 선언적 명령입니다.
        rule.putArray("command").add("apply");

        final ObjectNode target = rule.putObject("rule_target");
        target.putArray("acl_name").add(aclName());
        // ⚠️ ovs-ofctl 의 대상은 <b>브리지</b>입니다. 업링크 포트 이름을
        //    넣으면 "no bridge named eth0" 로 실패합니다.
        target.putArray("bridge_name").add(bridgeName());
        target.putArray("applied_interface").add(uplinkPort());

        // Flow rules carry a cookie so OVS can safely remove only our rules.
        rule.put("replace", true);
        rule.putArray("cookie").add(COOKIE);

        final ArrayNode entries = rule.putArray("acl_rules");
        for (final PolicyBuildContext.ConnectionView connection : forbidden) {



            if (connection.sourceCidr() == null || connection.destinationCidr() == null) {
                continue;
            }
            final ObjectNode match = entries.addObject();
            final ObjectNode criteria = match.putObject("match_criteria");
            criteria.putArray("ip_saddr").add(connection.sourceCidr());
            criteria.putArray("ip_daddr").add(connection.destinationCidr());
            criteria.putArray("protocol").add("ip");
            match.putArray("action").add("deny");
            if (connection.reason() != null) {
                match.putArray("reason").add(connection.reason());
            }
        }
        return rule;
    }

    public static String aclName() {
        return "SONAR-CSO";
    }

    private static boolean isOpenVSwitch(String product) {
        if (product == null) {
            return false;
        }
        final String normalized = product.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("openvswitch") || normalized.contains("open vswitch")
                || normalized.equals("ovs");
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
