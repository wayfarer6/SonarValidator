package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Config.SiteProperties;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectRule;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Policy.strategy.BatchPolicyContext;
import org.sonar.sonarvalidator_backend.Policy.strategy.DevicePolicies;
import org.sonar.sonarvalidator_backend.Policy.strategy.DevicePolicy;
import org.sonar.sonarvalidator_backend.Policy.strategy.PolicyBuildContext;
import org.sonar.sonarvalidator_backend.Policy.strategy.PolicyJson;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Agent 에게 내려보낼 정책을 만들어 주는 서비스입니다.
 *
 * <h2>역할 — 무엇을 조립하는가</h2>
 * <p>이 서비스는 <b>정책의 뼈대</b>(어느 프로젝트/서브넷인가, 어떤 연결이 있는가,
 * 의도 요약)를 만들고, <b>유형별 규칙 내용</b>은 {@link DevicePolicy}
 * 구현체에 위임합니다.
 *
 * <pre>
 *   Agent(policy-request)
 *        │  agent_id
 *        ▼
 *   ProjectSubnet(agent_id == agent_id)        ← 배정 정보
 *        │  project + rules + ZoneClass 인접 판정
 *        ▼
 *   Connection 목록 (중간 표현)
 *        │
 *        ├─ declarationRule  → DevicePolicy
 *        └─ enforcementRule  → DevicePolicy
 *        ▼
 *   정책 JSON
 * </pre>
 *
 * <h2>⚠️ 유형별 지식이 여기 없다</h2>
 * <p>이전에는 이 클래스에 유형별 {@code switch} 가 네 곳 있었고, 그래서
 * "VM 정책 수정" 이 서로 멀리 떨어진 네 곳을 고치는 일이었습니다. 지금은
 * 유형별 코드가 각자의 전략 클래스에 모여 있습니다.
 * 이 클래스는 <b>조립과 판정</b>만 합니다.
 *
 * <h2>⚠️ 정책 생성 실패가 요청을 막지 않는다</h2>
 * <p>에이전트는 응답이 없으면 재시도만 반복하며 <b>텔레메트리도 멈춥니다.</b>
 * 그래서 어떤 실패든 폴백 선언으로 응답합니다.
 *
 * <h2>문서 스키마 규칙</h2>
 * <p>{@code docs/Agent/*_Policy_Design.md} 의 스키마는 <b>스칼라 값도 배열로
 * 감싼다</b>는 관례를 씁니다. (예: {@code "command": ["create"]})
 * C++ 쪽 {@code policy_json::AsString} 가 배열/스칼라를 모두 받아주므로
 * 서버도 같은 관례를 따릅니다. (한쪽만 스칼라로 바꾸면 계약이 갈라집니다)
 */
@Service
public class PolicyRegistryService {

    private static final Logger log = LoggerFactory.getLogger(PolicyRegistryService.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /**
     * 정책 JSON 의 {@code valid_until} 유효 기간입니다.
     *
     * <p>24시간으로 두는 이유: Agent 가 3초마다 정책을 요청하므로 짧게 잡을
     * 이유가 없고, 서버 장애가 하루 미만이면 기존 정책으로 버틸 수 있습니다.
     */
    private static final long VALID_HOURS = 24;

    /**
     * 허용 목록이 인터넷을 막을 때 만들어지는 <b>합성 연결</b>의 규칙 식별자입니다.
     *
     * <h2>⚠️ 왜 규칙이 아니라 합성인가</h2>
     * <p>인터넷은 프로젝트 서브넷이 아니므로 규칙의 출발지/도착지로 적을 수
     * 없습니다. 그런데 "기밀망은 인터넷으로 나가면 안 된다" 는 요구사항을
     * 실행하려면 <b>기본 경로 전체</b>를 막아야 합니다. 그래서 운영자가 허용
     * 목록에 {@code internet} 을 넣지 않은 경우에만 이 연결을 만들어
     * {@code deny ip <대역> <와일드> 0.0.0.0 255.255.255.255} 를 내려보냅니다.
     *
     * <p>고정 식별자를 쓰는 이유는 UI/로그에서 "규칙 때문" 과 "허용 목록 때문" 을
     * 구분하기 위함입니다.
     */
    public static final String INTERNET_GUARD_RULE = "egress-internet-guard";

    /** 인터넷 전체를 가리키는 대역입니다. (기본 경로) */
    public static final String INTERNET_CIDR = "0.0.0.0/0";

    private boolean automaticEnforcementEnabled = true;

    /** Collect and validate configurations without competing with externally managed ACLs. */
    @org.springframework.beans.factory.annotation.Value("${sonar.policy.automatic-enforcement-enabled:true}")
    public void setAutomaticEnforcementEnabled(boolean enabled) {
        this.automaticEnforcementEnabled = enabled;
    }

    /**
     * 프로젝트 조회 통로입니다.
     *
     * <p>단위 테스트가 저장소 없이 이 서비스를 만들 수 있도록 {@code null} 을
     * 허용합니다. {@code null} 이면 프로젝트 기반 정책을 만들 수 없으므로
     * 항상 {@link #forDevice} 폴백을 씁니다.
     *
     * <p>{@link ProjectService} 가 아니라 저장소를 직접 받는 이유: 프로젝트
     * 서비스는 알림/이력 서비스를 함께 의존하므로, 정책 조회가 그 그래프를
     * 끌어오면 순환 위험과 무거운 초기화가 생깁니다.
     */
    private final ProjectRepository repository;

    /**
     * 유형별 정책 전략입니다. (스프링이 구현체를 주입)
     *
     * <p>{@code null} 을 허용하는 이유는 단위 테스트가 저장소만 넘기기
     * 때문입니다. 그 경우 {@link #FALLBACK_STRATEGIES} 를 씁니다.
     */
    private final DevicePolicies strategies;

    /**
     * 격리 상태 통로입니다. (선택 의존)
     *
     * <p>이 서비스는 정책 <b>내용</b>을 만들고, 격리는 정책을 <b>무효화</b>하는
     * 별개 관심사입니다. 그래서 생성자 주입이 아니라 선택 주입으로 둡니다.
     * 격리 서비스가 없으면(단위 테스트) 정책은 평소대로 만들어집니다.
     *
     * <p>{@code null} 을 허용하지만 <b>운영 경로에서는 반드시 채워집니다.</b>
     * 격리된 장치가 정상 정책을 받으면 격리가 무력해지므로, 이 연결이
     * 끊기면 격리 기능 전체가 조용히 사라집니다.
     */
    private QuarantineService quarantineService;

    /**
     * 사이트 설정입니다. (방화벽 테이블 이름 등)
     *
     * <p>하드코딩을 없애기 위해 주입합니다. 없으면(단위 테스트) 기본값을
     * 쓰는 설정 객체를 그대로 둡니다.
     */
    private SiteProperties siteProperties = new SiteProperties();

    /**
     * 방화벽 정책이 쓰는 nftables 테이블 이름을 돌려줍니다.
     *
     * @return 테이블 이름 (설정값)
     */
    private String firewallTable() {
        return siteProperties.getFirewallDefaults().getTableName();
    }

    /**
     * 사이트 설정을 주입합니다.
     *
     * @param siteProperties 사이트 설정 (테스트에서는 생략 가능)
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSiteProperties(SiteProperties siteProperties) {
        if (siteProperties != null) {
            this.siteProperties = siteProperties;
        }
    }

    /**
     * 저장소 없는 인스턴스를 만듭니다. (테스트/폴백 전용)
     */
    public PolicyRegistryService() {
        this(null, null);
    }

    /**
     * @param repository 프로젝트 저장소 (없으면 프로젝트 기반 정책을 만들지 않음)
     */
    public PolicyRegistryService(ProjectRepository repository) {
        this(repository, null);
    }

    /**
     * @param repository 프로젝트 저장소 (null 허용 — 테스트용)
     * @param strategies 유형별 전략 선택기 (null 이면 공용 폴백 사용)
     */
    @Autowired
    public PolicyRegistryService(ProjectRepository repository, DevicePolicies strategies) {
        this.repository = repository;
        this.strategies = strategies;
    }

    /**
     * 격리 상태 조회 통로를 주입합니다.
     *
     * <p>생성자에 넣지 않은 이유는 순환을 피하기 위함이 아니라,
     * 정책 생성이 격리 <b>없이도</b> 성립해야 하기 때문입니다. 격리는
     * 정책 위에 덧씌우는 제약이지 정책의 일부가 아닙니다.
     *
     * @param quarantineService 격리 서비스 (없으면 null)
     */
    @Autowired(required = false)
    public void setQuarantineService(QuarantineService quarantineService) {
        this.quarantineService = quarantineService;
    }

    // ------------------------------------------------------------------
    //  진입점
    // ------------------------------------------------------------------

    /**
     * Agent 에게 내려보낼 정책을 만듭니다.
     *
     * <p>배정된 서브넷이 있으면 그 프로젝트의 실제 규칙으로 정책을 만들고,
     * 없으면 {@link #forDevice} 로 폴백합니다. 폴백 여부는 응답의
     * {@code summary.source} 로 확인할 수 있습니다.
     *
     * @param agentId    Agent 식별자
     * @param deviceType 장치 유형 (null 이면 VM)
     * @param deviceId   장치 식별자 (null 이면 agentId)
     * @param vendor     텔레메트리에서 관측된 벤더 (모르면 null)
     * @param product    텔레메트리에서 관측된 제품명 (모르면 null)
     * @return Agent 가 바로 적용할 수 있는 정책 JSON
     */
    @Transactional(readOnly = true)
    public ObjectNode forAgent(String agentId,
                               DeviceType deviceType,
                               String deviceId,
                               String vendor,
                               String product) {
        final DeviceType type = (deviceType == null) ? DeviceType.VM : deviceType;
        final String id = PolicyJson.firstNonBlank(deviceId, agentId, "unknown");

        if (!automaticEnforcementEnabled) {
            final ObjectNode policy = forDevice(type, id);
            ((ObjectNode) policy.get("summary")).put("source", "external-enforcement");
            return quarantineOverride(agentId, policy);
        }

        if (this.repository == null) {
            return quarantineOverride(agentId, forDevice(type, id));
        }

        final ProjectMatch match = findSubnetForAgent(this.repository, agentId);
        if (match == null) {
            log.debug("no subnet assigned to agent={}; falling back to default declaration", agentId);
            return quarantineOverride(agentId, forDevice(type, id));
        }

        try {
            return quarantineOverride(agentId, projectPolicy(match, type, id, vendor, product));
        } catch (RuntimeException ex) {
            // 정책 생성 실패가 Agent 의 요청 자체를 막으면 안 됩니다.
            // (에이전트는 응답이 없으면 재시도만 반복하며 텔레메트리도 멈춥니다)
            log.warn("project policy build failed for agent={}: {}; using default declaration",
                    agentId, ex.getMessage());
            return quarantineOverride(agentId, forDevice(type, id));
        }
    }

    /**
     * 격리된 Agent 라면 정책을 <b>차단본</b>으로 덮어씁니다.
     *
     * <h2>⚠️ 왜 정책 생성 시점에 덮어쓰는가</h2>
     * <p>격리는 두 갈래로 장치에 전달됩니다.
     * <ol>
     *   <li>격리 <b>명령</b> ({@code action=quarantine}) — 즉시 적용</li>
     *   <li>정책의 <b>차단본</b> — 재접속/재요청 시에도 유지</li>
     * </ol>
     * 1번만 있으면 장치가 재부팅되거나 프로버가 재시작될 때 격리가 풀립니다.
     * 그래서 격리된 장치가 정책을 요청하면 <b>항상</b> 차단본을 받아야 합니다.
     *
     * <h2>무엇을 덮어쓰는가</h2>
     * <p>정상 규칙을 지우지 않고 <b>차단 규칙을 앞에 세웁니다.</b> 지우면
     * 해제 시 원래 정책을 복원할 근거가 사라지고, 남겨두면 Agent 가
     * {@code drop} 을 먼저 만나 트래픽이 끊깁니다.
     *
     * <p>같은 목록의 기존 {@code accept} 규칙은 {@code drop} 으로 바꿉니다.
     * 순서를 바꾸지 않고 값만 바꾸는 이유는, 장치별 적용기가 규칙 순서에
     * 의존할 수 있기 때문입니다.
     *
     * @param agentId Agent 식별자
     * @param policy  원래 정책 (null 이면 그대로 반환)
     * @return 격리 반영 정책
     */
    private ObjectNode quarantineOverride(String agentId, ObjectNode policy) {
        if (policy == null || quarantineService == null) {
            return policy;
        }

        // ⚠️ DB Design v1.5 — 격리는 두 방식이 있고, 정책 반영도 달라야 합니다.
        //   - NODE      : 장치가 인터페이스를 내림. 정책도 "전부 차단" 으로 동반.
        //   - CONNECTION: 방화벽의 특정 서브넷만 차단. 정책은 <b>그 연결만</b>
        //                 차단으로 제한해야 합니다. 전부 차단하면 트렁크에 붙은
        //                 무관한 존이 함께 죽어, 방화벽을 노드 격리하지 않는
        //                 이유 자체가 무너집니다.
        // Desired state is sent even when empty, so releasing the final VLAN removes
        // only the agent-owned quarantine rules. Never append vendor-specific drops.
        final ArrayNode targets = policy.putArray("subnet_quarantine");
        final var states = quarantineService.activeStates(agentId);
        for (final var state : states) {
            if (state.getScope() != org.sonar.sonarvalidator_backend.Model.entity.QuarantineState.Scope.CONNECTION) continue;
            final ObjectNode target = targets.addObject();
            target.put("id", state.getId());
            target.put("cidr", state.getTargetCidr());
            if (repository != null && state.getProjectKey() != null) {
                repository.findByProjectKey(state.getProjectKey()).ifPresent(project ->
                    project.getSubnets().stream()
                        .filter(subnet -> agentId.equalsIgnoreCase(subnet.getAgentId())
                            && PolicySubnet.normalizeCidr(state.getTargetCidr()).equals(PolicySubnet.normalizeCidr(subnet.getCidr())))
                        .findFirst().ifPresent(subnet -> {
                            if (subnet.getVlanId() != null) target.put("vlan_id", subnet.getVlanId());
                        }));
            }
        }
        if (states.isEmpty()) {
            return policy;
        }

        final var connectionStates = states.stream()
            .filter(state -> state.getScope()
                == org.sonar.sonarvalidator_backend.Model.entity.QuarantineState.Scope.CONNECTION)
            .toList();
        final boolean hasNodeScope = states.stream().anyMatch(state -> state.getScope()
            != org.sonar.sonarvalidator_backend.Model.entity.QuarantineState.Scope.CONNECTION);
        if (hasNodeScope) {
            applyQuarantinePolicy(policy, agentId, false, null);
            return policy;
        }
        if (connectionStates.isEmpty()) {
            return policy;
        }

        final List<String> targetCidrs = connectionStates.stream()
            .map(org.sonar.sonarvalidator_backend.Model.entity.QuarantineState::getTargetCidr)
            .filter(cidr -> cidr != null && !cidr.isBlank())
            .distinct()
            .toList();
        applyQuarantinePolicy(policy, agentId, true, targetCidrs);
        return policy;
    }

    /**
     * 격리를 정책에 반영합니다.
     *
     * @param policy          원래 정책 (직접 수정됩니다)
     * @param agentId         Agent 식별자 (로그용)
     * @param connectionScoped 연결 단위 격리인지 여부
     * @param targetCidr      연결 단위 격리 대상 CIDR (없으면 null)
     */
    private void applyQuarantinePolicy(ObjectNode policy, String agentId,
                                        boolean connectionScoped, List<String> targetCidrs) {
        final String targets = targetCidrs == null ? "" : String.join(", ", targetCidrs);
        // 차단 사실을 응답 자체에 남깁니다. Agent 로그와 서버 상태를 대조할 때
        // "왜 정책이 차단본인가" 를 설명할 유일한 단서입니다.
        policy.put("quarantined", true);
        policy.put("quarantine_scope", connectionScoped ? "connection" : "node");
        if (connectionScoped && !targets.isBlank()) {
            policy.put("quarantine_target_cidr", targets);
        }
        policy.put("quarantine_note", connectionScoped
                ? "이 장치는 운영자에 의해 격리되었습니다. 대상 대역(" + targets
                + ")의 송수신 연결만 차단됩니다."
                : "이 장치는 운영자에 의해 격리되었습니다. 모든 전달 트래픽이 차단됩니다.");

        final ObjectNode summary = policy.has("summary")
                ? (ObjectNode) policy.get("summary")
                : policy.putObject("summary");
        summary.put("quarantined", true);
        summary.put("source", "quarantine");

        // 격리 의도를 가장 앞에 세웁니다.
        final ArrayNode intents = policy.has("intents")
                ? (ArrayNode) policy.get("intents")
                : policy.putArray("intents");
        final ObjectNode intent = JSON.objectNode();
        intent.put("rule_id", "quarantine");
        intent.put("direction", "both");
        intent.put("action", "deny");
        intent.put("reason", connectionScoped
            ? "운영자 격리 — 대상 대역 " + targets + " 연결 차단"
                : "운영자 격리 — 모든 트래픽 차단");
        if (connectionScoped && !targets.isBlank()) {
            final ArrayNode targetArray = intent.putArray("destination_subnet");
            targetCidrs.forEach(targetArray::add);
        }
        intents.insert(0, intent);

        if (connectionScoped) {
            // ⚠️ 연결 단위 격리는 기존 규칙을 전부 뒤집지 않습니다.
            //   대상 대역의 송수신 트래픽만 차단합니다. 나머지는 그대로 두어
            //   무관한 존이 살아 있게 합니다.
            // Prober reconciles subnet_quarantine independently of normal policies,
            // using OVS, nftables or iptables for its actual platform.
            log.warn("policy for agent={} overridden to quarantine (connection-only targets={})",
                    agentId, targets);
            return;
        }

        // 기존 규칙의 허용을 차단으로 되돌립니다. 새 규칙을 추가하지 않는 이유는
        // 장치가 벤더마다 다른 순서 규칙을 만들 수 있어, 값만 바꾸는 편이
        // 결과가 예측 가능하기 때문입니다.
        if (policy.has("policies") && policy.get("policies").isArray()) {
            for (final JsonNode node : policy.get("policies")) {
                if (!node.isObject()) {
                    continue;
                }
                final ObjectNode rule = (ObjectNode) node;
                if (rule.has("action")) {
                    rule.putArray("action").add("drop");
                }
                if (rule.has("rule_target") && rule.get("rule_target").isObject()) {
                    ((ObjectNode) rule.get("rule_target")).putArray("chain_name").add("input");
                }
            }
        }

        log.warn("policy for agent={} overridden to quarantine (block-all)", agentId);
    }

    /**
     * 장비 변경 명령이 없는 빈 정책을 만듭니다. (폴백 경로)
     *
     * <p>배정된 서브넷이 없는 Agent 용입니다. 등록과 수집만으로 기존 네트워크
     * 설정이 바뀌지 않도록 식별 정보와 빈 {@code policies} 배열을 반환합니다.
     *
     * @param deviceType 장치 유형 (null 이면 VM 으로 간주)
     * @param deviceId 장치 식별자
     * @return Agent 가 바로 적용할 수 있는 정책 JSON
     */
    public ObjectNode forDevice(DeviceType deviceType, String deviceId) {
        final DeviceType type = (deviceType == null) ? DeviceType.VM : deviceType;
        final String id = PolicyJson.firstNonBlank(deviceId, "unknown");

        final ObjectNode policy = JSON.objectNode();
        policy.put("policy_id", "pol-" + type.name().toLowerCase(Locale.ROOT) + "-0001");
        policy.put("device_type", type.name());
        policy.put("device_id", id);
        policy.put("valid_until", validUntil());

        // 폴백임을 응답만 보고 알 수 있게 합니다.
        // ("정책이 왜 비어 있지" 를 추적할 때 유일한 단서입니다)
        final ObjectNode summary = policy.putObject("summary");
        summary.put("source", "default");
        summary.put("allowed", 0);
        summary.put("denied", 0);
        summary.put("peers", 0);

        // Registration/collection is not permission to configure the device.
        // In particular, the example firewall declaration contains drop chains.
        // An unassigned agent must receive no commands until a policy is configured.
        policy.putArray("policies");
        return policy;
    }

    // ------------------------------------------------------------------
    //  프로젝트 기반 정책
    // ------------------------------------------------------------------

    /**
     * 프로젝트 데이터로 정책을 만듭니다.
     *
     * @param match      배정된 프로젝트/서브넷
     * @param type       장치 유형
     * @param deviceId   장치 식별자
     * @param vendor     관측된 벤더 (null 허용)
     * @param product    관측된 제품명 (null 허용)
     * @return 정책 JSON
     */
    private ObjectNode projectPolicy(ProjectMatch match,
                                     DeviceType type,
                                     String deviceId,
                                     String vendor,
                                     String product) {
        final Project project = match.project();
        final ProjectSubnet subnet = match.subnet();
        final ZoneClass zone = subnet.getZoneClass();
        final List<Connection> connections = connectionsOf(project, subnet, type);
        final DevicePolicy strategy = policyStrategyFor(type);

        final String subnetId = PolicyJson.firstNonBlank(subnet.getSubnetId(), "subnet");

        final ObjectNode policy = JSON.objectNode();
        policy.put("policy_id", "pol-" + type.name().toLowerCase(Locale.ROOT) + "-"
                + subnetId.toLowerCase(Locale.ROOT).replace(' ', '-'));
        policy.put("device_type", type.name());
        policy.put("device_id", deviceId);
        policy.put("valid_until", validUntil());

        // 정책이 어느 프로젝트/서브넷에서 나왔는지 남깁니다.
        // Agent 로그와 서버 로그를 대조할 때 유일한 연결고리입니다.
        policy.put("project_id", project.getProjectKey());
        policy.put("project_name",
                PolicyJson.firstNonBlank(project.getName(), project.getProjectKey()));
        policy.put("subnet_id", subnetId);
        policy.put("subnet_cidr", subnet.getCidr());
        if (zone != null) {
            policy.put("subnet_class", zone.label());
            policy.put("zone_level", zone.level());
        }
        policy.put("vendor", PolicyJson.firstNonBlank(vendor, strategy.defaultVendor()));
        policy.put("product", PolicyJson.firstNonBlank(product, strategy.defaultProduct()));
        policy.put("model", strategy.defaultProduct());

        // 의도 요약 — 벤더 중립 형태로 함께 실어 보냅니다.
        // UI 와 Prober 가 "무엇이 허용/금지인가" 를 같은 데이터로 봅니다.
        final ArrayNode intents = policy.putArray("intents");
        for (final Connection connection : connections) {
            addIntent(intents, connection);
        }

        final ObjectNode summary = policy.putObject("summary");
        summary.put("source", "project:" + project.getProjectKey());
        summary.put("allowed", countAllowed(connections));
        summary.put("denied", countDenied(connections));
        summary.put("peers", connections.size());

        // 실제 적용 규칙 — 유형별 전략이 만듭니다.
        final ArrayNode policies = policy.putArray("policies");
        policies.add(strategy.declarationRule(
                PolicyBuildContext.forDeclaration(subnet, vendor, product)));

        // 연결 뷰를 한 번만 만들어 규칙 생성과 일괄 집행이 같은 입력을 보게 합니다.
        final List<PolicyBuildContext.ConnectionView> views = new ArrayList<>(connections.size());
        for (final Connection connection : connections) {
            views.add(connection.toView());
        }
        for (final PolicyBuildContext.ConnectionView view : views) {
            final ObjectNode rule = strategy.enforcementRule(
                    PolicyBuildContext.forConnection(subnet, vendor, product, view));
            if (rule != null) {
                policies.add(rule);
            }
        }

        // 여러 연결을 한 번에 내려야 하는 유형(이름으로 다시 쓰는 ACL)은 별도
        // 키로 싣습니다. Prober 는 policies[] 를 적용하기 **전에** 이 노드를
        // 한 번 처리합니다.
        //
        // ⚠️ acl_apply 는 "지금 남아 있어야 하는 규칙 전체" 입니다. 여기서
        //    빼면 장치에서도 빠져야 합니다(운영자가 금지 연결을 지운 경우).
        final ObjectNode aclApply = strategy.batchEnforcementRule(
                new BatchPolicyContext(subnet, vendor, product, views));
        if (aclApply != null) {
            policy.set("acl_apply", aclApply);
        }

        log.info("policy built: project={} subnet={} type={} allowed={} denied={} rules={} acl={}",
                project.getProjectKey(), subnetId, type,
                countAllowed(connections), countDenied(connections), policies.size(),
                aclApply == null ? "none" : "applied");
        return policy;
    }

    /**
     * Agent 에 배정된 서브넷을 찾습니다.
     *
     * <p>프로젝트를 최신순으로 훑어 {@link ProjectSubnet#getAgentId()} 가
     * 일치하는 첫 서브넷을 씁니다. Agent 식별자는 대소문자를 구분하지 않고
     * 비교합니다. (운영자가 {@code VDI-1}, 텔레메트리는 {@code vdi-1} 처럼
     * 적어 넣는 경우가 흔합니다)
     *
     * @param repositoryRef 저장소 (여기서는 항상 non-null)
     * @param agentId       Agent 식별자
     * @return 일치하는 프로젝트/서브넷 (없으면 null)
     */
    private ProjectMatch findSubnetForAgent(ProjectRepository repositoryRef, String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        final String needle = agentId.trim();
        for (final Project project : repositoryRef.findAllByOrderByCreatedAtDesc()) {
            // Operator drafts must not become a partially classified enforcement policy.
            if (project.getSubnets().stream().anyMatch(item -> item.getZoneClass() == null
                    || item.getCidr() == null || item.getCidr().isBlank())) continue;
            for (final ProjectSubnet subnet : project.getSubnets()) {
                if (needle.equalsIgnoreCase(subnet.getAgentId())
                        && subnet.getZoneClass() != null
                        && subnet.getCidr() != null && !subnet.getCidr().isBlank()) {
                    return new ProjectMatch(project, subnet);
                }
            }
        }
        return null;
    }

    /**
     * 이 서브넷이 관련된 연결들을 모읍니다.
     *
     * <p>규칙은 식별자 또는 CIDR 로 서브넷을 가리킬 수 있으므로 양쪽을 색인해
     * 해석합니다. ({@code SegmentationBddEngine} 과 같은 관례)
     *
     * <p>방화벽은 <b>구역 사이의 집행 지점</b>이므로, 자기 서브넷에 닿지 않는
     * 금지 연결도 함께 받습니다. 그래야 방화벽이 실제로 막을 수 있습니다.
     * 다른 유형은 자기 인터페이스에 닿는 연결만 받습니다.
     *
     * @param project 프로젝트
     * @param subnet  이 장치에 배정된 서브넷
     * @param type    장치 유형
     * @return 연결 목록 (규칙 순서 유지)
     */
    private List<Connection> connectionsOf(Project project, ProjectSubnet subnet, DeviceType type) {
        final Map<String, ProjectSubnet> byId = new LinkedHashMap<>();
        final Map<String, ProjectSubnet> byCidr = new LinkedHashMap<>();
        for (final ProjectSubnet item : project.getSubnets()) {
            byId.put(item.getSubnetId(), item);
            if (item.getCidr() != null) {
                byCidr.put(PolicySubnet.normalizeCidr(item.getCidr()), item);
            }
        }

        final boolean firewall = (type == DeviceType.FIREWALL);
        final String ownId = subnet.getSubnetId();

        final List<Connection> result = new ArrayList<>();
        for (final ProjectRule rule : project.getRules()) {
            if (!rule.isEnabled()) {
                continue;
            }
            final ProjectSubnet source = resolve(rule.getSource(), byId, byCidr);
            final ProjectSubnet destination = resolve(rule.getDestination(), byId, byCidr);
            if (source == null || destination == null) {
                continue;
            }

            final boolean outgoing = ownId != null && ownId.equals(source.getSubnetId());
            final boolean incoming = ownId != null && ownId.equals(destination.getSubnetId());

            // 두 가지 이유로 금지될 수 있습니다.
            //   1) 등급을 건너뛰는 직접 연결 (망분리 규칙)
            //   2) 운영자가 적은 허용 목록 밖으로 나가는 연결
            // 등급 위반을 먼저 판정합니다 — 사유 문구가 더 구체적이고, 두 사유가
            // 겹쳐도 운영자가 먼저 고쳐야 할 것은 등급 문제입니다.
            final boolean zoneViolation = ZoneClass.forbidsDirectConnection(
                    source.getZoneClass(), destination.getZoneClass());
            // ⚠️ 상대의 <b>모든 표기</b>(식별자 + CIDR)를 넘깁니다. 화면은
            //    식별자를 보내고 저장된 상대는 CIDR 일 수 있어서, 한쪽만
            //    비교하면 운영자가 허용한 상대가 매칭되지 않습니다.
            final boolean egressViolation = !zoneViolation && outgoing
                    && !subnet.allowsAnyPeer(destination.getSubnetId(), destination.getCidr());
            final boolean forbidden = zoneViolation || egressViolation;

            // 방화벽은 자기 서브넷에 닿지 않아도 금지 연결을 집행합니다.
            if (!outgoing && !incoming && !(firewall && forbidden)) {
                continue;
            }

            final ProjectSubnet peer = outgoing ? destination : source;
            if (peer == null || peer.getCidr() == null) {
                continue;
            }

            result.add(new Connection(
                    rule.getRuleId(),
                    outgoing,
                    peer.getSubnetId(),
                    peer.getCidr(),
                    peer.getZoneClass(),
                    source.getCidr(),
                    destination.getCidr(),
                    rule.getProtocol() == null ? "tcp" : rule.getProtocol(),
                    rule.getPort(),
                    forbidden,
                    egressViolation
                            ? "허용 목록 밖: " + PolicyJson.label(subnet.getZoneClass())
                                    + " 는 [" + String.join(", ", subnet.allowedPeerList())
                                    + "] 만 연결하도록 지정되었습니다."
                            : forbidden
                                    ? "등급 건너뜀: " + PolicyJson.label(source.getZoneClass())
                                            + " -> " + PolicyJson.label(destination.getZoneClass())
                                            + " 직접 연결 금지"
                                    : "프로젝트 규칙 허용"));
        }

        // 허용 목록에 인터넷이 없으면 <b>기본 경로 전체</b>를 막습니다.
        //
        // ⚠️ 규칙에 인터넷이 적혀 있지 않아도 막아야 합니다. 운영자가 "기밀망은
        //    인터넷으로 나가면 안 된다" 고 목록으로 표현했는데, 인터넷은 규칙으로
        //    적기 어려운 대상(대역이 없음)이라 목록이 유일한 표현 수단입니다.
        //    목록이 있는데 인터넷을 안 막으면 요구사항이 그대로 실행되지 않습니다.
        if (subnet.isRestricted() && !subnet.allowsInternet()) {
            addRestrictedInternet(result, subnet);
        }
        return result;
    }

    /**
     * 허용 목록이 인터넷을 막는 경우, <b>기본 경로 전체</b>를 차단하는 연결을 추가합니다.
     *
     * <h2>⚠️ 왜 공개망(Open)에는 넣지 않는가</h2>
     * <p>공개망은 인터넷과 연동되는 것이 정상입니다. 목록에 인터넷을 안 적었다는
     * 이유로 공개망의 인터넷을 끊으면 <b>서비스가 통째로 멈춥니다</b>.
     * 그래서 등급이 Open 이면 인터넷 차단을 만들지 않습니다.
     *
     * <p>나머지 등급은 차단 대상 {@code 0.0.0.0/0} 하나로 표현합니다. ACL 은
     * {@code deny ip <대역> <와일드카드> 0.0.0.0 255.255.255.255} 가 되어
     * 어떤 목적지로도 나갈 수 없습니다.
     *
     * @param result  연결 목록 (여기에 추가합니다)
     * @param subnet  제한된 서브넷
     */
    private static void addRestrictedInternet(List<Connection> result, ProjectSubnet subnet) {
        if (subnet.getZoneClass() == null || subnet.getZoneClass() == ZoneClass.OPEN) {
            return;
        }
        if (subnet.getCidr() == null || subnet.getCidr().isBlank()) {
            return;
        }
        // 중복 추가 방지 (같은 서브넷을 두 번 처리하지 않게)
        for (final Connection existing : result) {
            if (INTERNET_GUARD_RULE.equals(existing.ruleId())) {
                return;
            }
        }
        result.add(new Connection(
                INTERNET_GUARD_RULE,
                true,
                "internet",
                INTERNET_CIDR,
                ZoneClass.OPEN,
                subnet.getCidr(),
                INTERNET_CIDR,
                "ip",
                null,
                true,
                "허용 목록 밖: 인터넷이 허용 대상에 없어 기본 경로(0.0.0.0/0)를 차단합니다."));
    }

    /**
     * 서브넷 참조를 해석합니다. 식별자를 먼저 보고, 없으면 CIDR 로 찾습니다.
     *
     * @param reference 참조 문자열
     * @param byId      식별자 색인
     * @param byCidr    CIDR 색인
     * @return 찾은 서브넷 (없으면 null)
     */
    private ProjectSubnet resolve(String reference,
                                  Map<String, ProjectSubnet> byId,
                                  Map<String, ProjectSubnet> byCidr) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        final String trimmed = reference.trim();
        final ProjectSubnet direct = byId.get(trimmed);
        if (direct != null) {
            return direct;
        }
        return byCidr.get(PolicySubnet.normalizeCidr(trimmed));
    }

    // ------------------------------------------------------------------
    //  헬퍼
    // ------------------------------------------------------------------

    /**
     * 유형에 맞는 정책 전략을 돌려줍니다.
     *
     * <h2>⚠️ 이름을 고친 이유</h2>
     * <p>이전 이름은 {@code strategyFor} 였습니다. "전략을 준다" 는 뜻은
     * 맞지만, 이 프로젝트에는 격리 전략({@code QuarantineMethod}) 처럼
     * <b>다른 종류의 전략</b>도 있습니다. 이름이 무엇의 전략인지 말하지
     * 않으면 호출부를 읽는 사람이 어느 전략인지 알 수 없습니다.
     * 그래서 <b>무엇의 전략인지</b>를 이름에 담습니다.
     *
     * <p>전략 선택기가 없으면(테스트가 저장소만 넘긴 경우) <b>공용 폴백</b>을
     * 씁니다. 이 폴백이 있어야 기존 테스트가 깨지지 않습니다 — 그 테스트들이
     * 검증하려는 것은 정책 내용이지 배선이 아닙니다.
     *
     * @param type 장치 유형
     * @return 전략 (null 이 아님)
     */
    private DevicePolicy policyStrategyFor(DeviceType type) {
        return (strategies != null) ? strategies.of(type) : FALLBACK_STRATEGIES.of(type);
    }

    /**
     * 전략 선택기가 주입되지 않았을 때 쓰는 <b>공용 폴백</b>입니다.
     *
     * <p>전략 구현이 스프링 빈이지만 상태가 없으므로 인스턴스를 재사용해도
     * 안전합니다. 매 호출마다 만들면 낭비입니다.
     *
     * <p>⚠️ 사이트 설정은 <b>기본값 인스턴스</b>를 씁니다. 이 폴백은 전략
     * 선택기가 없을 때(주로 단위 테스트)만 타는 경로이므로, 설정을 주입받지
     * 못해도 기동/정책 생성이 실패하면 안 됩니다. 운영 경로는 스프링이
     * {@link SiteProperties} 를 주입한 빈을 씁니다.
     */
    private static final DevicePolicies FALLBACK_STRATEGIES = buildFallbackPolicies();

    /**
     * 폴백 전략 집합을 만듭니다.
     *
     * @return 기본 설정으로 구성한 전략 선택기
     */
    private static DevicePolicies buildFallbackPolicies() {
        final SiteProperties defaults = new SiteProperties();
        return new DevicePolicies(List.of(
                new org.sonar.sonarvalidator_backend.Policy.strategy.vendor.canonical.ubuntu.VmPolicy(defaults),
                new org.sonar.sonarvalidator_backend.Policy.strategy.vendor.arista.veos.SwitchPolicy(defaults),
                new org.sonar.sonarvalidator_backend.Policy.strategy.vendor.cisco.iosxe.RouterPolicy(defaults),
                new org.sonar.sonarvalidator_backend.Policy.strategy.vendor.linux.nftables.FirewallPolicy(defaults)));
    }

    /** @return 정책 유효 기한 (ISO-8601) */
    private static String validUntil() {
        return Instant.now().plus(VALID_HOURS, ChronoUnit.HOURS).toString();
    }

    /** @param connections 연결 목록 @return 허용 연결 개수 */
    private static int countAllowed(List<Connection> connections) {
        int count = 0;
        for (final Connection connection : connections) {
            if (!connection.forbidden()) {
                count++;
            }
        }
        return count;
    }

    /** @param connections 연결 목록 @return 금지 연결 개수 */
    private static int countDenied(List<Connection> connections) {
        int count = 0;
        for (final Connection connection : connections) {
            if (connection.forbidden()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 의도 요약 한 건을 배열에 넣습니다.
     *
     * @param intents    대상 배열
     * @param connection 연결
     */
    private static void addIntent(ArrayNode intents, Connection connection) {
        final ObjectNode intent = intents.addObject();
        intent.put("rule_id", connection.ruleId());
        intent.put("direction", connection.outgoing() ? "outbound" : "inbound");
        intent.put("peer_subnet_id", connection.peerId());
        intent.put("peer_cidr", connection.peerCidr());
        if (connection.peerClass() != null) {
            intent.put("peer_class", connection.peerClass().label());
        }
        intent.put("protocol", connection.protocol());
        if (connection.port() != null) {
            intent.put("port", connection.port());
        }
        intent.put("action", connection.forbidden() ? "deny" : "allow");
        intent.put("reason", connection.reason());
    }

    /**
     * 이 장치에 배정된 프로젝트와 서브넷입니다.
     *
     * @param project 프로젝트
     * @param subnet  {@code agent_id} 가 일치한 서브넷
     */
    private record ProjectMatch(Project project, ProjectSubnet subnet) {
    }

    /**
     * 서브넷 사이의 연결 한 건입니다. (정책 변환의 중간 표현)
     *
     * <p>전략에 넘길 때는 {@link PolicyBuildContext.ConnectionView} 로 바꿉니다.
     * 전략이 이 record 타입에 의존하지 않게 하기 위함입니다.
     *
     * @param ruleId          규칙 식별자
     * @param outgoing        이 장치의 서브넷이 출발지인지 여부
     * @param peerId          상대 서브넷 식별자
     * @param peerCidr        상대 서브넷 대역
     * @param peerClass       상대 서브넷 등급
     * @param sourceCidr      출발 대역
     * @param destinationCidr 도착 대역
     * @param protocol        프로토콜
     * @param port            허용 포트 (null 이면 미지정)
     * @param forbidden       등급을 건너뛰는 금지 연결인지 여부
     * @param reason          사람이 읽는 판정 사유
     */
    private record Connection(String ruleId,
                              boolean outgoing,
                              String peerId,
                              String peerCidr,
                              ZoneClass peerClass,
                              String sourceCidr,
                              String destinationCidr,
                              String protocol,
                              Integer port,
                              boolean forbidden,
                              String reason) {

        /** @return 전략에 넘길 읽기 전용 뷰 */
        PolicyBuildContext.ConnectionView toView() {
            return new PolicyBuildContext.ConnectionView(
                    ruleId, outgoing, peerId, peerCidr, peerClass,
                    sourceCidr, destinationCidr, protocol, port, forbidden, reason);
        }
    }
}
