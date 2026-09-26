package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonar.sonarvalidator_backend.support.StubRepository.UNHANDLED;
import static org.sonar.sonarvalidator_backend.support.StubRepository.of;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Model.entity.ComplianceChange;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Model.entity.Notification;
import org.sonar.sonarvalidator_backend.Model.entity.QuarantineState;
import org.sonar.sonarvalidator_backend.Repository.ComplianceChangeRepository;
import org.sonar.sonarvalidator_backend.Repository.NotificationRepository;
import org.sonar.sonarvalidator_backend.Repository.QuarantineStateRepository;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.ComplianceService;
import org.sonar.sonarvalidator_backend.Service.NotificationService;
import org.sonar.sonarvalidator_backend.Service.QuarantineService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 격리 서비스의 계약을 DB/WebSocket 없이 검증합니다.
 *
 * <h2>왜 스텁으로 검증하는가</h2>
 * 격리 서비스의 위험한 로직은 <b>저장소 종류와 무관한 부분</b>에 있습니다.
 * <ul>
 *   <li>{@code "quarantine"}/{@code "release"} 문자열이 Agent 와 같은가</li>
 *   <li>명령 전달 실패가 <b>성공으로 보고되지 않는가</b></li>
 *   <li>해제 시 "격리 중이 아님" 을 거짓 성공으로 말하지 않는가</li>
 *   <li>이미 격리된 장치를 다시 격리할 때 행이 중복 생성되지 않는가</li>
 * </ul>
 *
 * <h2>⚠️ 이 테스트가 겨냥하는 최악의 실패 모드</h2>
 * <p>"장치는 살아 있는데 서버는 격리됐다고 믿는" 상태입니다. 이 상태가 되면
 * 운영자는 화면의 빨간 표시를 믿고 실제로는 뚫린 망을 방치합니다.
 * 그래서 여기서는 <b>전달 실패가 반드시 드러나는지</b>를 최우선으로 봅니다.
 *
 * <p>저장소 스텁은 {@link org.sonar.sonarvalidator_backend.support.StubRepository}
 * 로 만듭니다. ({@code JpaRepository} 를 직접 구현하면 120여 개 메서드를
 * 채워야 하고, 그 boilerplate 가 정작 검증할 계약을 가립니다)
 */
class QuarantineServiceTest {

    /** 저장된 격리 행. 파생 쿼리 의미를 흉내 내는 데 씁니다. */
    private final List<QuarantineState> quarantineRows = new ArrayList<>();
    /** 저장된 변경 이력. */
    private final List<ComplianceChange> complianceRows = new ArrayList<>();
    /** 저장된 알림. */
    private final List<Notification> notificationRows = new ArrayList<>();
    /** 연결된 것으로 취급할 Agent. */
    private final Set<String> connected = new LinkedHashSet<>();
    /** 실제로 전송된 봉투. */
    private final List<Envelope> sent = new ArrayList<>();

    private QuarantineService service;

