package org.sonar.sonarvalidator_backend.Model.Config;

import java.util.List;

import tools.jackson.databind.JsonNode;

public final class TelemetryFields {

    public NeutralDeviceConfig newConfig(String hostname, String product, JsonNode payload, String vendor, String format) {
        final NeutralDeviceConfig config = new NeutralDeviceConfig();
        config.setHostname(hostname);
        config.setProduct(product);
        config.setVendor(vendor);
        config.setFormat(format);
        config.setKernel(JsonReader.text(payload, "kernel"));
        return config;
    }

    public void applyNicStatus(NeutralDeviceConfig config, JsonNode payload) {
        for (final JsonNode iface : JsonReader.objects(JsonReader.at(payload, "nic_status"), "interfaces")) {
            final String name = JsonReader.text(iface, "name");
            final NeutralDeviceConfig.InterfaceConfig target = config.interfaceOrCreate(name);
            if (target == null) {
                continue;
            }
            target.setMtu(JsonReader.integer(iface, "mtu"));
            target.setMacAddress(JsonReader.text(iface, "mac"));
            target.setOperState(JsonReader.text(iface, "state"));
            target.setParent(JsonReader.text(iface, "parent"));
            target.getFlags().clear();
            target.getFlags().addAll(JsonReader.textList(iface, "flags"));

            for (final JsonNode address : JsonReader.objects(iface, "addresses")) {
                final String value = JsonReader.text(address, "address");
                if (value == null || value.isBlank()) {
                    continue;
                }
                final Integer prefixLength = JsonReader.integer(address, "prefix_len");
                final String cidr = (prefixLength == null) ? value : value + "/" + prefixLength;
                if (!target.getAddresses().contains(cidr)) {
                    target.getAddresses().add(cidr);
                }
            }
        }
    }

    public void applyRouteStatus(NeutralDeviceConfig config, JsonNode payload) {
        for (final JsonNode route : JsonReader.objects(JsonReader.at(payload, "route_status"), "routes")) {
            final NeutralDeviceConfig.RouteConfig target = new NeutralDeviceConfig.RouteConfig();

            String prefix = JsonReader.text(route, "prefix");
            if (prefix == null) {
                prefix = JsonReader.text(route, "destination");
            }
            target.setPrefix(prefix);

            String nextHop = JsonReader.text(route, "next_hop");
            if (nextHop == null) {
                nextHop = JsonReader.text(route, "via");
            }
            target.setNextHop(nextHop);

            target.setNextHopInterface(JsonReader.text(route, "interface_name"));
            target.setProtocol(JsonReader.text(route, "protocol"));

            final Integer metric = JsonReader.integer(route, "metric");
            target.setMetric(metric == null ? null : metric.longValue());

            target.setSelected(JsonReader.bool(route, "selected"));

            final Boolean explicitDefault = JsonReader.bool(route, "is_default");
            target.setDefaultRoute(Boolean.TRUE.equals(explicitDefault)
                    || "0.0.0.0/0".equals(prefix)
                    || "default".equals(prefix));

            config.getRoutes().add(target);
        }
    }

    public void applyArpTable(NeutralDeviceConfig config, JsonNode payload) {
        for (final JsonNode entry : JsonReader.objects(JsonReader.at(payload, "arp_table"), "entries")) {
            final NeutralDeviceConfig.ArpEntry target = new NeutralDeviceConfig.ArpEntry();
            target.setAddress(JsonReader.text(entry, "address"));
            target.setMac(JsonReader.text(entry, "mac"));
            target.setState(JsonReader.text(entry, "state"));

            String iface = JsonReader.text(entry, "interface");
            if (iface == null) {
                final List<String> interfaces = JsonReader.textList(entry, "interfaces");
                iface = interfaces.isEmpty() ? null : String.join(",", interfaces);
            }
            target.setInterfaceName(iface);
            config.getArpEntries().add(target);
        }
    }

    public void applyVlanStatus(NeutralDeviceConfig config, JsonNode payload) {
        for (final JsonNode vlan : JsonReader.objects(JsonReader.at(payload, "vlan_status"), "vlans")) {
            final Integer vlanId = JsonReader.integer(vlan, "vlan_id");
            final NeutralDeviceConfig.VlanConfig target = config.vlanOrCreate(vlanId);
            if (target == null) {
                continue;
            }
            target.setName(JsonReader.text(vlan, "name"));
            target.setStatus(JsonReader.text(vlan, "status"));
            target.getMembers().clear();
            target.getMembers().addAll(JsonReader.textList(vlan, "ports"));
        }
    }

