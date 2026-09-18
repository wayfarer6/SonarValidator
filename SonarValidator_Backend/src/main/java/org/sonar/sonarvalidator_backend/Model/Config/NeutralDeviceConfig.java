package org.sonar.sonarvalidator_backend.Model.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 벤더와 무관한 중립 장비 설정입니다. (Batfish 의 {@code Configuration} 대응)
 *
 * <h2>역할</h2>
 * <p>각 벤더 파서({@link DeviceConfigParser})가 자기 CLI 출력을 해석해 이 구조를
 * 채웁니다. 이후 단계 — 정책 검증, 존 간 도달성 분석, DB 저장, 프론트엔드 표시 —
 * 는 <b>벤더를 전혀 모른 채</b> 이 구조만 사용합니다. 그래서 새 벤더를 추가할 때
 * 파서 하나만 늘리면 되고 분석 코드는 건드리지 않습니다.
 *
 * <h2>설계 원칙</h2>
 * <ul>
 *   <li>모든 컬렉션은 비어 있을 수 있지만 {@code null} 은 아닙니다.</li>
 *   <li>벤더 고유 표현은 여기 들어오지 않습니다. (예: {@code eth1.131} 대신
 *       인터페이스 + VLAN ID 로 분해)</li>
 *   <li>이 클래스는 저장 DTO 가 아니라 <b>분석용 표현</b>입니다. JPA 매핑은
 *       {@code Configuration} 엔티티가 담당합니다.</li>
 * </ul>
 */
public class NeutralDeviceConfig {

    /** 인터페이스 한 개의 L2/L3 속성. */
    public static class InterfaceConfig {
        private String name;
        private String description;
        private String macAddress;
        private Integer mtu;
        private String adminState;
        private String operState;
        private final List<String> addresses = new ArrayList<>();
        private Integer accessVlan;
        private final List<Integer> trunkVlans = new ArrayList<>();
        private String mode;
        private String parent;
        private final List<String> flags = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getMacAddress() {
            return macAddress;
        }

        public void setMacAddress(String macAddress) {
            this.macAddress = macAddress;
        }

        public Integer getMtu() {
            return mtu;
        }

        public void setMtu(Integer mtu) {
            this.mtu = mtu;
        }

        public String getAdminState() {
            return adminState;
        }

        public void setAdminState(String adminState) {
            this.adminState = adminState;
        }

        public String getOperState() {
            return operState;
        }

        public void setOperState(String operState) {
            this.operState = operState;
        }

        /** CIDR 표기 주소 목록 (예: {@code 10.99.10.1/24}). */
        public List<String> getAddresses() {
            return addresses;
        }

        public Integer getAccessVlan() {
            return accessVlan;
        }

        public void setAccessVlan(Integer accessVlan) {
            this.accessVlan = accessVlan;
        }

        /** 트렁크로 허용한 VLAN ID 목록. */
        public List<Integer> getTrunkVlans() {
            return trunkVlans;
        }

        /** {@code access} / {@code trunk} / {@code routed}. */
        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        /** 서브인터페이스면 부모 인터페이스 이름 (예: {@code eth1.131} → {@code eth1}). */
        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }

