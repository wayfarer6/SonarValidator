package org.sonar.sonarvalidator_backend.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

/**
 * 라우팅 테이블에서 <b>대역 간 실제 연결</b>을 유도합니다.
 *
 * <h2>왜 필요한가</h2>
 * <p>토폴로지의 간선은 지금까지 <b>운영자가 적어 넣은 규칙</b>에서만 나왔습니다.
 * 그래서 장치가 실제로 VLAN 사이를 라우팅하고 있어도 화면에는 아무 선이 없고,
 * "설정된 정책" 과 "실제 연결" 이 다르다는 사실이 보이지 않았습니다.
 *
 * <h2>무엇을 근거로 보는가</h2>
 * <p>장치의 <b>L3 존재</b> 를 라우팅 정보에서 읽습니다.
 * <ol>
 *   <li>라우팅 테이블의 {@code connected}/{@code local} 경로 — 그 대역이 직접 연결되었다는 증거</li>
 *   <li>인터페이스 주소 — 라우팅 테이블을 내려주지 않는 장비를 위한 보완
 *       (실측: Arista vEOS 는 수집 경로가 0건이지만 SVI 에 주소가 있습니다)</li>
 * </ol>
 *
 * <p>한 장치가 두 대역에 L3 주소를 가지면 그 장치는 두 대역 사이를
 * <b>포워딩합니다</b>(L3 라우팅). 이것이 "VLAN 간 연결" 입니다.
 *
 * <h2>저장하지 않는 이유</h2>
 * <p>라우팅 테이블은 살아 있는 텔레메트리입니다. 규칙처럼 저장하면 장치가 바뀌어도
 * 화면에는 옛 연결이 남습니다. 그래서 토폴로지를 읽을 때마다 계산합니다.
 */
public final class ObservedConnectivity {

    /** 토폴로지에 그리는 인터넷 노드의 식별자입니다. */
    public static final String INTERNET_NODE_ID = "internet";

    /** 인터넷 노드의 표시 이름입니다. */
    public static final String INTERNET_NODE_LABEL = "Internet";

    private ObservedConnectivity() {
    }

    /**
     * 토폴로지 노드 종류 — 인터넷(외부) 노드를 일반 서브넷과 구분합니다.
     *
     * <p>화면이 인터넷을 원형/지구본 모양으로 그리려면 서브넷과 다른 종류임을
     * 알아야 합니다. 라벨만 보고 판단하면 이름을 바꾸는 순간 깨집니다.
     */
    public static final String KIND_INTERNET = "internet";

    /** 서브넷(내부 대역) 노드 종류입니다. */
    public static final String KIND_SUBNET = "subnet";

    /** 실제 라우팅 홉을 표시하는 장비 노드 종류입니다. */
    public static final String KIND_DEVICE = "device";

    /**
     * 기본 경로 한 건과 다음 홉 장비의 해석 결과입니다.
     *
     * @param sourceAgentId 기본 경로를 가진 장비
     * @param nextHop 기본 경로의 다음 홉 주소
     * @param nextHopInterface 기본 경로가 나가는 인터페이스
     * @param targetAgentId 다음 홉 주소를 인터페이스로 가진 내부 장비. 없으면 외부 경계
     * @param targetInterface 다음 홉 주소가 발견된 내부 장비 인터페이스
     */
    public record DefaultRouteHop(String sourceAgentId,
                                  String nextHop,
                                  String nextHopInterface,
                                  String targetAgentId,
                                  String targetInterface) {
    }

    /**
     * ARP/IPv6 이웃의 MAC 주소로 확인한 실제 장비 간 링크입니다.
     *
     * @param sourceAgentId 이웃을 관측한 장비
     * @param sourceInterface 이웃을 관측한 로컬 인터페이스
     * @param targetAgentId 이웃 MAC을 가진 장비
     * @param targetInterface 이웃 MAC을 가진 인터페이스
     */
    public record PhysicalLink(String sourceAgentId,
                               String sourceInterface,
                               String targetAgentId,
                               String targetInterface) {
    }

    private record InterfaceOwner(String agentId, String interfaceName) {
    }

