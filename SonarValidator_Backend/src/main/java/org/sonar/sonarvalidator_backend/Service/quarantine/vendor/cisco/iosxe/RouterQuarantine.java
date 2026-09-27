package org.sonar.sonarvalidator_backend.Service.quarantine.vendor.cisco.iosxe;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineContext;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineMethod;
import org.springframework.stereotype.Component;

/**
 * 라우터의 격리 전략입니다.
 *
 * <h2>방식 — 장치 단위(인터페이스 down)</h2>
 * <p>라우터는 Agent 가 붙어 있으므로 관리 경로를 뺀 데이터 인터페이스를
 * 내리는 방식이 유효합니다.
 *
 * <h2>⚠️ 제어평면 경고</h2>
 * <p>라우터는 관리 VRF 와 데이터 VRF 를 함께 들고 있을 수 있습니다.
 * 관리 인터페이스를 내리면 서버로 나가는 길이 사라지므로, Agent 의 관리
 * 경로 제외에 의존하지 말고 콘솔에서 확인하도록 경고합니다.
 */
@Component
public class RouterQuarantine implements QuarantineMethod {

    @Override
    public boolean supports(DeviceType type) {
        return type == DeviceType.ROUTER;
    }

    @Override
    public Mode mode() {
        return Mode.DEVICE;
    }

    @Override
    public List<String> warnings(QuarantineContext context) {
        return List.of(
                "라우터 인터페이스를 내리면 그 인터페이스를 지나는 모든 대역이 "
                        + "함께 끊깁니다.",
                "제어평면(" + (context.hasManagementPrefix() ? context.managementPrefix() : "관리 대역")
                        + ") 인터페이스가 포함되면 해제 명령이 도달하지 못합니다. "
                        + "Agent 는 관리 대역을 자동 제외합니다.");
    }
}