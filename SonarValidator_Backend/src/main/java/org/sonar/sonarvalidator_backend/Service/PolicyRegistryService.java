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

@Service
public class PolicyRegistryService {

    private static final Logger log = LoggerFactory.getLogger(PolicyRegistryService.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static final long VALID_HOURS = 24;

    public static final String INTERNET_GUARD_RULE = "egress-internet-guard";

    public static final String INTERNET_CIDR = "0.0.0.0/0";

    private boolean automaticEnforcementEnabled = true;

    @org.springframework.beans.factory.annotation.Value("${sonar.policy.automatic-enforcement-enabled:true}")
    public void setAutomaticEnforcementEnabled(boolean enabled) {
        this.automaticEnforcementEnabled = enabled;
    }

    private final ProjectRepository repository;

    private final DevicePolicies strategies;

    private QuarantineService quarantineService;

    private SiteProperties siteProperties = new SiteProperties();

    private String firewallTable() {
        return siteProperties.getFirewallDefaults().getTableName();
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSiteProperties(SiteProperties siteProperties) {
        if (siteProperties != null) {
            this.siteProperties = siteProperties;
        }
    }

    public PolicyRegistryService() {
        this(null, null);
    }

    public PolicyRegistryService(ProjectRepository repository) {
        this(repository, null);
    }

    @Autowired
    public PolicyRegistryService(ProjectRepository repository, DevicePolicies strategies) {
        this.repository = repository;
        this.strategies = strategies;
    }

    @Autowired(required = false)
    public void setQuarantineService(QuarantineService quarantineService) {
        this.quarantineService = quarantineService;
    }

    //  진입점

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

        final var validation = new org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine()
                .validate(match.project().toPolicySubnets(), match.project().toPolicyRules());
        if (!validation.isModelValid()) {
            final ObjectNode policy = forDevice(type, id);
            policy.put("model_valid", false);
            policy.put("analysis_scope", validation.getAnalysisScope());
            policy.put("project_id", match.project().getProjectKey());
            final ArrayNode errors = policy.putArray("input_errors");
            validation.getInputErrors().forEach(errors::add);
            ((ObjectNode) policy.get("summary")).put("source", "invalid-project-model");
            return quarantineOverride(agentId, policy);
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

    private ObjectNode quarantineOverride(String agentId, ObjectNode policy) {
        if (policy == null || quarantineService == null) {
            return policy;
        }

        final ObjectNode plan = policy.putObject("quarantine_plan");
        plan.put("revision", quarantineService.planRevision(agentId));
        final ArrayNode targets = plan.putArray("targets");
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
        final var nodeState = quarantineService.reconciliationStates(agentId).stream()
                .filter(state -> state.getScope() != org.sonar.sonarvalidator_backend.Model.entity.QuarantineState.Scope.CONNECTION)
                .max(java.util.Comparator.comparing(org.sonar.sonarvalidator_backend.Model.entity.QuarantineState::getId)).orElse(null);
        if (nodeState != null) {
            final ObjectNode node = plan.putObject("node");
            node.put("action", nodeState.isActive() ? "quarantine" : "release");
            node.put("scope", "node");
            node.put("request_id", nodeState.getRequestId());
        }
        return policy;
    }


    public ObjectNode forDevice(DeviceType deviceType, String deviceId) {
        final DeviceType type = (deviceType == null) ? DeviceType.VM : deviceType;
        final String id = PolicyJson.firstNonBlank(deviceId, "unknown");

        final ObjectNode policy = JSON.objectNode();
        policy.put("policy_id", "pol-" + type.name().toLowerCase(Locale.ROOT) + "-0001");
        policy.put("device_type", type.name());
        policy.put("device_id", id);
        policy.put("valid_until", validUntil());

        // 폴백임을 응답만 보고 알 수 있게 합니다.

        final ObjectNode summary = policy.putObject("summary");
        summary.put("source", "default");
        summary.put("allowed", 0);
        summary.put("denied", 0);
        summary.put("peers", 0);




        policy.putArray("policies");
        return policy;
    }

    //  프로젝트 기반 정책

    private ObjectNode projectPolicy(ProjectMatch match,
                                     DeviceType type,
                                     String deviceId,
                                     String vendor,
                                     String product) {
        final Project project = match.project();
        final ProjectSubnet subnet = match.subnet();
        final ZoneClass zone = subnet.getZoneClass();
        final List<Connection> connections = connectionsOf(project, subnet, type);
        final DevicePolicy strategy = policyStrategyFor(type, vendor, product);

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

        final ObjectNode aclApply = strategy.batchEnforcementRule(
                new BatchPolicyContext(subnet, vendor, product, views));
        if (aclApply != null) {
            policy.set("acl_apply", aclApply);
        }

        log.debug("policy built: project={} subnet={} type={} allowed={} denied={} rules={} acl={}",
                project.getProjectKey(), subnetId, type,
                countAllowed(connections), countDenied(connections), policies.size(),
                aclApply == null ? "none" : "applied");
        return policy;
    }

    private ProjectMatch findSubnetForAgent(ProjectRepository repositoryRef, String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        final String needle = agentId.trim();
        for (final Project project : repositoryRef.findAllByOrderByCreatedAtDesc()) {
            for (final ProjectSubnet subnet : project.getSubnets()) {
                if (needle.equalsIgnoreCase(subnet.getAgentId())) {
                    return new ProjectMatch(project, subnet);
                }
            }
        }
        return null;
    }

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

            final boolean zoneViolation = ZoneClass.forbidsDirectConnection(
                    source.getZoneClass(), destination.getZoneClass());



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

        if (subnet.isRestricted() && !subnet.allowsInternet()) {
            addRestrictedInternet(result, subnet);
        }
        return result;
    }

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

    //  헬퍼

    private DevicePolicy policyStrategyFor(DeviceType type, String vendor, String product) {
        return (strategies != null ? strategies : FALLBACK_STRATEGIES).of(type, vendor, product);
    }

    private static final DevicePolicies FALLBACK_STRATEGIES = buildFallbackPolicies();

    private static DevicePolicies buildFallbackPolicies() {
        final SiteProperties defaults = new SiteProperties();
        return new DevicePolicies(List.of(
                new org.sonar.sonarvalidator_backend.Policy.strategy.vendor.canonical.ubuntu.VmPolicy(defaults),
                new org.sonar.sonarvalidator_backend.Policy.strategy.vendor.arista.veos.SwitchPolicy(defaults),
                new org.sonar.sonarvalidator_backend.Policy.strategy.vendor.cisco.iosxe.RouterPolicy(defaults),
                new org.sonar.sonarvalidator_backend.Policy.strategy.vendor.linux.nftables.FirewallPolicy(defaults)));
    }

    private static String validUntil() {
        return Instant.now().plus(VALID_HOURS, ChronoUnit.HOURS).toString();
    }

    private static int countAllowed(List<Connection> connections) {
        int count = 0;
        for (final Connection connection : connections) {
            if (!connection.forbidden()) {
                count++;
            }
        }
        return count;
    }

    private static int countDenied(List<Connection> connections) {
        int count = 0;
        for (final Connection connection : connections) {
            if (connection.forbidden()) {
                count++;
            }
        }
        return count;
    }

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

    private record ProjectMatch(Project project, ProjectSubnet subnet) {
    }

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

        PolicyBuildContext.ConnectionView toView() {
            return new PolicyBuildContext.ConnectionView(
                    ruleId, outgoing, peerId, peerCidr, peerClass,
                    sourceCidr, destinationCidr, protocol, port, forbidden, reason);
        }
    }
}
