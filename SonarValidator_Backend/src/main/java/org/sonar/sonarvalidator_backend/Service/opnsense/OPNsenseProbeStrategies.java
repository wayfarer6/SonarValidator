package org.sonar.sonarvalidator_backend.Service.opnsense;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public final class OPNsenseProbeStrategies {

    private static final List<OPNsenseProbeStrategy> STRATEGIES = List.of(
            new ProbeStrategies.Interfaces(),
            new ProbeStrategies.FirewallRules(),
            new ProbeStrategies.NatRules(),
            new ProbeStrategies.Aliases(),
            new ProbeStrategies.ConnectionCheck());

    public OPNsenseProbeStrategy select(String target) {
        final String name = normalize(target);
        if (name != null) {
            for (final OPNsenseProbeStrategy strategy : STRATEGIES) {
                if (strategy.matches(name)) {
                    return strategy;
                }
            }
        }
        // 모르는 대상 → 접속 확인. 진단에서는 "일단 붙는가" 가 첫 질문입니다.
        return STRATEGIES.get(STRATEGIES.size() - 1);
    }

    public List<Map<String, Object>> describe() {
        final List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (final OPNsenseProbeStrategy strategy : STRATEGIES) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("target", strategy.name());
            entry.put("description", strategy.description());
            entry.put("default", strategy == STRATEGIES.get(STRATEGIES.size() - 1));
            list.add(entry);
        }
        return list;
    }

    static String normalize(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        return target.trim().toLowerCase(Locale.ROOT);
    }
}
