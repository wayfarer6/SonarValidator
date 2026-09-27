package org.sonar.sonarvalidator_backend.Service.quarantine.vendor.arista.veos;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineContext;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineMethod;
import org.springframework.stereotype.Component;

/**
 * 스위치의 격리 전략입니다.
 *
 * <h2>방식 — 장치 단위(인터페이스 down)</h2>
 * <p>스위치는 Agent(OVS/Arista)가 붙어 있으므로 관리 경로를 뺀 데이터
 * 인터페이스를 내리는 방식이 유효합니다. 스위치의 access 포트 하나를
 * 내려도 그 포트에 붙은 장치만 끊기고 트렁크는 살아 있습니다.
 *
 * <h2>⚠️ 제어평면 경고</h2>
 * <p>업링크(트렁크) 포트가 관리 대역을 나르고 있으면 그것을 내리는 순간
 * 해제 명령이 도달할 수 없습니다. 그래서 트렁크/제어평면 관련 경고를
 * 남깁니다.
 */
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

    /**
     * 제어평면 대역을 표시용으로 돌려줍니다.
     *
     * @param context 격리 대상 정보
     * @return 대역 CIDR (미지정이면 안내 문구)
     */
    private static String describeManagement(QuarantineContext context) {
        return context.hasManagementPrefix() ? context.managementPrefix() : "관리 대역";
    }
}