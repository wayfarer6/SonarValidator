package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.DeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.TelemetryFields;
import org.sonar.sonarvalidator_backend.Model.Config.JsonReader;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

@Component
public final class OpenVSwitchConfigParser implements DeviceConfigParser {

    @Override
    public int priority() { return 100; }

    private final TelemetryFields fields = new TelemetryFields();

    @Override
    public boolean supports(String productName, String deviceType) {
        if (productName == null) {
            return false;
        }
        return productName.contains("OpenVSwitch") || productName.contains("Open vSwitch");
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = fields.newConfig(hostname, product, payload, vendorName(), format());
        config.setDeviceType("SWITCH");

        if (fields.isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; OVS config left empty");
            return config;
        }

        fields.applyNicStatus(config, payload);
        fields.applyVlanStatus(config, payload);
        fields.applyTrunkStatus(config, payload);

        final JsonNode topology = JsonReader.at(payload, "ovs_topology");
        for (final JsonNode bridge : JsonReader.objects(topology, "bridges")) {
            final String bridgeName = JsonReader.text(bridge, "name");
            if (bridgeName != null && !config.getBridges().contains(bridgeName)) {
                config.getBridges().add(bridgeName);
            }
        }

        if (config.getInterfaces().isEmpty()) {
            config.getWarnings().add("ip a produced no interfaces for this switch");
        }
        return config;
    }

    private String vendorName() {
        return "Open vSwitch";
    }

    @Override
    public String format() {
        return "OpenvSwitch";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "vlans", "trunks");
    }
}