    /**
     * 수집된 이웃 테이블과 인터페이스 MAC을 맞춰 장비 간 물리 링크를 찾습니다.
     *
     * <p>IPv4 ARP는 같은 브로드캐스트 도메인의 종단 장비까지 보여 주므로 중간
     * 스위치를 건너뛴 직결선처럼 보일 수 있습니다. 여기서는 로컬 링크 이웃인
     * IPv6 link-local 항목만 쓰고, 프로젝트 관리망 인터페이스는 제외합니다.
     *
     * @param project 관리망 범위를 가진 프로젝트
     * @param configs 장비별 수집 설정
     * @return 중복을 제거한 물리 링크
     */
    public static List<PhysicalLink> physicalLinks(
            Project project, Map<String, NeutralDeviceConfig> configs) {
        final List<PhysicalLink> result = new ArrayList<>();
        if (project == null || configs == null || configs.isEmpty()) {
            return result;
        }

        final Set<String> management = ProjectSubnetAutoSyncService.managementNetworks(project);
        final Map<String, InterfaceOwner> ownerByMac = new LinkedHashMap<>();
        for (final Map.Entry<String, NeutralDeviceConfig> entry : configs.entrySet()) {
            final String agentId = entry.getKey();
            final NeutralDeviceConfig config = entry.getValue();
            if (agentId == null || config == null) {
                continue;
            }
            config.getInterfaces().forEach((interfaceName, iface) -> {
                if (iface == null || isManagementInterface(iface, management)) {
                    return;
                }
                final String mac = normalizeMac(iface.getMacAddress());
                if (!mac.isEmpty()) {
                    ownerByMac.putIfAbsent(mac, new InterfaceOwner(agentId, interfaceName));
                }
            });
        }

        final Set<String> seen = new LinkedHashSet<>();
        for (final Map.Entry<String, NeutralDeviceConfig> entry : configs.entrySet()) {
            final String sourceAgent = entry.getKey();
            final NeutralDeviceConfig config = entry.getValue();
            if (sourceAgent == null || config == null) {
                continue;
            }
            for (final NeutralDeviceConfig.ArpEntry neighbor : config.getArpEntries()) {
                if (neighbor == null || neighbor.getAddress() == null
                        || !neighbor.getAddress().toLowerCase().startsWith("fe80:")) {
                    continue;
                }
                final NeutralDeviceConfig.InterfaceConfig sourceInterface =
                        config.getInterfaces().get(neighbor.getInterfaceName());
                if (sourceInterface == null
                        || isManagementInterface(sourceInterface, management)) {
                    continue;
                }
                final InterfaceOwner target = ownerByMac.get(normalizeMac(neighbor.getMac()));
                if (target == null || target.agentId().equalsIgnoreCase(sourceAgent)) {
                    continue;
                }
                final String first = sourceAgent.compareToIgnoreCase(target.agentId()) <= 0
                        ? sourceAgent : target.agentId();
                final String second = first.equals(sourceAgent) ? target.agentId() : sourceAgent;
                final String key = first.toLowerCase() + "|" + second.toLowerCase();
                if (seen.add(key)) {
                    result.add(new PhysicalLink(sourceAgent, neighbor.getInterfaceName(),
                            target.agentId(), target.interfaceName()));
                }
            }
        }
        return result;
    }

    /**
     * 기본 경로 홉을 거슬러 올라가 최종 외부 경계까지 도달하는 장비를 찾습니다.
     *
     * @param hops 프로젝트 내부 기본 경로와 마지막 외부 홉
     * @return 인터넷에 도달 가능한 장비 식별자
     */
    public static Set<String> internetReachableAgents(List<DefaultRouteHop> hops) {
        final Set<String> reachable = new LinkedHashSet<>();
        if (hops == null || hops.isEmpty()) {
            return reachable;
        }
        for (final DefaultRouteHop hop : hops) {
            if (hop != null && hop.targetAgentId() == null) {
                reachable.add(hop.sourceAgentId());
            }
        }
        boolean changed;
        do {
            changed = false;
            for (final DefaultRouteHop hop : hops) {
                if (hop != null && hop.targetAgentId() != null
                        && containsIgnoreCase(reachable, hop.targetAgentId())
                        && !containsIgnoreCase(reachable, hop.sourceAgentId())) {
                    reachable.add(hop.sourceAgentId());
                    changed = true;
                }
            }
        } while (changed);
        return reachable;
    }

