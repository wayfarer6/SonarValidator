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

/**
 * 수신 봉투를 종류별로 분기해 처리합니다. (애플리케이션 계층의 유일한 진입점)
 *
 * <h2>처리 규칙</h2>
 * <table border="1">
 *   <caption>메시지 종류별 동작</caption>
 *   <tr><th>type</th><th>동작</th><th>응답</th></tr>
 *   <tr><td>{@code hello}</td><td>Agent 를 세션 레지스트리에 등록</td><td>{@code ack}</td></tr>
 *   <tr><td>{@code policy-request}</td><td>정책 생성</td><td>{@code policy-response}</td></tr>
 *   <tr><td>{@code telemetry}</td><td>최근 값 저장 (메모리)</td><td>없음</td></tr>
 *   <tr><td>{@code ack}</td><td>로그만</td><td>없음</td></tr>
 *   <tr><td>{@code error}</td><td>경고 로그</td><td>없음</td></tr>
 *   <tr><td>기타</td><td>거부</td><td>{@code error}</td></tr>
 * </table>
 *
 * <p>{@code telemetry}  {@code ack}/{@code error} 는 <b>응답을 보내지 않습니다.</b>
 * 일방향 메시지까지 응답하면 Agent 가 기대하지 않는 프레임을 받아 파싱 혼이 생깁니다.
 *
 * <h2> 상관관계 ID  직접 맞는가</h2>
 * <p>STOMP SimpleBroker 는 RECEIPT/ACK 를 지원하지 않습니다. plain WS 로 바꾸면
 * 브로커 자체가 없으므로 요청-응답 매칭은 100% 이 계층의 책임입니다.
 * Agent 는 자신이 보낸 {@code correlation_id} 와 같은 값이 응답에 있으면 그 요청의
 * 답으로 간주합니다.
 */
@Service
public class AgentMessageRouterService {

