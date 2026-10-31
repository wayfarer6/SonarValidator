package org.sonar.sonarvalidator_backend.Service.quarantine.vendor.canonical.ubuntu;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineContext;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineMethod;
import org.springframework.stereotype.Component;

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
