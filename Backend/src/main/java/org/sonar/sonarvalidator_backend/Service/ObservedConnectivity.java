package org.sonar.sonarvalidator_backend.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
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
            if (Boolean.TRUE.equals(route.getDefaultRoute())
                    || "0.0.0.0/0".equals(route.getPrefix())) {
                return true;
            }
        }
        return false;
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
