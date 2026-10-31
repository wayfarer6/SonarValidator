package org.sonar.sonarvalidator_backend.Policy.strategy;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;

public record BatchPolicyContext(
        ProjectSubnet subnet,
        String vendor,
        String product,
        List<PolicyBuildContext.ConnectionView> connections) {

    public List<PolicyBuildContext.ConnectionView> forbiddenConnections() {
        if (connections == null) {
            return List.of();
        }
        return connections.stream().filter(PolicyBuildContext.ConnectionView::forbidden).toList();
    }

    public String vendorOr(String fallback) {
        return PolicyJson.firstNonBlank(vendor, fallback);
    }

    public String productOr(String fallback) {
        return PolicyJson.firstNonBlank(product, fallback);
    }
}
