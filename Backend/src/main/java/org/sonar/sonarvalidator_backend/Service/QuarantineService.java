package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Config.SiteProperties;
import org.sonar.sonarvalidator_backend.Model.Configuration;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Model.entity.QuarantineState;
import org.sonar.sonarvalidator_backend.Repository.QuarantineStateRepository;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineContext;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineMethods;
import org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Agent 격리(isolation)와 해제를 담당합니다.
 *
 * <h2>격리 명령이 지나는 길</h2>
 * <pre>
 *   운영자 클릭 (프론트)
 *     → POST /api/v1/quarantine/{agentId}   또는  node_id 지정
 *       → QuarantineService.isolate()
 *         ├─ 전략 선택 (QuarantineMethods)
 *         ├─ DB 에 QuarantineState 기록        (판단 근거 남기기)
 *         ├─ WebSocket 으로 command 봉투 전송  (장치에게 실제로 시키기)
 *         ├─ ComplianceService 이력 기록       (무엇이 바뀌었나)
 *         └─ NotificationService 알림 기록     (운영자에게 알리기)
 * </pre>
 *
 * <h2>⚠️ 순서가 중요하다: DB 먼저, 전송 나중</h2>
 * <p>전송을 먼저 하면, 전송은 성공했는데 DB 저장이 실패하는 순간
 * <b>장치는 격리됐는데 서버는 모르는 상태</b>가 됩니다. 그러면 해제 버튼이
 * 뜨지 않아 운영자가 장치를 되살릴 방법이 없습니다.
 *
 * <p>반대로 DB 를 먼저 하면, 전송이 실패해도 <b>"격리하려 했으나 전달 실패"</b>
 * 라는 사실이 남습니다. 운영자가 재시도할 수 있고, 위험한 방향(장치는 살아
 * 있는데 서버는 격리됐다고 믿는 것)이 아닙니다.
 *
 * <h2>⚠️ 격리 방법은 장치마다 다르다 — 전략에 위임한다</h2>
 * <p>이전에는 이 서비스가 "인터페이스를 내린다" 는 <b>한 가지 방법</b>만
 * 알고, 방화벽은 격리 자체를 거부했습니다. 이제 방법은
 * {@link QuarantineMethod} 가 정합니다.
 * <ul>
 *   <li><b>스위치/라우터/VM</b> — Agent 에 명령을 보내 인터페이스를 내림
 *       ({@link QuarantineMethod.Mode#DEVICE})</li>
 *   <li><b>방화벽</b> — 특정 서브넷 연결만 차단
 *       ({@link QuarantineMethod.Mode#SUBNET})</li>
 * </ul>
 * 그래서 벤더/제품이 늘어도 이 서비스는 고치지 않습니다. (개방-폐쇄 원칙)
 *
 * <h2>⚠️ 자동 격리는 하지 않는다</h2>
 * <p>오탐 한 번으로 정상 장비를 끊으면 서비스가 마비됩니다. 격리는 반드시
 * 사람이 누릅니다. 이 서비스는 <b>요청받은 격리를 수행</b>할 뿐,
 * 스스로 판단해 격리하지 않습니다.
 *
 * <h2>⚠️ 연결이 끊긴 장치도 격리할 수 있다</h2>
 * <p>명령은 전달되지 않지만(사실을 응답에 남깁니다) 상태는 DB 에 남습니다.
 * 그래서 그 장치가 다시 접속해 정책을 요청하면
 * {@link #isQuarantined(String)} 가 {@code true} 라서 차단 정책을 받습니다.
 *
 * <h2>⚠️ DB Design v1.5 — node_id 로도 격리한다</h2>
 * <p>OPNsense 처럼 <b>Agent 없이 REST API 로만 연결</b>되는 장비는
 * {@code agent_id} 가 없습니다. 그래서 격리 대상은 {@code node_id} 로도
 * 지정할 수 있고, 상태도 둘 다 남깁니다.
 */
@Service
public class QuarantineService {

    private static final Logger log = LoggerFactory.getLogger(QuarantineService.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /** 격리 이력 조회 시 한 번에 가져올 최대 건수입니다. */
    private static final int HISTORY_LIMIT = 200;

    /**
     * ⚠️ {@code "quarantine"} / {@code "release"} 문자열은 Prober 의
     * {@code envelope.hpp} 에 있는 상수와 <b>정확히 같아야</b> 합니다.
     * 한 글자만 달라도 Agent 는 알 수 없는 명령으로 무시합니다.
     */
    private static final String ACTION_QUARANTINE = "quarantine";
    private static final String ACTION_RELEASE = "release";

    private final QuarantineStateRepository repository;
    private final AgentSessionRegistry registry;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;

    /**
     * 장치 유형 판별기입니다. (방화벽 격리 제외 판단)
     *
     * <p><b>선택 의존</b>입니다. 격리 서비스 단위 테스트가 이 판별기 없이
     * 돌아야 하기 때문입니다(저장소 목이 필요). 없으면 <b>식별자 추론만</b>으로
     * 유형을 판단하므로 {@code GNS3.Firewall} 같은 이름도 제대로 걸러집니다.
     */
    private DeviceTypeResolver deviceTypeResolver;

    /**
     * 보낸 격리/해제 명령의 <b>실제 적용 결과</b>를 기억합니다.
     *
     * <h2>왜 필요한가</h2>
     * <p>{@link AgentSessionRegistry#sendTo} 가 {@code true} 를 돌려주는 것은
     * "소켓에 써 넣었다" 는 뜻일 뿐입니다. 장치가 인터페이스를 실제로 내렸는지는
     * <b>알 수 없습니다.</b> Agent 는 적용 결과를 {@code ack} 봉투로 돌려주고,
     * 그때 격리가 비로소 완결됩니다.
     *
     * <p>그래서 운영자 응답에는 {@code delivered}(보냈나)와
     * {@code applied}(적용됐나)를 구분해 실어 보냅니다. 두 값이 다르면
     * "장치는 살아 있고 서버는 격리됐다고 믿는" 위험한 상태를 화면에서
     * 즉시 알아챌 수 있습니다.
     */
    private final Map<String, Map<String, Object>> lastAck = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 격리 방법 선택기입니다. (장치 유형별 모듈화)
     *
     * <p>{@code null} 을 허용하는 이유는 단위 테스트가 저장소만 넘기기
     * 때문입니다. 없으면 기본 전략 집합을 직접 만듭니다.
     */
    private QuarantineMethods strategies;

    /**
     * 노드 정본 조회 통로입니다. (선택 의존)
     *
     * <p>Agent 없는 장비(OPNsense)를 {@code node_id} 로 격리하거나,
     * {@code agent_id} 로 들어온 요청을 노드 번호로 해석할 때 씁니다.
     * 없으면 문자열 식별자만으로 동작합니다.
     */
    private NodeRegistryService nodeRegistry;

    /**
     * 사이트 설정입니다. (제어평면 대역의 전역 기본값)
     *
     * <h2>⚠️ 왜 @Value 가 아니라 설정 클래스인가</h2>
     * <p>제어평면 대역은 <b>프로젝트마다 지정</b>할 수 있어야 합니다
     * (요구사항). {@code @Value} 하나로는 전역 값만 읽을 수 있어 프로젝트
     * 지정을 표현할 수 없습니다. 그래서 {@link SiteProperties} 를 주입해
     * {@code managementPrefixFor(프로젝트값)} 로 해석합니다.
     *
     * <p>설정 객체가 없으면(단위 테스트) 기본 대역을 씁니다.
     */
    private SiteProperties siteProperties = new SiteProperties();

    /**
     * 프로젝트 저장소입니다. (제어평면 대역 조회용, 선택 의존)
     *
     * <p>프로젝트가 지정한 관리 대역을 읽기 위해서만 씁니다. 없으면 전역
     * 기본값을 쓰므로 단위 테스트가 저장소 없이 돌아갑니다.
     */
    private org.sonar.sonarvalidator_backend.Repository.ProjectRepository projectRepository;

    /**
     * @param repository          격리 상태 저장소
     * @param registry            Agent 세션 레지스트리 (명령 전송)
     * @param complianceService   변경 이력 서비스
     * @param notificationService 알림 서비스
     */
    public QuarantineService(QuarantineStateRepository repository,
                             AgentSessionRegistry registry,
                             ComplianceService complianceService,
                             NotificationService notificationService) {
        this.repository = repository;
        this.registry = registry;
        this.complianceService = complianceService;
        this.notificationService = notificationService;
    }

    /**
     * 격리 상태 조회 통로를 주입합니다.
     *
     * @param deviceTypeResolver 장치 유형 판별기 (테스트에서는 생략 가능)
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDeviceTypeResolver(DeviceTypeResolver deviceTypeResolver) {
        this.deviceTypeResolver = deviceTypeResolver;
    }

    /**
     * 격리 방법 선택기를 주입합니다.
     *
     * @param strategies 격리 전략 선택기 (테스트에서는 생략 가능)
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setStrategies(QuarantineMethods strategies) {
        this.strategies = strategies;
    }

    /**
     * 노드 정본 조회 통로를 주입합니다.
     *
     * @param nodeRegistry 노드 등록 서비스 (테스트에서는 생략 가능)
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNodeRegistry(NodeRegistryService nodeRegistry) {
        this.nodeRegistry = nodeRegistry;
    }

    /**
     * 사이트 설정을 주입합니다.
     *
     * @param siteProperties 사이트 설정 (제어평면 대역 기본값)
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSiteProperties(SiteProperties siteProperties) {
        if (siteProperties != null) {
            this.siteProperties = siteProperties;
        }
    }

    /**
     * 프로젝트 저장소를 주입합니다. (제어평면 대역 조회용)
     *
     * @param projectRepository 프로젝트 저장소 (테스트에서는 생략 가능)
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProjectRepository(
            org.sonar.sonarvalidator_backend.Repository.ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    // ------------------------------------------------------------------
    //  격리 / 해제
    // ------------------------------------------------------------------

    /**
     * Agent 를 격리합니다.
     *
     * <p>이미 격리 중이면 <b>명령만 다시 보냅니다.</b> 새 행을 만들지 않는
     * 이유는, 격리 중에 재시도하는 것은 "그때 명령이 안 갔을 수도 있으니
     * 다시 보내자" 는 뜻이지 새로운 사건이 아니기 때문입니다.
     *
     * <p>격리 방법은 {@link QuarantineMethod} 가 정합니다. 방화벽처럼
     * 장치 단위 격리가 불가한 장치는 {@code targetCidr} 을 함께 넘겨
     * <b>특정 서브넷 연결만</b> 차단합니다.
     *
     * @param agentId     격리할 Agent 식별자 (없으면 null)
     * @param nodeId      격리할 노드 번호 (Agent 없는 장비용, 없으면 null)
     * @param projectKey  프로젝트 키 (없으면 null)
     * @param reason      격리 사유 (운영자 메모)
     * @param requestedBy 요청 주체
     * @param targetCidr  연결 단위 격리 대상 CIDR (방화벽용, 없으면 null)
     * @return 격리 결과
     */
    @Transactional
    public Map<String, Object> isolate(String agentId,
                                       Integer nodeId,
                                       String projectKey,
                                       String reason,
                                       String requestedBy,
                                       String targetCidr) {
        final String operator = requestedBy == null || requestedBy.isBlank() ? "operator" : requestedBy;

        // ⚠️ DB Design v1.5 — Agent 가 없어도(node_id 만으로) 격리할 수 있어야 합니다.
        //   OPNsense 처럼 REST API 전용 장비가 그래서, node_id 로 노드 정본을
        //   찾아 유형/식별자를 보완합니다.
        final Configuration node = resolveNode(agentId, nodeId);
        final String effectiveAgentId = firstNonBlank(agentId,
                node == null ? null : node.getAgentId());
        // ⚠️ 삼항 연산자에 int 와 Integer 가 섞이면 <b>전체가 int 로 강제</b>되어
        //   nodeId 가 null 일 때 NPE 가 납니다. 반드시 Integer 로 감싸세요.
        final Integer effectiveNodeId = node != null
                ? node.getNodeId() : nodeId;
        final String display = firstNonBlank(effectiveAgentId,
                effectiveNodeId == null ? null : "node-" + effectiveNodeId, "unknown");

        final DeviceType deviceType = resolveDeviceType(effectiveAgentId, node);
        final QuarantineMethod strategy = strategies().of(deviceType);
        // ⚠️ 제어평면 대역은 프로젝트가 지정하면 그 값을 우선합니다.
        //   (요구사항 — 프로젝트마다 관리망이 다를 수 있음)
        final QuarantineContext context = new QuarantineContext(
                effectiveNodeId, effectiveAgentId, deviceType, projectKey,
                targetCidr, resolveManagementPrefix(projectKey));

        // ⚠️ 격리 방식이 SUBNET 인데 대상이 없으면 거부합니다.
        //   "무엇을 막을 것인가" 없이 방화벽을 격리하면 전체 차단이 되어
        //   서비스가 마비됩니다. (예전에는 방화벽 격리를 아예 거부했지만,
        //   이제는 대상을 지정하면 특정 연결만 막습니다)
        if (strategy.mode() == QuarantineMethod.Mode.UNSUPPORTED) {
            final String why = strategy.exclusionReason() == null
                    ? "이 장치는 격리 대상이 아닙니다." : strategy.exclusionReason();
            log.warn("rejected isolation of {} ({}) — not isolatable", display, deviceType);
            return excludedResponse(display, projectKey, deviceType, why);
        }
        final boolean connectionScoped = context.hasSubnet();
        if (connectionScoped) {
            try {
                final var target = org.sonar.sonarvalidator_backend.Policy.PacketVariables.parseCidr(targetCidr);
                for (final String prefix : resolveManagementPrefix(projectKey).split(",")) {
                    final var management = org.sonar.sonarvalidator_backend.Policy.PacketVariables.parseCidr(prefix.trim());
                    final int length = Math.min(target.prefixLength(), management.prefixLength());
                    final int mask = length == 0 ? 0 : -1 << (32 - length);
                    if ((target.address() & mask) == (management.address() & mask)) {
                        return excludedResponse(display, projectKey, deviceType, "관리망과 겹치는 서브넷은 격리할 수 없습니다.");
                    }
                }
            } catch (IllegalArgumentException ex) {
                return excludedResponse(display, projectKey, deviceType, "유효한 IPv4 CIDR을 지정해야 합니다.");
            }
        }
        if (connectionScoped && deviceType != DeviceType.SWITCH
                && deviceType != DeviceType.ROUTER && deviceType != DeviceType.FIREWALL) {
            return excludedResponse(display, projectKey, deviceType,
                    "VLAN 서브넷 격리는 스위치, 라우터, 방화벽에서만 지원됩니다.");
        }
        if (targetCidr != null && !targetCidr.isBlank()
            && !isManagedSubnet(projectKey, effectiveAgentId, targetCidr)) {
            return excludedResponse(display, projectKey, deviceType,
                "선택한 서브넷이 해당 Agent의 프로젝트 관리 대상과 일치하지 않습니다.");
        }
        if (strategy.mode() == QuarantineMethod.Mode.SUBNET && !connectionScoped) {
            log.warn("rejected subnet-scoped isolation of {} — target_cidr missing", display);
            return excludedResponse(display, projectKey, deviceType,
                    strategy.exclusionReason() == null
                            ? "이 장치는 특정 서브넷만 격리할 수 있습니다. target_cidr 을 지정하세요."
                            : strategy.exclusionReason());
        }

        final Optional<QuarantineState> active = connectionScoped
            ? findActive(effectiveAgentId, effectiveNodeId, targetCidr)
            : findActive(effectiveAgentId, effectiveNodeId);
        final boolean retry = active.isPresent();

        final QuarantineState state;
        if (retry) {
            // 이미 격리 중 — 새 사건이 아니므로 상태는 그대로 두고 명령만 재전송합니다.
            state = active.get();
            if (reason != null && !reason.isBlank()) {
                state.setReason(reason);
            }
            log.info("agent {} already quarantined; re-sending command", display);
        } else {
            final QuarantineState created = new QuarantineState();
            created.setAgentId(effectiveAgentId);
            created.setNodeId(effectiveNodeId);
            created.setProjectKey(projectKey);
            created.setReason(reason);
            created.setRequestedBy(operator);
            created.setQuarantinedAt(new Date());
            created.setCommandDelivered(false);
                created.setScope(connectionScoped
                    ? QuarantineState.Scope.CONNECTION : QuarantineState.Scope.NODE);
                created.setTargetCidr(connectionScoped ? targetCidr : null);
            state = repository.save(created);
            log.warn("quarantining {} type={} scope={} target={} project={} by={} reason={}",
                    display, deviceType, created.getScope(), created.getTargetCidr(),
                    projectKey, operator, reason);
        }

        // DB 를 먼저 남긴 뒤에 명령을 보냅니다. (위 "순서가 중요하다" 참고)
        // ⚠️ 방식에 따라 전달 경로가 다릅니다.
        //   - DEVICE : Agent 에게 인터페이스를 내리라고 명령을 보냅니다.
        //   - SUBNET : Agent 에게 보내지 <b>않습니다.</b> 방화벽에 인터페이스
        //     down 을 지시하면 트렁크에 붙은 모든 VLAN 이 함께 죽어, 방화벽을
        //     노드 격리하지 않는 이유 자체가 무너집니다. 대신 서버가 정책
        //     푸시({@code quarantineOverride})로 <b>대상 서브넷만</b> 차단하는
        //     규칙을 내려보냅니다.
        final boolean delivered = !connectionScoped
            && strategy.mode() == QuarantineMethod.Mode.DEVICE && context.hasAgent()
                && sendCommand(effectiveAgentId, ACTION_QUARANTINE, projectKey, state.getReason(),
                        strategy.mode(), state.getTargetCidr());
        if (delivered != state.isCommandDelivered()) {
            state.setCommandDelivered(delivered);
            repository.save(state);
        }

        final List<String> warnings = buildWarnings(strategy, context);
        final String riskNote = warnings.isEmpty() ? "" : " ⚠️ " + String.join(" ", warnings);

        // 격리는 보안 사건입니다. 이력과 알림 <b>양쪽</b>에 남깁니다.
        // 이력은 "무엇이 바뀌었나"(감사), 알림은 "지금 조치가 필요하다"(경고)입니다.
        complianceService.recordQuietly(
                "Agent",
                projectKey,
                display,
                retry ? "QuarantineRetry" : "Quarantine",
                "Agent " + display + " 격리"
                        + (state.getReason() == null ? "" : " — " + state.getReason())
                        + (delivered ? " (명령 전달됨)" : " (⚠️ 명령 미전달 — 장치 미연결)")
                        + (state.getTargetCidr() == null ? "" : " [대상 " + state.getTargetCidr() + "]"),
                operator,
                "{\"agent_id\":\"" + display + "\",\"delivered\":" + delivered
                        + ",\"scope\":\"" + state.getScope() + "\"}");

        notificationService.notifyQuietly(
                "SECURITY",
                delivered ? "critical" : "warning",
                "Agent 격리: " + display,
                quarantineMessage(delivered, display, state) + riskNote,
                projectKey,
                effectiveAgentId,
                "quarantine",
                "/agent",
                "quarantine:" + display);

        return withWarnings(toResponse(state, delivered, retry), warnings);
    }

    /**
     * 하위 호환 격리 경로입니다. (agent_id 만으로 격리)
     *
     * <p>기존 호출부/테스트를 위해 남깁니다. 새 코드는
     * {@link #isolate(String, Integer, String, String, String, String)} 를
     * 쓰세요.
     *
     * @param agentId     격리할 Agent 식별자
     * @param projectKey  프로젝트 키
     * @param reason      격리 사유
     * @param requestedBy 요청 주체
     * @return 격리 결과
     */
    @Transactional
    public Map<String, Object> isolate(String agentId,
                                       String projectKey,
                                       String reason,
                                       String requestedBy) {
        return isolate(agentId, null, projectKey, reason, requestedBy, null);
    }

    /**
     * 노드 번호만으로 격리합니다. (Agent 없는 장비)
     *
     * @param nodeId      노드 번호 ({@code configuration.node_id})
     * @param projectKey  프로젝트 키
     * @param reason      격리 사유
     * @param requestedBy 요청 주체
     * @param targetCidr  연결 단위 격리 대상 CIDR (방화벽용)
     * @return 격리 결과
     */
    @Transactional
    public Map<String, Object> isolateByNode(Integer nodeId,
                                             String projectKey,
                                             String reason,
                                             String requestedBy,
                                             String targetCidr) {
        return isolate(null, nodeId, projectKey, reason, requestedBy, targetCidr);
    }

    /**
     * Agent 의 격리를 해제합니다.
     *
     * <p>격리 중이 아니면 <b>아무것도 하지 않고</b> 그 사실을 알려줍니다.
     * "해제했습니다" 라고 거짓 응답하면 운영자는 장치가 풀렸다고 믿습니다.
     *
     * @param agentId    해제할 Agent 식별자
     * @param releasedBy 요청 주체
     * @return 해제 결과
     */
    @Transactional
    public Map<String, Object> release(String agentId, String releasedBy) {
        return release(agentId, null, releasedBy);
    }

    /**
     * 격리를 해제합니다. (Agent 식별자 또는 노드 번호)
     *
     * <p>격리 중이 아니면 <b>아무것도 하지 않고</b> 그 사실을 알려줍니다.
     * "해제했습니다" 라고 거짓 응답하면 운영자는 장치가 풀렸다고 믿습니다.
     *
     * <p>⚠️ DB Design v1.5 — Agent 없는 장비(OPNsense)는 {@code nodeId} 로
     * 해제합니다.
     *
     * @param agentId    해제할 Agent 식별자 (없으면 null)
     * @param nodeId     해제할 노드 번호 (없으면 null)
     * @param releasedBy 요청 주체
     * @return 해제 결과
     */
    @Transactional
    public Map<String, Object> release(String agentId, Integer nodeId, String releasedBy) {
        return release(agentId, nodeId, releasedBy, null);
    }

    @Transactional
    public Map<String, Object> release(String agentId, Integer nodeId, String releasedBy, String targetCidr) {
        final String operator = releasedBy == null || releasedBy.isBlank() ? "operator" : releasedBy;

        final Configuration node = resolveNode(agentId, nodeId);
        final String effectiveAgentId = firstNonBlank(agentId,
                node == null ? null : node.getAgentId());
        // ⚠️ 삼항 연산자에 int 와 Integer 가 섞이면 <b>전체가 int 로 강제</b>되어
        //   nodeId 가 null 일 때 NPE 가 납니다. 반드시 Integer 로 감싸세요.
        final Integer effectiveNodeId = node != null
                ? node.getNodeId() : nodeId;
        final String display = firstNonBlank(effectiveAgentId,
                effectiveNodeId == null ? null : "node-" + effectiveNodeId, agentId);

        final Optional<QuarantineState> active = targetCidr == null || targetCidr.isBlank()
            ? findActive(effectiveAgentId, effectiveNodeId)
            : findActive(effectiveAgentId, effectiveNodeId, targetCidr);
        if (active.isEmpty()) {
            final Map<String, Object> body = new LinkedHashMap<>();
            body.put("agent_id", effectiveAgentId);
            body.put("node_id", effectiveNodeId);
            body.put("released", false);
            body.put("reason", "agent is not quarantined");
            return body;
        }

        final QuarantineState state = active.get();
        state.setReleasedAt(new Date());
        state.setReleasedBy(operator);
        repository.save(state);

        // 해제 명령을 보내 장치가 인터페이스를 다시 올리게 합니다.
        // ⚠️ 연결 단위 격리(방화벽)는 Agent 에게 아무 명령도 보내지 않았으므로
        //   해제도 보내지 않습니다. 규칙을 되돌리는 것은 정책 재생성입니다.
        final boolean deviceScoped = state.getScope() != QuarantineState.Scope.CONNECTION;
        final boolean delivered = deviceScoped
                && effectiveAgentId != null && !effectiveAgentId.isBlank()
                && sendCommand(effectiveAgentId, ACTION_RELEASE, state.getProjectKey(), null,
                        QuarantineMethod.Mode.DEVICE, null);

        log.info("released {} by={} delivered={}", display, operator, delivered);

        complianceService.recordQuietly(
                "Agent",
                state.getProjectKey(),
                display,
                "QuarantineRelease",
                "Agent " + display + " 격리 해제"
                        + (delivered ? " (명령 전달됨)" : " (⚠️ 명령 미전달 — 장치 미연결)"),
                operator,
                "{\"agent_id\":\"" + display + "\",\"delivered\":" + delivered + "}");

        notificationService.notifyQuietly(
                "SECURITY",
                delivered ? "info" : "warning",
                "Agent 격리 해제: " + display,
                delivered
                        ? "장치 " + display + " 의 격리를 해제했습니다. 정상 정책이 다시 적용됩니다."
                        : "장치 " + display + " 의 격리를 해제했으나 명령이 전달되지 않았습니다. "
                                + "장치가 연결되면 정상 정책이 적용됩니다.",
                state.getProjectKey(),
                effectiveAgentId,
                "quarantine",
                "/agent",
                "quarantine-release:" + display);

        // 해제는 같은 키로 알림을 합치지 않습니다. 격리/해제가 반복되면
        // 합쳐져서 "지금 격리 중인가" 를 알 수 없게 되기 때문입니다.
        return withReleasedFlag(toResponse(state, delivered, false), true);
    }

    /**
     * 해제 응답에 {@code released} 를 명시합니다.
     *
     * <h2>⚠️ 왜 필요한가</h2>
     * <p>{@link #toResponse} 는 격리 응답용이라 {@code released} 를 넣지
     * 않습니다. 그러면 해제 <b>성공</b> 응답에도 이 키가 없고,
     * 클라이언트는 {@code result.released} 가 {@code undefined} 인 것을 보고
     * <b>"격리 중이 아니었다"</b> 로 해석합니다. 실제로는 성공했는데 화면이
     * 정반대 메시지를 띄우는 셈입니다. (최종 E2E 에서 실제로 재현)
     *
     * <p>실패 경로만 {@code released:false} 를 넣고 성공 경로는 빠뜨리면,
     * "키가 없다 = 실패" 라는 규칙을 클라이언트가 알 수 없습니다.
     * <b>두 경로 모두 명시</b>해야 합니다.
     *
     * @param body     격리 응답 본문
     * @param released 해제가 실제로 일어났는지
     * @return {@code released} 가 포함된 본문
     */
    private static Map<String, Object> withReleasedFlag(Map<String, Object> body, boolean released) {
        body.put("released", released);
        return body;
    }

    /**
     * 격리 대상이 아니라는 응답을 만듭니다.
     *
     * <h2>⚠️ 행을 만들지 않는다</h2>
     * <p>거부된 요청에 대해 격리 행을 남기면 이후 목록/이력에 "격리 중" 인 것처럼
     * 보입니다. 거부는 <b>상태 변화가 없습니다.</b> 그래서 DB/명령/알림 어느 것도
     * 건드리지 않고 응답만 만듭니다.
     *
     * <p>대신 <b>이력에는 남깁니다.</b> "왜 방화벽이 격리 안 되나" 를 운영자가
     * 나중에 물을 수 있고, 시도 자체는 감사 대상입니다.
     *
     * @param agentId    Agent 식별자
     * @param projectKey 프로젝트 키
     * @param type       장치 유형
     * @param why        거부 사유
     * @return 운영자 응답
     */
    private Map<String, Object> excludedResponse(String agentId,
                                                 String projectKey,
                                                 DeviceType type,
                                                 String why) {
        complianceService.recordQuietly(
                "Agent",
                projectKey,
                agentId,
                "QuarantineRejected",
                "Agent " + agentId + " 격리 거부 — " + why,
                "system",
                "{\"agent_id\":\"" + agentId + "\",\"device_type\":\""
                        + DeviceTypeResolver.nameOf(type) + "\"}");

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", agentId);
        body.put("project_id", projectKey);
        body.put("device_type", DeviceTypeResolver.nameOf(type));
        body.put("quarantined", false);
        body.put("delivered", false);
        body.put("applied", false);
        body.put("rejected", true);
        body.put("reason", why);
        // ⚠️ 방화벽은 이제 "연결만 차단" 으로 격리할 수 있으므로, 안내를
        //   바꿉니다. (예전에는 "격리 불가" 였습니다)
        body.put("hint", type == DeviceType.FIREWALL
                ? "target_cidr 에 격리할 서브넷을 지정하면 그 연결만 차단합니다."
                : "프로젝트 규칙에서 해당 연결만 차단하세요.");
        return withReleasedFlag(body, false);
    }

    /**
     * 장치 유형을 판별합니다. (노드 정본이 있으면 그 유형 우선)
     *
     * @param agentId Agent 식별자 (없으면 null)
     * @param node    노드 정본 (없으면 null)
     * @return 장치 유형 (절대 null 이 아님)
     */
    private DeviceType resolveDeviceType(String agentId, Configuration node) {
        // 노드 정본에 유형이 있으면 그것이 가장 신뢰할 수 있습니다.
        if (node != null && node.getDeviceType() != null) {
            return node.getDeviceType();
        }
        if (deviceTypeResolver != null) {
            return deviceTypeResolver.resolve(agentId, null);
        }
        // 판별기가 없으면(단위 테스트) 식별자 관례만으로 판단합니다.
        // GNS3.Firewall / DMZ-Firewall 처럼 끝 토큰이 FIREWALL 이면 걸러집니다.
        return DeviceTypeResolver.resolveWithoutRepository(agentId, null);
    }

    /**
     * 격리 전략 선택기를 돌려줍니다. (없으면 기본 집합 생성)
     *
     * @return 선택기 (절대 null 이 아님)
     */
    private QuarantineMethods strategies() {
        if (strategies == null) {
            strategies = DEFAULT_STRATEGIES;
        }
        return strategies;
    }

    /**
     * 전략이 알려준 경고를 응답에 실어 보냅니다.
     *
     * @param body     응답 본문
     * @param warnings 경고 문장 목록
     * @return 경고가 포함된 본문
     */
    private static Map<String, Object> withWarnings(Map<String, Object> body, List<String> warnings) {
        if (warnings != null && !warnings.isEmpty()) {
            body.put("warnings", warnings);
        }
        return body;
    }

    /**
     * 격리 알림 본문을 만듭니다.
     *
     * @param delivered 명령 전달 여부
     * @param display   표시용 이름
     * @param state     격리 상태
     * @return 사람이 읽는 문장
     */
    private static String quarantineMessage(boolean delivered, String display, QuarantineState state) {
        if (state.getScope() == QuarantineState.Scope.CONNECTION) {
            return "위반 장치 " + display + " 의 연결 "
                    + (state.getTargetCidr() == null ? "(대상 미지정)" : state.getTargetCidr())
                    + " 을(를) 차단했습니다. (방화벽 규칙 — 인터페이스는 유지)";
        }
        return delivered
                ? "위반 장치 " + display + " 를 격리했습니다. 해당 장치의 트래픽이 차단됩니다."
                : "위반 장치 " + display + " 를 격리하려 했으나 명령이 전달되지 않았습니다. "
                        + "(장치 미연결) 재접속 시 차단 정책이 적용됩니다.";
    }

    /**
     * 제어평면(관리망) 대역을 해석합니다. (프로젝트 지정 우선)
     *
     * <h2>⚠️ 우선순위</h2>
     * <ol>
     *   <li>프로젝트가 지정한 {@code management_prefix}</li>
     *   <li>전역 기본값 ({@code sonar.site.management-prefix})</li>
     * </ol>
     *
     * <p>이 대역은 격리에서 <b>절대 차단 대상이 되어서는 안 됩니다.</b>
     * 해제 명령이 도달하지 못하면 장치를 되살릴 수 없습니다.
     *
     * @param projectKey 프로젝트 키 (없으면 null)
     * @return 실제 사용할 대역 (없으면 빈 문자열)
     */
    private String resolveManagementPrefix(String projectKey) {
        String projectPrefix = null;
        // 프로젝트 조회는 저장소가 없으면(단위 테스트) 건너뜁니다.
        if (projectKey != null && !projectKey.isBlank() && projectRepository != null) {
            try {
                projectPrefix = projectRepository.findByProjectKey(projectKey)
                        .map(org.sonar.sonarvalidator_backend.Model.entity.Project::getManagementPrefix)
                        .orElse(null);
            } catch (RuntimeException ex) {
                // 프로젝트 조회 실패가 격리를 막으면 안 됩니다. 전역 값으로 폴백합니다.
                log.warn("project lookup failed for management prefix: {}", ex.getMessage());
            }
        }
        return siteProperties.managementPrefixFor(projectPrefix);
    }

    /**
     * 격리 전략을 찾습니다. (Agent 식별자 또는 노드 번호)
     *
     * @param agentId Agent 식별자 (없으면 null)
     * @param nodeId  노드 번호 (없으면 null)
     * @return 노드 정본 (없으면 null)
     */
    private Configuration resolveNode(String agentId, Integer nodeId) {
        if (nodeRegistry == null) {
            return null;
        }
        try {
            if (agentId != null && !agentId.isBlank()) {
                final Configuration byAgent = nodeRegistry.find(agentId);
                if (byAgent != null) {
                    return byAgent;
                }
            }
            // 노드 번호만 온 경우 — 노드 등록 서비스가 번호 조회를 제공하지
            // 않으므로 여기서는 문자열 식별자 경로만 씁니다.
            return null;
        } catch (RuntimeException ex) {
            log.warn("node lookup failed for agent={} node={}: {}", agentId, nodeId, ex.getMessage());
            return null;
        }
    }

    /**
     * 현재 격리 중인 상태를 찾습니다. (Agent 식별자 또는 노드 번호)
     *
     * @param agentId Agent 식별자 (없으면 null)
     * @param nodeId  노드 번호 (없으면 null)
     * @return 격리 상태 (없으면 비어 있음)
     */
    private Optional<QuarantineState> findActive(String agentId, Integer nodeId) {
        if (agentId != null && !agentId.isBlank()) {
            final Optional<QuarantineState> byAgent =
                    repository.findByAgentIdAndReleasedAtIsNull(agentId);
            if (byAgent.isPresent()) {
                return byAgent;
            }
        }
        if (nodeId != null) {
            return repository.findByNodeIdAndReleasedAtIsNull(nodeId);
        }
        return Optional.empty();
    }

    private Optional<QuarantineState> findActive(String agentId, Integer nodeId, String targetCidr) {
        if (agentId != null && !agentId.isBlank()) {
                final Optional<QuarantineState> byAgent =
                    repository.findFirstByAgentIdAndTargetCidrAndReleasedAtIsNull(agentId, targetCidr);
            if (byAgent.isPresent()) {
                return byAgent;
            }
        }
        if (nodeId != null) {
            return repository.findFirstByNodeIdAndTargetCidrAndReleasedAtIsNull(nodeId, targetCidr);
        }
        return Optional.empty();
    }

    /**
     * 첫 번째로 비어 있지 않은 문자열을 돌려줍니다.
     *
     * @param values 후보 값들
     * @return 첫 유효 값 (모두 비면 null)
     */
    private static String firstNonBlank(String... values) {
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 격리 전략 선택기가 주입되지 않았을 때 쓰는 기본 집합입니다.
     *
     * <p>전략 구현이 스프링 빈이지만 상태가 없으므로 인스턴스를 재사용해도
     * 안전합니다. 매 호출마다 만들면 낭비입니다.
     */
    private static final QuarantineMethods DEFAULT_STRATEGIES = new QuarantineMethods(
            List.of(
                    new org.sonar.sonarvalidator_backend.Service.quarantine.vendor.arista.veos.SwitchQuarantine(),
                    new org.sonar.sonarvalidator_backend.Service.quarantine.vendor.cisco.iosxe.RouterQuarantine(),
                    new org.sonar.sonarvalidator_backend.Service.quarantine.vendor.canonical.ubuntu.VmQuarantine(),
                    new org.sonar.sonarvalidator_backend.Service.quarantine.vendor.linux.nftables.FirewallQuarantine()));

    /**
     * 전략이 알려준 경고 문장을 만듭니다.
     *
     * @param strategy 격리 전략
     * @param context  격리 대상 정보
     * @return 경고 문장 목록 (없으면 빈 목록)
     */
    private static List<String> buildWarnings(QuarantineMethod strategy, QuarantineContext context) {
        try {
            return QuarantineContext.nonNull(strategy.warnings(context));
        } catch (RuntimeException ex) {
            // 경고 생성 실패가 격리 자체를 막으면 안 됩니다.
            return List.of();
        }
    }

    /**
     * Agent 에게 격리/해제 명령을 보냅니다.
     *
     * <h2>⚠️ 방식에 따라 payload 가 다르다</h2>
     * <p>{@link QuarantineMethod.Mode#DEVICE} 는 장치가 인터페이스를 내리게
     * 하고, {@link QuarantineMethod.Mode#SUBNET} 은 장치가 아니라
     * <b>서버가 규칙을 넣는</b> 방식이라 Agent 에게 인터페이스 조작을
     * 지시하지 않습니다. 그래서 {@code scope} 와 {@code target_cidr} 을
     * payload 에 실어 Agent 가 다르게 해석하게 합니다.
     *
     * @param agentId    대상 Agent
     * @param action     {@code "quarantine"} 또는 {@code "release"}
     * @param projectKey 프로젝트 키 (Agent 가 로그에 남김)
     * @param reason     사유 (격리일 때만)
     * @param mode       격리 방식
     * @param targetCidr 연결 단위 격리 대상 CIDR (없으면 null)
     * @return 전달 성공 여부 (미연결이면 {@code false})
     */
    private boolean sendCommand(String agentId, String action, String projectKey, String reason,
                                QuarantineMethod.Mode mode, String targetCidr) {
        if (agentId == null || agentId.isBlank()) {
            // Agent 가 없는 장비(REST 전용)는 명령을 보낼 대상이 없습니다.
            return false;
        }
        final ObjectNode payload = JSON.objectNode();
        payload.put("action", action);
        payload.put("agent_id", agentId);
        // ⚠️ Agent 가 방식에 따라 다르게 동작하도록 scope 를 실어 보냅니다.
        //   SUBNET 은 "인터페이스를 내리지 말고 규칙만 반영" 이라는 뜻입니다.
        payload.put("scope", mode == QuarantineMethod.Mode.SUBNET ? "connection" : "node");
        if (targetCidr != null && !targetCidr.isBlank()) {
            payload.put("target_cidr", targetCidr);
        }
        if (projectKey != null) {
            payload.put("project_id", projectKey);
        }
        if (reason != null && !reason.isBlank()) {
            payload.put("reason", reason);
        }
        payload.put("issued_at", Instant.now().toString());

        final Envelope envelope = Envelope.of(Envelope.Types.COMMAND);
        envelope.setAgent_id(agentId);
        envelope.setPayload(payload);

        final boolean delivered = registry.sendTo(agentId, envelope);
        if (!delivered) {
            log.warn("quarantine {} command NOT delivered to agent {} (not connected)", action, agentId);
        }
        return delivered;
    }

    // ------------------------------------------------------------------
    //  조회
    // ------------------------------------------------------------------

    /**
     * 특정 Agent 가 <b>현재</b> 격리 중인지 확인합니다.
     *
     * <p>정책 푸시와 토폴로지 표시에서 호출합니다.
     *
     * @param agentId Agent 식별자
     * @return 격리 중이면 {@code true}
     */
    @Transactional(readOnly = true)
    public boolean isQuarantined(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return false;
        }
        return repository.findByAgentIdAndReleasedAtIsNull(agentId).isPresent();
    }

    /**
     * 특정 <b>노드</b>가 현재 격리 중인지 확인합니다.
     *
     * <p>Agent 없는 장비(OPNsense)의 격리 여부를 판단할 때 씁니다.
     * 먼저 Agent 식별자로 보고, 없으면 노드 번호로 봅니다.
     *
     * @param agentId Agent 식별자 (없으면 null)
     * @param nodeId  노드 번호 (없으면 null)
     * @return 격리 중이면 {@code true}
     */
    @Transactional(readOnly = true)
    public boolean isQuarantined(String agentId, Integer nodeId) {
        if (isQuarantined(agentId)) {
            return true;
        }
        return nodeId != null && repository.findByNodeIdAndReleasedAtIsNull(nodeId).isPresent();
    }

    /**
     * 현재 격리 중인 상태 한 건을 돌려줍니다. (없으면 null)
     *
     * <h2>⚠️ 왜 상태 객체가 필요한가</h2>
     * <p>정책 오버라이드(quarantineOverride)는 격리 <b>여부</b>만으로는 부족합니다.
     * 격리 방식({@code scope})에 따라 정책을 다르게 만들어야 합니다 —
     * NODE 는 전부 차단, CONNECTION(방화벽)은 대상 서브넷만 차단입니다.
     * 그래서 여기서 대상 CIDR 까지 담은 상태를 돌려줍니다.
     *
     * @param agentId Agent 식별자 (없으면 null)
     * @param nodeId  노드 번호 (없으면 null)
     * @return 현재 격리 상태 (없으면 null)
     */
    @Transactional(readOnly = true)
    public QuarantineState activeState(String agentId, Integer nodeId) {
        return findActive(agentId, nodeId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<QuarantineState> activeStates(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return List.of();
        }
        return repository.findAllByAgentIdAndReleasedAtIsNull(agentId);
    }

    @Transactional(readOnly = true)
    public List<QuarantineState> activeConnectionStates(String agentId) {
        return activeStates(agentId).stream()
                .filter(state -> state.getScope() == QuarantineState.Scope.CONNECTION)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isSubnetQuarantined(String agentId, String targetCidr) {
        return targetCidr != null && activeConnectionStates(agentId).stream()
                .anyMatch(state -> targetCidr.equalsIgnoreCase(state.getTargetCidr()));
    }

    private boolean isManagedSubnet(String projectKey, String agentId, String targetCidr) {
        if (projectRepository == null || projectKey == null || projectKey.isBlank() || agentId == null) {
            return false;
        }
        return projectRepository.findByProjectKey(projectKey)
                .map(project -> project.toPolicySubnets().stream().anyMatch(subnet ->
                        agentId.equalsIgnoreCase(subnet.getAgentId())
                                && org.sonar.sonarvalidator_backend.Policy.PolicySubnet.normalizeCidr(
                                        targetCidr).equals(
                                        org.sonar.sonarvalidator_backend.Policy.PolicySubnet.normalizeCidr(
                                                subnet.getCidr()))))
                .orElse(false);
    }

    /**
     * 현재 격리 중인 <b>노드 번호</b> 집합입니다.
     *
     * <p>Agent 없는 장비의 격리를 화면/토폴로지가 표시할 때 씁니다.
     *
     * @return 격리 중인 노드 번호 집합
     */
    @Transactional(readOnly = true)
    public Set<Integer> quarantinedNodeIds() {
        final List<QuarantineState> rows = repository.findByReleasedAtIsNullOrderByQuarantinedAtDesc(
                PageRequest.of(0, HISTORY_LIMIT));
        final Set<Integer> ids = new LinkedHashSet<>();
        for (final QuarantineState row : rows) {
            if (row.getNodeId() != null && row.getScope() != QuarantineState.Scope.CONNECTION) {
                ids.add(row.getNodeId());
            }
        }
        return ids;
    }

    /**
     * 현재 격리 중인 Agent 식별자 집합입니다.
     *
     * <p>정책 푸시에서 대상 제외에 씁니다. 매 서브넷마다 DB 를 치지 않도록
     * 한 번에 집합으로 받습니다.
     *
     * @return 격리 중인 Agent 식별자 (소문자 아님 — 원본 그대로)
     */
    @Transactional(readOnly = true)
    public Set<String> quarantinedAgentIds() {
        final List<QuarantineState> rows = repository.findByReleasedAtIsNullOrderByQuarantinedAtDesc(
                PageRequest.of(0, HISTORY_LIMIT));
        final Set<String> ids = new LinkedHashSet<>();
        for (final QuarantineState row : rows) {
            if (row.getScope() == QuarantineState.Scope.CONNECTION) {
                continue;
            }
            // ⚠️ Agent 없는 장비(REST 전용)는 agent_id 가 null 입니다.
            //   그대로 넣으면 집합에 null 이 섞여 호출부의 contains() 가
            //   예상 밖으로 true 를 돌려줄 수 있습니다.
            if (row.getAgentId() != null && !row.getAgentId().isBlank()) {
                ids.add(row.getAgentId());
            }
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public Set<String> quarantinedNodeAgentIds() {
        return listActive(null).stream()
                .filter(row -> !"CONNECTION".equals(row.get("scope")))
                .map(row -> (String) row.get("agent_id"))
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 현재 격리 중인 상태 목록을 반환합니다.
     *
     * @param projectKey 프로젝트 키 (없으면 전체)
     * @return 격리 상태 목록 (화면/API 용 맵)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listActive(String projectKey) {
        final List<QuarantineState> rows = (projectKey == null || projectKey.isBlank())
                ? repository.findByReleasedAtIsNullOrderByQuarantinedAtDesc(PageRequest.of(0, HISTORY_LIMIT))
                : repository.findByProjectKeyAndReleasedAtIsNullOrderByQuarantinedAtDesc(
                        projectKey, PageRequest.of(0, HISTORY_LIMIT));

        final List<Map<String, Object>> result = new ArrayList<>();
        for (final QuarantineState row : rows) {
            result.add(toSummary(row));
        }
        return result;
    }

    /**
     * 한 Agent 의 격리 이력을 반환합니다. (해제된 것 포함)
     *
     * @param agentId Agent 식별자
     * @return 격리 이력
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(String agentId) {
        final List<QuarantineState> rows = repository.findByAgentIdOrderByQuarantinedAtDesc(
                agentId, PageRequest.of(0, HISTORY_LIMIT));
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final QuarantineState row : rows) {
            result.add(toSummary(row));
        }
        return result;
    }

    /**
     * 격리 상태를 응답 맵으로 변환합니다.
     *
     * @param state     저장된 상태
     * @param delivered 이번 요청에서 명령이 전달됐는지
     * @param retry     이미 격리 중이었는지
     * @return API 응답
     */
    private Map<String, Object> toResponse(QuarantineState state, boolean delivered, boolean retry) {
        final Map<String, Object> body = toSummary(state);
        body.put("delivered", delivered);
        body.put("retry", retry);
        // ⚠️ 연결 단위 격리(방화벽)는 Agent 명령이 아니라 서버 규칙이므로,
        //   delivered=false 를 "실패" 로 읽으면 안 됩니다. 그래서 방식에 맞는
        //   안내 문구를 씁니다.
        if (!delivered) {
            if (state.getScope() == QuarantineState.Scope.CONNECTION) {
                body.put("warning", "방화벽 연결 격리입니다. 규칙은 서버가 정책 푸시로 "
                        + "내려보내며, 장치가 연결되면 적용됩니다.");
            } else {
                // 운영자가 "왜 반영이 안 되지" 를 묻지 않도록 이유를 문장으로 남깁니다.
                body.put("warning", "장치가 연결되어 있지 않아 명령이 전달되지 않았습니다. "
                        + "장치가 재접속하면 차단 정책이 자동 적용됩니다.");
            }
        }

        // Agent 가 ack 로 알려온 실제 적용 결과입니다.
        // (null 이면 아직 ack 가 오지 않았거나 구버전 Agent 입니다.)
        final String ackKey = firstNonBlank(state.getAgentId(),
                state.getNodeId() == null ? null : "node-" + state.getNodeId());
        final Map<String, Object> ack = ackFor(state);
        body.put("applied", ack == null ? null : ack.get("ok"));
        if (ack != null) {
            body.put("applied_detail", ack.get("detail"));
            body.put("available", ack.get("available"));
            body.put("blocked", ack.get("blocked"));
            body.put("preserved", ack.get("preserved"));
        }
        body.put("connected", state.getAgentId() != null
                && registry.connectedAgentIds().contains(state.getAgentId()));
        return body;
    }

    /**
     * 격리 상태를 요약 맵으로 변환합니다.
     *
     * @param row 저장된 상태
     * @return 요약 맵
     */
    private Map<String, Object> toSummary(QuarantineState row) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("agent_id", row.getAgentId());
        entry.put("node_id", row.getNodeId());
        entry.put("scope", row.getScope() == null ? null : row.getScope().name());
        entry.put("target_cidr", row.getTargetCidr());
        entry.put("project_id", row.getProjectKey());
        entry.put("reason", row.getReason());
        entry.put("requested_by", row.getRequestedBy());
        entry.put("command_delivered", row.isCommandDelivered());
        entry.put("quarantined_at", row.getQuarantinedAt() == null ? null : row.getQuarantinedAt().toInstant().toString());
        entry.put("released_at", row.getReleasedAt() == null ? null : row.getReleasedAt().toInstant().toString());
        entry.put("released_by", row.getReleasedBy());
        entry.put("active", row.isActive());
        final Map<String, Object> ack = ackFor(row);
        entry.put("applied", ack == null ? null : ack.get("ok"));
        entry.put("applied_detail", ack == null ? null : ack.get("detail"));
        return entry;
    }

    private Map<String, Object> ackFor(QuarantineState state) {
        // ⚠️ Agent 없는 장비(OPNsense 등 REST 전용)는 agent_id 가 null 입니다.
        //    ConcurrentHashMap 은 null 키를 허용하지 않으므로 먼저 걸러야 합니다.
        //    (걸러지지 않으면 응답을 만드는 toSummary 에서 NPE 가 나고,
        //     "격리는 저장됐는데 조회가 실패" 하는 상태가 됩니다)
        final String agentId = state.getAgentId();
        if (agentId == null || agentId.isBlank()) return null;
        if (state.getScope() != QuarantineState.Scope.CONNECTION) return lastAck.get(agentId);
        final var ack = lastAck.get(agentId + ":" + state.getTargetCidr());
        return ack != null && java.util.Objects.equals(ack.get("state_id"), state.getId()) ? ack : null;
    }

    /**
     * Agent 가 보낸 격리/해제 명령의 <b>적용 결과</b>(ack)를 기록합니다.
     *
     * <h2>왜 서버가 ack 를 해석해야 하는가</h2>
     * <p>{@code sendTo} 가 {@code true} 인 것은 소켓에 써 넣었다는 뜻일 뿐,
     * 장치가 인터페이스를 정말 내렸는지는 알 수 없습니다. Agent 가 ack 로
     * 돌려준 결과를 여기서 받아 두어야 운영자 화면이
     * "명령 보냄" 과 "실제 차단됨" 을 구분해 보여줄 수 있습니다.
     *
     * <p>⚠️ ack 를 받지 못했다고 격리를 실패로 처리하지 않습니다. 구버전
     * Agent 는 ack 를 보내지 않으며, 그렇다고 격리 자체가 안 된 것은
     * 아니기 때문입니다. {@code applied} 를 {@code null} 로 두어
     * "모름" 과 "실패" 를 구분합니다.
     *
     * @param agentId Agent 식별자
     * @param payload ack 의 payload (action/ok/affected/preserved/detail)
     */
    public void recordAck(String agentId, JsonNode payload) {
        if (agentId == null || agentId.isBlank() || payload == null || !payload.isObject()) {
            return;
        }

        final String action = text(payload, "action");
        if ("subnet-quarantine".equals(action) && payload.path("targets").isArray()) {
            final var active = activeConnectionStates(agentId);
            for (final JsonNode target : payload.path("targets")) {
                for (final var state : active) {
                    if (!java.util.Objects.equals(state.getTargetCidr(), target.path("cidr").asText())
                            || state.getId() == null || state.getId() != target.path("id").asLong(-1)) continue;
                    final Map<String, Object> record = new LinkedHashMap<>();
                    record.put("state_id", state.getId());
                    record.put("ok", payload.path("ok").asBoolean(false));
                    record.put("detail", text(payload, "detail"));
                    lastAck.put(agentId + ":" + state.getTargetCidr(), record);
                }
            }
            return;
        }
        // 격리/해제가 아닌 ack (예: 정책 적용 보고)는 여기서 다루지 않습니다.
        if (action == null || !(action.equals(ACTION_QUARANTINE) || action.equals(ACTION_RELEASE))) {
            return;
        }

        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("action", action);
        record.put("ok", payload.has("ok") && payload.get("ok").isBoolean()
                ? payload.get("ok").asBoolean() : null);
        record.put("detail", text(payload, "detail"));
        record.put("available", strings(payload, "affected"));
        record.put("blocked", strings(payload, "affected"));
        record.put("preserved", strings(payload, "preserved"));
        record.put("received_at", Instant.now().toString());
        lastAck.put(agentId, record);

        final Object ok = record.get("ok");
        if (Boolean.TRUE.equals(ok)) {
            log.info("quarantine ack agent={} action={} affected={} preserved={}",
                    agentId, action, record.get("affected"), record.get("preserved"));
        } else {
            // 적용 실패는 경고로 남깁니다. 운영자가 콘솔로 들어가야 할 수 있습니다.
            log.warn("quarantine ack FAILED agent={} action={} detail={}",
                    agentId, action, record.get("detail"));
            notificationService.notifyQuietly(
                    "SECURITY",
                    "critical",
                    "Agent 격리 적용 실패: " + agentId,
                    "장치 " + agentId + " 에 격리 명령을 보냈으나 적용에 실패했습니다. "
                            + "(" + record.get("detail") + ") 콘솔에서 직접 확인하세요.",
                    null,
                    agentId,
                    "quarantine",
                    "/agent",
                    "quarantine-ack-failed:" + agentId);
        }
    }

    /**
     * 저장된 최근 명령 적용 결과를 조회합니다. (없으면 {@code null})
     *
     * @param agentId Agent 식별자
     * @return 최근 ack 결과
     */
    public Map<String, Object> lastAck(String agentId) {
        return lastAck.get(agentId);
    }

    private static String text(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        return (value != null && value.isTextual()) ? value.asText() : null;
    }

    private static List<String> strings(JsonNode node, String field) {
        final JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        for (final JsonNode item : value) {
            if (item.isTextual()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    /**
     * 배포 예정 목록에서 격리 대상을 찾을 때 쓰는 헬퍼입니다.
     *
     * <p>{@link ExpectedAgent} 는 프로젝트 키를 문자열로 갖고, 격리는 그 값을
     * 그대로 저장합니다. 여기서는 편의를 위해 문자열 비교만 제공합니다.
     *
     * @param expected 배포 예정 정보
     * @param agentId  실제 연결된 Agent 식별자
     * @return 같은 장치로 보이면 {@code true} (대소문자 무시)
     */
    public static boolean sameAgent(ExpectedAgent expected, String agentId) {
        if (expected == null || expected.getAgentId() == null || agentId == null) {
            return false;
        }
        return expected.getAgentId().equalsIgnoreCase(agentId);
    }
}