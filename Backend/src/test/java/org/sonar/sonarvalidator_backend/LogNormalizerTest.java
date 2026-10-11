package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient;
import org.sonar.sonarvalidator_backend.Service.log.LogNormalizer;

/**
 * 로그 정규화와 OpenAI 호환 URL 조립을 검증합니다.
 *
 * <h2>여기서 잡으려는 실패 모드</h2>
 * <ul>
 *   <li><b>심각도 방향 반전</b>: syslog 는 숫자가 낮을수록 심각합니다.
 *       부등호를 반대로 쓰면 "warning 이상" 이 정반대 결과가 되는데,
 *       결과가 그럴듯해 보여 알아채기 어렵습니다.</li>
 *   <li><b>baseUrl 중복/누락</b>: {@code /v1/v1/chat/completions} 또는
 *       {@code //chat/completions} 는 404 를 냅니다. 사용자가 주소를
 *       어떻게 입력하든 동작해야 합니다.</li>
 *   <li><b>심각도 판단 실패의 기본값</b>: info 로 두면 어떤 필터에도 안 걸려
 *       조용히 묻히고, warning 으로 두면 모든 로그가 경고가 됩니다.</li>
 * </ul>
 *
 * <p>실제 벤더 로그 샘플로 검증합니다. 목을 쓰면 실제 형식 변화를 못 잡습니다.
 */
class LogNormalizerTest {

    private final LogNormalizer normalizer = new LogNormalizer();

    // ---------------------------------------------------------------------------
    // 심각도 이름 해석
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("심각도 이름과 숫자를 모두 해석한다")
    void parsesSeverityNamesAndNumbers() {
        // 화면과 스크립트가 각각 다른 표기를 써도 동작해야 합니다.
        assertEquals(4, normalizer.parseSeverity("warning"));
        assertEquals(4, normalizer.parseSeverity("WARN"));
        assertEquals(4, normalizer.parseSeverity("4"));
        assertEquals(3, normalizer.parseSeverity("error"));
        assertEquals(3, normalizer.parseSeverity("err"));
        assertEquals(2, normalizer.parseSeverity("critical"));
        assertEquals(2, normalizer.parseSeverity("crit"));
        assertEquals(6, normalizer.parseSeverity("info"));
        assertEquals(7, normalizer.parseSeverity("debug"));
        assertEquals(0, normalizer.parseSeverity("emergency"));
    }

    @Test
    @DisplayName("해석할 수 없는 심각도는 null 을 돌려준다 (필터 미적용)")
    void unknownSeverityReturnsNull() {
        // 오타로 필터가 조용히 무시되는 것보다, 전체를 보여주고 안내하는 편이 낫습니다.
        assertNull(normalizer.parseSeverity("banana"));
        assertNull(normalizer.parseSeverity(""));
        assertNull(normalizer.parseSeverity(null));
    }

    @Test
    @DisplayName("범위를 벗어난 숫자는 0~7 로 자른다")
    void clampsOutOfRangeNumbers() {
        assertEquals(0, normalizer.parseSeverity("-5"));
        assertEquals(7, normalizer.parseSeverity("99"));
    }

    @Test
    @DisplayName("심각도 번호를 표준 이름으로 바꾼다")
    void mapsSeverityNumbersToNames() {
        assertEquals("emergency", LogNormalizer.nameOf(0));
        assertEquals("critical", LogNormalizer.nameOf(2));
        assertEquals("warning", LogNormalizer.nameOf(4));
        assertEquals("info", LogNormalizer.nameOf(6));
        assertEquals("notice", LogNormalizer.nameOf(99));
    }

    // ---------------------------------------------------------------------------
    // Cisco IOS-XE
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Cisco 형식에서 facility/심각도/메시지 ID 를 뽑는다")
    void parsesCiscoStyle() {
        final var log = normalizer.normalize(
                "*Sep 19 08:12:33: %SYS-5-CONFIG_I: Configured from console by admin",
                "c8000v-1", "Cisco 8000v", null, "2026-09-19T12:00:00Z");

        assertEquals(5, log.severityNum, "SYS-5 는 notice");
        assertEquals("notice", log.severity);
        assertEquals("SYS", log.facility);
        assertEquals("%SYS-5-CONFIG_I", log.messageId);
        assertTrue(log.message.contains("Configured from console"));
        assertNotNull(log.loggedAt, "타임스탬프를 뽑아야 함");
    }

    @Test
    @DisplayName("Cisco 경고 로그는 warning 등급이 된다")
    void parsesCiscoWarning() {
        final var log = normalizer.normalize(
                "*Sep 19 08:15:01: %LINEPROTO-4-UPDOWN: Line protocol on Interface GigabitEthernet2, changed state to down",
                "c8000v-1", "Cisco 8000v", null, "2026-09-19T12:00:00Z");

        assertEquals(4, log.severityNum, "LINEPROTO-4 는 warning");
        assertEquals("LINEPROTO", log.facility);
    }

    @Test
    @DisplayName("Cisco 오류 로그는 error 등급이 된다")
    void parsesCiscoError() {
        final var log = normalizer.normalize(
                "%OSPF-3-ADJCHG: Process 1, Nbr 10.99.10.3 on Gi1 from FULL to DOWN",
                "c8000v-1", "Cisco 8000v", null, "2026-09-19T12:00:00Z");

        assertEquals(3, log.severityNum, "OSPF-3 은 error");
    }

