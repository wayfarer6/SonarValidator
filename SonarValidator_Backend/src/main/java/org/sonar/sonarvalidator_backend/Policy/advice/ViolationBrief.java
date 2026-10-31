package org.sonar.sonarvalidator_backend.Policy.advice;

import java.util.Locale;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Policy.PolicyViolation;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

public record ViolationBrief(String ruleId,
                             String severity,
                             String srcLabel,
                             String dstLabel,
                             String srcClass,
                             String dstClass,
                             int classGap,
                             boolean forbidden,
                             String packet,
                             Integer port,
                             String reason) {

    private static final String UNKNOWN = "미지정";

    public static ViolationBrief from(PolicyViolation violation) {
        if (violation == null) {
            return null;
        }

        final ZoneClass source = violation.sourceZone();
        final ZoneClass target = violation.targetZone();




        final int gap = (source == null || target == null)
                ? -1
                : Math.abs(source.level() - target.level());

        final boolean forbidden = source != null && target != null
                && ZoneClass.forbidsDirectConnection(source, target);

        // 반례 포트: ANY_PORT(-1) 는 "포트 제한 없음" 이라는 의미이므로
        // 숫자 대신 null 로 내보내고, 문장은 sampledPacket() 이 만듭니다.
        final Integer port = violation.sampledPort() == PacketVariables.ANY_PORT
                ? null
                : violation.sampledPort();

        return new ViolationBrief(
                text(violation.ruleId(), "-"),
                violation.severity() == null
                        ? "MINOR"
                        : violation.severity().name().toUpperCase(Locale.ROOT),
                text(violation.sourceSubnetId(), UNKNOWN),
                text(violation.targetSubnetId(), UNKNOWN),
                source == null ? UNKNOWN : source.label(),
                target == null ? UNKNOWN : target.label(),
                gap,
                forbidden,
                text(violation.sampledPacket(), "-"),
                port,
                text(violation.reason(), "사유 없음"));
    }

    public String describe() {
        final StringBuilder line = new StringBuilder();
        line.append('[').append(severity).append("] ").append(ruleId);
        line.append(" — ").append(srcLabel).append('(').append(srcClass).append(')');
        line.append(" → ").append(dstLabel).append('(').append(dstClass).append(')');

        if (classGap >= 0) {
            line.append(", 등급 차이 ").append(classGap);
        } else {
            line.append(", 등급 미지정");
        }
        if (forbidden) {
            line.append(", 금지 쌍");
        }
        line.append('\n');
        line.append("    반례 패킷: ").append(packet);
        if (port != null) {
            line.append(" (포트 ").append(port).append(')');
        } else {
            line.append(" (포트 제한 없음)");
        }
        line.append('\n');
        line.append("    사유: ").append(reason);
        return line.toString();
    }

    public Optional<Integer> knownClassGap() {
        return classGap >= 0 ? Optional.of(classGap) : Optional.empty();
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
