package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.AbstractDeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * Linux VM(Ubuntu 등)의 설정 파서입니다. (TOD-Cam / UAV / VDI-1 / VDI-2 / ATICS 등)
 *
 * <h2>수집 경로</h2>
 * <p>Agent 는 {@code ip a}, {@code ip -br addr show}, {@code ip route show},
 * {@code ip neigh show} 로 수집합니다. 즉 호스트 자체가 리눅스인 장비입니다.
 *
 * <h2>왜 FRR 과 분리했는가</h2>
 * <p>같은 명령을 쓰지만 의미가 다릅니다. 이 파서는 <b>일반 호스트</b>(단일
 * 기본 경로 + 커널 라우팅)를 다루고, {@link FrrRouterConfigParser} 는
 * <b>OSPF 를 돌리는 라우터</b>(ospf 프로토콜 라우트, 관리망 passive 인터페이스)를
 * 다룹니다. 중립 설정으로 합쳐지므로 이후 분석 코드는 둘을 구분할 필요가 없습니다.
 */
@Component
public class LinuxVmConfigParser extends AbstractDeviceConfigParser {

    @Override
    public boolean supports(String productName, String deviceType) {
        if (productName == null) {
            return false;
        }
        return productName.contains("Ubuntu") || productName.contains("Linux");
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = newConfig(hostname, product, payload);
        // Agent 가 보고한 장치 유형을 그대로 존중합니다. (VM / FIREWALL 등)
        config.setDeviceType("VM");

        if (isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; Linux VM config left empty");
            return config;
        }

        applyNicStatus(config, payload);
        applyRouteStatus(config, payload);
        applyArpTable(config, payload);

        // 리눅스 호스트는 VLAN 서브인터페이스(eth0.100)를 쓸 수 있습니다.
        // 있으면 VLAN 으로 등록해 L2 대조에 포함시킵니다.
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

        if (config.getRoutes().isEmpty()) {
            config.getWarnings().add("no routes collected (ip route show returned nothing)");
        }
        return config;
    }

    /**
     * {@code eth0.100} → {@code 100}. 서브인터페이스가 아니면 null.
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
     * {@code eth0.100} → {@code eth0}.
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
        return "Linux";
    }

    @Override
    protected String formatName() {
        return "LinuxVM";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "routes", "arp", "vlans(subinterfaces)");
    }
}
