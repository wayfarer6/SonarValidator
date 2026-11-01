package org.sonar.sonarvalidator_backend.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.core.annotation.Order;
import org.sonar.sonarvalidator_backend.Service.telemetry.TelemetryObserver;
import org.sonar.sonarvalidator_backend.Service.telemetry.TelemetrySnapshot;
import org.springframework.transaction.annotation.Transactional;

@Service
@Order(200)
public class ProjectSubnetAutoSyncService implements TelemetryObserver {

    @Override
    @Transactional
    public void onTelemetry(TelemetrySnapshot snapshot) {
        syncForAgent(snapshot.agentId(), snapshot.config());
    }

    private static final Logger log = LoggerFactory.getLogger(ProjectSubnetAutoSyncService.class);

    private static final int DEFAULT_MANAGEMENT_PREFIX_LEN = 24;

    private final ProjectRepository projects;
    private final ExpectedAgentRepository expected;

    public ProjectSubnetAutoSyncService(ProjectRepository projects, ExpectedAgentRepository expected) {
        this.projects = projects;
        this.expected = expected;
    }

    @Transactional
    public boolean syncForAgent(String agentId, NeutralDeviceConfig config) {
        if (agentId == null || agentId.isBlank() || config == null
                || !config.isDiscoveryComplete()
                || (config.getInterfaces().isEmpty() && config.getVlans().isEmpty()
                    && config.getBridges().isEmpty())) {
            return false;
        }
        try {
            return sync(agentId, config);
        } catch (RuntimeException ex) {
            // 텔레메트리 수신 경로이므로 흡수합니다. (위 클래스 주석 참고)
            log.warn("subnet auto-sync failed: agent={} reason={}", agentId, ex.getMessage());
            return false;
        }
    }

    private boolean sync(String agentId, NeutralDeviceConfig config) {
        final String projectKey = expected.findByAgentId(agentId)
                .map(ExpectedAgent::getProjectKey)
                .filter(key -> key != null && !key.isBlank())
                .orElse(null);
        if (projectKey == null) {
            // 프로젝트에 등록되지 않은 Agent 는 정책 대상이 아닙니다.
            return false;
        }
        final Project project = projects.findByProjectKey(projectKey).orElse(null);
        if (project == null) {
            return false;
        }

        final Set<String> management = managementNetworks(project);
        final List<PolicySubnet> existing = project.toPolicySubnets();

        // 이 장치가 자동으로 만든 서브넷의 이전 상태 (등급·이름 보존용)
        final Map<String, PolicySubnet> previousAuto = new LinkedHashMap<>();
        for (final PolicySubnet subnet : existing) {
            if (isAutoOwned(subnet, agentId)) {
                previousAuto.putIfAbsent(normalize(subnet.getCidr()), subnet);
            }
        }

        // 이 장치가 만든 것이 아니거나 수동 편집된 것은 그대로 둡니다.
        final List<PolicySubnet> merged = new ArrayList<>();
        for (final PolicySubnet subnet : existing) {
            if (!isAutoOwned(subnet, agentId)) {
                merged.add(subnet);
            }
        }

        for (final PolicySubnet draft : ProjectDiscoveryService.drafts(agentId, config)) {
            final String cidr = normalize(draft.getCidr());
            // 주소가 없는 VLAN(대역 미정의)은 정책에 넣을 수 없습니다.
            if (cidr.isEmpty()) {
                continue;
            }
            // 제어평면(관리망)은 정책 대상이 아닙니다.
            if (isManagement(cidr, management)) {
                continue;
            }
            final PolicySubnet previous = previousAuto.get(cidr);
            if (previous != null) {
                // 운영자가 지정한 등급과 이름은 유지합니다.
                draft.setZoneClass(previous.getZoneClass() == ZoneClass.OPEN ? null : previous.getZoneClass());
                draft.setNetworkRole(previous.getNetworkRole());
                draft.setAllowedPeers(previous.getAllowedPeers());
                if (previous.getName() != null && !previous.getName().isBlank()) {
                    draft.setName(previous.getName());
                }



                draft.setAllowedPeers(previous.getAllowedPeers());
                draft.setManuallyEdited(previous.isManuallyEdited());
            } else if (draft.getZoneClass() == null) {
                // 새로 감지된 대역은 안전한 쪽(Open)으로 채웁니다.
                draft.setZoneClass(null);
            }
            merged.add(draft);
        }

        // IOS brief output retains the configured host IP while its connected route/mask disappears on shutdown.
        for (final var previous : previousAuto.values()) {
            final boolean addressStillConfigured = config.getInterfaces().values().stream()
                    .flatMap(iface -> iface.getAddresses().stream())
                    .filter(address -> address != null && !address.contains("/") && !address.contains(":"))
                    .anyMatch(address -> isManagement(address + "/32", Set.of(previous.getCidr())));
            if (addressStillConfigured) merged.add(previous);
        }

        final Map<String, PolicySubnet> uniqueIdentity = new LinkedHashMap<>();
        for (final PolicySubnet subnet : merged) {
            final String identity = (subnet.getId() == null || subnet.getId().isBlank())
                    ? "cidr:" + normalize(subnet.getCidr())
                    : subnet.getId();
            uniqueIdentity.putIfAbsent(identity, subnet);
        }
        final List<PolicySubnet> deduped = new ArrayList<>(uniqueIdentity.values());

        if (!changed(existing, deduped)) {
            // 30초마다 오는 텔레메트리마다 쓰기를 하면 DB 만 바쁩니다.
            return false;
        }

        project.replacePolicy(deduped, null);
        project.setUpdatedAt(new Date());
        projects.save(project);
        log.info("서브넷 갱신: 노드={} 프로젝트={} 서브넷={} 변경={}",
                agentId, projectKey, deduped.size(), deduped.size() - (existing.size() - previousAuto.size()));
        return true;
    }