    private static final Logger log = LoggerFactory.getLogger(AgentMessageRouterService.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /**
     * 한 번의 텔레메트리에서 적재할 수 있는 최대 로그 줄 수입니다.
     *
     * <p>프로버가 장비의 로그 버퍼 전체를 매 주기(30초) 보내면 로그가 폭증합니다.
     * 30초마다 2000줄이면 하루에 약 576만 건입니다. 그대로 두면 DB 가 가득 차고
     * 조회가 느려집니다. 그래서 한 번에 받는 양을 제한합니다.
     *
     * <p>초과분을 버리더라도 <b>경고 로그</b>를 남깁니다. 조용히 버리면 운영자가
     * 로그가 왜 안 보이는지 알 수 없습니다.
     */
    private static final int MAX_TELEMETRY_LOG_LINES = 500;

    /** 정책 요청 처리 건수 (모니터링용). */
    private final AtomicLong policyRequestCount = new AtomicLong();

    /**
     * 최근 관측값 저장소입니다.
     *
     * <p>수신은 이 라우터가 하고 <b>보관/조회는 저장소</b>가 합니다.
     * 조회 API 들이 라우터를 통해 값만 꺼내러 들어오던 것을 끊어,
     * 라우터가 어떤 봉투를 처리하는지와 무관하게 조회할 수 있게 했습니다.
     */
    private final AgentTelemetryStore telemetryStore;

    /**
     * 중립 장비 설정 변환 서비스.
     *
     * <p>텔레메트리를 받을 때마다 벤더별 파서를 거쳐 벤더 중립 구조로 바꿔 둡니다.
     * 분석/저장/표시 계층이 벤더를 모르게 하려면 수집 시점에 변환해 두는 것이
     * 가장 단순합니다.
     */
    private final DeviceConfigService deviceConfigService;

    /**
     * 파일 업로드로 설정이 들어온 Agent 식별자 집합입니다.
     *
     * <p>이 값이 필요한 이유: 오프라인 스냅샷은 세션이 없으므로
     * {@code registry.connectedAgentIds()} 에 나타나지 않습니다. 그런데 화면에는
     * "연결은 안 됐지만 설정은 확보된 장비" 로 보여야 운영자가 다음 단계로
     * 갈 수 있습니다. 그래서 출처를 따로 표시합니다.
     */
    private final AgentSessionRegistry registry;
    private final PolicyRegistryService policyRegistry;

    /**
     * 로그 적재 서비스입니다.
     *
     * <p>텔레메트리에 로그가 실려 오면 여기로 넘겨 정규화·저장합니다.
     * 라우터가 직접 저장하지 않는 이유: 로그 정규화(벤더별 심각도 해석)와
     * 중복 제거는 로그 도메인의 책임이고, 라우터는 분기만 해야 합니다.
     *
     * <p>⚠️ {@code @Lazy} 를 쓰지 않으면 순환 참조가 생깁니다.
     * ({@code LogService} → 저장소, 라우터 → {@code LogService})
     * 실제로는 순환이 아니지만, 향후 로그 서비스가 에이전트 정보를 참조하면
     * 바로 순환이 되므로 미리 끊어 둡니다.
     */
    private final LogService logService;

    /**
     * 알림 기록기입니다.
     *
     * <p>Agent 연결/해제는 운영자가 기다리는 사건입니다. "프로버를 띄웠는데
     * 왜 안 보이지" 를 확인할 수 있는 유일한 기록이므로 알림으로 남깁니다.
     *
     * <p>{@link NotificationService} 는 저장소만 의존하므로 순환 참조가
     * 생기지 않습니다.
     */
    private final NotificationService notificationService;

    /**
     * 원문 CLI 문자열을 내부 계약 형태로 되돌리는 폴백 파서입니다.
     *
     * <p>정상 경로에서는 Prober 가 이미 파싱한 JSON 이 오므로 이 서비스는
     * 아무 일도 하지 않습니다. 구버전 Prober 나 운영자가 붙여 넣은 원문처럼
     * <b>파싱되지 않은 문자열</b>이 들어올 때만 동작합니다.
     */
    private final CliIngestionService cliIngestionService;

    /**
     * 격리 명령의 적용 결과를 받을 곳입니다.
     *
     * <p>Agent 는 격리/해제를 적용한 뒤 결과를 {@code ack} 로 돌려줍니다.
     * 라우터는 그 ack 를 이 서비스로 넘겨 기록하게 합니다. 라우터가 직접
     * 저장하지 않는 이유는, 격리 상태의 소유자가 격리 서비스이기 때문입니다
     * (두 곳이 상태를 가지면 반드시 어긋납니다).
     *
     * <p>{@code @Autowired(required = false)} 로 두는 이유는 라우터 단위
     * 테스트가 격리 서비스 없이도 돌아야 하기 때문입니다.
     */
    private QuarantineService quarantineService;

    public AgentMessageRouterService(AgentSessionRegistry registry,
                                     PolicyRegistryService policyRegistry,
                                     DeviceConfigService deviceConfigService,
                                     LogService logService,
                                     NotificationService notificationService,
                                     CliIngestionService cliIngestionService) {
        this(registry, policyRegistry, new AgentTelemetryStore(), deviceConfigService,
                logService, notificationService, cliIngestionService);
    }

    /**
     * 전체 의존을 받는 생성자입니다.
     *
     * <p>테스트가 저장소를 <b>직접 관찰</b>할 수 있어야 해서 열어 둡니다.
     * (라우터를 통해 간접 관찰하면 검증하려는 계약이 흐려집니다)
     *
     * @param registry            Agent 세션 레지스트리
     * @param policyRegistry      정책 생성 서비스
     * @param telemetryStore      최근 관측값 저장소
     * @param deviceConfigService 중립 설정 변환 서비스
     * @param logService          로그 적재 서비스
     * @param notificationService 알림 서비스
     * @param cliIngestionService 원문 CLI 폴백 파서
     */
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

    /**
     * 격리 서비스를 연결합니다.
     *
     * <p>생성자 주입을 쓰지 않는 이유: {@link QuarantineService} 가
     * {@link AgentSessionRegistry} 와 알림/이력 서비스에 의존하므로, 생성자로
     * 엮으면 이 라우터를 쓰는 단위 테스트가 불필요하게 무거워집니다.
     *
     * @param quarantineService 격리 서비스 (테스트에서는 생략 가능)
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setQuarantineService(QuarantineService quarantineService) {
        this.quarantineService = quarantineService;
    }

    /**
     * 수신 봉투를 처리합니다.
     *
     * @param session 메시지를 보낸 세션
     * @param envelope 파싱된 봉투
     * @return Agent 에게 돌려줄 응답 (응답이 없으면 {@code null})
     */
    public Envelope handle(WebSocketSession session, Envelope envelope) {
        final String agentId = resolveAgentId(session, envelope);
        telemetryStore.touch(agentId);
        log.debug("recv {} from agent={}", envelope.summary(), agentId);

        return switch (envelope.getType()) {
            case Envelope.Types.HELLO -> onHello(session, envelope, agentId);
            case Envelope.Types.POLICY_REQUEST -> onPolicyRequest(session, envelope, agentId);
            case Envelope.Types.TELEMETRY -> onTelemetry(envelope, agentId);
            // Agent 가 우리 푸시(command)에 대한 확인을 보낼 때 사용합니다.
            case Envelope.Types.ACK -> onAck(envelope, agentId);
            case Envelope.Types.ERROR -> onError(envelope, agentId);
            default -> Envelope.error(envelope.getCorrelation_id(),
                    "unsupported message type: " + envelope.getType());
        };
    }

    /**
     * {@code hello} 처리: Agent 를 등록하고 ack 를 돌려니다.
     *
     * <p>Agent 의 첫 메시지가 hello 가 아니어도(예: 구버전 Agent 가 바로 telemetry 를
     * 보내도) 동작하도록, 등록은 hello 뿐 아니라 모든 메시지에서 지연 등록됩니다.
     *
     * @param session 세션
     * @param envelope 요청 봉투
     * @param agentId 해석된 Agent 식별자
     * @return ack 봉투
     */
    private Envelope onHello(WebSocketSession session, Envelope envelope, String agentId) {
        final boolean firstConnect = agentId != null
                && !registry.connectedAgentIds().contains(agentId);

        registry.register(agentId, session);

        // 새 장치가 붙는 것은 운영자가 기다리는 사건입니다.
        // (재접속마다 알리면 하트비트처럼 쌓여 쓸모가 없어집니다)
        if (firstConnect) {
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
        return Envelope.replyTo(Envelope.Types.ACK, envelope, payload);
    }

    /**
     * {@code policy-request} 처리: 장치 유형/식별자를 해석해 정책을 돌려줍니다.
     *
     * <p>장치 유형 결정 순서:
     * <ol>
     *   <li>투의 {@code device_type} (문자열 → {@link DeviceType#fromString})</li>
     *   <li>payload 의 {@code device_id} 접두사 ({@link DeviceType#inferFromDeviceId})</li>
     *   <li>둘 다 없으면 {@link DeviceType#VM}</li>
     * </ol>
     *
     * @param session 세션
     * @param envelope 요청 봉투
     * @param agentId 해석된 Agent 식별자
     * @return policy-response 봉투
     */
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
        log.info("policy-request from agent={} device={} type={} -> {} (source={})",
                agentId, deviceId, deviceType, policy.path("policy_id").asString("?"),
                policy.path("summary").path("source").asString("?"));

        return Envelope.replyTo(Envelope.Types.POLICY_RESPONSE, envelope, policy);
    }

    /**
     * {@code telemetry} 처리: 최근 값만 저장하고 응답하지 않습니다.
     *
     * <p>payload 를 그대로 보관하는 것에 더해, 벤더별 파서를 거쳐
     * {@link NeutralDeviceConfig} 로도 변환해 둡니다. 이후 분석(정책 위반, 도달성)
     * 과 DB 저장은 벤더를 모르는 그 구조를 사용합니다.
     *
     * @param envelope 수신 봉투
     * @param agentId 해석된 Agent 식별자
     * @return 항상 {@code null} (일방향)
     */
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
            // Prober 버전에 따라 payload 에 원문 CLI 문자열만 실려 올 수 있습니다.
            // 그 경우 여기서 ANTLR 문법으로 파싱해 내부 계약 형태로 맞춥니다.
            // (정상 경로처럼 이미 구조화된 JSON 이면 아무 일도 하지 않습니다)
            final JsonNode normalized = normalizedPayload(payload, product);
            final NeutralDeviceConfig config = deviceConfigService.parse(agentId, product, normalized);
            telemetryStore.putConfig(agentId, config);

            log.info("telemetry from agent={} keys={} format={} ifaces={} routes={} vlans={}",
                    agentId, payload.size(), config.getFormat(),
                    config.getInterfaces().size(), config.getRoutes().size(),
                    config.getVlans().size());
        } catch (RuntimeException ex) {
            // 변환 실패가 수신 자체를 막지 않도록 흡수합니다. (일방향 메시지)
            log.warn("config parse failed for agent={}: {}", agentId, ex.getMessage());
            log.info("telemetry from agent={} keys={}", agentId, payload.size());
        }

