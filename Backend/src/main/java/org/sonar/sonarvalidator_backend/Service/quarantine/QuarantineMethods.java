package org.sonar.sonarvalidator_backend.Service.quarantine;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.springframework.stereotype.Component;

/**
 * 장치 유형 → 격리 전략을 찾아 주는 <b>전략 선택기</b>입니다.
 *
 * <h2>⚠️ switch 를 대신하는 것</h2>
 * <p>호출부는 {@link #of(DeviceType)} 만 부르고 어떤 클래스가 처리하는지
 * 모릅니다. 새 장치를 추가할 때 이 클래스도 고칠 필요가 없습니다 —
 * {@link QuarantineMethod} 를 구현한 빈을 하나 더 만들면 스프링이
 * 자동으로 목록에 넣습니다. (생성자 주입)
 *
 * <h2>⚠️ 기본 전략은 "격리 불가" 다</h2>
 * <p>알 수 없는 유형을 <b>VM 처럼</b> 격리하면 어떻게 될까요? 관리 경로가
 * 아닌 인터페이스를 내리는데, 그 장치가 무슨 역할인지 모르므로 무관한
 * 네트워크를 끊을 수 있습니다. 그래서 여기서는 <b>모르면 거부</b>합니다 —
 * {@link DevicePolicies} 가 VM 으로 폴백하는 것과 반대입니다.
 * (정책은 틀려도 되돌릴 수 있지만, 격리는 서비스 단절입니다)
 */
@Component
public class QuarantineMethods {

    private static final Logger log = LoggerFactory.getLogger(QuarantineMethods.class);

    /** 유형 → 전략. 생성 시 한 번만 채우고 이후 읽기만 합니다. */
    private final Map<DeviceType, QuarantineMethod> byType = new EnumMap<>(DeviceType.class);

    /** 담당이 없을 때 쓰는 "격리 불가" 전략입니다. */
    private final QuarantineMethod unsupported = new UnsupportedQuarantine();

    /**
     * 스프링이 등록된 모든 전략을 주입합니다.
     *
     * @param strategies 발견된 전략 빈 목록
     */
    public QuarantineMethods(List<QuarantineMethod> strategies) {
        for (final QuarantineMethod strategy : strategies) {
            for (final DeviceType type : DeviceType.values()) {
                if (strategy.supports(type)) {
                    final QuarantineMethod previous = byType.put(type, strategy);
                    if (previous != null) {
                        log.warn("duplicate quarantine strategy for {}: {} overrides {}",
                                type, strategy.getClass().getSimpleName(),
                                previous.getClass().getSimpleName());
                    }
                }
            }
        }
        log.info("quarantine strategies registered: {} (types={})",
                strategies.size(), byType.keySet());
    }

    /**
     * 유형에 맞는 격리 전략을 돌려줍니다.
     *
     * @param type 장치 유형 (null 이면 격리 불가)
     * @return 전략 (절대 null 이 아님)
     */
    public QuarantineMethod of(DeviceType type) {
        if (type == null) {
            return unsupported;
        }
        final QuarantineMethod strategy = byType.get(type);
        if (strategy != null) {
            return strategy;
        }
        log.debug("no quarantine strategy for {}; treating as unsupported", type);
        return unsupported;
    }

    /**
     * 등록된 유형 목록을 돌려줍니다. (진단/테스트용)
     *
     * @return 담당 유형 집합
     */
    public java.util.Set<DeviceType> supportedTypes() {
        return java.util.Collections.unmodifiableSet(byType.keySet());
    }

    /**
     * 담당 전략이 없는 유형에 쓰는 기본 전략입니다.
     *
     * <p>알 수 없는 장치를 "일단 격리" 하면 무관한 네트워크가 끊길 수 있어
     * 안전한 쪽(거부)으로 떨어집니다.
     */
    static final class UnsupportedQuarantine implements QuarantineMethod {

        @Override
        public boolean supports(DeviceType type) {
            // 선택기가 유형별로만 쓰므로 어떤 유형도 직접 담당하지 않습니다.
            return false;
        }

        @Override
        public Mode mode() {
            return Mode.UNSUPPORTED;
        }

        @Override
        public String exclusionReason() {
            return "장치 유형을 알 수 없어 격리하지 않았습니다. "
                    + "무관한 네트워크가 끊길 수 있으므로 유형을 먼저 지정하세요.";
        }
    }
}