    // ---------------------------------------------------------------------------
    // FRR
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("FRR 형식도 같은 규칙으로 파싱한다")
    void parsesFrrStyle() {
        final var log = normalizer.normalize(
                "2026/09/19 08:20:11 BGP: %DAEMON-3-BGP_PEER_DOWN: peer 10.99.10.3 down",
                "frr-1", "FRR", null, "2026-09-19T12:00:00Z");

        assertEquals(3, log.severityNum);
        assertEquals("DAEMON", log.facility);
        assertEquals("%DAEMON-3-BGP_PEER_DOWN", log.messageId);
    }

    // ---------------------------------------------------------------------------
    // Linux / syslog (벤더 코드 없음)
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("벤더 코드가 없으면 키워드로 심각도를 추정한다")
    void infersSeverityFromKeywords() {
        final var down = normalizer.normalize(
                "Sep 19 08:30:00 fw1 kernel: eth1: link is down", "fw-1", "nftables",
                null, "2026-09-19T12:00:00Z");
        assertEquals(3, down.severityNum, "'down' 은 error 로 추정");

        final var deny = normalizer.normalize(
                "Sep 19 08:30:01 fw1 nft: rule dropped connection from 10.0.0.5",
                "fw-1", "nftables", null, "2026-09-19T12:00:00Z");
        assertEquals(3, deny.severityNum, "drop 은 error 로 추정");

        final var warn = normalizer.normalize(
                "Sep 19 08:30:02 vm1 sshd[1234]: warning: cannot read config",
                "vm-1", "Ubuntu", null, "2026-09-19T12:00:00Z");
        assertEquals(4, warn.severityNum, "warning 키워드");
    }

    @Test
    @DisplayName("판단 근거가 없으면 notice(5) 로 둔다 (조용히 묻히지 않게)")
    void defaultsToNoticeWhenUnknown() {
        final var log = normalizer.normalize(
                "Sep 19 08:40:00 vm1 systemd[1]: Started something ordinary",
                "vm-1", "Ubuntu", null, "2026-09-19T12:00:00Z");

        // info(6) 로 두면 "warning 이상" 필터에서 빠져 조용히 묻힙니다.
        // warning(4) 로 두면 모든 로그가 경고가 되어 알림이 무의미해집니다.
        assertEquals(5, log.severityNum, "모르면 notice");
    }

    @Test
    @DisplayName("본문 중간의 error 단어에 속지 않는다")
    void doesNotOverreactToKeywordsDeepInBody() {
        // 앞 120자만 검사하는 이유: 설정 설명 문자열에 'error' 가 들어가도
        // 전체를 오류로 판정하면 오탐이 쏟아집니다.
        final String longLine = "Sep 19 08:41:00 vm1 app: "
                + "x".repeat(150)
                + " the word error appears only here";

        final var log = normalizer.normalize(longLine, "vm-1", "Ubuntu", null,
                "2026-09-19T12:00:00Z");

        assertEquals(5, log.severityNum, "앞부분이 평범하면 notice");
    }

    @Test
    @DisplayName("모든 로그에 원문을 보존한다 (AI 근거 자료)")
    void alwaysKeepsRawLine() {
        final String raw = "*Sep 19 08:12:33: %SYS-5-CONFIG_I: Configured from console";
        final var log = normalizer.normalize(raw, "c8000v-1", "Cisco 8000v", null,
                "2026-09-19T12:00:00Z");

        // 원문을 버리면 나중에 AI 가 볼 근거가 사라집니다.
        assertEquals(raw, log.raw);
        assertFalse(log.raw.isBlank());
    }

    @Test
    @DisplayName("빈 줄도 예외 없이 처리한다")
    void handlesBlankLine() {
        final var log = normalizer.normalize("   ", "a1", "FRR", null, "2026-09-19T12:00:00Z");

        assertEquals("", log.raw);
        assertEquals(6, log.severityNum, "빈 줄은 info");
        assertNotNull(log.loggedAt);
    }

    @Test
    @DisplayName("타임스탬프가 없으면 수집 시각을 쓴다")
    void fallsBackToCollectedTime() {
        final String collectedAt = "2026-09-19T12:34:56Z";
        final var log = normalizer.normalize(
                "%SYS-5-CONFIG_I: Configured from console", "c8000v-1", "Cisco 8000v",
                null, collectedAt);

        // 장비 시각을 못 읽었을 때 정렬이 깨지지 않도록 수집 시각을 씁니다.
        assertEquals(collectedAt, log.loggedAt);
    }

    @Test
    @DisplayName("ISO 타임스탬프를 우선 사용한다")
    void prefersIsoTimestamp() {
        final var log = normalizer.normalize(
                "2026-09-19T08:12:33Z %SYS-5-CONFIG_I: Configured from console",
                "c8000v-1", "Cisco 8000v", null, "2026-09-19T12:00:00Z");

        assertTrue(log.loggedAt.startsWith("2026-09-19T08:12:33"),
                "로그의 ISO 시각을 써야 함: " + log.loggedAt);
    }
}
