package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Service.AgentNodeType;
import org.sonar.sonarvalidator_backend.Service.log.LogLineReader;
import org.sonar.sonarvalidator_backend.Service.notification.NotificationCategory;
import org.sonar.sonarvalidator_backend.Service.notification.NotificationFilter;
import org.sonar.sonarvalidator_backend.Service.notification.NotificationSeverity;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseProbeStrategies;
import org.sonar.sonarvalidator_backend.Service.policy.PolicyPushNotifier;
import org.sonar.sonarvalidator_backend.Service.policy.PushOutcomeStrategy;

/**
 * 전략 패턴으로 분리한 컴포넌트들의 계약을 검증합니다.
 *
 * <h2>⚠️ 왜 한 파일에 모았는가</h2>
 * <p>각 컴포넌트는 <b>분기 하나</b>를 대신합니다. 개별 파일로 나누면
 * 6개의 작은 테스트 클래스가 생기지만, 여기서 지키려는 계약은 하나입니다 —
 * <b>같은 사실이 두 곳에 있지 않다.</b>
 *
 * <p>리팩토링의 목적이 "변경 지역화" 였으므로, 그 목적이 지켜졌는지를
 * 검증하는 것도 한 곳에 모으는 편이 읽기 쉽습니다.
 */
class StrategyExtractionTest {

    // ------------------------------------------------------------------
    //  AgentNodeType — 번들의 장치 유형 (switch 2곳을 대신)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("장치 유형은 표기가 달라도 같은 유형으로 해석된다")
    void nodeTypeAcceptsAllSpellings() {
        // 프론트엔드는 "Router", 정책 서비스는 "ROUTER", 운영자는 " router ".
        // 비교마다 equalsIgnoreCase 를 반복하지 않도록 한 곳에서 흡수합니다.
        for (final String spelling : List.of("Router", "ROUTER", "router", "  Router  ")) {
            assertEquals(AgentNodeType.ROUTER, AgentNodeType.parse(spelling), spelling);
        }
        assertEquals(AgentNodeType.SWITCH, AgentNodeType.parse("SWITCH"));
        assertEquals(AgentNodeType.FIREWALL, AgentNodeType.parse("Firewall"));
    }

    @Test
    @DisplayName("VM 은 여러 이름으로 들어와도 같은 유형이다")
    void nodeTypeAcceptsVmAliases() {
        // Prober 와 문서가 각각 다른 표기를 씁니다.
        for (final String alias : List.of("VM", "vm", "VirtualMachine", "virtual-machine")) {
            assertEquals(AgentNodeType.VM, AgentNodeType.parse(alias), alias);
        }
    }

    @Test
    @DisplayName("모르는 유형은 VM 으로 둔다 (설치를 거부하지 않는다)")
    void unknownNodeTypeFallsBackToVm() {
        // 번들은 "지금 무언가를 설치하려는" 상황에서 만들어집니다.
        // 유형을 모른다고 거부하면 운영자는 아무것도 못 합니다.
        assertEquals(AgentNodeType.VM, AgentNodeType.parse(null));
        assertEquals(AgentNodeType.VM, AgentNodeType.parse(""));
        assertEquals(AgentNodeType.VM, AgentNodeType.parse("quantum-switch"));
    }

    @Test
    @DisplayName("정규화된 표기는 프로버가 기대하는 형태다")
    void nodeTypeCanonicalForm() {
        assertEquals("Router", AgentNodeType.canonicalOf("router"));
        assertEquals("Switch", AgentNodeType.canonicalOf("SWITCH"));
        assertEquals("VM", AgentNodeType.canonicalOf(null));
    }

    @Test
    @DisplayName("방화벽 안내에는 격리 제외 사실이 들어간다")
    void firewallNoteMentionsIsolationExclusion() {
        // 방화벽을 격리하면 트렁크에 붙은 VLAN 이 함께 끊깁니다.
        // README 를 읽는 운영자가 이 사실을 모르면 위험한 조치를 합니다.
        final String note = AgentNodeType.FIREWALL.prepareNote();
        assertTrue(note.contains("격리 대상이 아닙니다"), note);
        assertTrue(note.contains("eth1"), note);
    }

