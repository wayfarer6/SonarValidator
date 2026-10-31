package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.DeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.TelemetryFields;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

@Component
public final class FrrRouterConfigParser implements DeviceConfigParser {

    @Override
    public int priority() { return 100; }

    private final TelemetryFields fields = new TelemetryFields();

    @Override
    public boolean supports(String productName, String deviceType) {
        return productName != null && productName.contains("FRR");
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = fields.newConfig(hostname, product, payload, vendorName(), format());
        config.setDeviceType("ROUTER");

        if (fields.isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; FRR config left empty");
            return config;
        }

        fields.applyNicStatus(config, payload);
        fields.applyRouteStatus(config, payload);
        fields.applyArpTable(config, payload);

        fields.applyRoutedVlans(config);

        final long ospfCount = config.getRoutes().stream()
                .filter(route -> "ospf".equalsIgnoreCase(route.getProtocol()))
                .count();
        config.getMetadata().put("ospfRouteCount", Long.toString(ospfCount));
        config.getMetadata().put("routeCount", Integer.toString(config.getRoutes().size()));

        if (config.getRoutes().isEmpty()) {
            config.getWarnings().add("no routes collected; FRR kernel/vtysh output may be empty");
        }
        return config;
    }

    private String vendorName() {
        return "FRR";
    }

    @Override
    public String format() {
        return "FRRRouter";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "routes", "arp", "vlans(subinterfaces)");
    }
}
