package org.sonar.sonarvalidator_backend.Service.quarantine.vendor.cisco.iosxe;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineContext;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineMethod;
import org.springframework.stereotype.Component;

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
