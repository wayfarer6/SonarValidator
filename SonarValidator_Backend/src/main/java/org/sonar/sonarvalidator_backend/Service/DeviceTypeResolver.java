package org.sonar.sonarvalidator_backend.Service;

import java.util.Locale;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent 식별자로 장치 유형을 알아냅니다.
 *
 * <h2>⚠️ 왜 별도 클래스인가</h2>
 * <p>장치 유형을 필요로 하는 곳이 늘었습니다 — 정책 분기, 방화벽 격리 제외,
 * 수집 요약. 각자 추론 로직을 복사하면 <b>같은 장치가 화면마다 다른 유형</b>으로
 * 보입니다. (실측: 라우터 5대가 화면에는 라우터, 정책은 VM)
 *
 * <h2>판단 순서</h2>
 * <ol>
 *   <li><b>기대 목록(ExpectedAgent)</b> — 운영자가 "이건 방화벽" 이라고
 *       등록한 값. 가장 신뢰할 수 있습니다.</li>
 *   <li><b>텔레메트리 설정</b> — Prober 가 보고한 {@code device_type}.
 *       운영자 등록이 없어도 장치가 자기를 압니다.</li>
 *   <li><b>식별자 추론</b> — 이름의 끝 토큰. 위 둘 다 없을 때만.</li>
 * </ol>
 *
 * <p>순서를 뒤집으면 문제가 생깁니다. 식별자 추론은 어디까지나 <b>관례</b>이고,
 * 운영자가 명시적으로 등록한 적이 있습니다 — {@code edge-box-1} 처럼 이름에
 * 유형이 없는 장치가 그래서 정상 동작합니다.
 *
 * <h2>⚠️ 결과는 캐시하지 않는다</h2>
 * <p>운영자가 등록을 바꾸거나 장치가 다시 붙으면 유형이 달라질 수 있습니다.
 * 이 조회는 요청당 몇 번뿐이라 비용이 문제되지 않고,
 * 잘못된 캐시는 "왜 방화벽이 갑자기 격리되나" 를 만듭니다.
 */
@Service
public class DeviceTypeResolver {

    private static final Logger log = LoggerFactory.getLogger(DeviceTypeResolver.class);

    /**
     * 운영자 등록 저장소입니다.
     *
     * <p>{@code null} 을 허용하는 이유: 격리 서비스 단위 테스트가 저장소 없이
     * 서비스를 만들 수 있어야 합니다. 없으면 다음 판단 근거로 넘어갑니다.
     */
    private final ExpectedAgentRepository expectedAgentRepository;

    /**
     * @param expectedAgentRepository 기대 목록 저장소 (테스트에서는 null 가능)
     */
    public DeviceTypeResolver(ExpectedAgentRepository expectedAgentRepository) {
        this.expectedAgentRepository = expectedAgentRepository;
    }

    /**
     * Agent 의 장치 유형을 알아냅니다. (저장소 조회 포함)
     *
     * @param agentId Agent 식별자
     * @param config  최근 텔레메트리 설정 (없으면 null)
     * @return 장치 유형 (절대 null 이 아님 — 최후에는 {@link DeviceType#VM})
     */
    public DeviceType resolve(String agentId, NeutralDeviceConfig config) {
        final DeviceType registered = registeredType(agentId);
        if (registered != null) {
            return registered;
        }
        return resolveWithoutRepository(agentId, config);
    }

    /**
     * 저장소를 보지 않고 유형을 알아냅니다.
     *
     * <p>격리 서비스가 이미 조회한 값을 재사용할 때 쓰는 경로입니다.
     *
     * @param agentId Agent 식별자
     * @param config  최근 텔레메트리 설정 (없으면 null)
     * @return 장치 유형 (절대 null 이 아님)
     */
    public static DeviceType resolveWithoutRepository(String agentId, NeutralDeviceConfig config) {
        // 텔레메트리가 "내 유형" 을 명시했으면 그 값을 우선합니다.
        final DeviceType reported = DeviceType.fromString(config == null ? null : config.getDeviceType());
        if (reported != null) {
            return reported;
        }
        // 최후에는 식별자 관례. (Gateway-Router, GNS3.Firewall 처럼 끝 토큰)
        return DeviceType.inferFromDeviceId(agentId);
    }

