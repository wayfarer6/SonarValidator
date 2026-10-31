package org.sonar.sonarvalidator_backend.Service;

import java.util.Locale;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DeviceTypeResolver {

    private static final Logger log = LoggerFactory.getLogger(DeviceTypeResolver.class);

    private final ExpectedAgentRepository expectedAgentRepository;

    public DeviceTypeResolver(ExpectedAgentRepository expectedAgentRepository) {
        this.expectedAgentRepository = expectedAgentRepository;
    }

    public DeviceType resolve(String agentId, NeutralDeviceConfig config) {
        final DeviceType registered = registeredType(agentId);
        if (registered != null) {
            return registered;
        }
        return resolveWithoutRepository(agentId, config);
    }

    public static DeviceType resolveWithoutRepository(String agentId, NeutralDeviceConfig config) {
        // 텔레메트리가 "내 유형" 을 명시했으면 그 값을 우선합니다.
        final DeviceType reported = DeviceType.fromString(config == null ? null : config.getDeviceType());
        if (reported != null) {
            return reported;
        }
        // 최후에는 식별자 관례. (Gateway-Router, GNS3.Firewall 처럼 끝 토큰)
        return DeviceType.inferFromDeviceId(agentId);
    }

    private DeviceType registeredType(String agentId) {
        if (expectedAgentRepository == null || agentId == null || agentId.isBlank()) {
            return null;
        }
        try {
            // 대소문자 관용: 화면은 VDI-1, 텔레메트리는 vdi-1 을 씁니다.
            final var found = expectedAgentRepository.findByAgentId(agentId.trim());
            if (found.isPresent()) {
                final ExpectedAgent agent = found.get();
                final DeviceType type = DeviceType.fromString(agent.getDeviceType());
                if (type != null) {
                    return type;
                }
                // 등록은 되어 있는데 유형이 비었으면 식별자로 추론합니다.
                return DeviceType.inferFromDeviceId(agent.getAgentId());
            }
        } catch (RuntimeException ex) {
            // 유형 조회 실패가 격리/정책 요청 자체를 막으면 안 됩니다.
            log.warn("expected-agent lookup failed for agent={}: {}", agentId, ex.getMessage());
        }
        return null;
    }

    @Deprecated
    public static boolean isIsolatable(DeviceType type) {
        return type != DeviceType.FIREWALL;
    }

    @Deprecated
    public static String exclusionReason(DeviceType type) {
        if (type != DeviceType.FIREWALL) {
            return null;
        }
        return "방화벽은 장치 단위로 격리할 수 없습니다 — 트렁크(eth1)에 연결된 모든 VLAN 이 "
                + "함께 끊깁니다. 대신 격리할 서브넷(target_cidr)을 지정하면 그 연결만 차단합니다.";
    }

    public static String nameOf(DeviceType type) {
        return type == null ? "UNKNOWN" : type.name().toUpperCase(Locale.ROOT);
    }
}
