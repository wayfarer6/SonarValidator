package org.sonar.sonarvalidator_backend.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

public final class ObservedConnectivity {

    public static final String INTERNET_NODE_ID = "internet";

    public static final String INTERNET_NODE_LABEL = "Internet";

    private ObservedConnectivity() {
    }

    public static final String KIND_INTERNET = "internet";

    public static final String KIND_SUBNET = "subnet";

    public static Set<String> internetExposedAgents(Map<String, NeutralDeviceConfig> configs) {
        final Set<String> exposed = new LinkedHashSet<>();
        if (configs == null || configs.isEmpty()) {
            return exposed;
        }
        for (final Map.Entry<String, NeutralDeviceConfig> entry : configs.entrySet()) {
            if (entry.getKey() != null && hasDefaultRoute(entry.getValue())) {
                exposed.add(entry.getKey());
            }
        }
        return exposed;
    }

    public static boolean hasDefaultRoute(NeutralDeviceConfig config) {
        if (config == null) {
            return false;
        }
        for (final NeutralDeviceConfig.RouteConfig route : config.getRoutes()) {
            if (route == null) {
                continue;
            }
            if (usableRoute(config, route) && (Boolean.TRUE.equals(route.getDefaultRoute())
                    || "0.0.0.0/0".equals(route.getPrefix()))) {
                return true;
            }
        }
        return false;
    }

    public record Link(String agentId,
                       String sourceId,
                       String targetId,
                       String sourceCidr,
                       String targetCidr,
                       boolean forbidden) {
    }

    public static List<Link> betweenSubnetsOfEachDevice(Project project,
                                                        Map<String, NeutralDeviceConfig> configs) {
        final List<Link> result = new ArrayList<>();
        if (project == null || configs == null || configs.isEmpty()) {
            return result;
        }

        // 장치별로 프로젝트 서브넷을 모읍니다.
        final Map<String, List<ProjectSubnet>> subnetsByAgent = new LinkedHashMap<>();
        for (final ProjectSubnet subnet : project.getSubnets()) {
            final String agentId = subnet.getAgentId();
            if (agentId == null || agentId.isBlank()) {
                continue;
            }
            subnetsByAgent.computeIfAbsent(agentId, key -> new ArrayList<>()).add(subnet);
        }

        for (final Map.Entry<String, List<ProjectSubnet>> entry : subnetsByAgent.entrySet()) {
            final String agentId = entry.getKey();
            final NeutralDeviceConfig config = findConfig(configs, agentId);
            if (config == null) {
                continue;
            }
            final Set<String> routed = routedNetworks(config);
            if (routed.isEmpty()) {
                continue;
            }

            // 이 장치가 실제로 L3 를 가지는 서브넷만 남깁니다.
            final List<ProjectSubnet> onDevice = new ArrayList<>();
            for (final ProjectSubnet subnet : entry.getValue()) {
                if (subnet.getCidr() == null || subnet.getCidr().isBlank()) {
                    continue;
                }
                if (isRouted(subnet.getCidr(), routed)) {
                    onDevice.add(subnet);
                }
            }
            // 대역(CIDR) 기준으로 중복을 없앱니다 — 같은 장치에 같은 대역이 두 번 있으면
            // (이름만 다른 행) 연결이 중복 생성됩니다.
            final Map<String, ProjectSubnet> byCidr = new LinkedHashMap<>();
            for (final ProjectSubnet subnet : onDevice) {
                byCidr.putIfAbsent(normalize(subnet.getCidr()), subnet);
            }
            final List<ProjectSubnet> unique = new ArrayList<>(byCidr.values());
            if (unique.size() < 2) {
                continue;
            }

            for (int i = 0; i < unique.size(); ++i) {
                for (int j = i + 1; j < unique.size(); ++j) {
                    final ProjectSubnet first = unique.get(i);
                    final ProjectSubnet second = unique.get(j);
                    final ZoneClass firstZone = first.getZoneClass();
                    final ZoneClass secondZone = second.getZoneClass();
                    result.add(new Link(
                            agentId,
                            first.getSubnetId(),
                            second.getSubnetId(),
                            first.getCidr(),
                            second.getCidr(),
                            ZoneClass.forbidsDirectConnection(firstZone, secondZone)));
                }
            }
        }
        return result;
    }

