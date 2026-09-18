package org.sonar.sonarvalidator_backend.Model.Config;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * 벤더 파서들의 공통 뼈대입니다.
 *
 * <p>Agent 텔레메트리 payload 는 벤더가 달라도 <b>키 이름과 구조가 동일</b>합니다.
 * (수집기 {@code collector::CollectedState} 가 모든 장치 유형에 같은 스냅샷 모양을
 * 만들어 보내기 때문입니다.) 그래서 대부분의 변환 로직을 여기 모으고,
 * 각 벤더 파서는 <b>자기에게 해당하는 부분만 골라</b> 채택합니다.
 *
 * <h2>payload 키</h2>
 * <ul>
 *   <li>{@code nic_status} — 인터페이스/주소 ({@code interfaces[]})</li>
 *   <li>{@code route_status} — 라우팅 ({@code routes[]})</li>
 *   <li>{@code arp_table} — 이웃 ({@code entries[]})</li>
 *   <li>{@code vlan_status} — VLAN ({@code vlans[]})</li>
 *   <li>{@code trunk_status} — 포트 모드/트렁크 ({@code ports[]})</li>
 *   <li>{@code firewall_rules} — nftables ({@code tables[]})</li>
 *   <li>{@code ovs_topology} / {@code ovs_ports} — Open vSwitch L2</li>
 * </ul>
 */
public abstract class AbstractDeviceConfigParser implements DeviceConfigParser {

    /**
     * 새 중립 설정을 만들고 공통 헤더(이름/제품/커널/형식)를 채웁니다.
     *
     * @param  hostname Agent 식별자
     * @param  product  제품명
     * @param  payload  텔레메트리 payload (null 허용)
     * @return 비어 있는 중립 설정 (헤더만 채워짐)
     */
    protected NeutralDeviceConfig newConfig(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = new NeutralDeviceConfig();
        config.setHostname(hostname);
        config.setProduct(product);
        config.setVendor(vendorName());
        config.setFormat(format());
        config.setKernel(JsonReader.text(payload, "kernel"));
        return config;
    }

    /**
     * 이 파서의 벤더 표시 이름입니다.
     *
     * @return 벤더 이름
     */
    protected abstract String vendorName();

    // ------------------------------------------------------------------
    //  공통 변환 조각
    // ------------------------------------------------------------------

    /**
     * {@code nic_status} 를 인터페이스 목록으로 변환합니다.
     *
     * @param config  채울 설정
     * @param payload 텔레메트리 payload
     */
    protected void applyNicStatus(NeutralDeviceConfig config, JsonNode payload) {
        for (final JsonNode iface : JsonReader.objects(JsonReader.at(payload, "nic_status"), "interfaces")) {
            final String name = JsonReader.text(iface, "name");
            final NeutralDeviceConfig.InterfaceConfig target = config.interfaceOrCreate(name);
            if (target == null) {
                continue;
            }
            target.setMtu(JsonReader.integer(iface, "mtu"));
            target.setMacAddress(JsonReader.text(iface, "mac"));
            target.setOperState(JsonReader.text(iface, "state"));
            target.setParent(JsonReader.text(iface, "parent"));
            target.getFlags().clear();
            target.getFlags().addAll(JsonReader.textList(iface, "flags"));

            for (final JsonNode address : JsonReader.objects(iface, "addresses")) {
                final String value = JsonReader.text(address, "address");
                if (value == null || value.isBlank()) {
                    continue;
                }
                final Integer prefixLength = JsonReader.integer(address, "prefix_len");
                final String cidr = (prefixLength == null) ? value : value + "/" + prefixLength;
                if (!target.getAddresses().contains(cidr)) {
                    target.getAddresses().add(cidr);
                }
            }
        }
    }

