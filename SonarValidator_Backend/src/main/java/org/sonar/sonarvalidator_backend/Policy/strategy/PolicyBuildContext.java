package org.sonar.sonarvalidator_backend.Policy.strategy;

import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

public record PolicyBuildContext(
        ProjectSubnet subnet,
        String vendor,
        String product,
        ConnectionView connection) {

    public record ConnectionView(String ruleId,
                                 boolean outgoing,
                                 String peerId,
                                 String peerCidr,
                                 ZoneClass peerClass,
                                 String sourceCidr,
                                 String destinationCidr,
                                 String protocol,
                                 Integer port,
                                 boolean forbidden,
                                 String reason) {
    }

    public static PolicyBuildContext forDeclaration(ProjectSubnet subnet,
                                                    String vendor,
                                                    String product) {
        return new PolicyBuildContext(subnet, vendor, product, null);
    }

    public static PolicyBuildContext forConnection(ProjectSubnet subnet,
                                                   String vendor,
                                                   String product,
                                                   ConnectionView connection) {
        return new PolicyBuildContext(subnet, vendor, product, connection);
    }

    public String vendorOr(String fallback) {
        return PolicyJson.firstNonBlank(vendor, fallback);
    }

    public String productOr(String fallback) {
        return PolicyJson.firstNonBlank(product, fallback);
    }
}
