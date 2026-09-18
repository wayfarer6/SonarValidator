package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.AbstractDeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.JsonReader;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * Open vSwitch 스위치의 설정 파서입니다. (GNS3 컨테이너 스위치)
 *
 * <h2>수집 경로</h2>
 * <p>Agent 는 {@code ovs-vsctl show} 로 브리지/포트/인터페이스 계층을,
 * {@code ovs-vsctl list port} 를 대안으로, {@code ip a} 로 포트의 MAC/상태를
 * 모읍니다. 이 스위치는 <b>L2 전용</b>이라 관리 IP 도 라우팅도 없습니다.
 *
 * <h2>변환 규칙</h2>
 * <p>Agent 는 OVS 결과를 다른 장비와 같은 모양으로 정규화해 보냅니다.
 * <ul>
 *   <li>포트의 {@code tag} → {@code vlan_status.vlans[].vlan_id} + {@code members}</li>
 *   <li>포트의 {@code tag}/{@code trunks} → {@code trunk_status.ports[]}
 *       ({@code access_vlan} / {@code trunk_vlans} / {@code mode})</li>
 * </ul>
 * 그래서 여기서는 공통 변환만으로 충분하고, 추가로 {@code ovs_topology} 의
 * 브리지 이름을 기록해 어느 브리지의 포트인지 남깁니다.
 */
@Component
public class OpenVSwitchConfigParser extends AbstractDeviceConfigParser {

    @Override
    public boolean supports(String productName, String deviceType) {
        if (productName == null) {
            return false;
        }
        return productName.contains("OpenVSwitch") || productName.contains("Open vSwitch");
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = newConfig(hostname, product, payload);
        config.setDeviceType("SWITCH");

        if (isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; OVS config left empty");
            return config;
        }

        applyNicStatus(config, payload);
        applyVlanStatus(config, payload);
        applyTrunkStatus(config, payload);

        // 브리지를 기록합니다. OVS 는 브리지(br0) 아래 포트가 붙는 구조라
        // 여러 브리지를 쓰는 구성에서 포트 소속을 구분할 수 있어야 합니다.
        final JsonNode topology = JsonReader.at(payload, "ovs_topology");
        for (final JsonNode bridge : JsonReader.objects(topology, "bridges")) {
            final String bridgeName = JsonReader.text(bridge, "name");
            if (bridgeName != null && !config.getBridges().contains(bridgeName)) {
                config.getBridges().add(bridgeName);
            }
        }

        // 스위치는 L3 가 없습니다. 라우팅/ARP 미수집은 결함이 아니라 정상이므로
        // (경고가 아니라 정보로만) 남깁니다.
        if (config.getInterfaces().isEmpty()) {
            config.getWarnings().add("ip a produced no interfaces for this switch");
        }
        return config;
    }

    @Override
    protected String vendorName() {
        return "Open vSwitch";
    }

    @Override
    protected String formatName() {
        return "OpenvSwitch";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "vlans", "trunks");
    }
}
