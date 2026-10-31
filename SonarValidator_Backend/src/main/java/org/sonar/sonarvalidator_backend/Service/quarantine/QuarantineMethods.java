package org.sonar.sonarvalidator_backend.Service.quarantine;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.springframework.stereotype.Component;

@Component
public class QuarantineMethods {

    private static final Logger log = LoggerFactory.getLogger(QuarantineMethods.class);

    private final Map<DeviceType, QuarantineMethod> byType = new EnumMap<>(DeviceType.class);

    private final QuarantineMethod unsupported = new UnsupportedQuarantine();

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

    public java.util.Set<DeviceType> supportedTypes() {
        return java.util.Collections.unmodifiableSet(byType.keySet());
    }

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