        // 텔레메트리에 로그가 실려 오면 적재합니다.
        // 로그 적재 실패가 텔레메트리 수신을 막으면 안 되므로 예외를 흡수합니다.
        ingestTelemetryLogs(agentId, payload);

        return null;
    }

    /**
     * payload 에 원문 CLI 문자열이 섞여 있으면 파싱 결과를 덧붙인 새 노드를 만듭니다.
     *
     * <h2>왜 원본을 그대로 쓰지 않는가</h2>
     * <p>payload 는 {@code lastTelemetry} 에 그대로 보관되어 조회 API 로도 나갑니다.
     * 원본을 제자리에서 고치면 "Agent 가 보낸 그대로" 라는 보관 의미가 깨집니다.
     * 그래서 추가할 파싱 결과가 있을 때만 <b>얕은 복사</b>를 만들어 씁니다.
     *
     * @param payload 텔레메트리 payload
     * @param product 제품명 (벤더 판별용)
     * @return 파싱 결과가 덧붙은 payload (추가분이 없으면 원본)
     */
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

    /**
     * 텔레메트리 payload 에서 로그를 꺼내 적재합니다.
     *
     * <h2>왜 payload 키를 여러 개 확인하는가</h2>
     * <p>프로버 버전과 장치 유형에 따라 로그 키가 다를 수 있습니다.
     * 하나만 보면 장치에 따라 조용히 로그를 잃습니다.
     * ({@code log_status} → {@code logs} → {@code syslog} 순으로 시도)
     *
     * <p>지원하는 형태:
     * <pre>
     *   {"log_status": {"entries": [{"raw": "..."}, ...]}}
     *   {"log_status": {"lines": ["...", "..."]}}
     *   {"logs": ["...", "..."]}
     * </pre>
     *
     * @param agentId 장비 식별자
     * @param payload 텔레메트리 payload
     */
    private void ingestTelemetryLogs(String agentId, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return;
        }

        try {
            final List<String> lines = extractLogLines(payload);
            if (lines.isEmpty()) {
                return;
            }

            // 가드: 프로버가 장비 버퍼 전체를 매 주기 보내면 로그가 폭증합니다.
            // 30초 주기로 2000줄을 계속 받으면 하루에 수백만 건이 됩니다.
            // 그래서 한 번에 받는 줄 수를 제한합니다.
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

    /** 텔레메트리 payload 에서 로그 줄 목록을 꺼냅니다. */
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

    /**
     * 특정 Agent 의 최근 중립 설정을 조회합니다. (분석/저장 계층의 진입점)
     *
     * @param agentId Agent 식별자
     * @return 최근 설정 (없으면 {@code null})
     */
    public NeutralDeviceConfig lastConfigOf(String agentId) {
        return telemetryStore.configOf(agentId);
    }

    /**
     * 오프라인 스냅샷의 payload 를 <b>온라인 텔레메트리와 같은 방식</b>으로 반영합니다.
     *
     * <h2>왜 라우터 서비스에 두는가</h2>
     * <p>오프라인 경로(파일 업로드)가 {@code onTelemetry} 와 다른 코드로 변환하면
     * 두 경로가 조용히 어긋납니다. 예를 들어 오프라인 쪽에서만 {@code product}
     * 폴백을 빠뜨리면, 화면에는 어떤 장비는 보이고 어떤 장비는 안 보이게 됩니다.
     * 그래서 변환·저장 지점을 이 한 곳으로 모읍니다.
     *
     * <p>차이는 <b>연결 상태</b>뿐입니다. 파일로 들어온 설정은 현재 세션이
     * 없으므로 {@code lastSeen} 을 채우지 않습니다 — 채우면 UI 가 "연결됨" 으로
     * 잘못 표시합니다. 대신 오프라인 표시는
     * {@link #isOfflineOrigin(String)} 로 따로 조회합니다.
     *
     * @param agentId Agent 식별자
     * @param product 제품명 (없으면 payload 에서 추론)
     * @param payload 텔레메트리 payload
     * @return 변환된 중립 설정 (null 이 아님)
     */
    public NeutralDeviceConfig acceptOfflineTelemetry(String agentId, String product, JsonNode payload) {
        final String resolvedProduct = (product == null || product.isBlank())
                ? text(payload, "product", text(payload, "vendor", "unknown"))
                : product;

        // 온라인 경로와 같은 폴백 파싱을 거칩니다. 오프라인 스냅샷에는
        // 원문 CLI 가 그대로 들어 있는 경우가 더 흔하므로 여기서도 필요합니다.
        final JsonNode normalized = normalizedPayload(payload, resolvedProduct);

        final NeutralDeviceConfig config =
                deviceConfigService.parse(agentId, resolvedProduct, normalized);

        // 오프라인 출처로 표시합니다. (UI 가 "연결 끊김 + 파일 업로드" 로 표시)
        // ⚠️ 연결 상태는 채우지 않습니다 — 채우면 UI 가 "연결됨" 으로 잘못 봅니다.
        telemetryStore.putTelemetry(agentId, payload);
        telemetryStore.putOfflineConfig(agentId, config);

        log.info("offline telemetry accepted: agent={} product={} format={} keys={}",
                agentId, resolvedProduct, config.getFormat(), payload == null ? 0 : payload.size());
        return config;
    }

    /**
     * 이 Agent 의 최근 설정이 파일 업로드로 들어왔는지 확인합니다.
     *
     * @param agentId Agent 식별자
     * @return 오프라인 업로드로 반영된 설정이면 true
     */
    public boolean isOfflineOrigin(String agentId) {
        return telemetryStore.isOfflineOrigin(agentId);
    }

    /**
     * 지금까지 변환된 모든 중립 설정을 돌려줍니다.
     *
     * @return Agent 식별자 → 중립 설정
     */
    public Map<String, NeutralDeviceConfig> allConfigs() {
        return telemetryStore.allConfigs();
    }

    /**
     * 최근 관측값 저장소를 돌려줍니다.
     *
     * <p>조회 API 들이 이 저장소를 직접 쓰도록 열어 둡니다. 그래야 다음에
     * 저장 위치가 DB 로 바뀌어도 이 라우터를 고칠 필요가 없습니다.
     *
     * @return 저장소
     */
    public AgentTelemetryStore telemetryStore() {
        return telemetryStore;
    }

    /**
     * Agent 가 보낸 {@code ack} 를 기록합니다.
     *
     * @param envelope 수신 봉투
     * @param agentId 해석된 Agent 식별자
     * @return 항상 {@code null}
     */
    private Envelope onAck(Envelope envelope, String agentId) {
        log.info("ack from agent={} correlation={}", agentId, envelope.getCorrelation_id());

        // 격리/해제 명령의 적용 결과는 격리 서비스가 소유합니다.
        // 라우터는 전달만 합니다. (두 곳이 상태를 가지면 반드시 어긋납니다)
        if (quarantineService != null) {
            quarantineService.recordAck(agentId, envelope.payloadOrEmpty());
        }
        return null;
    }

    /**
     * Agent 가 보고한 오류를 경고로 기록합니다.
     *
     * @param envelope 수신 봉투
     * @param agentId 해석된 Agent 식별자
     * @return 항상 {@code null}
     */
    private Envelope onError(Envelope envelope, String agentId) {
        log.warn("agent={} reported error: {}", agentId, envelope.getError());
        return null;
    }

    /**
     * 특정 Agent 의 최근 텔레메트리를 조회합니다. (REST/프론트엔드 연동 지점)
     *
     * @param agentId Agent 식별자
     * @return 최근 텔레메트리 (없으면 {@code null})
     */
    public JsonNode lastTelemetryOf(String agentId) {
        return telemetryStore.telemetryOf(agentId);
    }

    /**
     * 원본 텔레메트리라도 보낸 적 있는 모든 Agent 식별자를 돌려줍니다.
     *
     * <p>{@link #allConfigs()} 는 <b>파싱까지 성공한</b> Agent 만 담습니다.
     * 그래서 파서가 모르는 벤더의 장치가 붙으면 목록에서 통째로 사라집니다.
     * 배포 확인 화면은 "서버가 이 장치를 봤는가" 를 알아야 하므로 원본 기준의
     * 목록도 필요합니다.
     *
     * @return 원본 텔레메트리를 보낸 Agent 식별자 집합
     */
    public Set<String> allTelemetryAgentIds() {
        return telemetryStore.allTelemetryAgentIds();
    }

    /**
     * Agent 의 마지막 수신 시각입니다. (헬스 체크 지점)
     *
     * @param agentId Agent 식별자
     * @return 마지막 수신 시각 (없으면 {@code null})
     */
    public Instant lastSeenOf(String agentId) {
        return telemetryStore.lastSeenOf(agentId);
    }

    /**
     * 누적 정책 요청 건수입니다.
     *
     * @return 처리한 policy-request 수
     */
    public long policyRequestCount() {
        return policyRequestCount.get();
    }

    /**
     * Agent 식별자를 결정합니다.
     *
     * <p>봉투에 {@code agent_id} 가 없으면 세션 속성({@code agent_id})을,
     * 그것도 없으면 {@code "session:<id>"} 를 니다.
     *
     * @param session 세션
     * @param envelope 수신 봉투
     * @return Agent 식별자 (항상 non-null)
     */
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

    /**
     * JSON 노드에서 문자열 필드를 안전하게 꺼니다.
     *
     * @param node 대상 노드
     * @param field 필드명
     * @param fallback 값이 없을 때 사용할 문자열
     * @return 필드 값 또는 fallback
     */
    private String text(JsonNode node, String field, String fallback) {
        final JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        final String text = value.asString(null);
        return (text == null || text.isBlank()) ? fallback : text;
    }
}