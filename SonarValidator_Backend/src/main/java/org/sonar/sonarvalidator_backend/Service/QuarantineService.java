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

@Service
public class QuarantineService {

    private static final Logger log = LoggerFactory.getLogger(QuarantineService.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static final int HISTORY_LIMIT = 200;

    private static final String ACTION_QUARANTINE = "quarantine";
    private static final String ACTION_RELEASE = "release";

    private final QuarantineStateRepository repository;
    private final AgentSessionRegistry registry;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;

    private DeviceTypeResolver deviceTypeResolver;

    private final Map<String, Map<String, Object>> lastAck = new java.util.concurrent.ConcurrentHashMap<>();

    private QuarantineMethods strategies;

    private NodeRegistryService nodeRegistry;

    private SiteProperties siteProperties = new SiteProperties();

    private org.sonar.sonarvalidator_backend.Repository.ProjectRepository projectRepository;

    public QuarantineService(QuarantineStateRepository repository,
                             AgentSessionRegistry registry,
                             ComplianceService complianceService,
                             NotificationService notificationService) {
        this.repository = repository;
        this.registry = registry;
        this.complianceService = complianceService;
        this.notificationService = notificationService;
    }

    private org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository expectedAgents;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setExpectedAgents(org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository expectedAgents) {
        this.expectedAgents = expectedAgents;
    }

    private AgentTelemetryStore telemetryStore;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTelemetryStore(AgentTelemetryStore telemetryStore) {
        this.telemetryStore = telemetryStore;
    }

    private boolean overlapsControlInterface(String agentId, String targetCidr) {
        final var config = telemetryStore == null || agentId == null ? null : telemetryStore.configOf(agentId);
        if (config == null) return false;
        final var target = org.sonar.sonarvalidator_backend.Policy.PacketVariables.parseCidr(targetCidr);
        return config.getInterfaces().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("VirtualPortGroup") || entry.getKey().startsWith("Management"))
                .flatMap(entry -> entry.getValue().getAddresses().stream())
                .filter(address -> address != null && !address.contains(":"))
                .anyMatch(address -> {
                    final var control = org.sonar.sonarvalidator_backend.Policy.PacketVariables.parseCidr(
                            address.contains("/") ? address : address + "/32");
                    final int length = Math.min(target.prefixLength(), control.prefixLength());
                    final int mask = length == 0 ? 0 : -1 << (32 - length);
                    return (target.address() & mask) == (control.address() & mask);
                });
    }

    private org.sonar.sonarvalidator_backend.Repository.OPNsenseCredentialRepository apiCredentials;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setApiCredentials(org.sonar.sonarvalidator_backend.Repository.OPNsenseCredentialRepository apiCredentials) {
        this.apiCredentials = apiCredentials;
    }

