package org.sonar.sonarvalidator_backend.Model.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NeutralDeviceConfig {

    private boolean discoveryComplete = true;

    public boolean isDiscoveryComplete() { return discoveryComplete; }
    public void setDiscoveryComplete(boolean value) { discoveryComplete = value; }


    public static class InterfaceConfig {
        private String name;
        private String description;
        private String macAddress;
        private Integer mtu;
        private String adminState;
        private String operState;
        private final List<String> addresses = new ArrayList<>();
        private Integer accessVlan;
        private final List<Integer> trunkVlans = new ArrayList<>();
        private String mode;
        private String parent;
        private final List<String> flags = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getMacAddress() {
            return macAddress;
        }

        public void setMacAddress(String macAddress) {
            this.macAddress = macAddress;
        }

        public Integer getMtu() {
            return mtu;
        }

        public void setMtu(Integer mtu) {
            this.mtu = mtu;
        }

        public String getAdminState() {
            return adminState;
        }

        public void setAdminState(String adminState) {
            this.adminState = adminState;
        }

        public String getOperState() {
            return operState;
        }

        public void setOperState(String operState) {
            this.operState = operState;
        }

        public List<String> getAddresses() {
            return addresses;
        }

        public Integer getAccessVlan() {
            return accessVlan;
        }

        public void setAccessVlan(Integer accessVlan) {
            this.accessVlan = accessVlan;
        }

        public List<Integer> getTrunkVlans() {
            return trunkVlans;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }

        public List<String> getFlags() {
            return flags;
        }
    }

    public static class RouteConfig {
        private String protocol;
        private String prefix;
        private String nextHop;
        private String nextHopInterface;
        private Long metric;
        private Boolean selected;
        private Boolean defaultRoute;

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getNextHop() {
            return nextHop;
        }

        public void setNextHop(String nextHop) {
            this.nextHop = nextHop;
        }

        public String getNextHopInterface() {
            return nextHopInterface;
        }

        public void setNextHopInterface(String nextHopInterface) {
            this.nextHopInterface = nextHopInterface;
        }

        public Long getMetric() {
            return metric;
        }

        public void setMetric(Long metric) {
            this.metric = metric;
        }

        public Boolean getSelected() {
            return selected;
        }

        public void setSelected(Boolean selected) {
            this.selected = selected;
        }

        public Boolean getDefaultRoute() {
            return defaultRoute;
        }

        public void setDefaultRoute(Boolean defaultRoute) {
            this.defaultRoute = defaultRoute;
        }
    }

    public static class VlanConfig {
        private Integer vlanId;
        private String name;
        private String status;
        private final List<String> members = new ArrayList<>();

        public Integer getVlanId() {
            return vlanId;
        }

        public void setVlanId(Integer vlanId) {
            this.vlanId = vlanId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public List<String> getMembers() {
            return members;
        }
    }

    public static class ArpEntry {
        private String address;
        private String mac;
        private String interfaceName;
        private String state;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getMac() {
            return mac;
        }

        public void setMac(String mac) {
            this.mac = mac;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public void setInterfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }

    //  노드 수준 정보

    private String hostname;
    private String vendor;
    private String product;
    private String deviceType;
    private String kernel;
    private String format;

    private final Map<String, InterfaceConfig> interfaces = new LinkedHashMap<>();
    private final List<RouteConfig> routes = new ArrayList<>();
    private final Map<Integer, VlanConfig> vlans = new LinkedHashMap<>();
    private final List<ArpEntry> arpEntries = new ArrayList<>();
    private final List<String> firewallRules = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private final List<String> bridges = new ArrayList<>();

    private final Map<String, String> metadata = new LinkedHashMap<>();

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getKernel() {
        return kernel;
    }

    public void setKernel(String kernel) {
        this.kernel = kernel;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Map<String, InterfaceConfig> getInterfaces() {
        return interfaces;
    }

    public List<RouteConfig> getRoutes() {
        return routes;
    }

    public Map<Integer, VlanConfig> getVlans() {
        return vlans;
    }

    public List<ArpEntry> getArpEntries() {
        return arpEntries;
    }

    public List<String> getFirewallRules() {
        return firewallRules;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getBridges() {
        return bridges;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public InterfaceConfig interfaceOrCreate(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return interfaces.computeIfAbsent(name, key -> {
            final InterfaceConfig created = new InterfaceConfig();
            created.setName(key);
            return created;
        });
    }

    public VlanConfig vlanOrCreate(Integer vlanId) {
        if (vlanId == null) {
            return null;
        }
        return vlans.computeIfAbsent(vlanId, key -> {
            final VlanConfig created = new VlanConfig();
            created.setVlanId(key);
            return created;
        });
    }

    public Map<String, List<String>> addressesByInterface() {
        final Map<String, List<String>> result = new LinkedHashMap<>();
        interfaces.forEach((name, config) -> result.put(name, List.copyOf(config.getAddresses())));
        return result;
    }

    public boolean isEmpty() {
        return interfaces.isEmpty() && routes.isEmpty() && vlans.isEmpty();
    }
}