    // ------------------------------------------------------------------
    //  NotificationCategory / Severity / Filter
    // ------------------------------------------------------------------

    @Test
    @DisplayName("분류: 기록은 하나로 확정하고, 필터는 모르면 전체로 둔다")
    void categoryWriteVersusFilter() {
        // ⚠️ 기록에 null 을 저장하면 조회가 그 행을 못 찾습니다.
        assertEquals("SECURITY", NotificationCategory.parseForWrite("security").value());
        assertEquals("SYSTEM", NotificationCategory.parseForWrite("nonsense").value());
        assertEquals("SYSTEM", NotificationCategory.parseForWrite(null).value());

        // ⚠️ 필터에서 모르는 값을 SYSTEM 으로 바꾸면 "SYSTEM 만 보기" 가 되어
        //    화면이 비고, 운영자는 알림이 없는 줄 압니다.
        assertEquals(NotificationCategory.SECURITY, NotificationCategory.parse("security"));
        assertEquals(null, NotificationCategory.parse("nonsense"));
        assertEquals(null, NotificationCategory.parse(null));
    }

    @Test
    @DisplayName("심각도: 기록은 info 로 내리고, 순서로 강도를 비교한다")
    void severityWriteVersusOrder() {
        assertEquals(NotificationSeverity.CRITICAL, NotificationSeverity.parse("CRITICAL"));
        assertTrue(NotificationSeverity.isKnown("warning"));
        assertFalse(NotificationSeverity.isKnown("severe"));

        // 모르는 값을 CRITICAL 로 올리면 잘못된 입력 하나가 화면을 빨갛게 만듭니다.
        assertEquals(NotificationSeverity.INFO, NotificationSeverity.parseForWrite("severe"));

        // 선언 순서가 곧 강도입니다. (INFO < WARNING < CRITICAL)
        assertTrue(NotificationSeverity.CRITICAL.isAtLeast(NotificationSeverity.WARNING));
        assertFalse(NotificationSeverity.INFO.isAtLeast(NotificationSeverity.WARNING));
    }

    @Test
    @DisplayName("필터: 모든 정규화가 생성 시점에 끝난다")
    void filterNormalizesAtConstruction() {
        final NotificationFilter filter = NotificationFilter.of(
                " security ", "WARNING", "UNREAD", "  PRJ-1  ", " agent-1 ", "  BGP  ", 0);

        assertEquals("SECURITY", filter.categoryValue());
        assertEquals("warning", filter.severityValue());
        assertEquals("unread", filter.readState());
        assertEquals("PRJ-1", filter.projectKey(), "공백 제거");
        assertEquals("agent-1", filter.agentId());
        assertEquals("BGP", filter.term());
        // 0 이하는 기본값으로 (빈 결과가 아니라 "전체" 를 보여줍니다)
        assertEquals(NotificationFilter.DEFAULT_LIMIT, filter.limit());
    }

    @Test
    @DisplayName("필터: 모르는 값은 조건 없음(null)으로 정리된다")
    void filterDropsUnknownValues() {
        final NotificationFilter filter = NotificationFilter.of(
                "nonsense", "severe", "halfway", null, null, null, 50);

        assertEquals(null, filter.categoryValue());
        assertEquals(null, filter.severityValue());
        assertEquals(null, filter.readState());
        assertEquals(50, filter.limit());
    }

    @Test
    @DisplayName("필터: LIKE 패턴은 Java 에서 만든다 (PostgreSQL bytea 사고 방지)")
    void filterBuildsLikePattern() {
        assertEquals("%bgp%", NotificationFilter.of(null, null, null, null, null, "BGP", 10)
                .likePattern());
        // 검색어가 없으면 null 이어야 합니다.
        // 빈 문자열을 넘기면 PostgreSQL 이 LOWER(bytea) 로 해석해 500 이 됩니다.
        assertEquals(null, NotificationFilter.recent(10).likePattern());
        assertFalse(NotificationFilter.recent(10).hasTerm());
    }

