package org.sonar.sonarvalidator_backend.Model;

public enum DeviceType {
    SWITCH,
    ROUTER,
    FIREWALL,
    VM;

    public static DeviceType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase()) {
            case "SWITCH" -> SWITCH;
            case "ROUTER" -> ROUTER;
            case "FIREWALL" -> FIREWALL;
            case "VM", "VIRTUALMACHINE", "VIRTUAL_MACHINE" -> VM;
            default -> null;
        };
    }

    public static DeviceType inferFromDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return VM;
        }
        final String upper = deviceId.trim().toUpperCase();

        final int cut = Math.max(
                Math.max(upper.lastIndexOf('-'), upper.lastIndexOf('.')),
                Math.max(upper.lastIndexOf('_'), upper.lastIndexOf('/')));
        final String last = (cut < 0) ? "" : upper.substring(cut + 1);

        if ("ROUTER".equals(last)) {
            return ROUTER;
        }
        if ("SWITCH".equals(last)) {
            return SWITCH;
        }
        if ("FIREWALL".equals(last)) {
            return FIREWALL;
        }

        if (upper.startsWith("VIRTUAL") || upper.startsWith("VM")) {
            return VM;
        }
        if (upper.startsWith("SWITCH") || upper.startsWith("SW")) {
            return SWITCH;
        }
        if (upper.startsWith("ROUTER") || upper.startsWith("RT")) {
            return ROUTER;
        }
        if (upper.startsWith("FIREWALL") || upper.startsWith("FW")) {
            return FIREWALL;
        }
        return VM;
    }
}
