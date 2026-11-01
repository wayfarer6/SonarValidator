package org.sonar.sonarvalidator_backend.Service.telemetry;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class SnapshotPayloadGuard {
    private static final Map<String, List<String>> ARRAYS = Map.of(
            "nic_status", List.of("interfaces"), "route_status", List.of("routes"),
            "vlan_status", List.of("vlans"), "trunk_status", List.of("ports"),
            "arp_table", List.of("entries"), "ovs_topology", List.of("bridges"),
            "firewall_rules", List.of("tables", "nftables"));

    private SnapshotPayloadGuard() {}

    public static void requireComplete(JsonNode payload, JsonNode previous) {
        for (final var entry : ARRAYS.entrySet()) {
            final String name = entry.getKey();
            final JsonNode value = payload.path(name);
            final boolean required = previous != null && previous.has(name);
            if (value.isMissingNode() && !required) continue;
            if (!value.isObject() || !value.path("parsed").asBoolean(true)
                    || !value.path("error").asString("").isBlank() || !value.path("parse_error").asString("").isBlank()
                    || entry.getValue().stream().noneMatch(key -> value.path(key).isArray())) {
                throw new IllegalArgumentException("Incomplete telemetry section: " + name
                        + "; previous configuration retained");
            }
        }
    }
}