    @Test
    @DisplayName("필터: 최대 건수는 500 으로 자른다")
    void filterClampsLimit() {
        assertEquals(NotificationFilter.MAX_LIMIT, NotificationFilter.recent(100000).limit());
        assertEquals(1, NotificationFilter.recent(1).limit());
    }

    // ------------------------------------------------------------------
    //  PolicyPushNotifier — 푸시 결과 분기 (switch/if 대신)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("푸시 결과: 대상이 없으면 '대상 없음' 알림을 남긴다")
    void pushNoTargets() {
        final PolicyPushNotifier notifier = new PolicyPushNotifier();
        final var outcome = new PushOutcomeStrategy.PushOutcome(
                "PRJ-1", "테스트", 0, 0, List.of(), 0, false);

        assertEquals("no-targets", notifier.nameOf(outcome));
        final var notices = notifier.classify(outcome);
        assertEquals(1, notices.size());
        // "푸시 완료" 로 보이면 안 됩니다 — 서브넷에 Agent 매핑이 없습니다.
        assertTrue(notices.get(0).title().contains("대상 없음"), notices.get(0).title());
    }

    @Test
    @DisplayName("푸시 결과: 대상은 있는데 0대 전달이면 '실패' 로 구분한다")
    void pushNothingDelivered() {
        final PolicyPushNotifier notifier = new PolicyPushNotifier();
        final var outcome = new PushOutcomeStrategy.PushOutcome(
                "PRJ-1", "테스트", 3, 0, List.of(), 0, false);

        assertEquals("nothing-delivered", notifier.nameOf(outcome));
        assertTrue(notifier.classify(outcome).get(0).title().contains("실패"));
    }

    @Test
    @DisplayName("푸시 결과: 강제 전송은 심각도를 올린다 (위반이 남아 있다)")
    void pushForcedRaisesSeverity() {
        final PolicyPushNotifier notifier = new PolicyPushNotifier();
        final var outcome = new PushOutcomeStrategy.PushOutcome(
                "PRJ-1", "테스트", 2, 2, List.of(), 3, true);

        assertEquals("delivered", notifier.nameOf(outcome));
        final var notice = notifier.classify(outcome).get(0);
        assertEquals(NotificationSeverity.WARNING, notice.severity());
        assertTrue(notice.title().contains("강제"), notice.title());
    }

    @Test
    @DisplayName("푸시 결과: 정상 전송은 info 이고 중복 키를 두지 않는다")
    void pushNormalIsInfoWithoutDedupe() {
        final PolicyPushNotifier notifier = new PolicyPushNotifier();
        final var outcome = new PushOutcomeStrategy.PushOutcome(
                "PRJ-1", "테스트", 2, 2, List.of(), 0, false);

        final var notice = notifier.classify(outcome).get(0);
        assertEquals(NotificationSeverity.INFO, notice.severity());
        // 반복 푸시가 정상 운영이므로 합치면 "마지막 푸시 시각" 을 잃습니다.
        assertEquals(null, notice.dedupeKey());
    }

    @Test
    @DisplayName("푸시 결과: 격리 제외 알림은 결과와 독립적으로 남는다")
    void pushSkipNoticeIsIndependent() {
        final PolicyPushNotifier notifier = new PolicyPushNotifier();
        // 전달이 성공했는데(2/2) 격리로 1대가 빠진 경우
        final var outcome = new PushOutcomeStrategy.PushOutcome(
                "PRJ-1", "테스트", 2, 2, List.of("FW-1"), 0, false);

        final var notices = notifier.classify(outcome);
        assertEquals(2, notices.size(), "제외 알림 + 전달 완료 알림");
        // "왜 대상 수가 줄었나" 가 결과보다 먼저 와야 읽는 순서가 자연스럽습니다.
        assertTrue(notices.get(0).title().contains("격리된 장치 제외"), notices.get(0).title());
        assertTrue(notices.get(0).message().contains("FW-1"));
    }

