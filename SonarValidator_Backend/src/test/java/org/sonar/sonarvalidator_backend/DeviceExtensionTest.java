package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Config.*;
import org.sonar.sonarvalidator_backend.Model.Config.Vendors.*;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Policy.strategy.*;
import org.sonar.sonarvalidator_backend.Service.DeviceConfigService;
import org.sonar.sonarvalidator_backend.extension.StrategyRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class DeviceExtensionTest {
    record Parser(String format, int priority) implements DeviceConfigParser {
        public boolean supports(String product, String type) { return "custom".equals(product); }
        public NeutralDeviceConfig parse(String host, String product, JsonNode payload) {
            var config = new NeutralDeviceConfig(); config.setHostname(host); config.setFormat(format); return config;
        }
    }
    record Policy(DeviceType type, String product, int priority) implements DevicePolicy {
        public boolean supports(DeviceType candidate) { return type == candidate; }
        public boolean supports(DeviceType candidate, String vendor, String observedProduct) {
            return supports(candidate) && (product == null || product.equals(observedProduct));
        }
        public String defaultVendor() { return "custom"; }
        public String defaultProduct() { return product; }
        public ObjectNode defaultRule() { return JsonNodeFactory.instance.objectNode(); }
        public ObjectNode declarationRule(PolicyBuildContext context) { return defaultRule(); }
        public ObjectNode enforcementRule(PolicyBuildContext context) { return defaultRule(); }
    }
    @Test
    void aParserIsAddedByRegistrationWithoutABaseClass() {
        var service = new DeviceConfigService(List.of(new Parser("generic", 0), new Parser("specific", 100)));
        assertThat(service.parse("node", "custom", JsonNodeFactory.instance.objectNode()).getFormat()).isEqualTo("specific");
    }
    @Test
    void specificDeviceParserWinsOverGenericLinuxRegardlessOfRegistrationOrder() {
        for (var parsers : List.of(List.<DeviceConfigParser>of(new LinuxVmConfigParser(), new FrrRouterConfigParser()),
                List.<DeviceConfigParser>of(new FrrRouterConfigParser(), new LinuxVmConfigParser()))) {
            assertThat(new DeviceConfigService(parsers).parserFor("FRR Linux", "ROUTER").orElseThrow())
                    .isInstanceOf(FrrRouterConfigParser.class);
        }
    }
    @Test
    void ambiguousParsersCannotSilentlySelectADevice() {
        var service = new DeviceConfigService(List.of(new Parser("one", 0), new Parser("two", 0)));
        assertThat(service.parserFor("custom", "ROUTER")).isEmpty();
    }
    @Test
    void vendorPolicyUsesObservedProductAndRejectsEqualPriorityAmbiguity() {
        var vm = new Policy(DeviceType.VM, null, 0);
        var generic = new Policy(DeviceType.ROUTER, null, 0);
        var specialized = new Policy(DeviceType.ROUTER, "custom", 100);
        var registry = new DevicePolicies(List.of(vm, specialized, generic));
        assertThat(registry.of(DeviceType.ROUTER, "vendor", "custom")).isSameAs(specialized);
        assertThat(registry.of(DeviceType.ROUTER, "vendor", "other")).isSameAs(generic);
        var ambiguous = new DevicePolicies(List.of(vm, generic, new Policy(DeviceType.ROUTER, null, 0)));
        assertThatThrownBy(() -> ambiguous.of(DeviceType.ROUTER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DevicePolicies(List.of(vm)).of(DeviceType.SWITCH))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test
    void ambiguityAtALowerPriorityDoesNotHideAnExplicitOverride() {
        var registry = new StrategyRegistry<>(List.of(0, 0, 100), Integer::intValue);
        assertThat(registry.select(value -> true)).contains(100);
    }
    @Test
    void routedVlanMappingRejectsInvalidIdsWithoutAParentImplementation() {
        var config = new NeutralDeviceConfig();
        config.interfaceOrCreate("eth0.100"); config.interfaceOrCreate("eth0.5000");
        new TelemetryFields().applyRoutedVlans(config);
        assertThat(config.getInterfaces().get("eth0.100").getAccessVlan()).isEqualTo(100);
        assertThat(config.getInterfaces().get("eth0.5000").getAccessVlan()).isNull();
    }
}
