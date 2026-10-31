package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

public final class CliQueryStrategies {

    private static final Map<CliVendor, String> VENDOR_DEFAULT = new LinkedHashMap<>();

    private final List<CliQueryStrategy> strategies;

    static {
        // ⚠️ 대상이 지정되지 않았을 때 쓰는 기본 조회입니다.
        //    이전에는 `switch (resolved)` 였고, 벤더가 늘면 여기도 늘어야 했습니다.
        VENDOR_DEFAULT.put(CliVendor.OPEN_VSWITCH, "topology");
        VENDOR_DEFAULT.put(CliVendor.FRR, "route");
        VENDOR_DEFAULT.put(CliVendor.CISCO, "route");
        VENDOR_DEFAULT.put(CliVendor.ARISTA, "vlan");
        VENDOR_DEFAULT.put(CliVendor.NFTABLES, "ruleset");
        VENDOR_DEFAULT.put(CliVendor.LINUX, "nic");
        VENDOR_DEFAULT.put(CliVendor.UNKNOWN, "nic");
    }

    public CliQueryStrategies() {
        final List<CliQueryStrategy> list = new ArrayList<>();
        list.add(new NicBriefQueryStrategy());
        list.add(new NicQueryStrategy());
        list.add(new RouteQueryStrategy());
        list.add(new InterfaceStatusQueryStrategy());
        list.add(new ArpQueryStrategy());
        list.add(new OvsTopologyQueryStrategy());
        list.add(new SwitchVlanQueryStrategy());
        list.add(new SwitchPortQueryStrategy());
        list.add(new RunningConfigQueryStrategy());
        list.add(new FirewallChainQueryStrategy());
        list.add(new FirewallRulesQueryStrategy());
        this.strategies = List.copyOf(list);
    }

    public CliQueryStrategies(List<CliQueryStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public CliQueryStrategy select(String target, CliVendor vendor) {
        final CliVendor resolved = (vendor == null) ? CliVendor.UNKNOWN : vendor;
        final String name = normalize(target);

        if (name != null) {
            final CliQueryStrategy byName = byName(name);
            if (byName != null) {
                return byName;
            }
        }

        // 이름을 못 찾았거나 대상이 비었으면 벤더 기본 조회입니다.
        final String fallback = VENDOR_DEFAULT.getOrDefault(resolved, "nic");
        final CliQueryStrategy byVendor = byName(fallback);
        if (byVendor != null) {
            return byVendor;
        }

        // 여기까지 왔다면 기본 대상 이름을 담당하는 전략이 없습니다(설정 오류).
        // 그래도 예외를 던지지 않습니다 — 수집이 멈추는 것보다 낫습니다.
        return strategies.get(strategies.size() - 1);
    }

    private CliQueryStrategy byName(String name) {
        for (final CliQueryStrategy strategy : strategies) {
            if (strategy.matches(name)) {
                return strategy;
            }
        }
        return null;
    }

    static String normalize(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        return target.trim().toLowerCase(Locale.ROOT);
    }

    public List<CliQueryStrategy> strategies() {
        return strategies;
    }
}