    public void applyTrunkStatus(NeutralDeviceConfig config, JsonNode payload) {
        for (final JsonNode port : JsonReader.objects(JsonReader.at(payload, "trunk_status"), "ports")) {
            final String name = JsonReader.text(port, "name");
            final NeutralDeviceConfig.InterfaceConfig target = config.interfaceOrCreate(name);
            if (target == null) {
                continue;
            }
            target.setAccessVlan(JsonReader.integer(port, "access_vlan"));
            target.getTrunkVlans().clear();
            target.getTrunkVlans().addAll(JsonReader.intList(port, "trunk_vlans"));

            final String mode = JsonReader.text(port, "mode");
            if (mode != null) {
                target.setMode(mode);
            } else {
                target.setMode(target.getTrunkVlans().isEmpty() ? "access" : "trunk");
            }
        }
    }

    public void applyFirewallRules(NeutralDeviceConfig config, JsonNode payload) {
        final JsonNode firewall = JsonReader.at(payload, "firewall_rules");
        if (firewall != null && firewall.path("nftables").isArray()) {
            for (final JsonNode item : firewall.path("nftables")) {
                for (final String kind : java.util.List.of("set", "map", "chain", "rule")) {
                    final JsonNode value = item.path(kind);
                    if (!value.isObject()) continue;
                    if ("chain".equals(kind) && !value.has("policy")) continue;
                    config.getFirewallRules().add("nft " + kind + " " + value.toString());
                }
            }
            return;
        }
        if (firewall != null && firewall.has("parsed") && !firewall.path("parsed").asBoolean()) {
            config.getWarnings().add("Partial nftables text parse: " + firewall.path("parse_error").asText("unknown syntax"));
        }
        for (final JsonNode table : JsonReader.objects(firewall, "tables")) {
            final String family = JsonReader.text(table, "family");
            final String tableName = JsonReader.text(table, "name");
            final String tableLabel = (family == null ? "inet" : family) + " " + (tableName == null ? "?" : tableName);

            for (final JsonNode chain : JsonReader.objects(table, "chains")) {
                final String chainName = JsonReader.text(chain, "name");
                final String policy = JsonReader.text(chain, "policy");
                if (policy != null) {
                    config.getFirewallRules().add(tableLabel + " chain " + chainName + " policy " + policy);
                }
                for (final JsonNode rule : JsonReader.objects(chain, "rules")) {
                    final String raw = JsonReader.text(rule, "raw");
                    if (raw != null) {
                        config.getFirewallRules().add(tableLabel + " chain " + chainName + " " + raw);
                        continue;
                    }
                    final String expression = JsonReader.text(rule, "expression") != null
                            ? JsonReader.text(rule, "expression") : JsonReader.text(rule, "match");
                    final String action = JsonReader.text(rule, "action");
                    final StringBuilder line = new StringBuilder(tableLabel)
                            .append(" chain ").append(chainName).append(' ');
                    if (expression != null) {
                        line.append(expression).append(' ');
                    }
                    if (action != null) {
                        line.append(action);
                    }
                    config.getFirewallRules().add(line.toString().trim());
                }
            }
        }
    }

    public NeutralDeviceConfig applyCommon(NeutralDeviceConfig config, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            config.getWarnings().add("empty telemetry payload");
            return config;
        }
        applyNicStatus(config, payload);
        applyRouteStatus(config, payload);
        applyArpTable(config, payload);
        applyVlanStatus(config, payload);
        applyTrunkStatus(config, payload);
        return config;
    }

    public boolean isEmptyPayload(JsonNode payload) {
        return payload == null || payload.isNull() || !payload.isObject() || payload.isEmpty();
    }

    public void applyRoutedVlans(NeutralDeviceConfig config) {
        for (final var iface : config.getInterfaces().values()) {
            final String name = iface.getName();
            if (name == null) continue;
            final int dot = name.indexOf('.');
            if (dot <= 0 || dot == name.length() - 1) continue;
            final int vlanId;
            try { vlanId = Integer.parseInt(name.substring(dot + 1)); }
            catch (NumberFormatException ignored) { continue; }
            if (vlanId < 1 || vlanId > 4094) continue;
            iface.setAccessVlan(vlanId);
            iface.setMode("routed");
            if (iface.getParent() == null) iface.setParent(name.substring(0, dot));
            final var vlan = config.vlanOrCreate(vlanId);
            if (!vlan.getMembers().contains(name)) vlan.getMembers().add(name);
        }
    }
}
