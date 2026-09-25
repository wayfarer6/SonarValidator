package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.DeviceConfigService;
import org.sonar.sonarvalidator_backend.Service.PolicyRegistryService;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * plain WebSocket 봉투 처리 계약을 실소켓 없이 검증합니다.
 *
 * <p>{@link WebSocketSession} 은 무거운 목 의존성 대신 최소 스텁으로 대체합니다.
 * 검증 범위는 "봉투 → 응답 봉투" 변환이므로 세션은 식별/속성만 제공하면 충분합니다.
 */
class AgentMessageRouterTest {

    private JsonMapper mapper;
    private AgentSessionRegistry registry;
    private AgentMessageRouterService router;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder().build();
        registry = new AgentSessionRegistry(mapper);
        // 라우터는 텔레메트리 로그를 LogService 로 넘깁니다.
        // 이 테스트는 봉투 처리 계약만 보므로 로그 적재는 스텁으로 대체합니다.
        // (실제 LogService 는 저장소가 필요해 단위 테스트에 부적합합니다)
        router = new AgentMessageRouterService(
                registry,
                new PolicyRegistryService(),
                new DeviceConfigService(java.util.List.of()),
                new NoopLogService(),
                new NoopNotificationService(),
                // 원문 CLI 폴백 파서. 이 테스트는 구조화된 JSON 만 보내므로
                // 실제로 호출되지 않습니다.
                new org.sonar.sonarvalidator_backend.Service.cli.CliIngestionService(
                        new org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser()));
    }

    /**
     * 알림을 저장하지 않는 스텁입니다.
     *
     * <p>라우터는 Agent 연결/해제를 알림으로 남기지만, 이 테스트의 관심사는
     * "봉투 → 응답 봉투" 변환입니다. DB 쓰기가 끼어들면 테스트가 저장소를
     * 함께 띄워야 하므로 스텁으로 대체합니다.
     */
    private static class NoopNotificationService
            extends org.sonar.sonarvalidator_backend.Service.NotificationService {

        NoopNotificationService() {
            super(null);
        }

        @Override
        public void notifyQuietly(String category,
                                  String severity,
                                  String title,
                                  String message,
                                  String projectKey,
                                  String agentId,
                                  String source,
                                  String link,
                                  String dedupeKey) {
            // 알림을 저장하지 않습니다.
        }
    }

    /**
     * 로그 적재를 하지 않는 스텁입니다.
     *
     * <p>라우터 테스트의 관심사는 "봉투 → 응답 봉투" 변환이므로
     * DB 쓰기는 끼어들지 않아야 합니다.
     */
    private static class NoopLogService
            extends org.sonar.sonarvalidator_backend.Service.log.LogService {

        NoopLogService() {
            super(null, new org.sonar.sonarvalidator_backend.Service.log.LogNormalizer());
        }

        @Override
        public java.util.Map<String, Object> ingest(String agentId,
                                                    String product,
                                                    String projectKey,
                                                    String source,
                                                    java.util.List<String> lines) {
            // 아무것도 저장하지 않습니다.
            return java.util.Map.of("received", lines == null ? 0 : lines.size(),
                    "inserted", 0, "duplicated", 0, "skipped", 0);
        }
    }

    /**
     * 투가 JSON 로 왕복해도 필드가 보존되는지 확인합니다.
     * Agent(C++)가 직접 만드는 JSON 과 서버 DTO 의 계약이 어긋나지 않게 하는 회귀 테스트입니다.
     */
    @Test
    @DisplayName("Envelope 은 snake_case JSON 과 왕복한다")
    void envelopeRoundTrip() {
        final String json = """
                {"type":"policy-request","agent_id":"vm-01","device_type":"VM",
                 "correlation_id":"c-1","payload":{"device_id":"vm-01"}}
                """;

        final Envelope envelope = mapper.readValue(json, Envelope.class);
        assertEquals(Envelope.Types.POLICY_REQUEST, envelope.getType());
        assertEquals("vm-01", envelope.getAgent_id());
        assertEquals("c-1", envelope.getCorrelation_id());
        assertEquals("vm-01", envelope.payloadOrEmpty().path("device_id").asString(""));

        final String again = mapper.writeValueAsString(envelope);
        assertTrue(again.contains("\"correlation_id\":\"c-1\""), again);
    }

    /**
     * hello 는 ack 를 돌려주고 Agent 를 레지스트리에 등록해야 합니다.
     */
    @Test
    @DisplayName("hello 는 ack 를 반환하고 Agent 를 등록한다")
    void helloRegistersAgent() {
        final Envelope hello = Envelope.of(Envelope.Types.HELLO);
        hello.setAgent_id("vm-01");
        hello.setCorrelation_id("c-1");

        final Envelope response = router.handle(session("s1"), hello);

        assertNotNull(response);
        assertEquals(Envelope.Types.ACK, response.getType());
        assertEquals("c-1", response.getCorrelation_id());
        assertEquals(1, registry.connectedCount());
        assertEquals(1, response.getPayload().path("connected_agents").asInt());
    }

    /**
     * policy-request  device_type 에 맞는 정책을 담은 policy-response 여야 합니다.
     */
    @Test
    @DisplayName("policy-request 는 장치 유형에 맞는 정책을 반환한다")
    void policyRequestReturnsPolicy() {
        final Envelope request = Envelope.of(Envelope.Types.POLICY_REQUEST);
        request.setAgent_id("rt-01");
        request.setDevice_type("ROUTER");
        request.setCorrelation_id("c-2");
        request.setPayload(mapper.readTree("{\"device_id\":\"rt-01\"}"));

        final Envelope response = router.handle(session("s2"), request);

        assertNotNull(response);
        assertEquals(Envelope.Types.POLICY_RESPONSE, response.getType());
        assertEquals("c-2", response.getCorrelation_id());

        final JsonNode policy = response.getPayload();
        assertEquals("ROUTER", policy.path("device_type").asString(""));
        assertEquals("rt-01", policy.path("device_id").asString(""));
        assertTrue(policy.path("policy_id").asString("").startsWith("pol-router-"));

        // C++ policy_receiver 는 "policies" 배열을 우선 처리합니다.
        final JsonNode rules = policy.path("policies");
        assertTrue(rules.isArray());
        assertEquals(1, rules.size());
        // 문서 스키마 규칙: 스칼라도 배열로 감쌈
        assertTrue(rules.get(0).path("command").isArray());
        assertEquals("on", rules.get(0).path("command").get(0).asString(""));
        assertEquals(1L, router.policyRequestCount());
    }

    /**
     * device_type 이 없어도 device_id 접두사로 유형을 추론해야 합니다.
     * (C++ Agent 는 device_id 만 보내는 경로가 있음)
     */
    @Test
    @DisplayName("device_type 이 없으면 device_id 접두사로 추론한다")
    void policyRequestInfersTypeFromDeviceId() {
        final Envelope request = Envelope.of(Envelope.Types.POLICY_REQUEST);
        request.setAgent_id("sw-01");
        request.setPayload(mapper.readTree("{\"device_id\":\"switch-3\"}"));

        final Envelope response = router.handle(session("s3"), request);

        assertEquals("SWITCH", response.getPayload().path("device_type").asString(""));
    }

    /**
     * telemetry  일방향이므로 응답이 없어야 합니다.
     * (응답을 보내면 Agent 의 요청-응답 매칭이 깨집니다.)
     */
    @Test
    @DisplayName("telemetry 는 응답하지 않고 최근 값만 저장한다")
    void telemetryIsUniDirectional() {
        final Envelope telemetry = Envelope.of(Envelope.Types.TELEMETRY);
        telemetry.setAgent_id("vm-01");
        telemetry.setPayload(mapper.readTree("{\"nic_status\":[{\"name\":\"ens33\"}]}"));

        final Envelope response = router.handle(session("s4"), telemetry);

        assertNull(response);
        assertNotNull(router.lastTelemetryOf("vm-01"));
        assertNotNull(router.lastSeenOf("vm-01"));
    }

    /**
     * 알 수 없는 type 은 error 봉투로 거부해야 합니다.
     */
    @Test
    @DisplayName("알 수 없는 type 은 error 로 거부한다")
    void unknownTypeRejected() {
        final Envelope unknown = Envelope.of("something-else");
        unknown.setCorrelation_id("c-9");

        final Envelope response = router.handle(session("s5"), unknown);

        assertEquals(Envelope.Types.ERROR, response.getType());
        assertEquals("c-9", response.getCorrelation_id());
        assertTrue(response.getError().contains("unsupported"));
    }

    /**
     * 연결되지 않은 Agent 로의 푸시는 예외 없이 false 를 돌려줘야 합니다.
     */
    @Test
    @DisplayName("미연결 Agent 로의 푸시는 false 를 반환한다")
    void pushToDisconnectedAgentFails() {
        final Envelope command = Envelope.of(Envelope.Types.COMMAND);
        command.setPayload(mapper.readTree("{\"monitor_interval\":60}"));

        assertFalse(registry.sendTo("nobody", command));
        assertEquals(0, registry.broadcast(command));
    }

    /**
     * DeviceType 파싱은 문서 약어와 C++ 이름을 모두 허용해야 합니다.
     */
    @Test
    @DisplayName("DeviceType 은 VM/Switch 표기를 모두 허용한다")
    void deviceTypeParsing() {
        assertEquals(DeviceType.VM, DeviceType.fromString("VM"));
        assertEquals(DeviceType.VM, DeviceType.fromString("VirtualMachine"));
        assertEquals(DeviceType.SWITCH, DeviceType.fromString("switch"));
        assertEquals(DeviceType.FIREWALL, DeviceType.fromString(" Firewall "));
        assertNull(DeviceType.fromString("nope"));
    }

    /**
     * 최소 WebSocketSession 스텁. 식별자와 속성만 제공합니다.
     *
     * @param id 세션 ID
     * @return 스텁 세션
     */
    private WebSocketSession session(String id) {
        return new StubSession(id);
    }

    /** 테스트용 최소 세션 구현. */
    private static final class StubSession implements WebSocketSession {
        private final String id;
        private final java.util.Map<String, Object> attributes = new HashMap<>();

        private StubSession(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public java.util.Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) {
            // 전송은 이 테스트의 관심사가 아닙니다.
        }

        @Override
        public void close() {
        }

        @Override
        public void close(org.springframework.web.socket.CloseStatus status) {
        }

        @Override
        public java.net.URI getUri() {
            return java.net.URI.create("ws://localhost:3000/api/v1/management");
        }

        @Override
        public org.springframework.http.HttpHeaders getHandshakeHeaders() {
            return new org.springframework.http.HttpHeaders();
        }

        @Override
        public java.security.Principal getPrincipal() {
            return null;
        }

        @Override
        public java.net.InetSocketAddress getLocalAddress() {
            return new java.net.InetSocketAddress("127.0.0.1", 3000);
        }

        @Override
        public java.net.InetSocketAddress getRemoteAddress() {
            return new java.net.InetSocketAddress("127.0.0.1", 50000);
        }

        @Override
        public java.util.List<org.springframework.web.socket.WebSocketExtension> getExtensions() {
            return java.util.List.of();
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 8192;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 8192;
        }
    }
}