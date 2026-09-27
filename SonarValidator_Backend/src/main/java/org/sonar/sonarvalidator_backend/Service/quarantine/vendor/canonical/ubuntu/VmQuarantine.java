package org.sonar.sonarvalidator_backend.Service.quarantine.vendor.canonical.ubuntu;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineContext;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineMethod;
import org.springframework.stereotype.Component;

/**
 * VM(호스트/데이터평면) 격리 전략입니다.
 *
 * <h2>방식 — 장치 단위(인터페이스 down)</h2>
 * <p>VM 은 NIC 하나가 곧 연결이므로 인터페이스를 내리는 방식이 정확합니다.
 *
 * <h2>⚠️ Agent 가 없을 수 있다</h2>
 * <p>데이터평면 전용 VM 은 관리 IP 가 없는 경우가 있습니다
 * (RVI 랩의 Ubuntu VM). Agent 가 없으면 격리 명령을 보낼 대상이 없으므로
 * 격리 서비스가 "전달 실패" 를 정직하게 보고해야 합니다.
 */
@Component
public class VmQuarantine implements QuarantineMethod {

    @Override
    public boolean supports(DeviceType type) {
        return type == DeviceType.VM;
    }

    @Override
    public Mode mode() {
        return Mode.DEVICE;
    }

    @Override
    public List<String> warnings(QuarantineContext context) {
        if (!context.hasAgent()) {
            return List.of(
                    "이 장치에는 Agent 가 없습니다. 격리 명령을 전달할 경로가 "
                            + "없으므로 상태만 기록되고 실제 차단은 되지 않습니다.");
        }
        return List.of();
    }
}