    private static boolean isManagementInterface(
            NeutralDeviceConfig.InterfaceConfig iface, Set<String> management) {
        if (iface == null || management == null || management.isEmpty()) {
            return false;
        }
        for (final String address : iface.getAddresses()) {
            final String network = normalizedNetworkOf(address);
            if (network != null && ProjectSubnetAutoSyncService.isManagement(network, management)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeMac(String mac) {
        return mac == null ? "" : mac.replaceAll("[^0-9A-Fa-f]", "").toLowerCase();
    }

    private static boolean containsIgnoreCase(Set<String> values, String candidate) {
        if (candidate == null) {
            return false;
        }
        for (final String value : values) {
            if (value != null && value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 장비별 기본 경로를 실제 다음 홉 장비에 연결합니다.
     *
     * <p>기본 경로가 있다는 사실만으로 그 장비의 모든 VLAN을 인터넷에 직접
     * 연결하면 방화벽과 게이트웨이를 건너뛴 그림이 됩니다. 먼저 모든 인터페이스
     * 주소의 소유자를 색인하고, 기본 경로의 {@code nextHop}이 내부 장비 주소면
     * 장비 간 홉으로, 찾을 수 없으면 외부 경계로 판정합니다.
     *
     * @param configs 장비별 수집 설정
     * @return 수집 순서를 유지한 기본 경로 홉 목록
     */
    public static List<DefaultRouteHop> defaultRouteHops(
            Map<String, NeutralDeviceConfig> configs) {
        final List<DefaultRouteHop> result = new ArrayList<>();
        if (configs == null || configs.isEmpty()) {
            return result;
        }

        final Map<String, InterfaceOwner> ownerByAddress = new LinkedHashMap<>();
        for (final Map.Entry<String, NeutralDeviceConfig> entry : configs.entrySet()) {
            final String agentId = entry.getKey();
            final NeutralDeviceConfig config = entry.getValue();
            if (agentId == null || config == null) {
                continue;
            }
            config.getInterfaces().forEach((interfaceName, iface) -> {
                if (iface == null) {
                    return;
                }
                for (final String address : iface.getAddresses()) {
                    final String host = hostAddress(address);
                    if (host != null) {
                        ownerByAddress.putIfAbsent(host,
                                new InterfaceOwner(agentId, interfaceName));
                    }
                }
            });
        }

        final Set<String> seen = new LinkedHashSet<>();
        for (final Map.Entry<String, NeutralDeviceConfig> entry : configs.entrySet()) {
            final String sourceAgentId = entry.getKey();
            final NeutralDeviceConfig config = entry.getValue();
            if (sourceAgentId == null || config == null) {
                continue;
            }
            for (final NeutralDeviceConfig.RouteConfig route : config.getRoutes()) {
                if (!isDefaultRoute(route) || Boolean.FALSE.equals(route.getSelected())) {
                    continue;
                }
                final String nextHop = hostAddress(route.getNextHop());
                final InterfaceOwner owner = nextHop == null ? null : ownerByAddress.get(nextHop);
                final String targetAgentId = owner != null
                        && !owner.agentId().equalsIgnoreCase(sourceAgentId)
                        ? owner.agentId()
                        : null;
                final String targetInterface = targetAgentId == null
                        ? null
                        : owner.interfaceName();
                final String key = sourceAgentId.toLowerCase() + "|"
                        + String.valueOf(nextHop) + "|"
                        + String.valueOf(targetAgentId).toLowerCase();
                if (seen.add(key)) {
                    result.add(new DefaultRouteHop(
                            sourceAgentId,
                            nextHop,
                            route.getNextHopInterface(),
                            targetAgentId,
                            targetInterface));
                }
            }
        }
        return result;
    }

    /**
     * 인터넷 기본 경로({@code 0.0.0.0/0})를 가진 장치 식별자를 모읍니다.
     *
     * <p>"장치가 인터넷으로 나갈 수 있다" 는 판단은 여러 곳에서 필요합니다.
     * (검증의 인터넷 노출 위반, 토폴로지의 인터넷 간선) 두 곳이 각자 판단하면
     * 한쪽만 고쳐져 화면과 검증이 어긋납니다. 그래서 여기 한 곳에 둡니다.
     *
     * @param configs 장치별 수집 설정
     * @return 기본 경로가 있는 장치 식별자
     */
    public static Set<String> internetExposedAgents(Map<String, NeutralDeviceConfig> configs) {
        final Set<String> exposed = new LinkedHashSet<>();
        if (configs == null || configs.isEmpty()) {
            return exposed;
        }
        for (final Map.Entry<String, NeutralDeviceConfig> entry : configs.entrySet()) {
            if (entry.getKey() != null && hasDefaultRoute(entry.getValue())) {
                exposed.add(entry.getKey());
            }
        }
        return exposed;
    }

    /**
     * 장치가 인터넷 기본 경로를 가지는지 확인합니다.
     *
     * @param config 수집 설정
     * @return 기본 경로가 있으면 {@code true}
     */
    public static boolean hasDefaultRoute(NeutralDeviceConfig config) {
        if (config == null) {
            return false;
        }
        for (final NeutralDeviceConfig.RouteConfig route : config.getRoutes()) {
            if (route == null) {
                continue;
            }
            if (isDefaultRoute(route)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDefaultRoute(NeutralDeviceConfig.RouteConfig route) {
        return route != null && (Boolean.TRUE.equals(route.getDefaultRoute())
                || "0.0.0.0/0".equals(route.getPrefix()));
    }

    /** IPv4/CIDR에서 호스트 주소 부분만 반환합니다. 특수 next-hop 표기는 제외합니다. */
    private static String hostAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String trimmed = value.trim();
        final int slash = trimmed.indexOf('/');
        final String host = slash < 0 ? trimmed : trimmed.substring(0, slash);
        return parseNetwork(host) == null ? null : host;
    }

    /**
     * 관측된 대역 간 연결 한 건입니다.
     *
     * @param agentId    이 연결을 만들어 주는 장치 식별자
     * @param sourceId   출발 서브넷 식별자
     * @param targetId   도착 서브넷 식별자
     * @param sourceCidr 출발 대역
     * @param targetCidr 도착 대역
     * @param forbidden  두 대역의 등급이 직접 연결을 금지하는 조합인가
     */
    public record Link(String agentId,
                       String sourceId,
                       String targetId,
                       String sourceCidr,
                       String targetCidr,
                       boolean forbidden) {
    }

    /**
     * 장치별로 "한 장치가 함께 라우팅하는 대역 쌍" 을 만들어 돌려줍니다.
     *
     * @param project 프로젝트 (서브넷 등급 포함)
     * @param configs 장치별 수집 설정 (없으면 빈 목록)
     * @return 관측된 연결 목록 (중복 제거, CIDR 순 정렬)
     */
    public static List<Link> betweenSubnetsOfEachDevice(Project project,
                                                        Map<String, NeutralDeviceConfig> configs) {
        final List<Link> result = new ArrayList<>();
        if (project == null || configs == null || configs.isEmpty()) {
            return result;
        }

        // 장치별로 프로젝트 서브넷을 모읍니다.
        final Map<String, List<ProjectSubnet>> subnetsByAgent = new LinkedHashMap<>();
        for (final ProjectSubnet subnet : project.getSubnets()) {
            final String agentId = subnet.getAgentId();
            if (agentId == null || agentId.isBlank()) {
                continue;
            }
            subnetsByAgent.computeIfAbsent(agentId, key -> new ArrayList<>()).add(subnet);
        }

        for (final Map.Entry<String, List<ProjectSubnet>> entry : subnetsByAgent.entrySet()) {
            final String agentId = entry.getKey();
            final NeutralDeviceConfig config = findConfig(configs, agentId);
            if (config == null) {
                continue;
            }
            // ⚠️ 방화벽(존 경계를 강제하는 장비)은 VLAN 간 연결 추론에서 제외합니다.
            //
            //    방화벽이 여러 존에 SVI(L3 주소)를 가지는 것은 "두 존이 연결된다"
            //    가 아니라 "<b>여기서 존을 분리/통제한다</b>" 는 뜻입니다. 방화벽에
            //    주소가 있다는 사실만으로 Confidential ↔ Sensitive/Open 간선을
            //    그리면, 실제로 허용되지 않은 연결이 화면에 존재하는 것처럼 보입니다.
            //    존 간 허용은 규칙(policy rule)으로 표현되고, 방화벽 규칙 수집이
            //    그 근거입니다. 라우터·스위치는 실제로 VLAN 간 포워딩을 하므로
            //    기존 추론(SVI/connected 경로)을 유지합니다.
            final boolean enforcementDevice = isEnforcementDevice(agentId, config);
            final Set<String> routed = routedNetworks(config);
            if (routed.isEmpty()) {
                continue;
            }

            // 이 장치가 실제로 L3 를 가지는 서브넷만 남깁니다.
            final List<ProjectSubnet> onDevice = new ArrayList<>();
            for (final ProjectSubnet subnet : entry.getValue()) {
                if (subnet.getCidr() == null || subnet.getCidr().isBlank()) {
                    continue;
                }
                if (isRouted(subnet.getCidr(), routed)) {
                    onDevice.add(subnet);
                }
            }
            // 대역(CIDR) 기준으로 중복을 없앱니다 — 같은 장치에 같은 대역이 두 번 있으면
            // (이름만 다른 행) 연결이 중복 생성됩니다.
            final Map<String, ProjectSubnet> byCidr = new LinkedHashMap<>();
            for (final ProjectSubnet subnet : onDevice) {
                byCidr.putIfAbsent(normalize(subnet.getCidr()), subnet);
            }
            final List<ProjectSubnet> unique = new ArrayList<>(byCidr.values());
            if (unique.size() < 2) {
                continue;
            }

            for (int i = 0; i < unique.size(); ++i) {
                for (int j = i + 1; j < unique.size(); ++j) {
                    final ProjectSubnet first = unique.get(i);
                    final ProjectSubnet second = unique.get(j);
                    final ZoneClass firstZone = first.getZoneClass();
                    final ZoneClass secondZone = second.getZoneClass();
                    if (enforcementDevice
                            && !firewallAllowsPair(config, firstZone, secondZone)) {
                        continue;
                    }
                    result.add(new Link(
                            agentId,
                            first.getSubnetId(),
                            second.getSubnetId(),
                            first.getCidr(),
                            second.getCidr(),
                            ZoneClass.forbidsDirectConnection(firstZone, secondZone)));
                }
            }
        }
        return result;
    }

    /**
     * 방화벽 정책까지 고려해 해당 존이 외부 기본 경로를 사용할 수 있는지 봅니다.
     *
     * <p>기본 경로가 있어도 방화벽에 {@code C-to-Open-Internet deny} 같은 명시적
     * 차단 규칙이 있으면 그 VLAN을 인터넷 가능으로 표시해서는 안 됩니다.
     */
    public static boolean internetAllowed(String agentId,
                                          NeutralDeviceConfig config,
                                          ZoneClass zone) {
        if (!isEnforcementDevice(agentId, config)) {
            return true;
        }
        final String token = zoneToken(zone);
        final String full = zone == null ? "" : zone.label().toLowerCase();
        for (final String rule : config.getFirewallRules()) {
            final String normalized = rule == null ? "" : rule.toLowerCase();
            if (!hasDropAction(normalized)) {
                continue;
            }
            if (normalized.contains(token + "-to-open")
                    || normalized.contains(token + "-to-internet")
                    || (!full.isEmpty() && normalized.contains(full + "-to-open"))) {
                return false;
            }
        }
        return true;
    }

    /** 방화벽에서 두 존 사이를 명시적으로 accept한 경우만 VLAN 간선을 만듭니다. */
    private static boolean firewallAllowsPair(NeutralDeviceConfig config,
                                              ZoneClass first,
                                              ZoneClass second) {
        final String forward = zoneToken(first) + "-to-" + zoneToken(second);
        final String reverse = zoneToken(second) + "-to-" + zoneToken(first);
        for (final String rule : config.getFirewallRules()) {
            final String normalized = rule == null ? "" : rule.toLowerCase();
            if (hasAcceptAction(normalized)
                    && (normalized.contains(forward) || normalized.contains(reverse))) {
                return true;
            }
        }
        return false;
    }

    private static String zoneToken(ZoneClass zone) {
        if (zone == ZoneClass.CONFIDENTIAL) {
            return "c";
        }
        if (zone == ZoneClass.SENSITIVE) {
            return "s";
        }
        return "open";
    }

    private static boolean hasAcceptAction(String rule) {
        return rule.contains("\"accept\"") || rule.contains(" accept");
    }

    private static boolean hasDropAction(String rule) {
        return rule.contains("\"drop\"") || rule.contains(" drop");
    }

    /**
     * 장치 식별자를 대소문자 무시하고 찾습니다.
     *
     * <p>운영자가 적는 {@code agent_id} 는 대소문자가 섞입니다(VDI-1 / vdi-1).
     * 수집 쪽 키와 정확히 같지 않을 수 있어 관대하게 맞춥니다.
     *
     * @param configs 장치별 설정
     * @param agentId 찾을 식별자
     * @return 설정, 없으면 {@code null}
     */
    private static NeutralDeviceConfig findConfig(Map<String, NeutralDeviceConfig> configs,
                                                 String agentId) {
        final NeutralDeviceConfig exact = configs.get(agentId);
        if (exact != null) {
            return exact;
        }
        for (final Map.Entry<String, NeutralDeviceConfig> entry : configs.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(agentId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 장치가 존 경계를 강제하는 방화벽인지 확인합니다.
     *
     * <p>방화벽은 여러 존에 L3 주소를 가지는 것이 정상이며, 그 자체는
     * "존 간 연결" 이 아니라 "존을 분리·통제한다" 는 증거입니다. 그래서
     * 인터페이스/SVI 기반의 VLAN 간 연결 추론에서 제외합니다.
     *
     * <p>유형은 장치가 보고한 {@code device_type} 을 우선하고, 없으면 식별자
     * 관례로 추론합니다({@code Firewall}, {@code GNS3.Firewall} 등).
     *
     * @param agentId 장치 식별자
     * @param config  수집 설정
     * @return 방화벽이면 {@code true}
     */
    private static boolean isEnforcementDevice(String agentId, NeutralDeviceConfig config) {
        final DeviceType reported = DeviceType.fromString(
                config == null ? null : config.getDeviceType());
        final DeviceType resolved = reported != null
                ? reported
                : DeviceType.inferFromDeviceId(agentId);
        return resolved == DeviceType.FIREWALL;
    }

    /**
     * 장치가 L3 를 가지는(직접 라우팅하는) 대역을 모읍니다.
     *
     * @param config 수집 설정
     * @return 정규화된 대역 집합
     */
    private static Set<String> routedNetworks(NeutralDeviceConfig config) {
        final Set<String> networks = new LinkedHashSet<>();

        // 1) 라우팅 테이블 — directly connected / local 경로
        for (final NeutralDeviceConfig.RouteConfig route : config.getRoutes()) {
            if (route == null) {
                continue;
            }
            final String prefix = route.getPrefix();
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            final String protocol = route.getProtocol() == null ? "" : route.getProtocol();
            final String nextHop = route.getNextHop() == null ? "" : route.getNextHop();
            final boolean connected = protocol.equalsIgnoreCase("connected")
                    || protocol.equalsIgnoreCase("local")
                    || nextHop.equalsIgnoreCase("directly connected");
            if (connected) {
                networks.add(normalize(prefix));
            }
        }

        // 2) 인터페이스 주소 — 라우팅 테이블이 빈 장비를 위한 보완
        config.getInterfaces().forEach((name, iface) -> {
            if (name == null || name.equalsIgnoreCase("lo")) {
                return;
            }
            for (final String address : iface.getAddresses()) {
                final String cidr = normalizedNetworkOf(address);
                if (cidr != null) {
                    networks.add(cidr);
                }
            }
        });

        networks.remove("");
        return networks;
    }

    /**
     * 서브넷 대역이 장치가 라우팅하는 대역 안에 들어가는지 확인합니다.
     *
     * <p>장치는 {@code 192.168.122.0/24} 를 직접 연결로 보고하는데 프로젝트는
     * 그 안의 주소를 {@code 192.168.122.254/32} 로 가질 수 있습니다. 그래서
     * <b>포함</b> 관계로 봅니다.
     *
     * @param subnetCidr 서브넷 대역
     * @param routed     장치가 라우팅하는 대역들
     * @return 포함되면 {@code true}
     */
    private static boolean isRouted(String subnetCidr, Set<String> routed) {
        final long[] candidate = parseNetwork(subnetCidr);
        if (candidate == null) {
            return false;
        }
        // 정확히 같은 대역은 바로 참입니다.
        if (routed.contains(normalize(subnetCidr))) {
            return true;
        }
        for (final String network : routed) {
            final long[] routedNet = parseNetwork(network);
            if (routedNet == null) {
                continue;
            }
            // 라우팅 대역이 서브넷보다 같거나 넓고, 서브넷 시작 주소가 그 안에 있으면 포함입니다.
            if (routedNet[1] <= candidate[1]
                    && withinPrefix(routedNet[0], candidate[0], (int) routedNet[1])) {
                return true;
            }
        }
        return false;
    }

    /**
     * 인터페이스 주소({@code 10.0.8.1/24})를 네트워크 대역({@code 10.0.8.0/24})으로 바꿉니다.
     *
     * @param address 인터페이스 주소
     * @return 대역, 형식이 아니거나 루프백이면 {@code null}
     */
    private static String normalizedNetworkOf(String address) {
        if (address == null || address.isBlank() || address.contains(":")) {
            return null;
        }
        try {
            final String host = address.contains("/") ? address : address + "/32";
            final long[] parsed = parseNetwork(host);
            if (parsed == null) {
                return null;
            }
            final long mask = parsed[1] == 0 ? 0L : (0xFFFFFFFFL << (32 - parsed[1])) & 0xFFFFFFFFL;
            final long network = parsed[0] & mask;
            if ((network >>> 24) == 127) {
                return null;
            }
            return ((network >>> 24) & 0xFF) + "." + ((network >>> 16) & 0xFF) + "."
                    + ((network >>> 8) & 0xFF) + "." + (network & 0xFF) + "/" + parsed[1];
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String normalize(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return "";
        }
        try {
            return PolicySubnet.normalizeCidr(cidr);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    /**
     * 주소가 프리픽스 안에 있는지 확인합니다.
     *
     * @param network 네트워크 주소
     * @param address 확인할 주소
     * @param length  프리픽스 길이
     * @return 포함되면 {@code true}
     */
    private static boolean withinPrefix(long network, long address, int length) {
        if (length <= 0) {
            return true;
        }
        final long mask = (0xFFFFFFFFL << (32 - length)) & 0xFFFFFFFFL;
        return (network & mask) == (address & mask);
    }

    /**
     * {@code a.b.c.d/len} 을 네트워크 주소와 프리픽스 길이로 바꿉니다.
     *
     * @param cidr 대역 문자열
     * @return {@code [네트워크 주소, 프리픽스 길이]}, 형식이 아니면 {@code null}
     */
    private static long[] parseNetwork(String cidr) {
        try {
            final int slash = cidr.indexOf('/');
            final String addressPart = slash < 0 ? cidr.trim() : cidr.substring(0, slash).trim();
            final int length = slash < 0 ? 32 : Integer.parseInt(cidr.substring(slash + 1).trim());
            final String[] octets = addressPart.split("\\.");
            if (octets.length != 4 || length < 0 || length > 32) {
                return null;
            }
            long value = 0;
            for (final String octet : octets) {
                final int part = Integer.parseInt(octet.trim());
                if (part < 0 || part > 255) {
                    return null;
                }
                value = (value << 8) | part;
            }
            return new long[]{value & 0xFFFFFFFFL, length};
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