    public record VlanAttachment(String subnetId, int vlanId, boolean up) {}

    public static List<VlanAttachment> vlanAttachments(Project project, String agentId, NeutralDeviceConfig config) {
        final List<VlanAttachment> result = new ArrayList<>();
        for (final var subnet : project.getSubnets()) {
            final Integer vlanId = subnet.getVlanId();
            if (vlanId == null || !config.getVlans().containsKey(vlanId)) continue;
            final var vlan = config.getVlans().get(vlanId);
            final long active = vlan.getMembers().stream().distinct().filter(name -> {
                final String expanded = name.matches("Et[0-9].*") ? "Ethernet" + name.substring(2) : name;
                return config.getInterfaces().keySet().stream().anyMatch(port -> port.equalsIgnoreCase(expanded))
                        && isInterfaceUsable(config, expanded);
            }).count();
            result.add(new VlanAttachment(subnet.getSubnetId(), vlanId, active >= 2 && !isDown(vlan.getStatus())));
        }
        return result;
    }

    private static NeutralDeviceConfig findConfig(Map<String, NeutralDeviceConfig> configs,
                                                 String agentId) {
        final NeutralDeviceConfig exact = configs.get(agentId);
        if (exact != null) {
            return exact;
        }
        for (final Map.Entry<String, NeutralDeviceConfig> entry : configs.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(agentId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Set<String> routedNetworks(NeutralDeviceConfig config) {
        final Set<String> networks = new LinkedHashSet<>();

        // 1) 라우팅 테이블 — directly connected / local 경로
        for (final NeutralDeviceConfig.RouteConfig route : config.getRoutes()) {
            if (route == null || !usableRoute(config, route)) {
                continue;
            }
            final String prefix = route.getPrefix();
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            final String protocol = route.getProtocol() == null ? "" : route.getProtocol();
            final String nextHop = route.getNextHop() == null ? "" : route.getNextHop();
            final boolean connected = protocol.equalsIgnoreCase("connected")
                    || protocol.equalsIgnoreCase("local")
                    || nextHop.equalsIgnoreCase("directly connected");
            if (connected) {
                networks.add(normalize(prefix));
            }
        }

        // 2) 인터페이스 주소 — 라우팅 테이블이 빈 장비를 위한 보완
        config.getInterfaces().forEach((name, iface) -> {
            if (name == null || name.equalsIgnoreCase("lo") || !isInterfaceUsable(config, name)) {
                return;
            }
            for (final String address : iface.getAddresses()) {
                final String cidr = normalizedNetworkOf(address);
                if (cidr != null) {
                    networks.add(cidr);
                }
            }
        });

        networks.remove("");
        return networks;
    }

    public static boolean isSubnetObserved(NeutralDeviceConfig config, String cidr) {
        return config != null && cidr != null && isRouted(cidr, routedNetworks(config));
    }

    public static boolean isInterfaceUsable(NeutralDeviceConfig config, String name) {
        return config == null || isInterfaceUsable(config, name, Set.of());
    }

    private static boolean isInterfaceUsable(NeutralDeviceConfig config, String name, Set<String> seen) {
        if (name == null || name.isBlank()) return true;
        final String key = name.toLowerCase(java.util.Locale.ROOT);
        if (seen.contains(key)) return false;
        final var iface = config.getInterfaces().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue)
                .findFirst().orElse(null);
        if (iface == null) return true;
        if (isDown(iface.getAdminState()) || isDown(iface.getOperState())
                || iface.getFlags().stream().anyMatch(flag -> "NO-CARRIER".equalsIgnoreCase(flag))) return false;
        final Set<String> next = new LinkedHashSet<>(seen);
        next.add(key);
        return isInterfaceUsable(config, iface.getParent(), next);
    }

    private static boolean isDown(String state) {
        if (state == null) return false;
        final String normalized = state.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return normalized.equals("down") || normalized.equals("administratively") || normalized.contains("administratively down")
                || normalized.equals("lowerlayerdown") || normalized.equals("lower-layer-down")
                || normalized.equals("notpresent") || normalized.equals("disabled")
                || normalized.equals("notconnect") || normalized.equals("not-connected")
                || normalized.equals("shutdown") || normalized.equals("inactive")
                || normalized.equals("suspended") || normalized.equals("errdisabled");
    }

    private static boolean usableRoute(NeutralDeviceConfig config, NeutralDeviceConfig.RouteConfig route) {
        if (route == null || Boolean.FALSE.equals(route.getSelected())) return false;
        if (!isInterfaceUsable(config, route.getNextHopInterface())) return false;
        if (route.getNextHopInterface() != null && !route.getNextHopInterface().isBlank()) return true;
        final String prefix = normalize(route.getPrefix());
        boolean known = false;
        for (final var entry : config.getInterfaces().entrySet()) {
            for (final String address : entry.getValue().getAddresses()) {
                final String network = normalizedNetworkOf(address);
                final boolean related = prefix.equals(network) || (route.getNextHop() != null
                        && network != null && isRouted(route.getNextHop() + "/32", Set.of(network)));
                if (related) {
                    known = true;
                    if (isInterfaceUsable(config, entry.getKey())) return true;
                }
            }
        }
        return !known;
    }

    private static boolean isRouted(String subnetCidr, Set<String> routed) {
        final long[] candidate = parseNetwork(subnetCidr);
        if (candidate == null) {
            return false;
        }
        // 정확히 같은 대역은 바로 참입니다.
        if (routed.contains(normalize(subnetCidr))) {
            return true;
        }
        for (final String network : routed) {
            final long[] routedNet = parseNetwork(network);
            if (routedNet == null) {
                continue;
            }
            // 라우팅 대역이 서브넷보다 같거나 넓고, 서브넷 시작 주소가 그 안에 있으면 포함입니다.
            if (routedNet[1] <= candidate[1]
                    && withinPrefix(routedNet[0], candidate[0], (int) routedNet[1])) {
                return true;
            }
        }
        return false;
    }

    private static String normalizedNetworkOf(String address) {
        if (address == null || address.isBlank() || address.contains(":")) {
            return null;
        }
        try {
            final String host = address.contains("/") ? address : address + "/32";
            final long[] parsed = parseNetwork(host);
            if (parsed == null) {
                return null;
            }
            final long mask = parsed[1] == 0 ? 0L : (0xFFFFFFFFL << (32 - parsed[1])) & 0xFFFFFFFFL;
            final long network = parsed[0] & mask;
            if ((network >>> 24) == 127) {
                return null;
            }
            return ((network >>> 24) & 0xFF) + "." + ((network >>> 16) & 0xFF) + "."
                    + ((network >>> 8) & 0xFF) + "." + (network & 0xFF) + "/" + parsed[1];
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String normalize(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return "";
        }
        try {
            return PolicySubnet.normalizeCidr(cidr);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static boolean withinPrefix(long network, long address, int length) {
        if (length <= 0) {
            return true;
        }
        final long mask = (0xFFFFFFFFL << (32 - length)) & 0xFFFFFFFFL;
        return (network & mask) == (address & mask);
    }

    private static long[] parseNetwork(String cidr) {
        try {
            final int slash = cidr.indexOf('/');
            final String addressPart = slash < 0 ? cidr.trim() : cidr.substring(0, slash).trim();
            final int length = slash < 0 ? 32 : Integer.parseInt(cidr.substring(slash + 1).trim());
            final String[] octets = addressPart.split("\\.");
            if (octets.length != 4 || length < 0 || length > 32) {
                return null;
            }
            long value = 0;
            for (final String octet : octets) {
                final int part = Integer.parseInt(octet.trim());
                if (part < 0 || part > 255) {
                    return null;
                }
                value = (value << 8) | part;
            }
            return new long[]{value & 0xFFFFFFFFL, length};
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