    @Test
    @DisplayName("푸시 결과: 분류되지 않아도 예외를 던지지 않는다")
    void pushUnclassifiedDoesNotThrow() {
        // 예외를 던지면 정책은 이미 장치에 전달됐는데 서버가 500 을 돌려줍니다.
        final PolicyPushNotifier notifier = new PolicyPushNotifier(List.of());
        final var outcome = new PushOutcomeStrategy.PushOutcome(
                "PRJ-1", "테스트", 1, 1, List.of(), 0, false);

        assertEquals(PolicyPushNotifier.unclassifiedName(), notifier.nameOf(outcome));
        assertTrue(notifier.classify(outcome).isEmpty());
    }

    // ------------------------------------------------------------------
    //  LogLineReader — 줄 쪼개기 (split 이 두 곳에 있었다)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("로그 줄: 줄 배열은 그대로 쓴다 (원문 개행을 존중)")
    void logLinesFromArray() {
        final LogLineReader reader = new LogLineReader();
        final var read = reader.read(
                Map.of("lines", List.of("line1", "line2"), "agent_id", "a"), null);

        assertEquals("manual", read.source());
        assertEquals(2, read.count());
        assertTrue(read.recognized());
    }

    @Test
    @DisplayName("로그 줄: text 덩어리는 서버가 쪼갠다")
    void logLinesFromText() {
        final LogLineReader reader = new LogLineReader();
        final var read = reader.read(Map.of("text", "a\nb\r\nc"), null);

        assertEquals(3, read.count());
        assertTrue(read.recognized());
    }

    @Test
    @DisplayName("로그 줄: 배열과 text 는 같은 쪼개기를 쓴다 (경로별 차이 없음)")
    void logLinesArePathIndependent() {
        // ⚠️ 이전에는 ingest 와 upload 에 split("\\R") 이 각각 있었습니다.
        //    한쪽만 바꾸면 <b>같은 내용을 붙여넣기와 업로드로 넣었을 때
        //    줄 수가 달라집니다.</b>
        final LogLineReader reader = new LogLineReader();
        final String content = "line1\nline2\nline3";

        final var asText = reader.read(Map.of("text", content), null);
        final var asArray = reader.read(
                Map.of("lines", List.of("line1", "line2", "line3")), null);

        assertEquals(asText.count(), asArray.count());
    }

    @Test
    @DisplayName("로그 줄: 모르는 요청 모양은 recognized=false 로 구분한다")
    void logLinesUnrecognizedIsDistinct() {
        // "내용이 빈" 것과 "모양을 모르는" 것은 다른 답을 줘야 합니다.
        final LogLineReader reader = new LogLineReader();

        final var empty = reader.read(Map.of("text", ""), null);
        assertTrue(empty.recognized(), "모양은 알겠음");
        assertEquals(0, empty.count());

        final var unknown = reader.read(Map.of("nonsense", "x"), null);
        assertFalse(unknown.recognized(), "모양을 모름");
    }

    @Test
    @DisplayName("로그 줄: 빈 줄을 버리지 않는다 (장비 로그의 구분자)")
    void logLinesKeepBlankLines() {
        // 빈 줄을 버리면 "설정 블록 A" 와 "설정 블록 B" 가 붙어 보입니다.
        final LogLineReader reader = new LogLineReader();
        final var read = reader.read(Map.of("text", "a\n\nb"), null);

        assertEquals(3, read.count());
    }

    @Test
    @DisplayName("로그 줄: 출처 표기는 전략이 정한다")
    void logSourceLabels() {
        assertEquals("manual", new LogLineReader().sourceLabelOf(Map.of("text", "a"), null));
    }