    @BeforeEach
    void setUp() {
        quarantineRows.clear();
        complianceRows.clear();
        notificationRows.clear();
        connected.clear();
        sent.clear();

        // 격리 저장소: released_at == null 이 곧 "현재 격리 중" 입니다.
        final QuarantineStateRepository quarantineRepository = of(
                QuarantineStateRepository.class,
                (method, args) -> switch (method) {
                    case "save" -> {
                        final QuarantineState state = (QuarantineState) args[0];
                        if (state.getId() == null) {
                            // 실제 DB 의 auto-increment 를 흉내 냅니다.
                            // 채우지 않으면 "행 중복 검사" 가 통과해버립니다.
                            state.setId((long) (quarantineRows.size() + 1));
                        }
                        if (!quarantineRows.contains(state)) {
                            quarantineRows.add(state);
                        }
                        yield state;
                    }
                    case "findByAgentIdAndReleasedAtIsNull" -> quarantineRows.stream()
                            .filter(s -> args[0] != null && args[0].equals(s.getAgentId())
                                    && s.getReleasedAt() == null)
                            .findFirst();
                    case "findByReleasedAtIsNullOrderByQuarantinedAtDesc" -> quarantineRows.stream()
                            .filter(s -> s.getReleasedAt() == null)
                            .toList();
                    case "findByProjectKeyAndReleasedAtIsNullOrderByQuarantinedAtDesc" ->
                            quarantineRows.stream()
                                    .filter(s -> s.getReleasedAt() == null
                                            && java.util.Objects.equals(args[0], s.getProjectKey()))
                                    .toList();
                    case "findByAgentIdOrderByQuarantinedAtDesc" -> quarantineRows.stream()
                            .filter(s -> args[0] != null && args[0].equals(s.getAgentId()))
                            .toList();
                    case "count" -> (long) quarantineRows.size();
                    default -> UNHANDLED;
                });

        // 이력 저장소: save 만 씁니다.
        final ComplianceChangeRepository complianceRepository = of(
                ComplianceChangeRepository.class,
                (method, args) -> switch (method) {
                    case "save" -> {
                        complianceRows.add((ComplianceChange) args[0]);
                        yield args[0];
                    }
                    default -> UNHANDLED;
                });

        // 알림 저장소: save 와 중복 키 조회, 안읽음 개수만 씁니다.
        final NotificationRepository notificationRepository = of(
                NotificationRepository.class,
                (method, args) -> switch (method) {
                    case "save" -> {
                        notificationRows.add((Notification) args[0]);
                        yield args[0];
                    }
                    case "findFirstByDedupeKeyOrderByOccurredAtDesc" -> {
                        if (args[0] == null) {
                            yield Optional.empty();
                        }
                        yield notificationRows.stream()
                                .filter(n -> args[0].equals(n.getDedupeKey()))
                                .findFirst();
                    }
                    case "countByReadFalse" -> notificationRows.stream()
                            .filter(n -> !n.isRead())
                            .count();
                    default -> UNHANDLED;
                });

        service = new QuarantineService(
                quarantineRepository,
                new RecordingRegistry(connected, sent),
                new ComplianceService(complianceRepository),
                new NotificationService(notificationRepository));
    }

    // ------------------------------------------------------------------
    //  격리
    // ------------------------------------------------------------------

    @Test
    @DisplayName("연결된 장치를 격리하면 command 봉투가 실제로 전송된다")
    void isolateSendsCommandToConnectedAgent() {
        connected.add("ATICS-agent");

        final Map<String, Object> result = service.isolate("ATICS-agent", "PRJ-1", "테스트", "tester");

        assertEquals(1, sent.size(), "명령 1건 전송");
        final Envelope envelope = sent.get(0);
        assertEquals(Envelope.Types.COMMAND, envelope.getType());
        assertEquals("ATICS-agent", envelope.getAgent_id());
        // ⚠️ Agent 의 quarantine::kIsolate 와 같아야 합니다.
        assertEquals("quarantine", envelope.payloadOrEmpty().get("action").asString());
        assertEquals("PRJ-1", envelope.payloadOrEmpty().get("project_id").asString());
        assertEquals("테스트", envelope.payloadOrEmpty().get("reason").asString());

        assertEquals(Boolean.TRUE, result.get("delivered"), "delivered=true");
        assertTrue((Boolean) result.get("active"), "격리 중으로 표시");
        assertFalse((Boolean) result.get("retry"), "첫 격리이므로 retry=false");
        // ack 가 아직 없으므로 "실패" 가 아니라 "모름" 이어야 합니다.
        assertNull(result.get("applied"), "ack 없으면 applied=null (실패 아님)");
    }

    @Test
    @DisplayName("미연결 장치 격리는 전달 실패를 숨기지 않고 이유를 남긴다")
    void isolateOnDisconnectedAgentReportsNotDelivered() {
        // 아무것도 연결하지 않음 = 미연결

        final Map<String, Object> result = service.isolate("UAV-agent", "PRJ-1", "미연결", "tester");

        assertEquals(Boolean.FALSE, result.get("delivered"), "delivered=false");
        // 상태는 저장되어야 합니다. 재접속 시 차단 정책을 받아야 하므로.
        assertTrue((Boolean) result.get("active"), "미연결이어도 격리 상태는 저장");
        assertNotNull(result.get("warning"), "운영자가 이유를 알 수 있어야 함");
        assertTrue(String.valueOf(result.get("warning")).contains("재접속"),
                "재접속 시 적용된다는 안내 포함");

        assertEquals(1, complianceRows.size(), "이력 1건");
        assertEquals(1, notificationRows.size(), "알림 1건");
        assertEquals("warning", notificationRows.get(0).getSeverity(),
                "미전달은 critical 이 아니라 warning");
    }

