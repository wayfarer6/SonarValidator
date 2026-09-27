package org.sonar.sonarvalidator_backend.Service.quarantine;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;

/**
 * 장치 유형 하나의 <b>격리 방법</b>을 담는 전략입니다.
 *
 * <h2>⚠️ 왜 격리를 모듈화하는가 — 벤더마다 명령이 다르다</h2>
 * <p>기존에는 {@code QuarantineService} 가 "관리 경로를 뺀 인터페이스를
 * 내린다" 는 <b>한 가지 방법</b>만 알고 있었습니다. 그런데 실제 격리 방법은
 * 장치에 따라 다릅니다.
 *
 * <ul>
 *   <li><b>스위치/라우터/VM</b> — Agent 가 인터페이스를 내림
 *       ({@code ip link set down}, {@code interface shutdown})</li>
 *   <li><b>방화벽</b> — 인터페이스를 내리면 트렁크에 붙은 모든 VLAN 이
 *       함께 죽으므로, 대신 <b>특정 서브넷 연결만</b> 차단 규칙을 넣음</li>
 *   <li><b>OPNsense</b> — Agent 없이 REST API 로 규칙을 넣음</li>
 * </ul>
 *
 * <p>이 지식을 서비스의 {@code if/switch} 로 두면 새 벤더를 추가할 때마다
 * 서비스를 고쳐야 하고, 한 곳을 빠뜨리면 조용히 잘못된 방법이 적용됩니다.
 * 대신 <b>유형 하나 = 구현체 하나</b>로 두고, 이 인터페이스를 구현한 빈을
 * 추가하면 나머지는 자동으로 연결되게 합니다. (개방-폐쇄 원칙)
 *
 * <h2>⚠️ 격리 방식은 세 갈래다</h2>
 * <p>{@link Mode} 가 그 구분을 나타냅니다. 호출자(격리 서비스)는 방식에
 * 따라 다른 경로를 태웁니다.
 * <ul>
 *   <li>{@link Mode#DEVICE} — Agent 에 명령을 보내 인터페이스를 내림</li>
 *   <li>{@link Mode#SUBNET} — 서버가 정책/규칙으로 특정 연결만 차단</li>
 *   <li>{@link Mode#UNSUPPORTED} — 이 장치는 격리할 수 없음 (사유 포함)</li>
 * </ul>
 *
 * <h2>⚠️ 예외를 던지지 않는다</h2>
 * <p>격리 판정 실패가 운영자의 요청 자체를 막으면 안 됩니다. 판단할 수
 * 없으면 {@link Mode#UNSUPPORTED} 와 사유를 돌려주고, 호출자가 응답에 남깁니다.
 */
public interface QuarantineMethod {

    /**
     * 격리 방식입니다.
     */
    enum Mode {
        /** Agent 가 인터페이스를 내리는 장치 단위 격리. */
        DEVICE,
        /** 서버가 특정 서브넷 연결만 차단하는 격리. (방화벽/트렁크 장치) */
        SUBNET,
        /** 이 장치는 격리 대상이 아님. (사유 필수) */
        UNSUPPORTED
    }

    /**
     * 이 전략이 담당하는 장치 유형인지 확인합니다.
     *
     * @param type 장치 유형 (null 이면 false)
     * @return 담당하면 true
     */
    boolean supports(DeviceType type);

    /**
     * 이 장치의 격리 방식입니다.
     *
     * @return 방식 (null 이 아님)
     */
    Mode mode();

    /**
     * 격리가 불가한 이유를 사람이 읽는 문장으로 돌려줍니다.
     *
     * <p>{@link Mode#UNSUPPORTED} 일 때만 의미 있는 값이 있습니다.
     *
     * @return 사유 (격리 가능하면 null)
     */
    default String exclusionReason() {
        return null;
    }

    /**
     * 이 장치에서 격리하면 <b>함께 끊기는</b> 위험 요소를 알려줍니다.
     *
     * <h2>⚠️ 왜 필요한가 — 제어평면 상실 경고</h2>
     * <p>트렁크로 여러 VLAN 을 들고 있는 장치를 격리하면 무관한 존이 함께
     * 끊깁니다. 특히 <b>제어평면(관리망) 대역</b>이 걸린 인터페이스를
     * 내리면 서버로 나가는 길이 사라져 해제 명령조차 도달하지 못합니다.
     *
     * <p>그래서 전략이 "내가 격리되면 무엇이 위험한가" 를 스스로 알려줍니다.
     * 호출자는 이 경고를 응답에 실어 운영자가 위험을 인지하게 합니다.
     *
     * @param context 격리 대상 정보
     * @return 경고 문장 목록 (없으면 빈 목록)
     */
    default List<String> warnings(QuarantineContext context) {
        return List.of();
    }
}