package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Policy.PolicyRule;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicyViolation;
import org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

/**
 * 망분리 검증 엔진(BDD) 테스트입니다.
 *
 * <p>BDD 자체의 정확성과 함께, <b>실제 랩 대역</b>
 * ({@code docs/Agent_Command.md} 의 검증된 주소)을 써서 프론트엔드가 보내는
 * 형태의 규칙이 올바르게 판정되는지 확인합니다.
 */
class SegmentationBddEngineTest {

    private final SegmentationBddEngine engine = new SegmentationBddEngine();

    @Test
    void aggregateSensitiveLabelCannotHideConfidentialOverlapInEitherDirection() {
        var subnets = List.of(
                PolicySubnet.of("C", "10.10.131.0/24", ZoneClass.CONFIDENTIAL),
                PolicySubnet.of("Aggregate", "10.10.0.0/16", ZoneClass.SENSITIVE),
                PolicySubnet.of("O", "10.30.141.0/24", ZoneClass.OPEN));
        for (boolean reverse : List.of(false, true)) {
            var rule = new PolicyRule("overlap", reverse ? "O" : "Aggregate", reverse ? "Aggregate" : "O", 443);
            var report = engine.validate(subnets, List.of(rule));
            assertThat(report.isCompliant()).isFalse();
            assertThat(report.getViolations()).singleElement().satisfies(v -> {
                assertThat(v.severity()).isEqualTo(PolicyViolation.Severity.CRITICAL);
                assertThat(v.sourceZone()).isEqualTo(reverse ? ZoneClass.OPEN : ZoneClass.CONFIDENTIAL);
                assertThat(v.targetZone()).isEqualTo(reverse ? ZoneClass.CONFIDENTIAL : ZoneClass.OPEN);
                assertThat(reverse ? v.sampledTargetIp() : v.sampledSourceIp()).startsWith("10.10.131.");
                assertThat(v.sampledPort()).isEqualTo(443);
            });
        }
    }

    @Test
    void nonOverlappingSensitiveRangeRemainsAllowed() {
        var subnets = List.of(
                PolicySubnet.of("C", "10.10.131.0/24", ZoneClass.CONFIDENTIAL),
                PolicySubnet.of("S", "10.10.132.0/24", ZoneClass.SENSITIVE),
                PolicySubnet.of("O", "10.30.141.0/24", ZoneClass.OPEN));
        assertThat(engine.validate(subnets, List.of(new PolicyRule("ok", "S", "O", 443))).isCompliant()).isTrue();
    }

    /** 랩 실제 대역을 흉내 낸 서브넷 3개 (Confidential / Sensitive / Open). */
    private static List<PolicySubnet> labSubnets() {
        return List.of(
                PolicySubnet.of("Subnet-0001", "192.168.0.0/24", ZoneClass.OPEN),
                PolicySubnet.of("Subnet-0002", "10.20.111.0/24", ZoneClass.SENSITIVE),
                PolicySubnet.of("Subnet-0004", "10.10.131.0/24", ZoneClass.CONFIDENTIAL));
    }

