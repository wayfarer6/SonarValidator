package org.sonar.sonarvalidator_backend.Model.Config;

import java.util.List;

import tools.jackson.databind.JsonNode;

public interface DeviceConfigParser {

    default int priority() { return 0; }

    boolean supports(String productName, String deviceType);

    NeutralDeviceConfig parse(String hostname, String product, JsonNode payload);

    String format();

    default List<String> capabilities() {
        return List.of("interfaces", "routes", "vlans", "trunks");
    }
}
