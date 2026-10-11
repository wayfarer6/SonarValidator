package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

/**
 * 조회 대상 이름 → {@link CliQueryStrategy} 를 찾아 주는 <b>전략 선택기</b>입니다.
 *
 * <h2>⚠️ 이 클래스가 대신하는 두 개의 {0}switch{1}</h2>
 * <p>이전 구조에서는 대상 이름이 <b>세 곳</b>에 흩어져 있었습니다.
 *
 * <pre>
 *   CliOutputParser.parseQueryOutput   switch (target) { case "route" -&gt; ... }
 *   CliOutputParser.parseQueryOutput   switch (vendor) { case FRR, CISCO -&gt; ... }
 *   CliIngestService.contractKeyOf     switch (vendor) { ... } + TARGET_KEYS 표
 * </pre>
 *
 * <p>세 곳이 같은 사실을 세 가지 형태로 담고 있었으므로, 대상 하나를
 * 추가하면 <b>세 곳을 모두</b> 고쳐야 했습니다. 지금은 전략 하나를 추가하면
 * 이름·계약 키·파싱·벤더 폴백이 한 번에 따라옵니다.
 *
 * <h2>판단</h2>
 * <p>정규화된 이름이 {@code matches} 하는 <b>첫</b> 전략을 씁니다. 이름이 없으면
 * 벤더 기본 전략으로 폴백합니다.
 *
 * <h2>⚠️ 이름 하나 = 전략 하나 (불변식)</h2>
 * <p>같은 이름을 두 전략이 담당하면 <b>등록 순서가 곧 정확성</b>이 되고,
 * 새 전략을 끼워 넣는 사람이 그 사실을 모르면 조용히 깨집니다. 그래서
 * 계약을 평평하게 유지합니다 — 내용으로 갈리는 대상({@code brief})은
 * 전략 <b>안에서</b> 원문을 보고 고릅니다.
 * {@code CliQueryStrategiesTest} 가 이 불변식을 검증합니다.
 *
 * <h2>⚠️ 기본값도 실패할 수 있다</h2>
 * <p>벤더 기본 전략이 없으면(예: 새 벤더 추가) <b>Linux 기본</b>으로
 * 폴백합니다. 예외를 던지면 텔레메트리 수집이 멈추므로, 최소한 주소라도
 * 읽어 보고한다는 판단입니다.
 */
public final class CliQueryStrategies {

    /** 벤더 → 그 벤더의 기본 대상 이름. */
    private static final Map<CliVendor, String> VENDOR_DEFAULT = new LinkedHashMap<>();

    /** 모든 전략. 순서가 곧 우선순위입니다. */
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

    /**
     * 기본 전략 집합으로 선택기를 만듭니다.
     *
     * <p>순서가 우선순위이므로 {@code brief}(내용 분기)를 앞에 둡니다.
     * 뒤에 두면 이름이 같은 다른 전략이 먼저 잡을 수 있습니다.
     */
    public CliQueryStrategies() {
        final List<CliQueryStrategy> list = new ArrayList<>();
        // ⚠️ 이름이 겹치는 전략을 두지 않습니다.
        //    `brief` 는 두 문법을 받지만 NicBriefQueryStrategy 하나가
        //    원문을 보고 고릅니다. 두 전략으로 나누면 <b>등록 순서가 곧
        //    정확성</b>이 되어, 새 전략을 끼워 넣는 사람이 그 사실을
        //    모르면 조용히 깨집니다.
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

    /**
     * 테스트/확장용 생성자입니다.
     *
     * @param strategies 순서대로 시도할 전략 목록
     */
    public CliQueryStrategies(List<CliQueryStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    /**
     * 대상 이름에 맞는 전략을 고릅니다.
     *
     * <h2>⚠️ 원문을 받지 않는다</h2>
     * <p>선택 시점에 원문을 보면 <b>등록 순서가 곧 정확성</b>이 됩니다.
     * 원문 모양으로 문법이 갈리는 대상({@code brief})은 전략이 자기 안에서
     * {@code parse} 할 때 고릅니다. 그래야 선택기는 이름만 알면 됩니다.
     *
     * @param target 조회 대상 (null/빈 문자열이면 벤더 기본)
     * @param vendor 판별된 벤더 (null 이면 {@link CliVendor#UNKNOWN})
     * @return 전략 (절대 null 이 아님)
     */
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

    /**
     * 이름이 맞는 첫 전략을 찾습니다.
     *
     * <p>내용 판단은 없습니다 — 이름 하나가 전략 하나이므로 여기서 끝납니다.
     *
     * @param name 정규화된 이름
     * @return 전략 (없으면 null)
     */
    private CliQueryStrategy byName(String name) {
        for (final CliQueryStrategy strategy : strategies) {
            if (strategy.matches(name)) {
                return strategy;
            }
        }
        return null;
    }

    /**
     * 대상 이름을 정규화합니다.
     *
     * <p>정규화를 <b>선택기에서 한 번만</b> 하는 이유: 구현체마다 다른 방식으로
     * 다듬으면 {@code "Route "} 같은 입력이 어느 구현체에도 안 걸려 조용히
     * 기본 조회로 폴백됩니다.
     *
     * @param target 입력 (null 허용)
     * @return 소문자·trim 된 이름 (비었으면 null)
     */
    static String normalize(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        return target.trim().toLowerCase(Locale.ROOT);
    }

    /** @return 등록된 전략 목록 (진단/테스트용) */
    public List<CliQueryStrategy> strategies() {
        return strategies;
    }
}