    // ------------------------------------------------------------------
    //  OPNsenseProbeStrategies — 진단 대상 분기
    // ------------------------------------------------------------------

    @Test
    @DisplayName("OPNsense 진단: 대상 이름이 올바른 API 로 간다")
    void opnsenseTargetsResolve() {
        final OPNsenseProbeStrategies strategies = new OPNsenseProbeStrategies();

        assertEquals("interfaces", strategies.select("interfaces").name());
        assertEquals("rules", strategies.select("rules").name());
        assertEquals("nat", strategies.select("NAT").name(), "대소문자 무시");
        assertEquals("aliases", strategies.select("  alias  ").name(), "공백 제거");
    }

    @Test
    @DisplayName("OPNsense 진단: 모르는 대상은 접속 확인으로 폴백한다")
    void opnsenseUnknownFallsBackToConnectionCheck() {
        // 진단에서는 "일단 붙는가" 가 첫 질문입니다.
        final OPNsenseProbeStrategies strategies = new OPNsenseProbeStrategies();

        assertEquals("firmware", strategies.select(null).name());
        assertEquals("firmware", strategies.select("").name());
        assertEquals("firmware", strategies.select("gateway").name(), "아직 없는 대상");
    }

    @Test
    @DisplayName("OPNsense 진단: 기본 전략은 이름으로 선택되지 않는다")
    void opnsenseDefaultIsNotNameSelectable() {
        // 이름으로 선택되면 다른 대상이 가려집니다.
        final OPNsenseProbeStrategies strategies = new OPNsenseProbeStrategies();
        // "firmware" 라고 명시해도 접속 확인으로만 갑니다(폴백 경로 하나).
        assertEquals("firmware", strategies.select("firmware").name());
    }

    @Test
    @DisplayName("OPNsense 진단: 대상 목록을 서버가 알려준다")
    void opnsenseDescribesTargets() {
        // 화면이 목록을 하드코딩하면 서버가 대상을 늘렸을 때 뒤처집니다.
        final var described = new OPNsenseProbeStrategies().describe();

        assertEquals(5, described.size());
        assertTrue(described.stream().anyMatch(entry -> "interfaces".equals(entry.get("target"))));
        // 기본 대상 하나만 default=true 여야 합니다.
        assertEquals(1, described.stream().filter(e -> Boolean.TRUE.equals(e.get("default"))).count());
    }

    @Test
    @DisplayName("OPNsense 진단: 모든 전략이 설명을 갖는다")
    void opnsenseAllStrategiesHaveDescriptions() {
        for (final var entry : new OPNsenseProbeStrategies().describe()) {
            final String description = String.valueOf(entry.get("description"));
            assertNotNull(description);
            assertFalse(description.isBlank(), entry.get("target") + " 설명이 비었음");
        }
    }

    // ------------------------------------------------------------------
    //  경계 계약
    // ------------------------------------------------------------------

    @Test
    @DisplayName("strategy 목록을 외부에서 주입해 확장할 수 있다")
    void strategiesAreInjectable() {
        // 목록을 생성자로 받으므로 테스트가 가짜 전략을 끼워 넣을 수 있습니다.
        // 다만 실제 전략은 상태가 없어 매 요청마다 만들지 않습니다.
        final PolicyPushNotifier notifier = new PolicyPushNotifier(List.of());
        assertTrue(notifier.strategies().isEmpty());
        assertNotNull(new LogLineReader().sources());
        assertNotNull(new OPNsenseProbeStrategies().describe());
    }

    @Test
    @DisplayName("Optional 반환은 '알림 없음' 을 null 과 구분한다")
    void optionalDistinguishesNoNotice() {
        // null 이면 호출자가 "만들다 실패했나" 를 의심해야 합니다.
        // Optional.empty() 는 "남길 알림이 없다" 는 의도입니다.
        final Optional<PushOutcomeStrategy.Notification> none = PolicyPushNotifier.none();
        assertTrue(none.isEmpty());
    }
}