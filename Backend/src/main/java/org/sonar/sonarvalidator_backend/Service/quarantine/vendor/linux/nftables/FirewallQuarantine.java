package org.sonar.sonarvalidator_backend.Service.quarantine.vendor.linux.nftables;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineContext;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineMethod;
import org.springframework.stereotype.Component;

/**
 * 방화벽의 격리 전략입니다.
 *
 * <h2>⚠️ 방화벽은 장치 단위로 격리하지 않는다</h2>
 * <p>랩의 방화벽은 {@code eth1} 트렁크로 여러 VLAN(VLAN 131/132/133)을
 * <b>동시에</b> 들고 있습니다. 인터페이스를 내리면 격리하려던 대역 하나가
 * 아니라 <b>무관한 존 전체</b>가 함께 끊깁니다.
 *
 * <p>이전 구현은 그래서 방화벽 격리를 <b>아예 거부</b>했습니다. 하지만
 * "망분리 위반 장치를 격리한다" 는 요구를 방화벽에 대해서만 포기하는 것은
 * 맞지 않습니다. 방화벽은 오히려 <b>구역 사이의 집행 지점</b>이라
 * 가장 정확하게 특정 연결만 막을 수 있습니다.
 *
 * <h2>방식 — 서브넷(연결) 단위 차단</h2>
 * <p>그래서 방화벽은 {@link Mode#SUBNET} 입니다. 인터페이스를 내리는 대신
 * <b>특정 서브넷으로 가는 연결만</b> 차단 규칙을 넣습니다.
 * ({@code FirewallPolicy} 가 만드는 nftables 규칙과 같은 계열이며,
 * OPNsense 는 같은 목적을 REST API 로 수행합니다)
 *
 * <h2>⚠️ 대상을 지정해야 한다</h2>
 * <p>"무엇을 막을 것인가" 없이 방화벽을 격리하면 전체 차단이 되어
 * 서비스가 마비됩니다. 그래서 서브넷 CIDR 이 없으면 거부합니다.
 */
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

    /**
     * 서브넷이 지정되지 않았으면 거부 사유를 돌려줍니다.
     *
     * <p>노드 단위 격리는 항상 불가하지만, 서브넷이 있으면
     * {@link Mode#SUBNET} 경로로 수행되므로 이 메서드는 서브넷이 없을 때만
     * "불가" 를 뜻합니다. 호출자는 {@link #mode()} 를 먼저 보고 판단합니다.
     *
     * @return 서브넷 미지정 시 사유
     */
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