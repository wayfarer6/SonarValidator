package org.sonar.sonarvalidator_backend.Service.quarantine;

import java.util.List;
import java.util.Map;
import org.sonar.sonarvalidator_backend.Model.entity.QuarantineState;

public final class QuarantineTopologyView {
    private QuarantineTopologyView() {}

    public static Map<String, Object> summarize(String projectKey, String cidr, List<QuarantineState> states) {
        final var rows = states.stream()
                .filter(row -> java.util.Objects.equals(projectKey, row.getProjectKey()))
                .filter(row -> row.getScope() != QuarantineState.Scope.CONNECTION
                        || java.util.Objects.equals(cidr, row.getTargetCidr()))
                .filter(row -> row.isActive() || !"APPLIED".equals(row.getApplyStatus()))
                .toList();
        final boolean blocked = rows.stream().anyMatch(row -> Boolean.TRUE.equals(row.getConfirmedBlocked()));
        final String status = rows.stream().anyMatch(row -> "FAILED".equals(row.getApplyStatus())) ? "FAILED"
                : rows.stream().anyMatch(row -> !"APPLIED".equals(row.getApplyStatus())) ? "PENDING"
                : rows.isEmpty() ? "NONE" : "APPLIED";
        return Map.of("quarantined", blocked, "quarantine_requested", rows.stream().anyMatch(QuarantineState::isActive),
                "quarantine_status", status);
    }
}