    @Test
    @DisplayName("이미 격리된 장치를 다시 격리하면 새 행을 만들지 않는다")
    void isolateIsIdempotent() {
        connected.add("VDI-1-agent");

        service.isolate("VDI-1-agent", "PRJ-1", "1차", "tester");
        final Map<String, Object> second = service.isolate("VDI-1-agent", "PRJ-1", "2차", "tester");

        assertEquals(1, quarantineRows.size(), "행은 1개만 (중복 생성 금지)");
        assertEquals(2, sent.size(), "명령은 다시 전송 (전달 누락 대비)");
        assertEquals(Boolean.TRUE, second.get("retry"), "retry=true 로 구분");
    }

    @Test
    @DisplayName("격리 이력 유형은 재시도와 최초를 구분한다")
    void isolateRecordsDistinctTypes() {
        connected.add("SW-1");

        service.isolate("SW-1", "PRJ-1", null, "tester");
        service.isolate("SW-1", "PRJ-1", null, "tester");

        assertEquals("Quarantine", complianceRows.get(0).getType());
        assertEquals("QuarantineRetry", complianceRows.get(1).getType());
    }

    // ------------------------------------------------------------------
    //  해제
    // ------------------------------------------------------------------

    @Test
    @DisplayName("격리 해제는 상태를 닫고 release 명령을 보낸다")
    void releaseSendsReleaseCommand() {
        connected.add("AFCCS-agent");
        service.isolate("AFCCS-agent", "PRJ-1", "격리", "tester");
        sent.clear();

        final Map<String, Object> result = service.release("AFCCS-agent", "tester2");

        assertEquals(1, sent.size(), "release 명령 1건");
        assertEquals("release", sent.get(0).payloadOrEmpty().get("action").asString());
        assertEquals(Boolean.TRUE, result.get("delivered"));
        assertFalse((Boolean) result.get("active"), "더 이상 격리 중이 아님");
        assertEquals("tester2", result.get("released_by"));
        assertNotNull(result.get("released_at"), "해제 시각 기록");

        // ⚠️ 회귀: 성공 응답에도 released 키가 있어야 합니다.
        //    이 키가 없으면 클라이언트가 undefined 를 "실패" 로 읽어
        //    성공을 "격리 중이 아니었다" 로 정반대로 표시합니다.
        //    (최종 E2E 에서 실제로 재현된 버그)
        assertEquals(Boolean.TRUE, result.get("released"),
                "해제 성공 응답에 released=true 가 명시되어야 함");

        assertFalse(service.isQuarantined("AFCCS-agent"), "isQuarantined=false");
        assertTrue(service.quarantinedAgentIds().isEmpty(), "격리 목록에서 빠짐");
    }

    @Test
    @DisplayName("격리 중이 아닌 장치 해제는 거짓 성공을 말하지 않는다")
    void releaseWithoutQuarantineReportsFalse() {
        final Map<String, Object> result = service.release("KNCCS-agent", "tester");

        assertEquals(Boolean.FALSE, result.get("released"), "released=false");
        assertTrue(sent.isEmpty(), "명령을 보내지 않음");
        // 조용히 실패하면 운영자는 해제된 줄 알고 넘어갑니다.
        assertEquals(0, complianceRows.size(), "이력도 남기지 않음");
    }

    @Test
    @DisplayName("해제 후 다시 격리할 수 있다 (왕복)")
    void isolateReleaseIsolateRoundTrip() {
        connected.add("PWS-agent");

        service.isolate("PWS-agent", "PRJ-1", "1차", "tester");
        service.release("PWS-agent", "tester");
        final Map<String, Object> third = service.isolate("PWS-agent", "PRJ-1", "2차", "tester");

        assertEquals(2, quarantineRows.size(), "격리 이력 2건");
        assertFalse((Boolean) third.get("retry"), "해제 후에는 새 격리");
        assertTrue(service.isQuarantined("PWS-agent"));
    }

    // ------------------------------------------------------------------
    //  격리 제외 (방화벽)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("방화벽은 격리 대상이 아니므로 명령을 보내지 않는다")
    void firewallIsNotIsolatable() {
        connected.add("GNS3.Firewall");

        final Map<String, Object> result =
                service.isolate("GNS3.Firewall", "PRJ-1", "위반", "tester");

        // ⚠️ 트렁크(eth1)에 VLAN 131/132/133 이 동시에 붙어 있어, 방화벽을
        //    격리하면 무관한 존 전체가 끊깁니다. 그래서 거부가 정답입니다.
        assertEquals(Boolean.TRUE, result.get("rejected"), "거부 표시");
        assertEquals(Boolean.FALSE, result.get("quarantined"), "격리되지 않음");
        assertEquals(Boolean.FALSE, result.get("released"), "해제도 아님");
        assertEquals("FIREWALL", result.get("device_type"));
        assertTrue(sent.isEmpty(), "명령을 보내지 않음");
        assertTrue(quarantineRows.isEmpty(), "격리 행을 만들지 않음");
        assertFalse(service.isQuarantined("GNS3.Firewall"), "격리 상태가 아님");
    }

