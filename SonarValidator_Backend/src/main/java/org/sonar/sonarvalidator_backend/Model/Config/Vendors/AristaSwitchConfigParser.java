package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.AbstractDeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * Arista vEOS 스위치의 설정 파서입니다.
 *
 * <h2>수집 경로</h2>
 * <p>Agent 는 FastCli 영속 세션으로 {@code show vlan brief},
 * {@code show ip interface brief}, {@code show ip route},
 * {@code show interfaces switchport}, {@code show arp} 를 실행합니다.
 *
 * <h2>다른 스위치(OVS)와의 차이</h2>
 * <p>Arista 는 <b>L3 를 가질 수 있는</b> 스위치입니다. {@code show ip interface brief}
 * 가 나오므로 인터페이스에 IP 가 붙고, 관리 인터페이스({@code Management1})나
 * {@code Vlan8} 같은 SVI 로 접근합니다.
 *
 * <p>⚠️ <b>라우팅 테이블도 수집합니다.</b> vEOS 는 기본 경로를 가질 수 있고
 * (실측: {@code S 0.0.0.0/0 via 172.18.10.1, Ethernet1}),
 * 그 사실이 빠지면 "이 스위치는 인터넷으로 나가지 않는다" 로 <b>잘못 판정</b>합니다.
 * 기밀망 인터넷 노출 검사와 VLAN 간 연결(대역 간 라우팅)이 모두 이 수집에
 * 기대고 있습니다.
 *
 * <p>VLAN 은 두 곳에서 옵니다.
 * <ul>
 *   <li>{@code show vlan brief} → VLAN 정의와 멤버 포트</li>
 *   <li>{@code show interfaces switchport} → 포트별 모드(access/trunk)와
 *       {@code Access Mode VLAN}, {@code Trunking VLANs}</li>
 * </ul>
 * 두 정보를 합쳐야 "이 포트가 어느 VLAN 인지" 가 완성됩니다.
 */
@Component
public class AristaSwitchConfigParser extends AbstractDeviceConfigParser {

    @Override
    public boolean supports(String productName, String deviceType) {
        return productName != null && productName.contains("Arista");
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = newConfig(hostname, product, payload);
        config.setDeviceType("SWITCH");

        if (isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; Arista config left empty");
            return config;
        }

        applyNicStatus(config, payload);
        // ⚠️ 라우팅 테이블을 빼면 안 됩니다. vEOS 의 기본 경로/직접 연결 경로가
        //    유일한 인터넷 노출·대역 간 연결 근거입니다.
        applyRouteStatus(config, payload);
        applyVlanStatus(config, payload);
        applyTrunkStatus(config, payload);
        applyArpTable(config, payload);

        // 포트가 스위치포트인데 VLAN 정의에 없으면 VLAN 을 만들어 둡니다.
        // `show vlan brief` 에 빈 VLAN(멤버 없음)이 안 나오는 경우를 보완합니다.
        config.getInterfaces().values().forEach(iface -> {
            if (iface.getAccessVlan() == null || iface.getAccessVlan() <= 0) {
                return;
            }
            final NeutralDeviceConfig.VlanConfig vlan = config.vlanOrCreate(iface.getAccessVlan());
            if (vlan != null && !vlan.getMembers().contains(iface.getName())) {
                vlan.getMembers().add(iface.getName());
            }
        });

        final long trunkPorts = config.getInterfaces().values().stream()
                .filter(iface -> "trunk".equals(iface.getMode()))
                .count();
        config.getMetadata().put("trunkPortCount", Long.toString(trunkPorts));
        config.getMetadata().put("vlanCount", Integer.toString(config.getVlans().size()));

        if (config.getVlans().isEmpty()) {
            config.getWarnings().add("no VLANs collected; `show vlan brief` may have failed");
        }
        return config;
    }

    @Override
    protected String vendorName() {
        return "Arista";
    }

    @Override
    protected String formatName() {
        return "ARISTA_vEOS";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "routes", "vlans", "trunks", "arp");
    }
}
