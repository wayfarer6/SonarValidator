package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.DeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.TelemetryFields;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

@Component
public final class CiscoRouterConfigParser implements DeviceConfigParser {

    @Override
    public int priority() { return 100; }

    private final TelemetryFields fields = new TelemetryFields();

    private static final String PRODUCT_MARKER = "Cisco";

    @Override
    public boolean supports(String productName, String deviceType) {
        return productName != null && productName.contains(PRODUCT_MARKER);
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = fields.newConfig(hostname, product, payload, vendorName(), format());
        config.setDeviceType("ROUTER");

        if (fields.isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; Cisco config left empty");
            return config;
        }

        fields.applyNicStatus(config, payload);
        fields.applyRouteStatus(config, payload);
        completeInterfacePrefixes(config);
        fields.applyArpTable(config, payload);

        config.getWarnings().add("VLAN/trunk not collected for Cisco (IOS CLI lacks switchport show)");
        return config;
    }

    private static void completeInterfacePrefixes(NeutralDeviceConfig config) {
        for (var iface : config.getInterfaces().values()) {
            iface.getAddresses().replaceAll(address -> {
                if (address.contains("/") || address.contains(":")) return address;
                String result = address;
                int best = -1;
                for (var route : config.getRoutes()) {
                    if (!"connected".equalsIgnoreCase(route.getProtocol())
                            || !iface.getName().equals(route.getNextHopInterface())
                            || route.getPrefix() == null || !route.getPrefix().contains("/")) continue;
                    try {
                        int prefix = Integer.parseInt(route.getPrefix().split("/")[1]);
                        String candidate = address + "/" + prefix;
                        if (prefix > best && org.sonar.sonarvalidator_backend.Policy.PolicySubnet.normalizeCidr(candidate)
                                .equals(org.sonar.sonarvalidator_backend.Policy.PolicySubnet.normalizeCidr(route.getPrefix()))) {
                            result = candidate;
                            best = prefix;
                        }
                    } catch (IllegalArgumentException ignored) { }
                }
                return result;
            });
        }
    }

    private String vendorName() {
        return "Cisco";
    }

    @Override
    public String format() {
        return "CISCO_IOS";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "routes", "arp");
    }
}
