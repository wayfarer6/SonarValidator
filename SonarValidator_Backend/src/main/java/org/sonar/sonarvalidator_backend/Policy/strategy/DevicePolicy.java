package org.sonar.sonarvalidator_backend.Policy.strategy;

import org.sonar.sonarvalidator_backend.Model.DeviceType;

public interface DevicePolicy {

    boolean supports(DeviceType type);

    default boolean supports(DeviceType type, String vendor, String product) { return supports(type); }

    default int priority() { return 0; }

    String defaultVendor();

    String defaultProduct();

    tools.jackson.databind.node.ObjectNode defaultRule();

    tools.jackson.databind.node.ObjectNode declarationRule(PolicyBuildContext context);

    tools.jackson.databind.node.ObjectNode enforcementRule(PolicyBuildContext context);

    default tools.jackson.databind.node.ObjectNode batchEnforcementRule(BatchPolicyContext context) {
        return null;
    }
}
