package org.sonar.sonarvalidator_backend.Policy.strategy.vendor.canonical.ubuntu;

import org.sonar.sonarvalidator_backend.Config.SiteProperties;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Policy.strategy.DevicePolicy;
import org.sonar.sonarvalidator_backend.Policy.strategy.PolicyBuildContext;
import org.sonar.sonarvalidator_backend.Policy.strategy.PolicyJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Component
public class VmPolicy implements DevicePolicy {

    private static final Logger log = LoggerFactory.getLogger(VmPolicy.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    public static final String PRIMARY_INTERFACE_TOKEN = "__primary__";

    private final SiteProperties site;

    public VmPolicy(SiteProperties site) {
        this.site = site;
    }

    private String configBackend() {
        final String configured = site == null ? null : site.getVmDefaults().getConfigBackend();
        return PolicyJson.firstNonBlank(configured, "netplan");
    }

    @Override
    public boolean supports(DeviceType type) {
        return type == DeviceType.VM;
    }

    @Override
    public String defaultVendor() {
        return "Canonical";
    }

    @Override
    public String defaultProduct() {
        return "Ubuntu Linux";
    }

    @Override
    public ObjectNode defaultRule() {
        final ObjectNode rule = JSON.objectNode();
        rule.putArray("vendor").add("Canonical");
        rule.putArray("product").add("Ubuntu Linux");
        rule.putArray("model").add("Ubuntu VM");
        rule.putArray("command").add("on");
        rule.putArray("interface").add(PRIMARY_INTERFACE_TOKEN);
        return rule;
    }

    @Override
    public ObjectNode declarationRule(PolicyBuildContext context) {
        final ObjectNode rule = baseRule(context);

        // netplan 스키마 형태로 대역을 알립니다.
        rule.putArray("command").add("create");
        rule.putArray("config_backend").add(configBackend());

        final String cidr = context.subnet().getCidr();
        if (cidr == null || cidr.isBlank()) {
            return rule;
        }

        final ObjectNode ethernets = rule.putObject("network_config").putObject("ethernets");
        final ObjectNode nic = ethernets.putObject(PRIMARY_INTERFACE_TOKEN);
        nic.putArray("dhcp4").add("false");

        final String host = PolicyJson.hostCidrOf(cidr);
        if (host != null) {
            nic.putArray("addresses").add(host);
        } else {
            // 주소를 계산할 수 없으면 <b>넣지 않습니다.</b> 틀린 주소를 넣으면
            // 장치가 통신 불능이 되어 프로버까지 멈춥니다.
            log.warn("VM address not derivable from subnet cidr={}; omitting address", cidr);
        }
        return rule;
    }

    @Override
    public ObjectNode enforcementRule(PolicyBuildContext context) {
        // VM 은 트래픽 집행 지점이 아닙니다. (라우터/방화벽이 담당)
        // 의도(intents)는 공통으로 함께 전달되므로 화면은 같은 정보를 봅니다.
        return null;
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
