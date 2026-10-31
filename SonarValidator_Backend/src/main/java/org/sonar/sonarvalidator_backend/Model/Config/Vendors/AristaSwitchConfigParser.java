package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.DeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.TelemetryFields;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

@Component
public final class AristaSwitchConfigParser implements DeviceConfigParser {

    @Override
    public int priority() { return 100; }

    private final TelemetryFields fields = new TelemetryFields();

    @Override
    public boolean supports(String productName, String deviceType) {
        return productName != null && productName.contains("Arista");
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = fields.newConfig(hostname, product, payload, vendorName(), format());
        config.setDeviceType("SWITCH");

        if (fields.isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; Arista config left empty");
            return config;
        }

        fields.applyNicStatus(config, payload);
        fields.applyRouteStatus(config, payload);
        fields.applyVlanStatus(config, payload);
        fields.applyTrunkStatus(config, payload);
        fields.applyArpTable(config, payload);

        config.getInterfaces().values().forEach(iface -> {
            if (iface.getAccessVlan() == null || iface.getAccessVlan() <= 0) {
                return;
            }
            final NeutralDeviceConfig.VlanConfig vlan = config.vlanOrCreate(iface.getAccessVlan());
            if (vlan != null && !vlan.getMembers().contains(iface.getName())) {
                vlan.getMembers().add(iface.getName());
            }
        });

        final long trunkPorts = config.getInterfaces().values().stream()
                .filter(iface -> "trunk".equals(iface.getMode()))
                .count();
        config.getMetadata().put("trunkPortCount", Long.toString(trunkPorts));
        config.getMetadata().put("vlanCount", Integer.toString(config.getVlans().size()));

        if (config.getVlans().isEmpty()) {
            config.getWarnings().add("no VLANs collected; `show vlan brief` may have failed");
        }
        return config;
    }

    private String vendorName() {
        return "Arista";
    }

    @Override
    public String format() {
        return "ARISTA_vEOS";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "routes", "vlans", "trunks", "arp");
    }
}
