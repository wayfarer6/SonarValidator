package org.sonar.sonarvalidator_backend.Config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sonar.site")
public class SiteProperties {

    private String managementPrefix = "172.16.255.0/24";

    private SwitchDefaults switchDefaults = new SwitchDefaults();

    private FirewallDefaults firewallDefaults = new FirewallDefaults();

    private RouterDefaults routerDefaults = new RouterDefaults();

    private VmDefaults vmDefaults = new VmDefaults();

    public static class SwitchDefaults {

        private String uplinkPort = "eth0";

        private String bridgeName = "br0";

        private String defaultPort = "Ethernet 1";

        public String getUplinkPort() {
            return uplinkPort;
        }

        public void setUplinkPort(String uplinkPort) {
            this.uplinkPort = uplinkPort;
        }

        public String getBridgeName() {
            return bridgeName;
        }

        public void setBridgeName(String bridgeName) {
            this.bridgeName = bridgeName;
        }

        public String getDefaultPort() {
            return defaultPort;
        }

        public void setDefaultPort(String defaultPort) {
            this.defaultPort = defaultPort;
        }
    }

    public static class FirewallDefaults {

        private String tableName = "sonar";

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }

    public static class RouterDefaults {

        private String defaultInterface = "GigabitEthernet0/0/1";

        public String getDefaultInterface() {
            return defaultInterface;
        }

        public void setDefaultInterface(String defaultInterface) {
            this.defaultInterface = defaultInterface;
        }
    }

    public static class VmDefaults {

        private String configBackend = "netplan";

        public String getConfigBackend() {
            return configBackend;
        }

        public void setConfigBackend(String configBackend) {
            this.configBackend = configBackend;
        }
    }

    public String managementPrefixFor(String projectManagementPrefix) {
        if (projectManagementPrefix != null && !projectManagementPrefix.isBlank()) {
            return projectManagementPrefix.trim();
        }
        return managementPrefix == null ? "" : managementPrefix.trim();
    }

    public List<String> managementPrefixesFor(String projectManagementPrefix) {
        final String resolved = managementPrefixFor(projectManagementPrefix);
        if (resolved.isBlank()) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        for (final String item : resolved.split("\\s*,\\s*")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    public String getManagementPrefix() {
        return managementPrefix;
    }

    public void setManagementPrefix(String managementPrefix) {
        this.managementPrefix = managementPrefix;
    }

    public SwitchDefaults getSwitchDefaults() {
        return switchDefaults;
    }

    public void setSwitchDefaults(SwitchDefaults switchDefaults) {
        this.switchDefaults = switchDefaults;
    }

    public FirewallDefaults getFirewallDefaults() {
        return firewallDefaults;
    }

    public void setFirewallDefaults(FirewallDefaults firewallDefaults) {
        this.firewallDefaults = firewallDefaults;
    }

    public RouterDefaults getRouterDefaults() {
        return routerDefaults;
    }

    public void setRouterDefaults(RouterDefaults routerDefaults) {
        this.routerDefaults = routerDefaults;
    }

    public VmDefaults getVmDefaults() {
        return vmDefaults;
    }

    public void setVmDefaults(VmDefaults vmDefaults) {
        this.vmDefaults = vmDefaults;
    }
}