    /**
     * {@code route_status} 를 라우팅 목록으로 변환합니다.
     *
     * <p>Linux 계열은 {@code destination}/{@code via} 를, FRR·Cisco 는
     * {@code prefix}/{@code next_hop} 을 씁니다. 두 표기를 모두 흡수합니다.
     *
     * @param config  채울 설정
     * @param payload 텔레메트리 payload
     */
    protected void applyRouteStatus(NeutralDeviceConfig config, JsonNode payload) {
        for (final JsonNode route : JsonReader.objects(JsonReader.at(payload, "route_status"), "routes")) {
            final NeutralDeviceConfig.RouteConfig target = new NeutralDeviceConfig.RouteConfig();

            String prefix = JsonReader.text(route, "prefix");
            if (prefix == null) {
                prefix = JsonReader.text(route, "destination");
            }
            target.setPrefix(prefix);

            String nextHop = JsonReader.text(route, "next_hop");
            if (nextHop == null) {
                nextHop = JsonReader.text(route, "via");
            }
            target.setNextHop(nextHop);

            target.setNextHopInterface(JsonReader.text(route, "interface_name"));
            target.setProtocol(JsonReader.text(route, "protocol"));

            final Integer metric = JsonReader.integer(route, "metric");
            target.setMetric(metric == null ? null : metric.longValue());

            target.setSelected(JsonReader.bool(route, "selected"));

            // 기본 경로 판정: 목적지가 0.0.0.0/0 이거나 is_default 플래그가 참.
            final Boolean explicitDefault = JsonReader.bool(route, "is_default");
            target.setDefaultRoute(Boolean.TRUE.equals(explicitDefault)
                    || "0.0.0.0/0".equals(prefix)
                    || "default".equals(prefix));

            config.getRoutes().add(target);
        }
    }

    /**
     * {@code arp_table} 을 이웃 목록으로 변환합니다.
     *
     * @param config  채울 설정
     * @param payload 텔레메트리 payload
     */
    protected void applyArpTable(NeutralDeviceConfig config, JsonNode payload) {
        for (final JsonNode entry : JsonReader.objects(JsonReader.at(payload, "arp_table"), "entries")) {
            final NeutralDeviceConfig.ArpEntry target = new NeutralDeviceConfig.ArpEntry();
            target.setAddress(JsonReader.text(entry, "address"));
            target.setMac(JsonReader.text(entry, "mac"));
            target.setState(JsonReader.text(entry, "state"));

            String iface = JsonReader.text(entry, "interface");
            if (iface == null) {
                // Arista 는 interfaces 배열로 복수 인터페이스를 보고합니다.
                final List<String> interfaces = JsonReader.textList(entry, "interfaces");
                iface = interfaces.isEmpty() ? null : String.join(",", interfaces);
            }
            target.setInterfaceName(iface);
            config.getArpEntries().add(target);
        }
    }

    /**
     * {@code vlan_status} 를 VLAN 맵으로 변환합니다.
     *
     * @param config  채울 설정
     * @param payload 텔레메트리 payload
     */
    protected void applyVlanStatus(NeutralDeviceConfig config, JsonNode payload) {
        for (final JsonNode vlan : JsonReader.objects(JsonReader.at(payload, "vlan_status"), "vlans")) {
            final Integer vlanId = JsonReader.integer(vlan, "vlan_id");
            final NeutralDeviceConfig.VlanConfig target = config.vlanOrCreate(vlanId);
            if (target == null) {
                continue;
            }
            target.setName(JsonReader.text(vlan, "name"));
            target.setStatus(JsonReader.text(vlan, "status"));
            target.getMembers().clear();
            target.getMembers().addAll(JsonReader.textList(vlan, "ports"));
        }
    }

    /**
     * {@code trunk_status} 를 인터페이스의 L2 속성으로 반영합니다.
     *
     * <p>인터페이스 객체를 새로 만들지 않고 있으면 갱신합니다. (NIC 수집이
     * 먼저 돌아 인터페이스가 이미 등록돼 있는 것이 보통입니다.)
     *
     * @param config  채울 설정
     * @param payload 텔레메트리 payload
     */
    protected void applyTrunkStatus(NeutralDeviceConfig config, JsonNode payload) {
        for (final JsonNode port : JsonReader.objects(JsonReader.at(payload, "trunk_status"), "ports")) {
            final String name = JsonReader.text(port, "name");
            final NeutralDeviceConfig.InterfaceConfig target = config.interfaceOrCreate(name);
            if (target == null) {
                continue;
            }
            target.setAccessVlan(JsonReader.integer(port, "access_vlan"));
            target.getTrunkVlans().clear();
            target.getTrunkVlans().addAll(JsonReader.intList(port, "trunk_vlans"));

            final String mode = JsonReader.text(port, "mode");
            if (mode != null) {
                target.setMode(mode);
            } else {
                target.setMode(target.getTrunkVlans().isEmpty() ? "access" : "trunk");
            }
        }
    }

