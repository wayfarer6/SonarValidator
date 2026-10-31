package org.sonar.sonarvalidator_backend.Service.quarantine.vendor.arista.veos;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineContext;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineMethod;
import org.springframework.stereotype.Component;

@Component
public class SwitchQuarantine implements QuarantineMethod {

    @Override
    public boolean supports(DeviceType type) {
        return type == DeviceType.SWITCH;
    }

    @Override
    public Mode mode() {
        return Mode.DEVICE;
    }

    @Override
    public List<String> warnings(QuarantineContext context) {
        // 스위치는 업링크 접두사/트렁크가 제어평면을 나를 수 있습니다.
        // 을(를) 내리면 무관한 VLAN 이 함께 끊길 수 있으므로 알립니다.
        return List.of(
                "스위치의 업링크(trunk) 포트가 내려가면 그 포트를 지나는 "
                        + "모든 VLAN 이 함께 끊깁니다.",
                "제어평면(" + describeManagement(context) + ") 대역이 업링크를 지나면 "
                        + "해제 명령이 도달하지 못할 수 있습니다. Agent 는 관리 대역을 "
                        + "자동으로 제외하지만 콘솔에서 확인하세요.");
    }

    private static String describeManagement(QuarantineContext context) {
        return context.hasManagementPrefix() ? context.managementPrefix() : "관리 대역";
    }
}