    @Test
    @DisplayName("방화벽 격리 거부는 사유와 대안을 남기고 이력에 기록한다")
    void firewallRejectionExplainsWhy() {
        final Map<String, Object> result =
                service.isolate("DMZ-Firewall", "PRJ-1", "위반", "tester");

        // "격리했습니다" 라고 거짓 응답하면 운영자는 뚫린 망을 방치합니다.
        final String reason = String.valueOf(result.get("reason"));
        assertTrue(reason.contains("VLAN"), "연결된 VLAN 이 끊긴다는 사유: " + reason);
        assertNotNull(result.get("hint"), "대안 안내");

        // 시도 자체는 감사 대상입니다. ("왜 방화벽이 격리 안 되나")
        assertEquals(1, complianceRows.size(), "이력 1건");
        assertEquals("QuarantineRejected", complianceRows.get(0).getType());
    }

    @Test
    @DisplayName("방화벽 격리 거부는 알림을 만들지 않는다 (조치 필요 사건이 아님)")
    void firewallRejectionDoesNotNotify() {
        service.isolate("GNS3.Firewall", "PRJ-1", "위반", "tester");

        // 거부는 상태 변화가 아니므로 "지금 조치가 필요하다" 경고를 남기면
        // 운영자가 반복해서 같은 경고를 보게 됩니다.
        assertEquals(0, notificationRows.size(), "알림 없음");
    }

    // ------------------------------------------------------------------
    //  ack (실제 적용 확인)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ack 성공은 applied=true 로 노출된다")
    void ackSuccessIsExposed() {
        connected.add("TOD-Cam-agent");
        service.isolate("TOD-Cam-agent", "PRJ-1", null, "tester");

        service.recordAck("TOD-Cam-agent", ack("quarantine", true,
                "2개 인터페이스를 내렸습니다", List.of("eth1 (10.20.111.1/24)"),
                List.of("eth7 (172.16.255.5/24)")));

        final Map<String, Object> result = service.isolate("TOD-Cam-agent", "PRJ-1", null, "tester");
        assertEquals(Boolean.TRUE, result.get("applied"), "applied=true");
        assertTrue(String.valueOf(result.get("applied_detail")).contains("2개"),
                "detail 전달됨");
    }

    @Test
    @DisplayName("ack 실패는 applied=false 이고 critical 알림을 남긴다")
    void ackFailureProducesCriticalNotification() {
        connected.add("FW-1");
        service.isolate("FW-1", "PRJ-1", null, "tester");
        notificationRows.clear();

        service.recordAck("FW-1", ack("quarantine", false,
                "관리 경로를 찾지 못해 격리를 중단했습니다", List.of(), List.of()));

        assertEquals(1, notificationRows.size(), "실패 알림 1건");
        assertEquals("critical", notificationRows.get(0).getSeverity());
        assertTrue(notificationRows.get(0).getTitle().contains("적용 실패"));
    }

    @Test
    @DisplayName("격리와 무관한 ack 는 무시한다")
    void unrelatedAckIsIgnored() {
        connected.add("SW-2");
        service.isolate("SW-2", "PRJ-1", null, "tester");
        final int before = notificationRows.size();

        // 정책 적용 보고 등 action 이 없는 ack
        service.recordAck("SW-2", ack(null, true, "정책 적용됨", List.of(), List.of()));

        assertNull(service.lastAck("SW-2"), "lastAck 에 기록되지 않음");
        assertEquals(before, notificationRows.size(), "알림도 늘지 않음");
    }

    // ------------------------------------------------------------------
    //  조회
    // ------------------------------------------------------------------

    @Test
    @DisplayName("quarantinedAgentIds 는 프로젝트 필터 없이 전체 격리를 반환한다")
    void quarantinedAgentIdsReturnsAllActive() {
        connected.add("a");
        service.isolate("a", "PRJ-1", null, "tester");
        service.isolate("b", "PRJ-2", null, "tester");
        service.isolate("c", "PRJ-1", null, "tester");
        service.release("a", "tester");

        final Set<String> ids = service.quarantinedAgentIds();

        assertEquals(2, ids.size(), "b, c 만 남음");
        assertTrue(ids.contains("b"));
        assertTrue(ids.contains("c"));
    }

