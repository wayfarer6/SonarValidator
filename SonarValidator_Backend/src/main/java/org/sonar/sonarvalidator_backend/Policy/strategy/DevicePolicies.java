package org.sonar.sonarvalidator_backend.Policy.strategy;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.extension.StrategyRegistry;
import org.springframework.stereotype.Component;

@Component
public final class DevicePolicies {
    private final StrategyRegistry<DevicePolicy> registry;
    private final Set<DeviceType> types;

    public DevicePolicies(List<DevicePolicy> strategies) {
        registry = new StrategyRegistry<>(strategies, DevicePolicy::priority);
        final var supported = EnumSet.noneOf(DeviceType.class);
        for (final var type : DeviceType.values()) {
            if (strategies.stream().anyMatch(strategy -> strategy.supports(type))) supported.add(type);
        }
        types = Set.copyOf(supported);
        if (!types.contains(DeviceType.VM)) throw new IllegalStateException("no strategy registered for VM");
    }

    public DevicePolicy of(DeviceType type) { return of(type, null, null); }

    public DevicePolicy of(DeviceType type, String vendor, String product) {
        final DeviceType resolved = type == null ? DeviceType.VM : type;
        return registry.select(strategy -> strategy.supports(resolved, vendor, product))
                .orElseThrow(() -> new IllegalStateException("no device policy for " + resolved + "/" + product));
    }

    public Set<DeviceType> supportedTypes() { return types; }
}
