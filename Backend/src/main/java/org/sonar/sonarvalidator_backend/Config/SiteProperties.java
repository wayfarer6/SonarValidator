package org.sonar.sonarvalidator_backend.Config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 랩/사이트 환경에 따라 달라지는 값을 모은 설정입니다.
 *
 * <h2>⚠️ 왜 이 클래스가 필요한가 — 하드코딩이 확장을 막는다</h2>
 * <p>이전에는 아래 값들이 코드 상수나 기본값으로 박혀 있었습니다.
 *
 * <ul>
 *   <li>제어평면(관리망) 대역 {@code 172.16.255.0/24}
 *       — 격리 경고({@code QuarantineService}), Prober 관리 경로 제외</li>
 *   <li>OVS 업링크 {@code eth0} / 브리지 {@code br0}
 *       — 스위치 정책({@code SwitchPolicy})</li>
 *   <li>nftables 전용 테이블 {@code sonar}
 *       — 방화벽 정책({@code FirewallPolicy})</li>
 * </ul>
 *
 * <p>랩이 바뀌면(대역·인터페이스 이름·테이블 이름) 코드를 고쳐야 하고,
 * 제품을 추가할수록 그 지점이 늘어납니다. 그래서 <b>환경에 따라 달라지는
 * 것은 전부 여기로</b> 모읍니다.
 *
 * <h2>⚠️ 프로젝트별 지정이 우선한다</h2>
 * <p>제어평면 대역은 요구사항상 <b>프로젝트마다 지정</b>할 수 있어야 합니다.
 * 여기 값은 <b>전역 기본값</b>이고, 프로젝트가 자기 값을 가지면 그것을
 * 우선합니다. (해석 순서는 {@code SiteProperties#managementPrefixFor} 참고)
 *
 * <h2>프로퍼티 예 (application.yml)</h2>
 * <pre>
 * sonar:
 *   site:
 *     management-prefix: 172.16.255.0/24
 *     switch:
 *       uplink-port: eth0
 *       bridge-name: br0
 *     firewall:
 *       table-name: sonar
 * </pre>
 */
@ConfigurationProperties(prefix = "sonar.site")
public class SiteProperties {

    /**
     * 제어평면(관리망) 대역 CIDR입니다. <b>전역 기본값</b>입니다.
     *
     * <p>이 대역은 격리에서 <b>절대 차단 대상이 되어서는 안 됩니다.</b>
     * 차단하면 서버로 나가는 길이 사라져 해제 명령조차 도달하지 못합니다.
     * 그래서 격리 경고가 이 값을 인용하고, Agent 는 이 대역의 인터페이스를
     * 내리지 않습니다.
     *
     * <p>기본값을 두는 이유: 랩이 하나뿐인 개발 환경에서도 격리 경고가
     * 의미 있게 나오게 하기 위함입니다. 운영에서는 반드시 지정하세요.
     */
    private String managementPrefix = "172.16.255.0/24";

    /** 스위치 관련 기본값. */
    private SwitchDefaults switchDefaults = new SwitchDefaults();

    /** 방화벽 관련 기본값. */
    private FirewallDefaults firewallDefaults = new FirewallDefaults();

    /** 라우터 관련 기본값. */
    private RouterDefaults routerDefaults = new RouterDefaults();

    /** VM 관련 기본값. */
    private VmDefaults vmDefaults = new VmDefaults();

    /**
     * 스위치(OVS) 관련 기본값입니다.
     *
     * <h2>⚠️ 왜 설정인가</h2>
     * <p>업링크 포트 이름과 브리지 이름은 <b>제품·배포마다 다릅니다.</b>
     * OVS 는 {@code br0}, Arista 는 {@code Bridge}, Cisco 는 다른 관례를
     * 씁니다. 상수로 박아 두면 제품을 추가할 때마다 코드를 고쳐야 합니다.
     */
    public static class SwitchDefaults {

        /**
         * ACL 플로우를 넣을 업링크(trunk) 포트 이름입니다.
         *
         * <p>access 포트에 넣으면 그 VLAN 안에서만 매칭되어 존 간 이동을
         * 보지 못합니다. 그래서 트렁크를 지정합니다.
         */
        private String uplinkPort = "eth0";

        /** OVS 브리지 이름입니다. */
        private String bridgeName = "br0";

        /**
         * 폴백 선언에 쓰는 기본 포트 이름입니다.
         *
         * <p>제품·배포마다 포트 표기가 다릅니다 — OVS 는 {@code eth1},
         * Arista 는 {@code Ethernet 1}, Cisco 는 {@code GigabitEthernet0/0/1}.
         * 그래서 코드 상수로 두지 않고 설정으로 뺐습니다.
         */
        private String defaultPort = "Ethernet 1";

        public String getUplinkPort() {
            return uplinkPort;
        }

        public void setUplinkPort(String uplinkPort) {
            this.uplinkPort = uplinkPort;
        }

        public String getBridgeName() {
            return bridgeName;
        }

        public void setBridgeName(String bridgeName) {
            this.bridgeName = bridgeName;
        }

        public String getDefaultPort() {
            return defaultPort;
        }

        public void setDefaultPort(String defaultPort) {
            this.defaultPort = defaultPort;
        }
    }

    /**
     * 방화벽(nftables) 관련 기본값입니다.
     *
     * <h2>⚠️ 기본 {@code filter} 테이블을 쓰지 않는 이유</h2>
     * <p>기존 방화벽 규칙을 덮어쓰면 랩 전체가 끊길 수 있습니다. 그래서
     * 별도 테이블을 만들고 그 안에서만 규칙을 다룹니다. 이름을 설정으로 둔
     * 이유는, 같은 장비에 여러 도구가 붙을 때 이름 충돌을 피하기 위함입니다.
     */
    public static class FirewallDefaults {

        /** 방화벽 정책이 쓰는 nftables 테이블 이름입니다. */
        private String tableName = "sonar";

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }

    /**
     * 라우터 관련 기본값입니다.
     *
     * <h2>⚠️ 왜 설정인가</h2>
     * <p>폴백 선언에 쓰는 인터페이스 이름은 <b>배포·모델마다 다릅니다</b> —
     * {@code GigabitEthernet0/0/1}, {@code Ethernet1}, {@code ge-0/0/0}.
     * 상수로 두면 모델을 추가할 때마다 코드를 고쳐야 합니다.
     */
    public static class RouterDefaults {

        /** 폴백 선언에 쓰는 기본 인터페이스 이름입니다. */
        private String defaultInterface = "GigabitEthernet0/0/1";

        public String getDefaultInterface() {
            return defaultInterface;
        }

        public void setDefaultInterface(String defaultInterface) {
            this.defaultInterface = defaultInterface;
        }
    }

    /**
     * VM 관련 기본값입니다.
     *
     * <h2>⚠️ 인터페이스 이름은 여기 두지 않는다</h2>
     * <p>VM 의 실제 NIC 이름({@code ens33}/{@code eth0}/{@code ens3})은
     * 서버가 알 수 없어 <b>설정으로도 확정할 수 없습니다.</b> 그래서 VM 정책은
     * {@code VmPolicy.PRIMARY_INTERFACE_TOKEN}({@code __primary__}) 을 보내고
     * Prober 가 실제 이름으로 치환합니다. 이 클래스는 그 밖의 VM 관련
     * 기본값(예: 설정 백엔드 이름)을 담기 위한 자리입니다.
     */
    public static class VmDefaults {

        /** VM 설정 백엔드 이름입니다. (예: {@code netplan}) */
        private String configBackend = "netplan";

        public String getConfigBackend() {
            return configBackend;
        }

        public void setConfigBackend(String configBackend) {
            this.configBackend = configBackend;
        }
    }

    /**
     * 제어평면 대역을 해석합니다. (프로젝트 지정 우선)
     *
     * <h2>⚠️ 우선순위</h2>
     * <ol>
     *   <li>프로젝트가 지정한 값 ({@code projectManagementPrefix})</li>
     *   <li>전역 기본값 ({@link #managementPrefix})</li>
     * </ol>
     *
     * <p>순서를 뒤집으면 "이 프로젝트는 관리 대역이 다르다" 를 표현할 수
     * 없습니다. 요구사항이 프로젝트별 지정을 허용하므로 프로젝트가 이깁니다.
     *
     * @param projectManagementPrefix 프로젝트가 지정한 대역 (없으면 null/blank)
     * @return 실제 사용할 대역 (null 이 아닐 수 있음 — 빈 문자열이면 미지정)
     */
    public String managementPrefixFor(String projectManagementPrefix) {
        if (projectManagementPrefix != null && !projectManagementPrefix.isBlank()) {
            return projectManagementPrefix.trim();
        }
        return managementPrefix == null ? "" : managementPrefix.trim();
    }

    /**
     * 관리 대역 목록을 돌려줍니다. (쉼표 구분 다중 대역 지원)
     *
     * <p>제어평면이 한 대역이 아닌 경우(예: 관리망 + 백업망)를 위해
     * 목록으로 받습니다.
     *
     * @param projectManagementPrefix 프로젝트가 지정한 대역 (없으면 null)
     * @return 대역 목록 (없으면 빈 목록)
     */
    public List<String> managementPrefixesFor(String projectManagementPrefix) {
        final String resolved = managementPrefixFor(projectManagementPrefix);
        if (resolved.isBlank()) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        for (final String item : resolved.split("\\s*,\\s*")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    public String getManagementPrefix() {
        return managementPrefix;
    }

    public void setManagementPrefix(String managementPrefix) {
        this.managementPrefix = managementPrefix;
    }

    public SwitchDefaults getSwitchDefaults() {
        return switchDefaults;
    }

    public void setSwitchDefaults(SwitchDefaults switchDefaults) {
        this.switchDefaults = switchDefaults;
    }

    public FirewallDefaults getFirewallDefaults() {
        return firewallDefaults;
    }

    public void setFirewallDefaults(FirewallDefaults firewallDefaults) {
        this.firewallDefaults = firewallDefaults;
    }

    public RouterDefaults getRouterDefaults() {
        return routerDefaults;
    }

    public void setRouterDefaults(RouterDefaults routerDefaults) {
        this.routerDefaults = routerDefaults;
    }

    public VmDefaults getVmDefaults() {
        return vmDefaults;
    }

    public void setVmDefaults(VmDefaults vmDefaults) {
        this.vmDefaults = vmDefaults;
    }
}