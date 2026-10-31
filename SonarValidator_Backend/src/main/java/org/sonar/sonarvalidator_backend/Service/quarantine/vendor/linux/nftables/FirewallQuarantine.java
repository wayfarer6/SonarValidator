package org.sonar.sonarvalidator_backend.Service.quarantine.vendor.linux.nftables;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineContext;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineMethod;
import org.springframework.stereotype.Component;

@Component
public class FirewallQuarantine implements QuarantineMethod {

    @Override
    public boolean supports(DeviceType type) {
        return type == DeviceType.FIREWALL;
    }

    @Override
    public Mode mode() {
        return Mode.SUBNET;
    }

    @Override
    public String exclusionReason() {
        return "방화벽은 인터페이스 단위로 격리할 수 없습니다 — 트렁크(eth1)에 연결된 "
                + "모든 VLAN 이 함께 끊깁니다. 대신 격리할 서브넷(target_cidr)을 지정하면 "
                + "그 연결만 차단합니다.";
    }

    @Override
    public List<String> warnings(QuarantineContext context) {
        final String target = context.hasSubnet() ? context.subnetCidr() : "(대상 미지정)";
        return List.of(
                "방화벽은 인터페이스가 아니라 규칙으로 격리합니다. "
                        + "대상 대역 " + target + " 으로 가는 연결만 차단됩니다.",
                "제어평면(" + (context.hasManagementPrefix()
                        ? context.managementPrefix() : "관리 대역") + ") 은 차단 대상에서 "
                        + "제외되어야 합니다. 차단 규칙이 관리 대역을 포함하면 "
                        + "해제 경로가 끊깁니다.");
    }
}
