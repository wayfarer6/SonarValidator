package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.DeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.TelemetryFields;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

@Component
public final class LinuxVmConfigParser implements DeviceConfigParser {

    private final TelemetryFields fields = new TelemetryFields();

    @Override
    public boolean supports(String productName, String deviceType) {
        if (productName == null) {
            return false;
        }
        return productName.contains("Ubuntu") || productName.contains("Linux");
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = fields.newConfig(hostname, product, payload, vendorName(), format());
        config.setDeviceType("VM");

        if (fields.isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; Linux VM config left empty");
            return config;
        }

        fields.applyNicStatus(config, payload);
        fields.applyRouteStatus(config, payload);
        fields.applyArpTable(config, payload);

        fields.applyRoutedVlans(config);

        if (config.getRoutes().isEmpty()) {
            config.getWarnings().add("no routes collected (ip route show returned nothing)");
        }
        return config;
    }

    private String vendorName() {
        return "Linux";
    }

    @Override
    public String format() {
        return "LinuxVM";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "routes", "arp", "vlans(subinterfaces)");
    }
}