    /**
     * {@code firewall_rules} (nftables) 를 규칙 문자열 목록으로 펼칩니다.
     *
     * <p>파서는 {@code tables[] → chains[] → rules[]} 구조를 돌려줍니다.
     * 사람이 읽을 수 있는 요약 문자열로 만들어 저장/표시에 쓰기 쉽게 합니다.
     *
     * @param config  채울 설정
     * @param payload 텔레메트리 payload
     */
    protected void applyFirewallRules(NeutralDeviceConfig config, JsonNode payload) {
        final JsonNode firewall = JsonReader.at(payload, "firewall_rules");
        for (final JsonNode table : JsonReader.objects(firewall, "tables")) {
            final String family = JsonReader.text(table, "family");
            final String tableName = JsonReader.text(table, "name");
            final String tableLabel = (family == null ? "inet" : family) + " " + (tableName == null ? "?" : tableName);

            for (final JsonNode chain : JsonReader.objects(table, "chains")) {
                final String chainName = JsonReader.text(chain, "name");
                final String policy = JsonReader.text(chain, "policy");
                if (policy != null) {
                    config.getFirewallRules().add(tableLabel + " chain " + chainName + " policy " + policy);
                }
                for (final JsonNode rule : JsonReader.objects(chain, "rules")) {
                    final String expression = JsonReader.text(rule, "expression");
                    final String action = JsonReader.text(rule, "action");
                    final StringBuilder line = new StringBuilder(tableLabel)
                            .append(" chain ").append(chainName).append(' ');
                    if (expression != null) {
                        line.append(expression).append(' ');
                    }
                    if (action != null) {
                        line.append(action);
                    }
                    config.getFirewallRules().add(line.toString().trim());
                }
            }
        }
    }

    /**
     * 벤더 파서가 공통적으로 하는 일을 순서대로 수행합니다.
     *
     * @param  config  채울 설정
     * @param  payload 텔레메트리 payload
     * @return 채워진 설정
     */
    protected NeutralDeviceConfig applyCommon(NeutralDeviceConfig config, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            config.getWarnings().add("empty telemetry payload");
            return config;
        }
        applyNicStatus(config, payload);
        applyRouteStatus(config, payload);
        applyArpTable(config, payload);
        applyVlanStatus(config, payload);
        applyTrunkStatus(config, payload);
        return config;
    }

    /**
     * payload 가 비어 있는지 확인합니다.
     *
     * @param  payload 텔레메트리 payload
     * @return 비어 있으면 {@code true}
     */
    protected boolean isEmptyPayload(JsonNode payload) {
        return payload == null || payload.isNull() || !payload.isObject() || payload.isEmpty();
    }

    /**
     * 기능 목록을 인터페이스·라우팅·VLAN·트렁크·규칙까지 확장해 돌려줍니다.
     *
     * @return 항목 이름 목록
     */
    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "routes", "arp", "vlans", "trunks", "firewallRules");
    }

    /**
     * 벤더별 형식 이름을 {@code ConfigurationFormat} 값으로 맞춥니다.
     *
     * @return 형식 이름
     */
    @Override
    public String format() {
        return formatName();
    }

    /**
     * 구현체가 돌려줄 {@code ConfigurationFormat} 이름입니다.
     *
     * @return 형식 이름 (예: {@code CISCO_IOS})
     */
    protected abstract String formatName();

    /**
     * 디버그용 요약을 만듭니다.
     *
     * @param  config 중립 설정
     * @return 한 줄 요약
     */
    protected String summarise(NeutralDeviceConfig config) {
        return Map.of(
                "host", String.valueOf(config.getHostname()),
                "format", String.valueOf(config.getFormat())).toString();
    }
}