    @Test
    @DisplayName("인접 등급 연결은 허용된다")
    void adjacentZonesAreAllowed() {
        final List<PolicyRule> rules = List.of(
                new PolicyRule("Rule-0001", "Subnet-0001", "Subnet-0002", 443));

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), rules);

        assertThat(report.isCompliant()).isTrue();
        assertThat(report.getViolationCount()).isZero();
        assertThat(report.getMessages()).anyMatch(message -> message.contains("준수"));
    }

    @Test
    @DisplayName("동일 CSO 등급의 명시적 연결 규칙은 등급 위반이 아니다")
    void sameZoneExplicitRuleIsNotAZoneViolation() {
        final List<PolicySubnet> sameZoneSubnets = List.of(
                PolicySubnet.of("Subnet-A", "10.20.1.0/24", ZoneClass.SENSITIVE),
                PolicySubnet.of("Subnet-B", "10.20.2.0/24", ZoneClass.SENSITIVE));
        final PolicyRule explicitAllow = new PolicyRule("Rule-ALLOW", "Subnet-A", "Subnet-B", 443);

        final SegmentationBddEngine.Report report = engine.validate(
                sameZoneSubnets, List.of(explicitAllow));

        assertThat(report.isCompliant()).isTrue();
        assertThat(report.getRuleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Confidential 과 Open 직접 연결은 위반으로 판정된다")
    void confidentialToOpenIsViolation() {
        final List<PolicyRule> rules = List.of(
                new PolicyRule("Rule-0001", "Subnet-0004", "Subnet-0001", 443));

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), rules);

        assertThat(report.isCompliant()).isFalse();
        assertThat(report.getViolatedRuleIds()).containsExactly("Rule-0001");
        assertThat(report.getViolations())
                .hasSize(1)
                .allSatisfy(violation -> {
                    assertThat(violation.severity()).isEqualTo(PolicyViolation.Severity.CRITICAL);
                    assertThat(violation.sourceZone()).isEqualTo(ZoneClass.CONFIDENTIAL);
                    assertThat(violation.targetZone()).isEqualTo(ZoneClass.OPEN);
                });
    }

    @Test
    @DisplayName("수집된 C와 O 직접 연결도 방화벽 보호가 확인되지 않으면 치명 위반으로 남는다")
    void discoveredConfidentialToOpenConnectionRemainsCritical() {
        final PolicyRule discovered = new PolicyRule("Rule-DISCOVERED", "Subnet-0004", "Subnet-0001", 443);
        discovered.setOrigin(PolicyRule.Origin.DISCOVERED);

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), List.of(discovered));

        assertThat(report.isCompliant()).isFalse();
        assertThat(report.getViolations())
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.severity()).isEqualTo(PolicyViolation.Severity.CRITICAL);
                    assertThat(violation.reason()).contains("Confidential", "Open");
                });
    }

    @Test
    @DisplayName("반례 패킷이 위반 대역 안에서 추출된다")
    void violationIncludesCounterExamplePacket() {
        final List<PolicyRule> rules = List.of(
                new PolicyRule("Rule-0001", "Subnet-0004", "Subnet-0001", 443));

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), rules);
        final PolicyViolation violation = report.getViolations().get(0);

        // 출발지는 Confidential 대역(10.10.131.0/24), 목적지는 Open 대역(192.168.0.0/24)
        assertThat(violation.sampledSourceIp()).startsWith("10.10.131.");
        assertThat(violation.sampledTargetIp()).startsWith("192.168.0.");
        assertThat(violation.sampledProtocol()).isEqualTo("tcp");
        assertThat(violation.sampledPort()).isEqualTo(443);
        assertThat(violation.sampledPacket()).contains("tcp").contains("->").contains(":443");
    }

    @Test
    @DisplayName("위반 반례에 UDP 포트 규칙의 프로토콜과 포트를 보존한다")
    void violationPreservesUdpProtocol() {
        final PolicyRule rule = new PolicyRule("Rule-UDP", "Subnet-0004", "Subnet-0001", 53);
        rule.setProtocol("udp");

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), List.of(rule));
        final PolicyViolation violation = report.getViolations().get(0);

        assertThat(violation.sampledProtocol()).isEqualTo("udp");
        assertThat(violation.sampledPort()).isEqualTo(53);
        assertThat(violation.sampledPacket()).startsWith("udp ");
    }

    @Test
    @DisplayName("프로토콜 any 는 TCP 단일 프로토콜보다 256배 넓은 패킷 집합이다")
    void protocolDimensionChangesAllowedPacketSet() {
        final PolicyRule tcpRule = new PolicyRule("Rule-TCP", "Subnet-0001", "Subnet-0002", 443);
        final PolicyRule anyRule = new PolicyRule("Rule-ANY", "Subnet-0001", "Subnet-0002", 443);
        anyRule.setProtocol("any");

        final var tcpReport = engine.validate(labSubnets(), List.of(tcpRule));
        final var anyReport = engine.validate(labSubnets(), List.of(anyRule));

        final var tcpCombinations = (java.math.BigInteger) tcpReport.getMetrics().get("allowed_combinations");
        final var anyCombinations = (java.math.BigInteger) anyReport.getMetrics().get("allowed_combinations");
        assertThat(anyCombinations).isEqualTo(tcpCombinations.multiply(java.math.BigInteger.valueOf(256)));
    }

    @Test
    @DisplayName("ICMP 는 포트 없이 유효하고 잘못된 TCP 포트는 위반으로 보고한다")
    void protocolAndPortValidation() {
        final PolicyRule icmp = new PolicyRule(
                "Rule-ICMP", "Subnet-0001", "Subnet-0002", PacketVariables.ANY_PORT);
        icmp.setProtocol("icmp");
        assertThat(engine.validate(labSubnets(), List.of(icmp)).isCompliant()).isTrue();

        final PolicyRule invalidPort = new PolicyRule(
                "Rule-BAD", "Subnet-0001", "Subnet-0002", 70000);
        final var report = engine.validate(labSubnets(), List.of(invalidPort));

        assertThat(report.isCompliant()).isFalse();
        assertThat(report.getViolations()).hasSize(1);
        assertThat(report.getViolations().get(0).severity()).isEqualTo(PolicyViolation.Severity.MINOR);
    }

    @Test
    @DisplayName("등급 차이가 2 이상인 쌍이 forbiddenPairs 에 나온다")
    void forbiddenPairsListsSkippedLevels() {
        final List<String[]> pairs = engine.forbiddenPairs(labSubnets());

        // Confidential(3) <-> Open(1) 양방향
        assertThat(pairs).hasSize(2);
        assertThat(pairs).anySatisfy(pair -> {
            assertThat(pair[0]).isEqualTo("Subnet-0004");
            assertThat(pair[1]).isEqualTo("Subnet-0001");
        });
        assertThat(pairs).anySatisfy(pair -> {
            assertThat(pair[0]).isEqualTo("Subnet-0001");
            assertThat(pair[1]).isEqualTo("Subnet-0004");
        });
    }

    @Test
    @DisplayName("허용 포트가 없으면 MAJOR 위반으로 보고된다")
    void missingPortIsMajorViolation() {
        final List<PolicyRule> rules = List.of(
                new PolicyRule("Rule-0001", "Subnet-0001", "Subnet-0002", PacketVariables.ANY_PORT));

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), rules);

        assertThat(report.isCompliant()).isFalse();
        assertThat(report.getViolations())
                .hasSize(1)
                .allSatisfy(violation -> assertThat(violation.severity())
                        .isEqualTo(PolicyViolation.Severity.MAJOR));
        assertThat(report.getMessages()).anyMatch(message -> message.contains("포트"));
    }

    @Test
    @DisplayName("비활성 규칙은 검증에서 제외된다")
    void disabledRuleIsIgnored() {
        final PolicyRule disabled = new PolicyRule("Rule-0001", "Subnet-0004", "Subnet-0001", 443);
        disabled.setEnabled(false);

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), List.of(disabled));

        assertThat(report.isCompliant()).isTrue();
        assertThat(report.getRuleCount()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 서브넷을 참조하면 MINOR 로 남고 다른 규칙은 계속 검사된다")
    void unknownSubnetIsMinor() {
        final List<PolicyRule> rules = List.of(
                new PolicyRule("Rule-0001", "Subnet-9999", "Subnet-0001", 80),
                new PolicyRule("Rule-0002", "Subnet-0004", "Subnet-0001", 443));

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), rules);

        assertThat(report.getViolations())
                .anySatisfy(violation -> assertThat(violation.severity())
                        .isEqualTo(PolicyViolation.Severity.MINOR))
                .anySatisfy(violation -> assertThat(violation.severity())
                        .isEqualTo(PolicyViolation.Severity.CRITICAL));
    }

    @Test
    @DisplayName("CIDR 문자열을 직접 참조해도 해석된다")
    void cidrReferenceIsResolved() {
        final List<PolicyRule> rules = List.of(
                new PolicyRule("Rule-0001", "10.10.131.0/24", "192.168.0.0/24", 443));

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), rules);

        assertThat(report.isCompliant()).isFalse();
        assertThat(report.getViolatedRuleIds()).containsExactly("Rule-0001");
    }

    @Test
    @DisplayName("BDD 가 규칙 수에 비해 작게 유지된다 (노드 공유 확인)")
    void bddStaysCompact() {
        final List<PolicyRule> rules = List.of(
                new PolicyRule("Rule-0001", "Subnet-0001", "Subnet-0002", 443),
                new PolicyRule("Rule-0002", "Subnet-0001", "Subnet-0002", 80),
                new PolicyRule("Rule-0003", "Subnet-0002", "Subnet-0004", 443));

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), rules);

        assertThat(report.getMetrics()).containsKeys(
                "bdd_variables", "bdd_nodes_allowed", "bdd_nodes_forbidden",
                "bdd_nodes_violating", "allowed_combinations");

        // 변수 80개(2^80 공간)를 다루면서도 노드 수는 규칙 수준에 머물러야 합니다.
        final int allowedNodes = (int) report.getMetrics().get("bdd_nodes_allowed");
        assertThat(allowedNodes).isLessThan(500);
    }

    @Test
    @DisplayName("허용 공간은 주소 대역 크기에 비례한다")
    void allowedCombinationCountReflectsPrefixLength() {
        // /24 x /24 x 포트 1개 = 2^8 x 2^8 x 1
        final List<PolicyRule> rules = List.of(
                new PolicyRule("Rule-0001", "Subnet-0001", "Subnet-0002", 443));

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), rules);
        final var allowed = (java.math.BigInteger) report.getMetrics().get("allowed_combinations");

        assertThat(allowed).isEqualTo(java.math.BigInteger.valueOf(256L * 256L));
    }
}
