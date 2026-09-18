package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.AbstractDeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * FRR 라우터(Alpine/Debian 호스트)의 설정 파서입니다.
 *
 * <h2>수집 경로와 함정</h2>
 * <p>FRR 은 라우팅 데몬일 뿐이고 실제 호스트는 리눅스(이 랩에서는 Alpine)입니다.
 * Agent 는 제품을 {@code "FRR"} 로 판정해 {@code ip a}, {@code ip route show},
 * {@code ip neigh show} 를 실행합니다.
 *
 * <p>라우팅은 <b>두 가지 형식</b>으로 나옵니다.
 * <ul>
 *   <li>(A) 호스트 커널 {@code ip route show} — 라우트 코드 없음, {@code nhid} 있음</li>
 *   <li>(B) {@code vtysh show ip route} — {@code O>*}, {@code C} 같은 라우트 코드 있음</li>
 * </ul>
 * Agent 는 출력 내용을 보고 문법을 골라 파싱하며, 결과 JSON 은 두 경우 모두
 * {@code routes[]} 로 정규화됩니다. 그래서 이 파서는 형식 차이를 신경 쓰지 않습니다.
 *
 * <h2>관리망(passive) 인터페이스</h2>
 * <p>이 랩의 라우터는 관리망 인터페이스를 OSPF {@code passive} 로 둡니다.
 * 그래서 관리망 대역({@code 172.16.255.0/24})에 대한 경로가 OSPF 로 광고되지
 * 않고 connected 로만 존재합니다. 이는 정상이며 경고 대상이 아닙니다.
 */
@Component
public class FrrRouterConfigParser extends AbstractDeviceConfigParser {

    @Override
    public boolean supports(String productName, String deviceType) {
        return productName != null && productName.contains("FRR");
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = newConfig(hostname, product, payload);
        config.setDeviceType("ROUTER");

        if (isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; FRR config left empty");
            return config;
        }

        applyNicStatus(config, payload);
        applyRouteStatus(config, payload);
        applyArpTable(config, payload);

        // VLAN 서브인터페이스가 있으면 등록합니다.
        // 라우터는 보통 트렁크를 서브인터페이스로 종단합니다.
        // (예: Survillance-Router 의 eth0.111 / eth0.112)
        config.getInterfaces().values().forEach(iface -> {
            final Integer vlanId = subinterfaceVlan(iface.getName());
            if (vlanId == null) {
                return;
            }
            iface.setAccessVlan(vlanId);
            iface.setMode("routed");
            iface.setParent(deriveParent(iface.getName()));
            final NeutralDeviceConfig.VlanConfig vlan = config.vlanOrCreate(vlanId);
            if (vlan != null && !vlan.getMembers().contains(iface.getName())) {
                vlan.getMembers().add(iface.getName());
            }
        });

        // OSPF 를 돌리는 장비이므로 프로토콜 분포를 기록해 두면
        // 이후 "라우팅이 OSPF 인지 static 인지" 검증에 쓸 수 있습니다.
        final long ospfCount = config.getRoutes().stream()
                .filter(route -> "ospf".equalsIgnoreCase(route.getProtocol()))
                .count();
        config.getMetadata().put("ospfRouteCount", Long.toString(ospfCount));
        config.getMetadata().put("routeCount", Integer.toString(config.getRoutes().size()));

        if (config.getRoutes().isEmpty()) {
            config.getWarnings().add("no routes collected; FRR kernel/vtysh output may be empty");
        }
        return config;
    }

    /**
     * {@code eth0.111} → {@code 111}.
     *
     * @param  name 인터페이스 이름
     * @return VLAN ID 또는 null
     */
    private Integer subinterfaceVlan(String name) {
        if (name == null) {
            return null;
        }
        final int dot = name.indexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return null;
        }
        try {
            return Integer.valueOf(name.substring(dot + 1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * {@code eth0.111} → {@code eth0}.
     *
     * @param  name 인터페이스 이름
     * @return 부모 이름
     */
    private String deriveParent(String name) {
        final int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    protected String vendorName() {
        return "FRR";
    }

    @Override
    protected String formatName() {
        return "FRRRouter";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "routes", "arp", "vlans(subinterfaces)");
    }
}
