package org.sonar.sonarvalidator_backend.Policy.strategy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 장치 유형 → 정책 전략을 찾아 주는 <b>전략 선택기</b>입니다.
 *
 * <h2>⚠️ switch 를 대신하는 것</h2>
 * <p>호출부는 {@link #of(DeviceType)} 만 부르고, 어떤 클래스가 처리하는지
 * 모릅니다. 새 장치를 추가할 때 이 클래스도 고칠 필요가 없습니다 —
 * {@link DevicePolicyStrategy} 를 구현한 빈을 하나 더 만들면 스프링이
 * 자동으로 목록에 넣습니다. (생성자 주입으로 전략 목록을 받음)
 *
 * <h2>⚠️ 기본 전략을 명시적으로 고른다</h2>
 * <p>알 수 없는 유형이면 {@link DeviceType#VM} 전략을 씁니다. 이전 switch 의
 * {@code default} 와 같은 동작이지만, <b>어느 전략으로 떨어졌는지 로그로
 * 남깁니다.</b> 조용히 VM 정책이 나가면 "왜 라우터에 VM 정책이?" 를
 * 추적할 단서가 없습니다.
 */
@Component
public class DevicePolicyStrategies {

    private static final Logger log = LoggerFactory.getLogger(DevicePolicyStrategies.class);

    /** 유형 → 전략. 생성 시 한 번만 채우고 이후 읽기만 합니다. */
    private final Map<DeviceType, DevicePolicyStrategy> byType = new EnumMap<>(DeviceType.class);

    /** 알 수 없는 유형이 떨어질 기본 전략입니다. */
    private final DevicePolicyStrategy fallback;

    /**
     * 스프링이 등록된 모든 전략을 주입합니다.
     *
     * @param strategies 발견된 전략 빈 목록
     */
    public DevicePolicyStrategies(List<DevicePolicyStrategy> strategies) {
        for (final DevicePolicyStrategy strategy : strategies) {
            // supports() 로 유형을 물어보는 이유: 전략이 자기 담당을 선언하므로
            // 이 클래스가 유형 목록을 알 필요가 없습니다.
            for (final DeviceType type : DeviceType.values()) {
                if (strategy.supports(type)) {
                    final DevicePolicyStrategy previous = byType.put(type, strategy);
                    if (previous != null) {
                        // 같은 유형을 두 전략이 담당하면 하나가 조용히 사라집니다.
                        // 조용한 무시는 "왜 내 전략이 안 먹지" 를 만들므로 경고합니다.
                        log.warn("duplicate strategy for {}: {} overrides {}",
                                type, strategy.getClass().getSimpleName(),
                                previous.getClass().getSimpleName());
                    }
                }
            }
        }

        this.fallback = byType.get(DeviceType.VM);
        if (this.fallback == null) {
            // VM 전략이 없으면 폴백이 불가능합니다. 기동 시점에 드러내는 편이
            // 운영 중 NPE 보다 낫습니다.
            throw new IllegalStateException(
                    "no strategy registered for VM; fallback is impossible");
        }
        log.info("device policy strategies registered: {} (types={})",
                strategies.size(), byType.keySet());
    }

    /**
     * 유형에 맞는 전략을 돌려줍니다.
     *
     * @param type 장치 유형 (null 이면 VM 전략)
     * @return 전략 (절대 null 이 아님)
     */
    public DevicePolicyStrategy of(DeviceType type) {
        if (type == null) {
            return fallback;
        }
        final DevicePolicyStrategy strategy = byType.get(type);
        if (strategy != null) {
            return strategy;
        }
        log.debug("no strategy for {}; falling back to VM strategy", type);
        return fallback;
    }

    /**
     * 등록된 유형 목록을 돌려줍니다. (진단/테스트용)
     *
     * @return 담당 유형 집합
     */
    public java.util.Set<DeviceType> supportedTypes() {
        return java.util.Collections.unmodifiableSet(byType.keySet());
    }
}