        public List<String> getFlags() {
            return flags;
        }
    }

    /** 라우팅 테이블 한 줄. */
    public static class RouteConfig {
        private String protocol;
        private String prefix;
        private String nextHop;
        private String nextHopInterface;
        private Long metric;
        private Boolean selected;
        private Boolean defaultRoute;

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getNextHop() {
            return nextHop;
        }

        public void setNextHop(String nextHop) {
            this.nextHop = nextHop;
        }

        public String getNextHopInterface() {
            return nextHopInterface;
        }

        public void setNextHopInterface(String nextHopInterface) {
            this.nextHopInterface = nextHopInterface;
        }

        public Long getMetric() {
            return metric;
        }

        public void setMetric(Long metric) {
            this.metric = metric;
        }

        public Boolean getSelected() {
            return selected;
        }

        public void setSelected(Boolean selected) {
            this.selected = selected;
        }

        public Boolean getDefaultRoute() {
            return defaultRoute;
        }

        public void setDefaultRoute(Boolean defaultRoute) {
            this.defaultRoute = defaultRoute;
        }
    }

    /** VLAN 한 개. */
    public static class VlanConfig {
        private Integer vlanId;
        private String name;
        private String status;
        private final List<String> members = new ArrayList<>();

        public Integer getVlanId() {
            return vlanId;
        }

        public void setVlanId(Integer vlanId) {
            this.vlanId = vlanId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public List<String> getMembers() {
            return members;
        }
    }

    /** ARP/이웃 항목. */
    public static class ArpEntry {
        private String address;
        private String mac;
        private String interfaceName;
        private String state;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getMac() {
            return mac;
        }

        public void setMac(String mac) {
            this.mac = mac;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public void setInterfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }

    // ------------------------------------------------------------------
    //  노드 수준 정보
    // ------------------------------------------------------------------

    private String hostname;
    private String vendor;
    private String product;
    private String deviceType;
    private String kernel;
    private String format;

    private final Map<String, InterfaceConfig> interfaces = new LinkedHashMap<>();
    private final List<RouteConfig> routes = new ArrayList<>();
    private final Map<Integer, VlanConfig> vlans = new LinkedHashMap<>();
    private final List<ArpEntry> arpEntries = new ArrayList<>();
    private final List<String> firewallRules = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /** 브리지 이름 등 벤더 고유의 부가 정보. 분석에는 쓰이지 않고 추적/표시용입니다. */
    private final List<String> bridges = new ArrayList<>();

    /**
     * 파서가 남기는 자유 형식 메모. (예: 어떤 명령을 수집하지 못했는지)
     * 분석 로직은 이 값을 신뢰하지 않습니다.
     */
    private final Map<String, String> metadata = new LinkedHashMap<>();

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getKernel() {
        return kernel;
    }

    public void setKernel(String kernel) {
        this.kernel = kernel;
    }

    /** 설정 형식 식별자 (예: {@code CISCO_IOS}, {@code OpenvSwitch}). */
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Map<String, InterfaceConfig> getInterfaces() {
        return interfaces;
    }

    public List<RouteConfig> getRoutes() {
        return routes;
    }

    public Map<Integer, VlanConfig> getVlans() {
        return vlans;
    }

    public List<ArpEntry> getArpEntries() {
        return arpEntries;
    }

    public List<String> getFirewallRules() {
        return firewallRules;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /** 브리지 이름 등 벤더 고유 부가 정보 (OVS 전용). */
    public List<String> getBridges() {
        return bridges;
    }

    /** 파서가 남기는 자유 형식 메모. */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 이름으로 인터페이스를 찾고, 없으면 만들어 등록합니다.
     * 벤더 파서가 NIC → 트렁크 순서로 두 번 방문할 때 같은 객체를 쓰기 위함입니다.
     *
     * @param  name 인터페이스 이름 (null/빈 문자열이면 null 반환)
     * @return 인터페이스 설정 (호출자가 채웁니다)
     */
    public InterfaceConfig interfaceOrCreate(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return interfaces.computeIfAbsent(name, key -> {
            final InterfaceConfig created = new InterfaceConfig();
            created.setName(key);
            return created;
        });
    }

    /**
     * VLAN 을 찾고, 없으면 만들어 등록합니다.
     *
     * @param  vlanId VLAN ID (null 이면 null 반환)
     * @return VLAN 설정 (호출자가 채웁니다)
     */
    public VlanConfig vlanOrCreate(Integer vlanId) {
        if (vlanId == null) {
            return null;
        }
        return vlans.computeIfAbsent(vlanId, key -> {
            final VlanConfig created = new VlanConfig();
            created.setVlanId(key);
            return created;
        });
    }

    /**
     * 관리 IP 목록을 인터페이스에서 뽑아냅니다. (도달성 분석·표시용)
     *
     * @return {@code 인터페이스명 → 주소} 맵
     */
    public Map<String, List<String>> addressesByInterface() {
        final Map<String, List<String>> result = new LinkedHashMap<>();
        interfaces.forEach((name, config) -> result.put(name, List.copyOf(config.getAddresses())));
        return result;
    }

    /**
     * 이 설정이 비어 있는지(수집 실패) 확인합니다.
     *
     * @return 인터페이스·라우팅·VLAN 이 모두 비어 있으면 {@code true}
     */
    public boolean isEmpty() {
        return interfaces.isEmpty() && routes.isEmpty() && vlans.isEmpty();
    }
}