    @Test
    @DisplayName("listActive 는 프로젝트로 걸러낼 수 있다")
    void listActiveFiltersByProject() {
        service.isolate("x", "PRJ-1", "사유1", "tester");
        service.isolate("y", "PRJ-2", "사유2", "tester");

        assertEquals(1, service.listActive("PRJ-1").size());
        assertEquals(2, service.listActive(null).size(), "프로젝트 미지정이면 전체");
        assertEquals("사유1", service.listActive("PRJ-1").get(0).get("reason"));
    }

    @Test
    @DisplayName("history 는 해제된 것도 남긴다 (감사)")
    void historyKeepsReleasedEntries() {
        connected.add("z");
        service.isolate("z", "PRJ-1", null, "tester");
        service.release("z", "tester");

        final List<Map<String, Object>> history = service.history("z");
        assertEquals(1, history.size(), "해제 후에도 이력 1건");
        assertFalse((Boolean) history.get(0).get("active"));
        assertNotNull(history.get(0).get("released_at"));
    }

    @Test
    @DisplayName("빈 식별자는 격리로 취급하지 않는다")
    void blankAgentIdIsNotQuarantined() {
        assertFalse(service.isQuarantined(null));
        assertFalse(service.isQuarantined(""));
        assertFalse(service.isQuarantined("   "));
    }

    @Test
    @DisplayName("sameAgent 는 대소문자가 섞여도 같은 장치로 본다")
    void sameAgentIgnoresCase() {
        final ExpectedAgent expected = new ExpectedAgent();
        expected.setAgentId("VDI-1-agent");

        // 운영자는 VDI-1-agent / vdi-1-agent 를 섞어 씁니다.
        // 대소문자를 구분하면 같은 장치가 두 줄로 나타나고 격리 배지가
        // 엉뚱한 행에 붙습니다.
        assertTrue(QuarantineService.sameAgent(expected, "vdi-1-agent"));
        assertTrue(QuarantineService.sameAgent(expected, "VDI-1-AGENT"));
        assertTrue(QuarantineService.sameAgent(expected, "VDI-1-agent"));

        assertFalse(QuarantineService.sameAgent(expected, "VDI-2-agent"));
        assertFalse(QuarantineService.sameAgent(expected, null));

        // null 안전성: 예정 정보가 없어도 예외가 나면 안 됩니다.
        assertFalse(QuarantineService.sameAgent(null, "VDI-1-agent"));

        final ExpectedAgent noId = new ExpectedAgent();
        assertFalse(QuarantineService.sameAgent(noId, "VDI-1-agent"));
    }

    // ------------------------------------------------------------------
    //  테스트 도우미
    // ------------------------------------------------------------------

    /** ack payload 를 실제 Agent 모양으로 만듭니다. */
    private static JsonNode ack(String action, boolean ok, String detail,
                                List<String> affected, List<String> preserved) {
        final var node = JsonMapper.builder().build().createObjectNode();
        if (action != null) {
            node.put("action", action);
        }
        node.put("ok", ok);
        node.put("detail", detail);
        final var affectedArray = node.putArray("affected");
        for (final String item : affected) {
            affectedArray.add(item);
        }
        final var preservedArray = node.putArray("preserved");
        for (final String item : preserved) {
            preservedArray.add(item);
        }
        return node;
    }

    /**
     * 연결된 Agent 집합을 직접 들고 있는 세션 레지스트리 스텁입니다.
     *
     * <p>{@link AgentSessionRegistry} 를 상속해 {@code sendTo} 만 바꿉니다.
     * 실제 구현은 WebSocket 세션을 요구하지만, 격리 서비스가 관심 있는 것은
     * "전달됐는가" 뿐입니다.
     */
    private static class RecordingRegistry extends AgentSessionRegistry {

        private final Set<String> connected;
        private final List<Envelope> sent;

        RecordingRegistry(Set<String> connected, List<Envelope> sent) {
            super(JsonMapper.builder().build());
            this.connected = connected;
            this.sent = sent;
        }

        @Override
        public boolean sendTo(String agentId, Envelope envelope) {
            if (!connected.contains(agentId)) {
                return false;
            }
            sent.add(envelope);
            return true;
        }

        @Override
        public Collection<String> connectedAgentIds() {
            return List.copyOf(connected);
        }
    }
}