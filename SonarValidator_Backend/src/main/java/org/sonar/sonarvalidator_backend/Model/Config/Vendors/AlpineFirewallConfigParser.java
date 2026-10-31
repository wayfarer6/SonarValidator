package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.DeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.TelemetryFields;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

@Component
public final class AlpineFirewallConfigParser implements DeviceConfigParser {

    @Override
    public int priority() { return 100; }

    private final TelemetryFields fields = new TelemetryFields();

    @Override
    public boolean supports(String productName, String deviceType) {
        if (productName == null) {
            return false;
        }
        return productName.contains("nftables")
                || productName.contains("Firewall")
                || (productName.contains("nft") && !productName.contains("OpenVSwitch"));
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = fields.newConfig(hostname, product, payload, vendorName(), format());
        config.setDeviceType("FIREWALL");

        if (fields.isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; firewall config left empty");
            return config;
        }

        fields.applyNicStatus(config, payload);
        fields.applyRouteStatus(config, payload);
        fields.applyArpTable(config, payload);
        fields.applyFirewallRules(config, payload);

        fields.applyRoutedVlans(config);

        if (config.getFirewallRules().isEmpty()) {
            config.getWarnings().add("nft ruleset not collected or empty");
        }
        return config;
    }

    private String vendorName() {
        return "Alpine";
    }

    @Override
    public String format() {
        return "AlpineFirewall";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "routes", "arp", "firewallRules", "vlans(subinterfaces)");
    }
}
