package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.AbstractDeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * Alpine + nftables 방화벽의 설정 파서입니다.
 *
 * <h2>수집 경로</h2>
 * <p>Agent 는 방화벽도 호스트가 Alpine 리눅스임을 이용해 {@code ip a},
 * {@code ip -br addr show}, {@code ip route show}, {@code ip neigh show} 로
 * NIC·라우팅·이웃을 모으고, {@code nft list ruleset} 으로 필터 규칙을 모읍니다.
 *
 * <h2>이 노드의 VLAN</h2>
 * <p>이 랩에서 방화벽은 <b>VLAN 종단 장치</b>입니다. {@code eth1.131} 같은
 * 서브인터페이스가 존 게이트웨이(10.10.131.1/24 등)를 가집니다. Agent 는
 * 커널 {@code ip a} 출력을 그대로 보내므로, 서브인터페이스는
 * {@code eth1.131} 이라는 <b>이름 그대로</b> 들어오고 {@code parent} 도 함께 옵니다.
 *
 * <p>여기서 {@code eth1.131} 을 VLAN 131 + 물리 포트 eth1 로 분해해 둡니다.
 * 그래야 L2 토폴로지 분석이 벤더 표기({@code eth1.131})에 의존하지 않습니다.
 */
@Component
public class AlpineFirewallConfigParser extends AbstractDeviceConfigParser {

    @Override
    public boolean supports(String productName, String deviceType) {
        if (productName == null) {
            return false;
        }
        return productName.contains("nftables")
                || productName.contains("Firewall")
                || (productName.contains("nft") && !productName.contains("OpenVSwitch"));
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = newConfig(hostname, product, payload);
        config.setDeviceType("FIREWALL");

        if (isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; firewall config left empty");
            return config;
        }

        applyNicStatus(config, payload);
        applyRouteStatus(config, payload);
        applyArpTable(config, payload);
        applyFirewallRules(config, payload);

        // 서브인터페이스 이름에서 (물리 포트, VLAN ID) 를 뽑아 인터페이스에 반영합니다.
        // 이 랩에서 방화벽이 VLAN 종단을 하므로 존 게이트웨이 매핑에 필요합니다.
        config.getInterfaces().values().forEach(iface -> {
            final int[] vlan = parseSubinterfaceVlan(iface.getName());
            if (vlan == null) {
                return;
            }
            iface.setAccessVlan(vlan[1]);
            iface.setMode("routed");
            if (iface.getParent() == null) {
                iface.setParent(deriveParent(iface.getName()));
            }
            // VLAN 종단 포트도 VLAN 목록에 등록해 L2/L3 대조가 가능하게 합니다.
            final NeutralDeviceConfig.VlanConfig vlanConfig = config.vlanOrCreate(vlan[1]);
            if (vlanConfig != null && !vlanConfig.getMembers().contains(iface.getName())) {
                vlanConfig.getMembers().add(iface.getName());
            }
        });

        if (config.getFirewallRules().isEmpty()) {
            config.getWarnings().add("nft ruleset not collected or empty");
        }
        return config;
    }

    /**
     * {@code eth1.131} → {@code [1, 131]} 로 분해합니다. 서브인터페이스가 아니면 null.
     *
     * @param  name 인터페이스 이름
     * @return {@code [부모 인덱스, VLAN ID]} 또는 null
     */
    private int[] parseSubinterfaceVlan(String name) {
        if (name == null) {
            return null;
        }
        final int dot = name.indexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return null;
        }
        try {
            return new int[] {0, Integer.parseInt(name.substring(dot + 1))};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * {@code eth1.131} → {@code eth1} 로 부모 이름을 만듭니다.
     *
     * @param  name 인터페이스 이름
     * @return 부모 이름 (서브인터페이스가 아니면 원래 이름)
     */
    private String deriveParent(String name) {
        final int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    protected String vendorName() {
        return "Alpine";
    }

    @Override
    protected String formatName() {
        return "AlpineFirewall";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "routes", "arp", "firewallRules", "vlans(subinterfaces)");
    }
}
