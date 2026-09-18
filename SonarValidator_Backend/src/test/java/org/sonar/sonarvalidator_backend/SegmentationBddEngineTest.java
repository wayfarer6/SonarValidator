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
    @DisplayName("반례 패킷이 위반 대역 안에서 추출된다")
    void violationIncludesCounterExamplePacket() {
        final List<PolicyRule> rules = List.of(
                new PolicyRule("Rule-0001", "Subnet-0004", "Subnet-0001", 443));

        final SegmentationBddEngine.Report report = engine.validate(labSubnets(), rules);
        final PolicyViolation violation = report.getViolations().get(0);

        // 출발지는 Confidential 대역(10.10.131.0/24), 목적지는 Open 대역(192.168.0.0/24)
        assertThat(violation.sampledSourceIp()).startsWith("10.10.131.");
        assertThat(violation.sampledTargetIp()).startsWith("192.168.0.");
        assertThat(violation.sampledPort()).isEqualTo(443);
        assertThat(violation.sampledPacket()).contains("->").contains(":443");
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
