package org.sonar.sonarvalidator_backend.Service.quarantine;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;

public record QuarantineContext(
        Integer nodeId,
        String agentId,
        DeviceType deviceType,
        String projectKey,
        String subnetCidr,
        String managementPrefix) {

    public boolean hasAgent() {
        return agentId != null && !agentId.isBlank();
    }

    public boolean hasSubnet() {
        return subnetCidr != null && !subnetCidr.isBlank();
    }

    public boolean hasManagementPrefix() {
        return managementPrefix != null && !managementPrefix.isBlank();
    }

    public String deviceTypeName() {
        return deviceType == null ? "UNKNOWN" : deviceType.name();
    }

    public String displayName() {
        if (hasAgent()) {
            return agentId;
        }
        return nodeId == null ? "unknown" : ("node-" + nodeId);
    }

    public static List<String> nonNull(List<String> items) {
        return items == null ? List.of() : items;
    }
}
