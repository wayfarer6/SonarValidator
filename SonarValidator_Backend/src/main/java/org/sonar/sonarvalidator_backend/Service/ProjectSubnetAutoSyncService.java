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
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집된 텔레메트리에서 프로젝트 서브넷을 <b>자동으로</b> 만들어 넣습니다.
 *
 * <h2>⚠️ 왜 필요한가</h2>
 * <p>프로버가 VLAN·SVI 를 수집해도 그것이 프로젝트 서브넷이 되지 않으면
 * 토폴로지가 <b>빈 화면</b>입니다. 운영자는 "장비를 붙였는데 아무것도 안 보인다"
 * 로 읽고, 화면 어디에도 다음 행동이 적혀 있지 않습니다. 그래서 장비가 붙는
 * 순간 대역을 만들어 둡니다.
 *
 * <h2>등급(CSO)은 Open 으로 둡니다</h2>
 * <p>등급은 <b>정책 판단</b>이라 기계가 정할 수 없습니다. 다만 등급이 비어 있으면
 * 검증 엔진이 그 서브넷을 규칙에 쓸 수 없으므로, 안전한 쪽(가장 덜 제한적인
 * {@link ZoneClass#OPEN})으로 채워 두고 운영자가 올리도록 합니다.
 * (막아야 할 대상을 열어 두는 것보다, 열어 둔 대상을 좁히는 편이 안전합니다)
 *
 * <h2>손대지 않는 것</h2>
 * <ul>
 *   <li>{@code manually_edited=true} 인 서브넷 — 운영자가 이름·등급·대역을
 *       고친 결과이므로 덮어쓰지 않습니다.</li>
 *   <li>다른 장치가 만든 서브넷 — 소유권을 {@code agent_id} 로 구분합니다.</li>
 *   <li>관리망 대역 — 제어평면 주소는 정책 대상이 아닙니다.</li>
 * </ul>
 *
 * <h2>수집에서 사라진 대역</h2>
 * <p>이 장치가 만들었고 수동 편집되지 않은 서브넷은 <b>함께 지웁니다.</b>
 * 장치에서 VLAN 을 지웠는데 정책에는 남아 있으면, 없는 대역에 대한 위반
 * 판정이 계속 나옵니다.
 *
 * <h2>⚠️ 호출 규칙</h2>
 * <p>{@link #syncForAgent} 는 <b>예외를 밖으로 던지지 않습니다.</b> 텔레메트리
 * 수신 경로에서 불리므로, 여기서 실패가 수신을 막으면 장치가 통째로
 * "무응답" 이 됩니다. 실패는 로그로만 남깁니다.
 */
@Service
public class ProjectSubnetAutoSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProjectSubnetAutoSyncService.class);

    /** 관리 서버 자신의 주소를 관리망으로 볼 때 쓰는 기본 프리픽스 길이입니다. */
    private static final int DEFAULT_MANAGEMENT_PREFIX_LEN = 24;

    private final ProjectRepository projects;
    private final ExpectedAgentRepository expected;

    /**
     * @param projects 프로젝트 저장소
     * @param expected 배포 예정(에이전트 ↔ 프로젝트) 저장소
     */
    public ProjectSubnetAutoSyncService(ProjectRepository projects, ExpectedAgentRepository expected) {
        this.projects = projects;
        this.expected = expected;
    }

    /**
     * 장치 하나의 수집 결과를 프로젝트 서브넷에 반영합니다.
     *
     * <p>프로젝트에 귀속되지 않은 장치는 조용히 건너뜁니다 — 어느 프로젝트의
     * 정책에 넣을지 알 수 없기 때문입니다.
     *
     * @param agentId 장치 식별자
     * @param config  수집된 중립 설정
     * @return 서브넷이 바뀌어 저장했으면 {@code true}
     */
    @Transactional
    public boolean syncForAgent(String agentId, NeutralDeviceConfig config) {
        if (agentId == null || agentId.isBlank() || config == null) {
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
                draft.setZoneClass(previous.getZoneClass());
                if (previous.getName() != null && !previous.getName().isBlank()) {
                    draft.setName(previous.getName());
                }
                // ⚠️ 허용 목록도 반드시 유지합니다. 초안에는 이 값이 없으므로
                //    그대로 저장하면 <b>운영자가 지정한 제한이 30초마다 사라집니다.</b>
                //    그러면 배포된 차단이 다음 동기화에 함께 풀립니다.
                draft.setAllowedPeers(previous.getAllowedPeers());
                draft.setManuallyEdited(previous.isManuallyEdited());
            } else if (draft.getZoneClass() == null) {
                // 새로 감지된 대역은 안전한 쪽(Open)으로 채웁니다.
                draft.setZoneClass(ZoneClass.OPEN);
            }
            merged.add(draft);
        }

        // ⚠️ 같은 subnet_id 가 merged 에 두 번 들어갈 수 있습니다.
        //    운영자가 등급을 바꾼 행은 "자동 소유"가 아니므로 위 루프에서 보존되는데,
        //    같은 대역이 초안으로 한 번 더 계산되어 뒤에 추가되기 때문입니다.
        //    그대로 저장하면 자동 증가 PK 만 다른 중복 행이 생겨
        //    대역 드롭다운에 같은 CIDR 이 두 번 뜨고 정책 검증도 이중으로 돕니다.
        //    (실측: 10.0.8.0/24 가 Sensitive(편집됨)·Open(자동) 두 행으로 공존)
        //    먼저 들어온 쪽(= 운영자가 손댄 행)을 남깁니다.
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
        log.info("subnets auto-synced: agent={} project={} subnets={} (added/changed {})",
                agentId, projectKey, deduped.size(), deduped.size() - (existing.size() - previousAuto.size()));
        return true;
    }

    /**
     * 이 서브넷이 해당 장치가 만든 자동 생성분인지 판단합니다.
     *
     * @param subnet  서브넷
     * @param agentId 장치 식별자
     * @return 자동 생성분이면 {@code true}
     */
    private static boolean isAutoOwned(PolicySubnet subnet, String agentId) {
        return subnet != null
                && agentId.equals(subnet.getAgentId())
                && !subnet.isManuallyEdited();
    }

    /**
     * 목록이 실질적으로 바뀌었는지 비교합니다. (저장 생략용)
     *
     * <p>순서가 아니라 <b>식별자·대역·등급·이름</b>의 집합으로 봅니다.
     * 순서만 다른 경우까지 저장하면 매 주기마다 쓰기가 발생합니다.
     *
     * @param before 이전 목록
     * @param after  이후 목록
     * @return 바뀌었으면 {@code true}
     */
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

    /**
     * 정책에서 제외할 관리망 대역을 모읍니다.
     *
     * <p>두 가지를 봅니다.
     * <ol>
     *   <li>프로젝트에 지정된 {@code management_prefix}</li>
     *   <li>관리 서버 주소가 속한 대역 — 이 값은 자주 지정되므로, 프리픽스가
     *       비어 있어도 관리 인터페이스가 정책에 섞여 들어오는 것을 막습니다.</li>
     * </ol>
     *
     * @param project 프로젝트
     * @return 정규화된 대역 집합
     */
    /**
     * 정책에서 제외할 관리망 대역을 모읍니다.
     *
     * <p>편집 화면({@code ProjectDiscoveryService.editingView})도 <b>같은 규칙</b>을
     * 써야 합니다. 한쪽만 관리망을 거르면 관리망 서브넷이 <b>매 주기 목록에서
     * 나타났다 사라졌다</b> 하며 저장을 반복합니다. (실측: added/changed 가
     * 30초마다 2~3건으로 계속 잡혔습니다)
     *
     * <p>두 가지를 봅니다.
     * <ol>
     *   <li>프로젝트에 지정된 {@code management_prefix}</li>
     *   <li>관리 서버 주소가 속한 대역 — 이 값은 자주 지정되므로, 프리픽스가
     *       비어 있어도 관리 인터페이스가 정책에 섞여 들어오는 것을 막습니다.</li>
     * </ol>
     *
     * @param project 프로젝트
     * @return 정규화된 대역 집합
     */
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

    /**
     * 대역이 관리망에 속하는지 확인합니다. (프리픽스가 달라도 포함되면 제외)
     *
     * <p>정확히 같은 대역({@code 10.20.0.0/24})뿐 아니라 그 <b>안쪽</b> 대역
     * ({@code 10.20.0.0/25})도 관리망으로 봅니다. 장치가 관리 인터페이스에
     * 더 좁은 프리픽스를 주는 경우가 있기 때문입니다.
     *
     * @param cidr       검사할 대역
     * @param management 관리망 대역 집합
     * @return 관리망이면 {@code true}
     */
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

    /**
     * {@code a.b.c.d/len} 을 네트워크 주소와 프리픽스 길이로 바꿉니다.
     *
     * @param cidr 대역 문자열
     * @return {@code [네트워크 주소, 프리픽스 길이]}, 형식이 아니면 {@code null}
     */
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

    /** @return 프리픽스 길이에 해당하는 32비트 마스크 */
    private static long maskOf(int length) {
        if (length <= 0) {
            return 0L;
        }
        return (0xFFFFFFFFL << (32 - length)) & 0xFFFFFFFFL;
    }

    /** @return 두 주소가 같은 프리픽스에 속하면 {@code true} */
    private static boolean withinPrefix(long a, long b, int length) {
        final long mask = maskOf(length);
        return (a & mask) == (b & mask);
    }
}
