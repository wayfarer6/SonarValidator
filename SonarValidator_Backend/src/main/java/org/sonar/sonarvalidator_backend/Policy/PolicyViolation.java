package org.sonar.sonarvalidator_backend.Policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PolicyViolation(
        String ruleId,
        String sourceSubnetId,
        String targetSubnetId,
        ZoneClass sourceZone,
        ZoneClass targetZone,
        String sampledSourceIp,
        String sampledTargetIp,
        int sampledPort,
        String reason,
        Severity severity,
        String sampledProtocol) {

    public enum Severity {
        CRITICAL,
        MAJOR,
        MINOR
    }

    public PolicyViolation(
            String ruleId,
            String sourceSubnetId,
            String targetSubnetId,
            ZoneClass sourceZone,
            ZoneClass targetZone,
            String sampledSourceIp,
            String sampledTargetIp,
            int sampledPort,
            String reason,
            Severity severity) {
        this(ruleId, sourceSubnetId, targetSubnetId, sourceZone, targetZone,
                sampledSourceIp, sampledTargetIp, sampledPort, reason, severity, "tcp");
    }

    public String evidenceType() {
        return SegmentationBddEngine.INTERNET_EXPOSURE_RULE.equals(ruleId)
                ? "DEFAULT_ROUTE_OBSERVATION" : "POLICY_MODEL_FINDING";
    }

    public String sampledPacket() {
        if (sampledSourceIp == null || sampledTargetIp == null
                || "-".equals(sampledSourceIp) || "-".equals(sampledTargetIp)) return null;
        final String protocol = sampledProtocol == null || sampledProtocol.isBlank()
                ? "tcp"
                : sampledProtocol;
        return protocol + " " + sampledSourceIp + " -> " + sampledTargetIp
                + (sampledPort == PacketVariables.ANY_PORT
                        ? (protocol.equalsIgnoreCase("icmp") ? "" : " (all ports)")
                        : (":" + sampledPort));
    }

    public String sampledPacket(String protocol) {
        if (sampledSourceIp == null || sampledTargetIp == null
                || "-".equals(sampledSourceIp) || "-".equals(sampledTargetIp)) return null;
        final String proto = protocol == null || protocol.isBlank()
                ? "tcp"
                : protocol.toLowerCase(java.util.Locale.ROOT);
        return proto + " " + sampledSourceIp + " -> " + sampledTargetIp
                + (sampledPort == PacketVariables.ANY_PORT
                        ? (proto.equals("icmp") ? "" : " (all ports)")
                        : (":" + sampledPort));
    }

    public static Map<String, List<PolicyViolation>> groupByRule(List<PolicyViolation> violations) {
        final Map<String, List<PolicyViolation>> grouped = new LinkedHashMap<>();
        if (violations == null) {
            return grouped;
        }
        for (final PolicyViolation violation : violations) {
            grouped.computeIfAbsent(violation.ruleId(), key -> new ArrayList<>()).add(violation);
        }
        return grouped;
    }
}
