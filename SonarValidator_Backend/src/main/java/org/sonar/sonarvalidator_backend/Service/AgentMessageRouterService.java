package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Service.telemetry.TelemetryEventPublisher;
import org.sonar.sonarvalidator_backend.Service.telemetry.TelemetrySnapshot;
import org.sonar.sonarvalidator_backend.Service.telemetry.SnapshotPayloadGuard;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Service.cli.CliIngestionService;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;
import org.sonar.sonarvalidator_backend.Service.log.LogService;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AgentMessageRouterService {

    private static final Logger log = LoggerFactory.getLogger(AgentMessageRouterService.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static final int MAX_TELEMETRY_LOG_LINES = 500;

    private final AtomicLong policyRequestCount = new AtomicLong();

    private final AgentTelemetryStore telemetryStore;

    private final DeviceConfigService deviceConfigService;

    private final AgentSessionRegistry registry;
    private final PolicyRegistryService policyRegistry;

    private final LogService logService;

    private final NotificationService notificationService;

    private final CliIngestionService cliIngestionService;

    private QuarantineService quarantineService;

    private TelemetryEventPublisher telemetryEvents = new TelemetryEventPublisher(List.of());

    public AgentMessageRouterService(AgentSessionRegistry registry,
                                     PolicyRegistryService policyRegistry,
                                     DeviceConfigService deviceConfigService,
                                     LogService logService,
                                     NotificationService notificationService,
                                     CliIngestionService cliIngestionService) {
        this(registry, policyRegistry, new AgentTelemetryStore(), deviceConfigService,
                logService, notificationService, cliIngestionService);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AgentMessageRouterService(AgentSessionRegistry registry,
                                     PolicyRegistryService policyRegistry,
                                     AgentTelemetryStore telemetryStore,
                                     DeviceConfigService deviceConfigService,
                                     LogService logService,
                                     NotificationService notificationService,
                                     CliIngestionService cliIngestionService) {
        this.registry = registry;
        this.policyRegistry = policyRegistry;
        this.telemetryStore = telemetryStore;
        this.deviceConfigService = deviceConfigService;
        this.logService = logService;
        this.notificationService = notificationService;
        this.cliIngestionService = cliIngestionService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setQuarantineService(QuarantineService quarantineService) {
        this.quarantineService = quarantineService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setTelemetryEvents(TelemetryEventPublisher publisher) {
        this.telemetryEvents = publisher;
    }

    private AgentMonitorSettingsService monitorSettings;

    @org.springframework.beans.factory.annotation.Autowired
    public void setMonitorSettings(AgentMonitorSettingsService service) { this.monitorSettings = service; }

    public Envelope handle(WebSocketSession session, Envelope envelope) {
        final String agentId = resolveAgentId(session, envelope);
        telemetryStore.touch(agentId);
        log.debug("recv {} from agent={}", envelope.summary(), agentId);

        return switch (envelope.getType()) {
            case Envelope.Types.HELLO -> onHello(session, envelope, agentId);
            case Envelope.Types.POLICY_REQUEST -> onPolicyRequest(session, envelope, agentId);
            case Envelope.Types.TELEMETRY -> onTelemetry(envelope, agentId);
            // Agent 가 우리 푸시(command)에 대한 확인을 보낼 때 사용합니다.
            case Envelope.Types.ACK -> onAck(session, envelope, agentId);
            case Envelope.Types.ERROR -> onError(envelope, agentId);
            default -> Envelope.error(envelope.getCorrelation_id(),
                    "unsupported message type: " + envelope.getType());
        };
    }

    private Envelope onHello(WebSocketSession session, Envelope envelope, String agentId) {
        final boolean firstConnect = agentId != null
                && !registry.connectedAgentIds().contains(agentId);

        final boolean telemetry = "telemetry".equals(envelope.payloadOrEmpty().path("channel").asString(""));
        if (telemetry) registry.registerTelemetry(agentId, session);
        else registry.register(agentId, session);

        // 새 장치가 붙는 것은 운영자가 기다리는 사건입니다.
        // (재접속마다 알리면 하트비트처럼 쌓여 쓸모가 없어집니다)
        if (firstConnect && !telemetry) {
            // payloadOrEmpty() 는 JsonNode 를 돌려주므로 text() 헬퍼로 읽습니다.
            final String hostname = text(envelope.payloadOrEmpty(), "hostname", null);
            notificationService.notifyQuietly(
                    "AGENT",
                    "info",
                    "Agent 연결: " + (hostname == null ? agentId : hostname),
                    "프로버 " + agentId + " 가 연결되었습니다. 잠시 뒤 텔레메트리가 수집됩니다.",
                    null,
                    agentId,
                    "agent",
                    "/agent",
                    "agent-connected:" + agentId);
        }

        final ObjectNode payload = JSON.objectNode();
        payload.put("agent_id", agentId == null ? "" : agentId);
        payload.put("server_time", Instant.now().toString());
        payload.put("connected_agents", registry.connectedCount());
        if (telemetry && monitorSettings != null) {
            final ObjectNode settings = monitorSettings.desired(agentId);
            if (settings != null) payload.set("monitor_settings", settings);
        }
        return Envelope.replyTo(Envelope.Types.ACK, envelope, payload);
    }

    private Envelope onPolicyRequest(WebSocketSession session, Envelope envelope, String agentId) {
        registry.register(agentId, session);

        final JsonNode payload = envelope.payloadOrEmpty();
        final String deviceId = text(payload, "device_id",
                text(payload, "agent_id", agentId));

        DeviceType deviceType = DeviceType.fromString(envelope.getDevice_type());
        if (deviceType == null) {
            deviceType = DeviceType.fromString(text(payload, "device_type", null));
        }
        if (deviceType == null) {
            deviceType = DeviceType.inferFromDeviceId(deviceId);
        }

        // 텔레메트리에서 관측된 벤더/제품을 정책에 실어 보냅니다.
        // (Agent 가 스스로 보고한 값이 기본값보다 정확합니다)
        final NeutralDeviceConfig observed = telemetryStore.configOf(agentId);
        final String vendor = (observed == null) ? null : observed.getVendor();
        final String product = (observed == null) ? null : observed.getProduct();

        // 배정된 서브넷이 있으면 그 프로젝트의 실제 규칙으로 정책을 만듭니다.
        // 배정이 없으면 서비스가 내부적으로 기본 선언으로 폴백합니다.
        final ObjectNode policy = policyRegistry.forAgent(agentId, deviceType, deviceId, vendor, product);
        policyRequestCount.incrementAndGet();
        log.debug("policy-request from agent={} device={} type={} -> {} (source={})",
                agentId, deviceId, deviceType, policy.path("policy_id").asString("?"),
                policy.path("summary").path("source").asString("?"));

        return Envelope.replyTo(Envelope.Types.POLICY_RESPONSE, envelope, policy);
    }

    private Envelope onTelemetry(Envelope envelope, String agentId) {
        if (agentId == null) {
            return null;
        }
        final JsonNode payload = envelope.payloadOrEmpty();
        telemetryStore.putTelemetry(agentId, payload);

        try {
            // Agent 가 payload 에 제품명을 넣지 않는 경우가 있으므로,
            // 봉투의 device_type 과 payload 의 product 를 함께 씁니다.
            final String product = text(payload, "product",
                    text(payload, "vendor", envelope.getDevice_type()));



            final JsonNode normalized = normalizedPayload(payload, product);
            final NeutralDeviceConfig config = deviceConfigService.parse(agentId, product, normalized);
            requireUsableConfig(config, normalized);
            SnapshotPayloadGuard.requireComplete(normalized, telemetryStore.acceptedPayloadOf(agentId));
            final boolean firstObservation = telemetryStore.configOf(agentId) == null;
            telemetryStore.putAcceptedSnapshot(agentId, normalized, config, false);
            if (firstObservation) log.info("장비 수집 확인: 노드={} 인터페이스={} 경로={}",
                    agentId, config.getInterfaces().size(), config.getRoutes().size());

            telemetryEvents.publish(new TelemetrySnapshot(agentId, config, TelemetrySnapshot.Source.LIVE, Instant.now()));

            log.debug("telemetry from agent={} keys={} format={} ifaces={} routes={} vlans={}",
                    agentId, payload.size(), config.getFormat(),
                    config.getInterfaces().size(), config.getRoutes().size(),
                    config.getVlans().size());
        } catch (RuntimeException ex) {
            // 변환 실패가 수신 자체를 막지 않도록 흡수합니다. (일방향 메시지)
            log.warn("config parse failed for agent={}: {}", agentId, ex.getMessage());
            log.debug("telemetry from agent={} keys={}", agentId, payload.size());
        }

        // 텔레메트리에 로그가 실려 오면 적재합니다.
        // 로그 적재 실패가 텔레메트리 수신을 막으면 안 되므로 예외를 흡수합니다.
        ingestTelemetryLogs(agentId, payload);

        return null;
    }

    private JsonNode normalizedPayload(JsonNode payload, String product) {
        if (payload == null || payload.isNull() || !payload.isObject()) {
            return payload;
        }
        try {
            final CliVendor vendor = cliIngestionService.vendorOf(product);
            final ObjectNode extra = cliIngestionService.extractParsed(payload, vendor);
            if (extra.isEmpty()) {
                return payload;
            }
            final ObjectNode merged = JSON.objectNode();
            merged.setAll((ObjectNode) payload);
            // 추가분이 우선합니다 — 원문이 곧 "이 대상의 진짜 내용" 이기 때문입니다.
            merged.setAll(extra);
            return merged;
        } catch (RuntimeException ex) {
            // 폴백 파싱 실패가 텔레메트리 처리 전체를 막지 않게 합니다.
            log.warn("cli fallback failed for product={}: {}", product, ex.getMessage());
            return payload;
        }
    }

    private void ingestTelemetryLogs(String agentId, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return;
        }

        try {
            final List<String> lines = extractLogLines(payload);
            if (lines.isEmpty()) {
                return;
            }




            final List<String> bounded = lines.size() > MAX_TELEMETRY_LOG_LINES
                    ? lines.subList(0, MAX_TELEMETRY_LOG_LINES)
                    : lines;

            final String product = text(payload, "product", text(payload, "vendor", null));

            logService.ingest(agentId, product, null, "agent", bounded);

            if (lines.size() > bounded.size()) {
                log.warn("telemetry log truncated for agent={}: received={} kept={}",
                        agentId, lines.size(), bounded.size());
            }
        } catch (RuntimeException ex) {
            // 로그 적재 실패가 텔레메트리 처리 전체를 막지 않게 합니다.
            log.warn("log ingestion failed for agent={}: {}", agentId, ex.getMessage());
        }
    }

    private List<String> extractLogLines(JsonNode payload) {
        final List<String> lines = new ArrayList<>();

        for (final String key : new String[]{"log_status", "logs", "syslog", "device_logs"}) {
            final JsonNode node = payload.path(key);
            if (node.isMissingNode() || node.isNull()) {
                continue;
            }

            // 배열이면 각 항목을, 객체면 entries/lines 를 봅니다.
            final JsonNode entries = node.isArray()
                    ? node
                    : (node.path("entries").isArray() ? node.path("entries")
                    : (node.path("lines").isArray() ? node.path("lines") : null));

            if (entries == null || !entries.isArray()) {
                continue;
            }

            for (final JsonNode item : entries) {
                if (item.isString()) {
                    lines.add(item.asString(""));
                    continue;
                }
                // 객체면 raw > line > message 순으로 씁니다.
                String value = item.path("raw").asString("");
                if (value.isBlank()) value = item.path("line").asString("");
                if (value.isBlank()) value = item.path("message").asString("");
                if (!value.isBlank()) {
                    lines.add(value);
                }
            }

            // 첫 번째로 찾은 키만 씁니다. 여러 키에 같은 로그가 중복될 수 있습니다.
            break;
        }

        return lines;
    }

    public NeutralDeviceConfig lastConfigOf(String agentId) {
        return telemetryStore.configOf(agentId);
    }

    public NeutralDeviceConfig acceptOfflineTelemetry(String agentId, String product, JsonNode payload) {
        final String resolvedProduct = (product == null || product.isBlank())
                ? text(payload, "product", text(payload, "vendor", "unknown"))
                : product;

        // 온라인 경로와 같은 폴백 파싱을 거칩니다. 오프라인 스냅샷에는
        // 원문 CLI 가 그대로 들어 있는 경우가 더 흔하므로 여기서도 필요합니다.
        final JsonNode normalized = normalizedPayload(payload, resolvedProduct);

        final NeutralDeviceConfig config =
                deviceConfigService.parse(agentId, resolvedProduct, normalized);

        requireUsableConfig(config, normalized);
        SnapshotPayloadGuard.requireComplete(normalized, telemetryStore.acceptedPayloadOf(agentId));
        telemetryStore.putAcceptedSnapshot(agentId, normalized, config, true);

        telemetryEvents.publish(new TelemetrySnapshot(agentId, config, TelemetrySnapshot.Source.OFFLINE, Instant.now()));

        log.debug("offline telemetry accepted: agent={} product={} format={} keys={}",
                agentId, resolvedProduct, config.getFormat(), payload == null ? 0 : payload.size());
        return config;
    }

    private static void requireUsableConfig(NeutralDeviceConfig config, JsonNode payload) {
        if (config.getInterfaces().isEmpty() && config.getRoutes().isEmpty()
                && config.getVlans().isEmpty() && config.getFirewallRules().isEmpty()
                && config.getBridges().isEmpty()) {
            throw new IllegalArgumentException("파서 '" + config.getFormat()
                    + "' 가 아무 항목도 해석하지 못했습니다. payload 키를 확인하세요. keys=" + payload.size());
        }
    }

    public boolean isOfflineOrigin(String agentId) {
        return telemetryStore.isOfflineOrigin(agentId);
    }

    public Map<String, NeutralDeviceConfig> allConfigs() {
        return telemetryStore.allConfigs();
    }

    public AgentTelemetryStore telemetryStore() {
        return telemetryStore;
    }

    private Envelope onAck(WebSocketSession session, Envelope envelope, String agentId) {
        log.debug("ack from agent={} correlation={}", agentId, envelope.getCorrelation_id());
        if (envelope.payloadOrEmpty().path("action").asString("").startsWith("monitor-interval-")) {
            if (monitorSettings != null) monitorSettings.recordApplied(agentId, session, envelope.payloadOrEmpty());
            return null;
        }

        // 격리/해제 명령의 적용 결과는 격리 서비스가 소유합니다.
        // 라우터는 전달만 합니다. (두 곳이 상태를 가지면 반드시 어긋납니다)
        if (quarantineService != null) {
            quarantineService.recordAck(agentId, envelope.payloadOrEmpty());
        }
        return null;
    }

    private Envelope onError(Envelope envelope, String agentId) {
        log.warn("agent={} reported error: {}", agentId, envelope.getError());
        return null;
    }

    public JsonNode lastTelemetryOf(String agentId) {
        return telemetryStore.telemetryOf(agentId);
    }

    public Set<String> allTelemetryAgentIds() {
        return telemetryStore.allTelemetryAgentIds();
    }

    public Instant observedAtOf(String agentId) {
        return telemetryStore.observedAtOf(agentId);
    }

    public Instant lastSeenOf(String agentId) {
        return telemetryStore.lastSeenOf(agentId);
    }

    public long policyRequestCount() {
        return policyRequestCount.get();
    }

    private String resolveAgentId(WebSocketSession session, Envelope envelope) {
        if (envelope.getAgent_id() != null && !envelope.getAgent_id().isBlank()) {
            return envelope.getAgent_id();
        }
        final Object fromAttributes = session.getAttributes().get("agent_id");
        if (fromAttributes instanceof String attr && !attr.isBlank()) {
            return attr;
        }
        return "session:" + session.getId();
    }

    private String text(JsonNode node, String field, String fallback) {
        final JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        final String text = value.asString(null);
        return (text == null || text.isBlank()) ? fallback : text;
    }
}