    /**
     * 운영자가 등록한 장치 유형을 조회합니다.
     *
     * @param agentId Agent 식별자
     * @return 등록된 유형 (없으면 null)
     */
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

    /**
     * 이 유형이 <b>장치 단위 격리</b> 대상이 될 수 있는지 판단합니다.
     *
     * <h2>⚠️ 방화벽은 장치 단위로 격리하지 않는다</h2>
     * <p>랩의 방화벽은 {@code eth1} 트렁크로 여러 VLAN(VLAN 131/132/133)을
     * 동시에 들고 있습니다. 방화벽 인터페이스를 내리면 그 VLAN 에 붙은
     * <b>모든 존</b>이 함께 끊깁니다 — 격리하려던 한 대가 아니라
     * <b>무관한 네트워크 전체</b>가 내려갑니다.
     *
     * <h2>⚠️ DB Design v1.5 — 판단의 소유자를 전략으로 옮겼다</h2>
     * <p>이전에는 이 클래스가 "무엇을 격리할 수 있나" 를 판단했습니다. 그런데
     * 격리 방법이 장치마다 달라지면서(스위치/라우터/VM = 인터페이스 down,
     * 방화벽 = 서브넷 차단) 판단 근거가 유형별로 흩어졌습니다. 지금은
     * {@code Service.quarantine.QuarantineMethod} 구현체가 그 지식을 소유하고,
     * 이 클래스는 <b>유형 판별만</b> 합니다. (중복 판단 제거)
     *
     * <p>남겨 둔 이유: 유형 문자열만 가진 호출부(예: 문서 생성, 진단)가
     * 전략 객체 없이 빠르게 물어볼 수 있는 편의 함수입니다.
     *
     * @param type 장치 유형 (null 이면 격리 가능으로 봄)
     * @return 장치 단위 격리가 가능하면 true
     * @deprecated 격리 판단은 {@code QuarantineMethods.of(type)} 로 위임하세요.
     */
    @Deprecated
    public static boolean isIsolatable(DeviceType type) {
        return type != DeviceType.FIREWALL;
    }

    /**
     * 장치 단위 격리가 불가한 이유를 사람이 읽는 문장으로 돌려줍니다.
     *
     * <p>⚠️ DB Design v1.5 — 사유 문구의 소유자는 이제
     * {@code Service.quarantine.FirewallQuarantine#exclusionReason()} 입니다.
     * 여기 문구는 유형 문자열만 가진 호출부를 위한 <b>간이 안내</b>입니다.
     *
     * @param type 장치 유형
     * @return 사유 (장치 단위 격리가 가능하면 null)
     * @deprecated 격리 사유는 {@code QuarantineMethods.of(type).exclusionReason()} 을 쓰세요.
     */
    @Deprecated
    public static String exclusionReason(DeviceType type) {
        if (type != DeviceType.FIREWALL) {
            return null;
        }
        return "방화벽은 장치 단위로 격리할 수 없습니다 — 트렁크(eth1)에 연결된 모든 VLAN 이 "
                + "함께 끊깁니다. 대신 격리할 서브넷(target_cidr)을 지정하면 그 연결만 차단합니다.";
    }

    /**
     * 유형 이름을 대문자 약어로 돌려줍니다. (로그/응답용)
     *
     * @param type 장치 유형 (null 허용)
     * @return 약어 (null 이면 {@code "UNKNOWN"})
     */
    public static String nameOf(DeviceType type) {
        return type == null ? "UNKNOWN" : type.name().toUpperCase(Locale.ROOT);
    }
}