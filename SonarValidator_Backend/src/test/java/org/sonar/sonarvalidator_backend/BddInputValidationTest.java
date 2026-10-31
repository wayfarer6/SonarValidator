package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Policy.*;

class BddInputValidationTest {
    private final SegmentationBddEngine engine = new SegmentationBddEngine();

    @Test
    void normalizedIcmpCannotCarryATransportPort() {
        var subnets = List.of(PolicySubnet.of("S", "10.0.0.0/24", ZoneClass.SENSITIVE));
        var rule = new PolicyRule("icmp", "S", "S", 443);
        rule.setProtocol(" icmp ");
        assertThat(engine.validate(subnets, List.of(rule)).isModelValid()).isFalse();
    }

    @Test
    void duplicateActiveRulesAndUnclassifiedSubnetsAreInvalidModels() {
        var subnets = List.of(PolicySubnet.of("S", "10.0.0.0/24", ZoneClass.SENSITIVE));
        var rule = new PolicyRule("duplicate", "S", "S", 443);
        assertThat(engine.validate(subnets, List.of(rule, rule)).isModelValid()).isFalse();
        var unclassified = PolicySubnet.of("unknown", "192.0.2.0/24", null);
        assertThat(engine.validate(List.of(unclassified), List.of()).isModelValid()).isFalse();
    }

    @Test
    void discoveredSubnetsRequireAnExplicitCsoClass() {
        var config = new org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig();
        var iface = new org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig.InterfaceConfig();
        iface.getAddresses().add("10.0.0.1/24");
        config.getInterfaces().put("eth0", iface);
        var subnets = org.sonar.sonarvalidator_backend.View.ProjectView.subnetsFromDevice("device", config);
        assertThat(subnets).singleElement().satisfies(s -> assertThat(s.getZoneClass()).isNull());
        assertThat(engine.validate(subnets, List.of()).isModelValid()).isFalse();
    }

    @Test
    void complianceIsExplicitlyLimitedToThePolicyModel() {
        var report = engine.validate(List.of(), List.of());
        var response = org.sonar.sonarvalidator_backend.Model.dto.ProjectMapper.toValidationResponse(report);
        assertThat(response).containsEntry("analysis_scope", "POLICY_HEADER_SPACE")
                .containsEntry("data_plane_analyzed", false).containsEntry("model_valid", true);
        assertThat(report.getMessages()).anyMatch(m -> m.contains("분석하지 않았습니다"));
    }

    @Test
    void invalidCidrCannotBecomeAnEmptyCompliantPolicy() {
        for (String cidr : List.of("not-a-network", "10.0.0.0/24/garbage", "10.0.0.0/")) {
            var subnets = List.of(PolicySubnet.of("C", cidr, ZoneClass.CONFIDENTIAL),
                    PolicySubnet.of("O", "192.0.2.0/24", ZoneClass.OPEN));
            for (var rules : List.of(List.<PolicyRule>of(), List.of(new PolicyRule("bad", "C", "O", 443)))) {
                var report = engine.validate(subnets, rules);
                assertThat(report.isCompliant()).as(cidr).isFalse();
                assertThat(report.isModelValid()).isFalse();
                assertThat(report.getViolations()).isNotEmpty();
            }
        }
    }

    @Test
    void duplicateSubnetIdsCannotOverwriteClassifications() {
        var subnets = List.of(PolicySubnet.of("same", "10.0.0.0/24", ZoneClass.CONFIDENTIAL),
                PolicySubnet.of("same", "10.1.0.0/24", ZoneClass.SENSITIVE));
        assertThat(engine.validate(subnets, List.of()).isCompliant()).isFalse();
    }

    @Test
    void nullEntriesCannotBeSilentlyIgnoredOrCrashValidation() {
        assertThat(engine.validate(Arrays.asList((PolicySubnet) null), List.of()).isCompliant()).isFalse();
        assertThat(engine.validate(List.of(), Arrays.asList((PolicyRule) null)).isCompliant()).isFalse();
    }

    @Test
    void ambiguousCidrReferenceRequiresSubnetId() {
        var subnets = List.of(PolicySubnet.of("one", "10.0.0.0/24", ZoneClass.SENSITIVE),
                PolicySubnet.of("two", "10.0.0.0/24", ZoneClass.SENSITIVE),
                PolicySubnet.of("other", "192.0.2.0/24", ZoneClass.OPEN));
        assertThat(engine.validate(subnets, List.of(new PolicyRule("ambiguous", "10.0.0.0/24", "other", 443)))
                .isCompliant()).isFalse();
        assertThat(engine.validate(subnets, List.of(new PolicyRule("explicit", "one", "other", 443)))
                .isCompliant()).isTrue();
    }
}
