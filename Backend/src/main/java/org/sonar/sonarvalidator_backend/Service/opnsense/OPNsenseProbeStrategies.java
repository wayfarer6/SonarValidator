package org.sonar.sonarvalidator_backend.Service.opnsense;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * OPNsense 진단 대상 이름 → {@link OPNsenseProbeStrategy} 를 찾아 주는
 * <b>전략 선택기</b>입니다.
 *
 * <h2>⚠️ 이 클래스가 대신하는 것</h2>
 * <p>이전에는 {@code OPNsenseController.probe} 안에 대상 {@code switch} 가
 * 있었고, 그래서 HTTP 계층이 "인터페이스/규칙/NAT/별칭" 이라는 도메인
 * 개념을 알고 있었습니다. 지금은 이 목록만 알면 됩니다.
 *
 * <p>새 대상을 추가하려면 구현체 하나를 만들고 {@link #STRATEGIES} 에
 * 넣습니다 — 컨트롤러는 건드리지 않습니다.
 */
@Component
public final class OPNsenseProbeStrategies {

    /**
     * 등록된 전략 목록입니다.
     *
     * <p>순서가 곧 우선순위입니다. 지금은 이름이 모두 다르므로 겹치지 않지만,
     * 나중에 별칭이 생기면 앞에 있는 쪽이 이깁니다.
     *
     * <p>상태가 없으므로 {@code static} 으로 두고 공유합니다. 컨트롤러가
     * 인스턴스를 주입받아 쓰므로 스프링은 이 클래스만 알면 됩니다.
     */
    private static final List<OPNsenseProbeStrategy> STRATEGIES = List.of(
            new ProbeStrategies.Interfaces(),
            new ProbeStrategies.FirewallRules(),
            new ProbeStrategies.NatRules(),
            new ProbeStrategies.Aliases(),
            new ProbeStrategies.ConnectionCheck());

    /**
     * 대상 이름에 맞는 전략을 고릅니다.
     *
     * @param target 대상 이름 (null/빈 값이면 접속 확인)
     * @return 전략 (절대 null 이 아님)
     */
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

    /**
     * 화면이 대상 목록을 그릴 수 있게 요약을 돌려줍니다.
     *
     * <p>하드코딩된 목록을 프론트엔드에 두면 서버가 대상을 늘렸을 때 화면이
     * 뒤처집니다. 서버가 자기 능력을 알려주는 편이 맞습니다.
     *
     * @return 이름·설명 목록
     */
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

    /**
     * 대상 이름을 정규화합니다.
     *
     * @param target 입력 (null 허용)
     * @return 소문자·trim (비었으면 null)
     */
    static String normalize(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        return target.trim().toLowerCase(Locale.ROOT);
    }
}