    private void lockAgent(String agentId) {
        if (expectedAgents != null && agentId != null) expectedAgents.findForMonitorUpdate(agentId);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDeviceTypeResolver(DeviceTypeResolver deviceTypeResolver) {
        this.deviceTypeResolver = deviceTypeResolver;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setStrategies(QuarantineMethods strategies) {
        this.strategies = strategies;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNodeRegistry(NodeRegistryService nodeRegistry) {
        this.nodeRegistry = nodeRegistry;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSiteProperties(SiteProperties siteProperties) {
        if (siteProperties != null) {
            this.siteProperties = siteProperties;
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProjectRepository(
            org.sonar.sonarvalidator_backend.Repository.ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    //  격리 / 해제

    @Transactional
    public Map<String, Object> isolate(String agentId,
                                       Integer nodeId,
                                       String projectKey,
                                       String reason,
                                       String requestedBy,
                                       String targetCidr) {
        final String operator = requestedBy == null || requestedBy.isBlank() ? "operator" : requestedBy;




        final Configuration node = resolveNode(agentId, nodeId);
        final String effectiveAgentId = firstNonBlank(agentId,
                node == null ? null : node.getAgentId());
        // ⚠️ 삼항 연산자에 int 와 Integer 가 섞이면 <b>전체가 int 로 강제</b>되어
        //   nodeId 가 null 일 때 NPE 가 납니다. 반드시 Integer 로 감싸세요.
        final Integer effectiveNodeId = node != null
                ? node.getNodeId() : nodeId;
        final String display = firstNonBlank(effectiveAgentId,
                effectiveNodeId == null ? null : "node-" + effectiveNodeId, "unknown");

        lockAgent(effectiveAgentId);
        final DeviceType deviceType = resolveDeviceType(effectiveAgentId, node);
        final QuarantineMethod strategy = strategies().of(deviceType);
        // ⚠️ 제어평면 대역은 프로젝트가 지정하면 그 값을 우선합니다.
        //   (요구사항 — 프로젝트마다 관리망이 다를 수 있음)
        final QuarantineContext context = new QuarantineContext(
                effectiveNodeId, effectiveAgentId, deviceType, projectKey,
                targetCidr, resolveManagementPrefix(projectKey));

        if (strategy.mode() == QuarantineMethod.Mode.UNSUPPORTED) {
            final String why = strategy.exclusionReason() == null
                    ? "이 장치는 격리 대상이 아닙니다." : strategy.exclusionReason();
            log.warn("rejected isolation of {} ({}) — not isolatable", display, deviceType);
            return excludedResponse(display, projectKey, deviceType, why);
        }
        if (effectiveNodeId != null && apiCredentials != null && apiCredentials.existsByNodeId(effectiveNodeId)) {
            return excludedResponse(display, projectKey, deviceType,
                    "OPNsense API 직접 격리 적용은 아직 지원하지 않습니다. 방화벽 관리 화면에서 규칙을 적용하세요.");
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
        if (connectionScoped && overlapsControlInterface(effectiveAgentId, targetCidr)) {
            return excludedResponse(display, projectKey, deviceType, "GuestShell 또는 장비 관리 인터페이스는 격리할 수 없습니다.");
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
            created.setConfirmedBlocked(effectiveAgentId != null && reconciliationStates(effectiveAgentId).stream()
                    .filter(previous -> java.util.Objects.equals(previous.getTargetCidr(), connectionScoped ? targetCidr : null))
                    .filter(previous -> (previous.getScope() == QuarantineState.Scope.CONNECTION) == connectionScoped)
                    .anyMatch(previous -> Boolean.TRUE.equals(previous.getConfirmedBlocked())));
                created.setScope(connectionScoped
                    ? QuarantineState.Scope.CONNECTION : QuarantineState.Scope.NODE);
                created.setTargetCidr(connectionScoped ? targetCidr : null);
            state = repository.save(created);
            log.warn("quarantining {} type={} scope={} target={} project={} by={} reason={}",
                    display, deviceType, created.getScope(), created.getTargetCidr(),
                    projectKey, operator, reason);
        }

        state.setRequestId(java.util.UUID.randomUUID().toString());
        state.setApplyStatus("PENDING");
        state.setApplyDetail(null);
        repository.save(state);

        final boolean delivered = false;
        if (delivered != state.isCommandDelivered()) {
            state.setCommandDelivered(delivered);
            repository.save(state);
        }

        final List<String> warnings = buildWarnings(strategy, context);
        final String riskNote = warnings.isEmpty() ? "" : " ⚠️ " + String.join(" ", warnings);

        // 격리는 보안 사건입니다. 이력과 알림 <b>양쪽</b>에 남깁니다.

        complianceService.recordQuietly(
                "Agent",
                projectKey,
                display,
                retry ? "QuarantineRetry" : "Quarantine",
                "Agent " + display + " 격리"
                        + (state.getReason() == null ? "" : " — " + state.getReason())
                        + (delivered ? " (명령 전달됨)" : " (정책 동기화 대기)")
                        + (state.getTargetCidr() == null ? "" : " [대상 " + state.getTargetCidr() + "]"),
                operator,
                "{\"agent_id\":\"" + display + "\",\"delivered\":" + delivered
                        + ",\"scope\":\"" + state.getScope() + "\"}");

        notificationService.notifyQuietly(
                "SECURITY",
                delivered ? "critical" : "warning",
                "Agent 격리 요청: " + display,
                quarantineMessage(delivered, display, state) + riskNote,
                projectKey,
                effectiveAgentId,
                "quarantine",
                "/agent",
                "quarantine:" + display);

        return withWarnings(toResponse(state, delivered, retry), warnings);
    }

    @Transactional
    public Map<String, Object> isolate(String agentId,
                                       String projectKey,
                                       String reason,
                                       String requestedBy) {
        return isolate(agentId, null, projectKey, reason, requestedBy, null);
    }

    @Transactional
    public Map<String, Object> isolateByNode(Integer nodeId,
                                             String projectKey,
                                             String reason,
                                             String requestedBy,
                                             String targetCidr) {
        return isolate(null, nodeId, projectKey, reason, requestedBy, targetCidr);
    }

    @Transactional
    public Map<String, Object> release(String agentId, String releasedBy) {
        return release(agentId, null, releasedBy);
    }

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

        lockAgent(effectiveAgentId);
        Optional<QuarantineState> active = targetCidr == null || targetCidr.isBlank()
            ? findActive(effectiveAgentId, effectiveNodeId)
            : findActive(effectiveAgentId, effectiveNodeId, targetCidr);
        if (active.isEmpty() && effectiveAgentId != null) active = reconciliationStates(effectiveAgentId).stream()
                .filter(row -> !row.isActive() && !"APPLIED".equals(row.getApplyStatus()))
                .filter(row -> targetCidr == null ? row.getScope() != QuarantineState.Scope.CONNECTION
                        : java.util.Objects.equals(targetCidr, row.getTargetCidr())).findFirst();
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
        state.setRequestId(java.util.UUID.randomUUID().toString());
        state.setApplyStatus("PENDING");
        state.setApplyDetail(null);
        repository.save(state);




        final boolean delivered = false;

        log.info("released {} by={} delivered={}", display, operator, delivered);

        complianceService.recordQuietly(
                "Agent",
                state.getProjectKey(),
                display,
                "QuarantineRelease",
                "Agent " + display + " 격리 해제"
                        + (delivered ? " (명령 전달됨)" : " (정책 동기화 대기)"),
                operator,
                "{\"agent_id\":\"" + display + "\",\"delivered\":" + delivered + "}");

        notificationService.notifyQuietly(
                "SECURITY",
                delivered ? "info" : "warning",
                "Agent 격리 해제 요청: " + display,
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

    private static Map<String, Object> withReleasedFlag(Map<String, Object> body, boolean released) {
        body.put("released", released);
        return body;
    }

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

    private QuarantineMethods strategies() {
        if (strategies == null) {
            strategies = DEFAULT_STRATEGIES;
        }
        return strategies;
    }

    private static Map<String, Object> withWarnings(Map<String, Object> body, List<String> warnings) {
        if (warnings != null && !warnings.isEmpty()) {
            body.put("warnings", warnings);
        }
        return body;
    }

    private static String quarantineMessage(boolean delivered, String display, QuarantineState state) {
        if (state.getScope() == QuarantineState.Scope.CONNECTION) {
            return "위반 장치 " + display + " 의 연결 "
                    + (state.getTargetCidr() == null ? "(대상 미지정)" : state.getTargetCidr())
                    + " 의 차단을 요청했습니다. 장비의 적용 확인을 기다립니다.";
        }
        return delivered
                ? "위반 장치 " + display + " 를 격리했습니다. 해당 장치의 트래픽이 차단됩니다."
                : "위반 장치 " + display + " 를 격리하려 했으나 명령이 전달되지 않았습니다. "
                        + "(장치 미연결) 재접속 시 차단 정책이 적용됩니다.";
    }

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

    private Optional<QuarantineState> findActive(String agentId, Integer nodeId) {
        if (agentId != null && !agentId.isBlank()) {
            final Optional<QuarantineState> byAgent =
                    repository.findAllByAgentIdAndReleasedAtIsNull(agentId).stream()
                            .filter(row -> row.getScope() != QuarantineState.Scope.CONNECTION).findFirst();
            if (byAgent.isPresent()) {
                return byAgent;
            }
        }
        if (nodeId != null) {
            return repository.findAllByNodeIdAndReleasedAtIsNull(nodeId).stream()
                    .filter(row -> row.getScope() != QuarantineState.Scope.CONNECTION).findFirst();
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

    private static String firstNonBlank(String... values) {
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static final QuarantineMethods DEFAULT_STRATEGIES = new QuarantineMethods(
            List.of(
                    new org.sonar.sonarvalidator_backend.Service.quarantine.vendor.arista.veos.SwitchQuarantine(),
                    new org.sonar.sonarvalidator_backend.Service.quarantine.vendor.cisco.iosxe.RouterQuarantine(),
                    new org.sonar.sonarvalidator_backend.Service.quarantine.vendor.canonical.ubuntu.VmQuarantine(),
                    new org.sonar.sonarvalidator_backend.Service.quarantine.vendor.linux.nftables.FirewallQuarantine()));

    private static List<String> buildWarnings(QuarantineMethod strategy, QuarantineContext context) {
        try {
            return QuarantineContext.nonNull(strategy.warnings(context));
        } catch (RuntimeException ex) {
            // 경고 생성 실패가 격리 자체를 막으면 안 됩니다.
            return List.of();
        }
    }

    //  조회

    @Transactional(readOnly = true)
    public boolean isQuarantined(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return false;
        }
        return activeStates(agentId).stream().anyMatch(state -> state.getScope() != QuarantineState.Scope.CONNECTION);
    }

    @Transactional(readOnly = true)
    public boolean isQuarantined(String agentId, Integer nodeId) {
        if (isQuarantined(agentId)) {
            return true;
        }
        return nodeId != null && repository.findByNodeIdAndReleasedAtIsNull(nodeId).isPresent();
    }

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

    @Transactional(readOnly = true)
    public Set<String> quarantinedAgentIds() {
        final List<QuarantineState> rows = repository.findByReleasedAtIsNullOrderByQuarantinedAtDesc(
                PageRequest.of(0, HISTORY_LIMIT));
        final Set<String> ids = new LinkedHashSet<>();
        for (final QuarantineState row : rows) {
            if (row.getScope() == QuarantineState.Scope.CONNECTION) {
                continue;
            }



            if (row.getAgentId() != null && !row.getAgentId().isBlank()) {
                ids.add(row.getAgentId());
            }
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public Set<String> quarantinedNodeAgentIds() {
        return java.util.stream.Stream.concat(listActive(null).stream(), pendingReleases(null).stream())
                .filter(row -> !"CONNECTION".equals(row.get("scope")))
                .filter(row -> Boolean.TRUE.equals(row.get("blocked")))
                .map(row -> (String) row.get("agent_id"))
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendingReleases(String projectKey) {
        return repository.findByReleasedAtIsNotNullAndApplyStatusInOrderByQuarantinedAtDesc(
                List.of("PENDING", "FAILED"), PageRequest.of(0, HISTORY_LIMIT)).stream()
                .filter(row -> projectKey == null || projectKey.equals(row.getProjectKey()))
                .filter(row -> reconciliationStates(row.getAgentId()).stream()
                        .anyMatch(current -> java.util.Objects.equals(row.getId(), current.getId())))
                .map(this::toSummary).toList();
    }

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

    private Map<String, Object> toResponse(QuarantineState state, boolean delivered, boolean retry) {
        final Map<String, Object> body = toSummary(state);
        body.put("delivered", delivered);
        body.put("retry", retry);



        if (!delivered) {
            if (state.getScope() == QuarantineState.Scope.CONNECTION) {
                body.put("warning", "연결 격리 요청이 대기 중입니다. 정책 동기화로 "
                        + "전달하며, 장비의 적용 확인이 필요합니다.");
            } else {

                body.put("warning", "명령을 정책 동기화 대기열에 등록했습니다. "
                        + "정책 동기화 후 장비의 적용 결과를 확인하세요.");
            }
        }

        // Agent 가 ack 로 알려온 실제 적용 결과입니다.
        // (null 이면 아직 ack 가 오지 않았거나 구버전 Agent 입니다.)
        final Map<String, Object> ack = ackFor(state);
        body.put("applied", ack == null ? null : ack.get("ok"));
        if (ack != null) {
            body.put("applied_detail", ack.get("detail"));
        }
        body.put("connected", state.getAgentId() != null
                && registry.connectedAgentIds().contains(state.getAgentId()));
        return body;
    }

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
        entry.put("request_id", row.getRequestId());
        entry.put("status", row.getApplyStatus() == null ? "PENDING" : row.getApplyStatus());
        entry.put("blocked", Boolean.TRUE.equals(row.getConfirmedBlocked()));
        entry.put("confirmed_at", row.getConfirmedAt() == null ? null : row.getConfirmedAt().toInstant().toString());
        final Map<String, Object> ack = ackFor(row);
        entry.put("applied", ack == null ? null : ack.get("ok"));
        entry.put("applied_detail", ack == null ? null : ack.get("detail"));
        return entry;
    }

    private Map<String, Object> ackFor(QuarantineState state) {
        if (state.getApplyStatus() == null || "PENDING".equals(state.getApplyStatus())) return null;
        final Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("ok", "APPLIED".equals(state.getApplyStatus()));
        ack.put("detail", state.getApplyDetail());
        return ack;
    }

    @Transactional(readOnly = true)
    public List<QuarantineState> reconciliationStates(String agentId) {
        final Map<Long, QuarantineState> rows = new LinkedHashMap<>();
        for (final var row : repository.findByAgentIdOrderByQuarantinedAtDesc(agentId, PageRequest.of(0, HISTORY_LIMIT)))
            rows.put(row.getId(), row);
        for (final var row : activeStates(agentId)) rows.put(row.getId(), row);
        final Map<String, QuarantineState> current = new LinkedHashMap<>();
        rows.values().stream().sorted(java.util.Comparator.comparing(QuarantineState::getId).reversed())
                .forEach(row -> current.putIfAbsent(row.getScope() + ":" + row.getTargetCidr(), row));
        return new ArrayList<>(current.values());
    }

    @Transactional(readOnly = true)
    public String planRevision(String agentId) {
        final String input = reconciliationStates(agentId).stream()
                .sorted(java.util.Comparator.comparing(QuarantineState::getId))
                .map(row -> row.getId() + ":" + row.getRequestId() + ":" + row.isActive())
                .collect(java.util.stream.Collectors.joining("|"));
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> topologyState(String projectKey, String agentId, String cidr) {
        return org.sonar.sonarvalidator_backend.Service.quarantine.QuarantineTopologyView.summarize(
                projectKey, cidr, agentId == null ? List.of() : reconciliationStates(agentId));
    }

    @Transactional
    public void recordAck(String agentId, JsonNode payload) {
        if (agentId == null || agentId.isBlank() || payload == null || !payload.isObject()) return;
        final String action = text(payload, "action");
        final boolean connection = "subnet-quarantine".equals(action);
        if (!connection && !ACTION_QUARANTINE.equals(action) && !ACTION_RELEASE.equals(action)) return;
        lockAgent(agentId);
        final var states = reconciliationStates(agentId);
        if (connection) {
            if (!planRevision(agentId).equals(text(payload, "revision"))) return;
        } else if (text(payload, "request_id") == null) return;
        for (final var state : states) {
            if (connection != (state.getScope() == QuarantineState.Scope.CONNECTION)) continue;
            if (!connection && (!java.util.Objects.equals(state.getRequestId(), text(payload, "request_id"))
                    || !java.util.Objects.equals(state.isActive() ? ACTION_QUARANTINE : ACTION_RELEASE, action))) continue;
            final boolean ok = payload.path("ok").asBoolean(false);
            final String nextStatus = ok ? "APPLIED" : "FAILED";
            final boolean changed = !nextStatus.equals(state.getApplyStatus());
            final String detail = text(payload, "detail");
            final String limitedDetail = detail == null ? null : detail.substring(0, Math.min(1000, detail.length()));
            if (!changed && java.util.Objects.equals(state.getApplyDetail(), limitedDetail)
                    && (!ok || java.util.Objects.equals(state.getConfirmedBlocked(), state.isActive()))) continue;
            state.setApplyStatus(nextStatus);
            state.setCommandDelivered(true);
            state.setApplyDetail(limitedDetail);
            if (ok) {
                state.setConfirmedBlocked(state.isActive());
                state.setConfirmedAt(new Date());
            }
            repository.save(state);
            if (changed) log.info("격리 상태 확인: 노드={} 동작={} 결과={}", agentId,
                    state.isActive() ? "격리" : "해제", nextStatus);
            if (!ok && changed) notificationService.notifyQuietly("SECURITY", "critical",
                    "Agent 격리 적용 실패: " + agentId, "장비의 적용 확인에 실패했습니다. 상세 로그를 확인하세요.",
                    state.getProjectKey(), agentId, "quarantine", "/agent", "quarantine-ack-failed:" + agentId);
        }
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("action", action);
        record.put("ok", payload.path("ok").asBoolean(false));
        record.put("detail", text(payload, "detail"));
        lastAck.put(agentId, record);
    }

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

    public static boolean sameAgent(ExpectedAgent expected, String agentId) {
        if (expected == null || expected.getAgentId() == null || agentId == null) {
            return false;
        }
        return expected.getAgentId().equalsIgnoreCase(agentId);
    }
}