    private static boolean isAutoOwned(PolicySubnet subnet, String agentId) {
        return subnet != null
                && agentId.equals(subnet.getAgentId())
                && !subnet.isManuallyEdited();
    }

    private static boolean changed(List<PolicySubnet> before, List<PolicySubnet> after) {
        if (before.size() != after.size()) {
            return true;
        }
        return !signatures(before).equals(signatures(after));
    }

    private static Set<String> signatures(List<PolicySubnet> subnets) {
        final Set<String> result = new LinkedHashSet<>();
        for (final PolicySubnet subnet : subnets) {
            // ⚠️ 허용 목록을 빼면 안 됩니다. 운영자가 제한을 바꿔도 "바뀌지 않았다"
            //    로 판정되어 저장이 생략되고, 배포는 옛 목록으로 나갑니다.
            final java.util.List<String> allowed = subnet.getAllowedPeers();
            java.util.Collections.sort(allowed);
            result.add(subnet.getId() + "|" + normalize(subnet.getCidr()) + "|"
                    + (subnet.getZoneClass() == null ? "" : subnet.getZoneClass().name()) + "|"
                    + (subnet.getName() == null ? "" : subnet.getName()) + "|"
                    + (subnet.getVlanId() == null ? "" : subnet.getVlanId()) + "|"
                    + String.join(",", allowed));
        }
        return result;
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

    static Set<String> managementNetworks(Project project) {
        final Set<String> result = new LinkedHashSet<>();
        final String declared = normalize(project.getManagementPrefix());
        if (!declared.isEmpty()) {
            result.add(declared);
        }
        final String serverIp = project.getManagementServerIp();
        if (serverIp != null && !serverIp.isBlank() && !serverIp.contains(":")) {
            try {
                final String host = serverIp.trim();
                // 관리 서버와 같은 /24 를 관리망으로 봅니다.
                result.add(PolicySubnet.normalizeCidr(
                        host + "/" + DEFAULT_MANAGEMENT_PREFIX_LEN));
            } catch (RuntimeException ex) {
                // 주소 형식이 아니면 무시합니다. (관리망을 못 알아내도
                // 서브넷 자동 생성은 계속되어야 합니다)
                log.debug("cannot derive management network from '{}'", serverIp);
            }
        }
        return result;
    }

    static boolean isManagement(String cidr, Set<String> management) {
        if (cidr == null || cidr.isBlank()) {
            return true;
        }
        if (management.contains(cidr)) {
            return true;
        }
        final long[] candidate = parseNetwork(cidr);
        if (candidate == null) {
            return false;
        }
        for (final String network : management) {
            final long[] managed = parseNetwork(network);
            if (managed == null) {
                continue;
            }
            // 관리망이 후보보다 같거나 넓고, 주소가 관리망 프리픽스에 속하면 포함입니다.
            if (managed[1] <= candidate[1]
                    && withinPrefix(managed[0], candidate[0], (int) managed[1])) {
                return true;
            }
        }
        return false;
    }

    private static long[] parseNetwork(String cidr) {
        try {
            final int slash = cidr.indexOf('/');
            if (slash <= 0) {
                return null;
            }
            final int length = Integer.parseInt(cidr.substring(slash + 1).trim());
            final String[] octets = cidr.substring(0, slash).trim().split("\\.");
            if (octets.length != 4 || length < 0 || length > 32) {
                return null;
            }
            long value = 0;
            for (final String octet : octets) {
                final int part = Integer.parseInt(octet);
                if (part < 0 || part > 255) {
                    return null;
                }
                value = (value << 8) | part;
            }
            return new long[]{value & maskOf(length), length};
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static long maskOf(int length) {
        if (length <= 0) {
            return 0L;
        }
        return (0xFFFFFFFFL << (32 - length)) & 0xFFFFFFFFL;
    }

    private static boolean withinPrefix(long a, long b, int length) {
        final long mask = maskOf(length);
        return (a & mask) == (b & mask);
    }
}
