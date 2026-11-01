package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Service.DeviceConfigService;
import org.sonar.sonarvalidator_backend.Service.OfflineSnapshotService;
import org.sonar.sonarvalidator_backend.Service.OfflineSnapshotService.ImportResult;
import org.sonar.sonarvalidator_backend.Service.OfflineSnapshotService.SnapshotResult;

import tools.jackson.databind.json.JsonMapper;

class OfflineSnapshotImportTest {

    private OfflineSnapshotService service;
    private org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService router;

    @BeforeEach
    void setUp() {
        // 파서 목록을 비우지 않습니다: 실제 파서가 payload 키를 읽는지 확인해야 합니다.
        final DeviceConfigService deviceConfigService =
                new DeviceConfigService(parsers());
        final var registry = new org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry(
                JsonMapper.builder().build());
        router = new org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService(
                registry,
                new org.sonar.sonarvalidator_backend.Service.PolicyRegistryService(),
                deviceConfigService,
                // 오프라인 가져오기 테스트는 로그 적재와 무관하므로 스텁을 씁니다.
                new NoopLogService(),



                new NoopNotificationService(),
                // 스냅샷 payload 는 이미 구조화된 JSON 이므로 폴백 파서는
                // 동작하지 않습니다.
                new org.sonar.sonarvalidator_backend.Service.cli.CliIngestionService(
                        new org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser()));
        service = new OfflineSnapshotService(deviceConfigService, router);
    }

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
            return java.util.Map.of("received", lines == null ? 0 : lines.size(),
                    "inserted", 0, "duplicated", 0, "skipped", 0);
        }
    }

    private List<org.sonar.sonarvalidator_backend.Model.Config.DeviceConfigParser> parsers() {
        return List.of(
                new org.sonar.sonarvalidator_backend.Model.Config.Vendors.FrrRouterConfigParser(),
                new org.sonar.sonarvalidator_backend.Model.Config.Vendors.CiscoRouterConfigParser(),
                new org.sonar.sonarvalidator_backend.Model.Config.Vendors.OpenVSwitchConfigParser(),
                new org.sonar.sonarvalidator_backend.Model.Config.Vendors.AlpineFirewallConfigParser(),
                new org.sonar.sonarvalidator_backend.Model.Config.Vendors.LinuxVmConfigParser(),
                new org.sonar.sonarvalidator_backend.Model.Config.Vendors.AristaSwitchConfigParser());
    }

    private String frrSnapshot() {
        return """
                {
                  "schema": "sonar.offline.snapshot",
                  "schema_version": 1,
                  "agent_id": "frr-1",
                  "agent_name": "frr-1",
                  "device_type": "ROUTER",
                  "product": "FRR",
                  "vendor": "FRR",
                  "kernel": "6.6.0",
                  "collected_at": "2026-09-19T04:00:00Z",
                  "reason": "server-unreachable",
                  "payload": {
                    "agent": "frr-1",
                    "product": "FRR",
                    "vendor": "FRR",
                    "device_type": "ROUTER",
                    "route_status": {
                      "routes": [
                        {"prefix": "10.99.10.0/24", "next_hop": "0.0.0.0",
                         "protocol": "connected", "interface_name": "eth0"},
                        {"prefix": "0.0.0.0/0", "next_hop": "192.168.122.1",
                         "protocol": "static", "interface_name": "eth1"}
                      ]
                    },
                    "nic_status": {
                      "interfaces": [
                        {"name": "eth0", "state": "UP", "mac": "aa:bb:cc:dd:ee:01",
                         "addresses": [{"address": "10.99.10.1", "prefix_len": 24}]}
                      ]
                    }
                  }
                }
                """;
    }

    @Test
    @DisplayName("정상 스냅샷은 파서를 거쳐 반영된다")
    void acceptsValidSnapshot() {
        final SnapshotResult result = service.importContent("frr.json", frrSnapshot());

        assertTrue(result.accepted(), "정상 파일은 수락되어야 함: " + result.errors());
        assertEquals("frr-1", result.agent_id());
        assertNotNull(result.format(), "형식이 판정되어야 함");
        assertEquals(1, result.interface_count(), "인터페이스 1건");
        assertEquals(2, result.route_count(), "라우트 2건");
        assertTrue(result.errors().isEmpty(), "오류가 없어야 함");
    }

    @Test
    @DisplayName("schema 가 다르면 사유와 함께 거부한다")
    void rejectsWrongSchema() {
        final String wrong = """
                {"schema":"some.other.format","agent_id":"x","payload":{"a":1}}
                """;
        final SnapshotResult result = service.importContent("x.json", wrong);

        assertFalse(result.accepted());
        assertNull(result.agent_id(), "거부 시 식별자를 채우지 않음");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("schema 불일치")),
                "스키마 불일치 사유가 남아야 함: " + result.errors());
    }

    @Test
    @DisplayName("payload 가 없으면 추측하지 않고 거부한다")
    void rejectsMissingPayload() {
        final String noPayload = """
                {"schema":"sonar.offline.snapshot","schema_version":1,
                 "agent_id":"a1","product":"FRR"}
                """;
        final SnapshotResult result = service.importContent("a1.json", noPayload);

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("payload")),
                "payload 누락 사유가 남아야 함: " + result.errors());
    }

    @Test
    @DisplayName("payload 가 비면 조용히 성공하지 않고 거부한다")
    void rejectsEmptyPayload() {
        // 이 케이스가 가장 위험합니다. 받아들이면 화면이 비는데

        final String emptyPayload = """
                {"schema":"sonar.offline.snapshot","schema_version":1,
                 "agent_id":"a1","product":"FRR","payload":{}}
                """;
        final SnapshotResult result = service.importContent("a1.json", emptyPayload);

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("비어 있습니다")),
                "빈 payload 사유가 남아야 함: " + result.errors());
    }

    @Test
    @DisplayName("제품명이 없으면 파서를 고를 수 없어 거부한다")
    void rejectsMissingProduct() {
        final String noProduct = """
                {"schema":"sonar.offline.snapshot","schema_version":1,
                 "agent_id":"a1","payload":{"route_status":{"routes":[]}}}
                """;
        final SnapshotResult result = service.importContent("a1.json", noProduct);

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("제품명")),
                "제품명 누락 사유가 남아야 함: " + result.errors());
    }

    @Test
    @DisplayName("파서가 아무것도 해석하지 못하면 조용히 성공하지 않는다")
    void rejectsUnparseablePayload() {
        // 제품명은 맞지만 payload 키가 전부 낯선 경우입니다.
        // (Agent 버전이 달라 키 이름이 바뀐 상황을 흉내낸다)
        final String unknownKeys = """
                {"schema":"sonar.offline.snapshot","schema_version":1,
                 "agent_id":"a1","product":"FRR",
                 "payload":{"unexpected_key_1":{"x":1},"unexpected_key_2":[1,2]}}
                """;
        final SnapshotResult result = service.importContent("a1.json", unknownKeys);

        assertFalse(result.accepted(),
                "해석된 항목이 0건이면 거부해야 함 (조용한 성공 방지)");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("해석하지 못했습니다")),
                "파서 실패 사유가 남아야 함: " + result.errors());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("keys=2")),
                "진단을 위해 payload 키 개수를 알려야 함: " + result.errors());
    }

    @Test
    @DisplayName("깨진 JSON 은 예외 대신 거부 결과를 돌려준다")
    void rejectsMalformedJson() {
        // 예외가 밖으로 나가면 업로드 전체가 500 이 되어 다른 장비 설정도 잃습니다.
        final SnapshotResult result = service.importContent("broken.json", "{ not json");

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("JSON 파싱 실패")),
                "파싱 실패 사유가 남아야 함: " + result.errors());
    }

    @Test
    @DisplayName("빈 파일은 거부한다")
    void rejectsEmptyFile() {
        final SnapshotResult result = service.importContent("empty.json", "");

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("빈 파일")),
                "빈 파일 사유가 남아야 함: " + result.errors());
    }

    @Test
    @DisplayName("여러 파일 중 하나가 깨져도 나머지는 반영된다")
    void partialFailureKeepsOthers() {
        final java.util.Map<String, java.io.InputStream> files = new java.util.LinkedHashMap<>();

        files.put("good.json", stream(frrSnapshot()));
        files.put("broken.json", stream("{ broken"));
        files.put("wrong-schema.json", stream("""
                {"schema":"other","payload":{"a":1}}
                """));

        final ImportResult result = service.importSnapshots(files);

        assertEquals(3, result.received(), "받은 건수 3");
        assertEquals(1, result.accepted(), "정상 1건만 수락");
        assertEquals(2, result.rejected(), "나머지 2건 거부");
        assertEquals(3, result.snapshots().size(), "파일별 결과가 모두 보고됨");

        // 실패한 파일의 사유가 각각 남아 있어야 합니다.
        final SnapshotResult broken = result.snapshots().stream()
                .filter(s -> "broken.json".equals(s.file_name())).findFirst().orElseThrow();
        assertFalse(broken.accepted());
        assertFalse(broken.errors().isEmpty(), "깨진 파일의 사유가 비어 있으면 안 됨");

        final SnapshotResult good = result.snapshots().stream()
                .filter(s -> "good.json".equals(s.file_name())).findFirst().orElseThrow();
        assertTrue(good.accepted());
        assertEquals("frr-1", good.agent_id());
    }

    @Test
    @DisplayName("파일이 하나도 없으면 안내 문구를 남긴다")
    void emptyUploadIsExplained() {
        final ImportResult result = service.importSnapshots(java.util.Map.of());

        assertEquals(0, result.received());
        assertTrue(result.server_warnings().stream().anyMatch(w -> w.contains("files")),
                "form 필드 이름을 알려줘야 함: " + result.server_warnings());
    }

    @Test
    @DisplayName("더 높은 schema_version 은 경고만 남기고 아는 필드로 처리한다")
    void futureSchemaVersionIsForwardCompatible() {
        // 거부하면 새 Agent 가 만든 파일을 구 서버가 못 읽게 됩니다.
        final String future = frrSnapshot().replace("\"schema_version\": 1",
                "\"schema_version\": 99");
        final SnapshotResult result = service.importContent("future.json", future);

        assertTrue(result.accepted(), "앞으로의 버전은 계속 처리해야 함: " + result.errors());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("schema_version")),
                "버전 차이를 경고로 알려야 함: " + result.warnings());
    }

    @Test
    @DisplayName("스키마 안내는 지원 파서 목록을 포함한다")
    void describesSchema() {
        final var description = service.describeSchema();

        assertEquals("sonar.offline.snapshot", description.get("schema"));
        assertEquals(1, description.get("schema_version"));
        assertTrue(description.containsKey("max_bytes"));
        assertTrue(description.containsKey("supported_formats"));
    }

    @Test
    void rejectedSnapshotPreservesAcceptedConfigAndOrigin() {
        assertTrue(service.importContent("good.json", frrSnapshot()).accepted());
        final var previousConfig = router.lastConfigOf("frr-1");
        final var previousPayload = router.lastTelemetryOf("frr-1");
        router.telemetryStore().putConfig("frr-1", previousConfig);
        final var result = service.importContent("bad.json", """
            {"schema":"sonar.offline.snapshot","agent_id":"frr-1","product":"FRR",
             "payload":{"unexpected":true}}
            """);
        assertFalse(result.accepted());
        org.junit.jupiter.api.Assertions.assertSame(previousConfig, router.lastConfigOf("frr-1"));
        assertEquals(previousPayload, router.lastTelemetryOf("frr-1"));
        assertFalse(router.isOfflineOrigin("frr-1"));
    }

    @Test
    void oversizedStreamStopsReadingAtLimit() {
        final long[] read = {0};
        final var stream = new java.io.InputStream() {
            @Override public int read() {
                if (++read[0] > OfflineSnapshotService.MAX_SNAPSHOT_BYTES + 1)
                    throw new AssertionError("read past snapshot byte limit");
                return ' ';
            }
        };
        final var result = service.importOne("large.json", stream);
        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("너무 큽니다")));
        assertEquals(OfflineSnapshotService.MAX_SNAPSHOT_BYTES + 1, read[0]);
    }

    @Test
    void partialAndFailedLiveUpdatesDoNotPublishOrReplaceAcceptedConfiguration() {
        assertTrue(service.importContent("good.json", frrSnapshot()).accepted());
        final var previous = router.lastConfigOf("frr-1");
        final var observer = org.mockito.Mockito.mock(
                org.sonar.sonarvalidator_backend.Service.telemetry.TelemetryObserver.class);
        router.setTelemetryEvents(new org.sonar.sonarvalidator_backend.Service.telemetry.TelemetryEventPublisher(
                List.of(observer)));
        final var session = org.mockito.Mockito.mock(org.springframework.web.socket.WebSocketSession.class);
        final var envelope = org.sonar.sonarvalidator_backend.Model.dto.Envelope.of("telemetry");
        envelope.setAgent_id("frr-1");
        envelope.setDevice_type("FRR");
        for (String payload : List.of(
                "{\"product\":\"FRR\",\"nic_status\":{\"interfaces\":[]}}",
                "{\"product\":\"FRR\",\"route_status\":{\"routes\":[{\"prefix\":\"10.2.0.0/24\"}]}}",
                "{\"product\":\"FRR\",\"nic_status\":{\"interfaces\":[]},\"route_status\":{\"routes\":[],\"parsed\":false}}")) {
            envelope.setPayload(JsonMapper.builder().build().readTree(payload));
            router.handle(session, envelope);
            org.junit.jupiter.api.Assertions.assertSame(previous, router.lastConfigOf("frr-1"));
        }
        org.mockito.Mockito.verifyNoInteractions(observer);
    }

    private java.io.InputStream stream(String content) {
        return new java.io.ByteArrayInputStream(
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
