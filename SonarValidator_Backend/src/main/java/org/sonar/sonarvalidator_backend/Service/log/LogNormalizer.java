package org.sonar.sonarvalidator_backend.Service.log;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 장비 로그 한 줄을 <b>정규화된 구조</b>로 바꿉니다.
 *
 * <h2>왜 정규화가 필요한가</h2>
 * <p>벤더마다 심각도 표기가 다릅니다. 정규화하지 않으면 "warning 이상만 보기"
 * 같은 필터를 벤더별로 따로 구현해야 하고, AI 프롬프트도 벤더마다 달라집니다.
 *
 * <table border="1">
 *   <caption>벤더별 표기</caption>
 *   <tr><th>장비</th><th>예시</th><th>심각도 위치</th></tr>
 *   <tr><td>Cisco IOS-XE</td><td>{@code *Sep 19 08:12:33: %SYS-5-CONFIG_I: Configured from console}</td><td>{@code SYS-<b>5</b>}</td></tr>
 *   <tr><td>FRR</td><td>{@code 2026/09/19 08:12:33 BGP: %DAEMON-3-...}</td><td>{@code DAEMON-<b>3</b>}</td></tr>
 *   <tr><td>Linux syslog</td><td>{@code Sep 19 08:12:33 host kernel: [123] ...}</td><td>키워드({@code error}/{@code warn})</td></tr>
 * </table>
 *
 * <h2>syslog 심각도 등급 (숫자가 낮을수록 심각)</h2>
 * <pre>
 *   0 emergency  1 alert  2 critical  3 error
 *   4 warning    5 notice 6 info      7 debug
 * </pre>
 *
 * <h2>⚠️ 판단이 어려울 때의 기본값</h2>
 * <p>심각도를 알아내지 못하면 <b>notice(5)</b> 로 둡니다. {@code info(6)} 로
 * 두면 어떤 필터에도 걸리지 않아 조용히 묻히고, {@code warning(4)} 로 두면
 * 모든 로그가 경고로 보여 알림이 무의미해집니다.
 */
@Component
public class LogNormalizer {

    /** Cisco / FRR 의 {@code %FACILITY-SEVERITY-MNEMONIC:} 형식입니다. */
    private static final Pattern CISCO_STYLE =
            Pattern.compile("%([A-Z0-9_]+)-(\\d)-([A-Z0-9_]+)\\s*:?\\s*(.*)", Pattern.DOTALL);

    /** RFC3164/5424 계열 타임스탬프 (예: {@code Sep 19 08:12:33}). */
    private static final Pattern SYSLOG_TIMESTAMP =
            Pattern.compile("([A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})");

    /** ISO 계열 타임스탬프 (예: {@code 2026-09-19T08:12:33}). */
    private static final Pattern ISO_TIMESTAMP =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z?(?:[+-]\\d{2}:?\\d{2})?)");

    /** 커널 스타일 타임스탬프 (예: {@code [12345.678]}). */
    private static final Pattern KERNEL_TIMESTAMP =
            Pattern.compile("^\\[(\\s*\\d+\\.\\d+)\\]");

    /**
     * 로그 한 줄을 정규화합니다.
     *
     * <p>예외를 던지지 않습니다. 해석할 수 없는 줄도 원문을 보존해 저장해야
     * 하기 때문입니다. (원문을 버리면 나중에 AI 가 볼 근거가 사라집니다)
     *
     * @param raw       로그 원문 한 줄
     * @param agentId   장비 식별자
     * @param product   제품명 (심각도 해석 힌트)
     * @param projectKey 프로젝트 키 (없으면 null)
     * @param fallbackTime 장비 시각을 못 읽었을 때 쓸 수집 시각
     * @return 정규화된 로그
     */
    public NormalizedLog normalize(String raw,
                                   String agentId,
                                   String product,
                                   String projectKey,
                                   String fallbackTime) {
        final NormalizedLog log = new NormalizedLog();
        log.agentId = agentId;
        log.product = product;
        log.projectKey = projectKey;
        log.raw = raw == null ? "" : raw.trim();
        log.collectedAt = fallbackTime == null ? Instant.now().toString() : fallbackTime;

        if (log.raw.isEmpty()) {
            log.message = "";
            log.severityNum = 6;
            log.severity = nameOf(6);
            log.loggedAt = log.collectedAt;
            return log;
        }

        String working = log.raw;

        // 1) 심각도/메시지 코드를 먼저 뽑습니다.
        //    (본문에서 제거하지 않고 message 에 그대로 남겨 AI 가 근거로 쓸 수 있게)
        final Matcher cisco = CISCO_STYLE.matcher(working);
        if (cisco.find()) {
            log.facility = cisco.group(1);
            try {
                log.severityNum = clampSeverity(Integer.parseInt(cisco.group(2)));
            } catch (NumberFormatException ex) {
                log.severityNum = 5;
            }
            log.messageId = "%" + cisco.group(1) + "-" + cisco.group(2) + "-" + cisco.group(3);
            log.message = cisco.group(4) == null ? "" : cisco.group(4).trim();
        } else {
            // 2) 벤더 코드가 없으면 키워드로 추정합니다. (Linux syslog 등)
            log.severityNum = inferFromKeywords(working);
            log.message = working;
        }

        log.severity = nameOf(log.severityNum);

        // 3) 타임스탬프를 찾습니다. 못 찾으면 수집 시각을 씁니다.
        log.loggedAt = extractTimestamp(working, log.collectedAt);

        // 4) facility 를 못 찾았으면 커널/데몬 이름을 본문 앞부분에서 유추합니다.
        if (log.facility == null) {
            log.facility = guessFacility(working);
        }

        return log;
    }

    /**
     * 심각도를 숫자로 해석합니다. (API 입력용)
     *
     * <p>{@code warning}, {@code warn}, {@code 4} 를 모두 받습니다.
     * 화면과 스크립트가 각각 다른 표기를 써도 동작하게 하기 위함입니다.
     *
     * @param value 심각도 이름 또는 숫자
     * @return 0~7, 해석 실패 시 null
     */
    public Integer parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        final String text = value.trim().toLowerCase(Locale.ROOT);

        // 숫자로 직접 지정한 경우
        try {
            return clampSeverity(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            // 이름으로 해석합니다.
        }

        return switch (text) {
            case "emergency", "emerg", "panic" -> 0;
            case "alert" -> 1;
            case "critical", "crit", "fatal" -> 2;
            case "error", "err" -> 3;
            case "warning", "warn" -> 4;
            case "notice" -> 5;
            case "info", "informational" -> 6;
            case "debug" -> 7;
            default -> null;
        };
    }

    /** 심각도 번호를 표준 이름으로 바꿉니다. */
    public static String nameOf(int severityNum) {
        return switch (severityNum) {
            case 0 -> "emergency";
            case 1 -> "alert";
            case 2 -> "critical";
            case 3 -> "error";
            case 4 -> "warning";
            case 5 -> "notice";
            case 6 -> "info";
            case 7 -> "debug";
            default -> "notice";
        };
    }

    /** 0~7 범위로 자릅니다. */
    private static int clampSeverity(int value) {
        return Math.max(0, Math.min(7, value));
    }

    /**
     * 키워드로 심각도를 추정합니다.
     *
     * <p>본문 전체를 훑지 않고 <b>앞부분만</b> 봅니다. 로그 본문 중간에
     * "error" 라는 단어가 들어갔다고 (예: 설정 설명 문자열) 전체를 오류로
     * 판정하면 오탐이 쏟아지기 때문입니다.
     *
     * <h3>⚠️ 단어 경계를 쓰는 이유</h3>
     * <p>{@code contains("down")} 로 검사하면 <b>{@code shutdown} 도 걸립니다.</b>
     * "interface shutdown" 은 정상적인 관리 조작인데 오류로 분류됩니다.
     * 그래서 {@code \bdown\b} 처럼 단어 경계를 씁니다.
     * ({@code shutdown} 은 't' 와 'd' 사이에 경계가 없어 걸리지 않습니다)
     */
    private int inferFromKeywords(String line) {
        // 앞 120자만 검사합니다.
        final String head = (line.length() > 120 ? line.substring(0, 120) : line)
                .toLowerCase(Locale.ROOT);

        if (matches(head, "emerg", "panic")) return 0;
        if (matches(head, "alert")) return 1;
        if (matches(head, "critical", "crit", "fatality", "fatal")) return 2;

        // 오류: 연결 실패/차단/프로토콜 다운 등 운영자가 즉시 봐야 하는 신호
        if (matches(head, "error", "err", "failed", "failure", "denied",
                "rejected", "unreachable", "timed out", "timeout",
                "down", "refused", "dropped", "drop",
                "panic", "segfault", "oom")) {
            return 3;
        }

        // ⚠️ "warn" 은 뒤에 단어 문자가 붙으므로( warning, warned )
        //    양쪽 경계(\\bwarn\\b)를 요구하면 'warning' 을 놓칩니다.
        //    앞 경계만 요구하는 접두 매칭을 씁니다.
        if (matchesPrefix(head, "warn")) return 4;
        if (matches(head, "notice")) return 5;
        if (matchesPrefix(head, "debug", "trace")) return 7;

        // ★ 판단 근거가 없을 때의 기본값은 notice(5) 입니다.
        //   info 로 두면 어떤 필터에도 안 걸려 조용히 묻히고,
        //   warning 으로 두면 모든 로그가 경고가 되어 알림이 무의미해집니다.
        return 5;
    }

    /**
     * 단어 경계(양쪽) 기준으로 키워드가 있는지 확인합니다.
     *
     * <p>{@code contains} 를 쓰면 "shutdown" 이 "down" 으로 걸리는 식의
     * 오탐이 생깁니다. 단어 경계를 두면 그것을 막을 수 있습니다.
     *
     * @param haystack 소문자로 변환된 본문
     * @param keywords 찾을 키워드들
     * @return 하나라도 있으면 true
     */
    private static boolean matches(String haystack, String... keywords) {
        for (final String keyword : keywords) {
            // 키워드 자체에 공백이 있으면 경계 판정이 어긋나므로 그대로 찾습니다.
            if (keyword.indexOf(' ') >= 0) {
                if (haystack.contains(keyword)) {
                    return true;
                }
                continue;
            }
            final Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b");
            if (pattern.matcher(haystack).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * <b>앞 경계만</b> 요구하고 접두로 키워드를 찾습니다.
     *
     * <p>어간만으로 판정해야 하는 경우에 씁니다. 영어는 어미가 붙기 때문입니다.
     * <ul>
     *   <li>{@code warning}, {@code warned}, {@code warns} ← {@code warn} 어간</li>
     *   <li>{@code debugged}, {@code debugging} ← {@code debug} 어간</li>
     * </ul>
     * <p>앞 경계를 요구하는 이유: {@code \bwarn} 은 "warning" 에 매치되지만
     * "iwarn" 같은 문자열에는 매치되지 않습니다.
     *
     * @param haystack 소문자로 변환된 본문
     * @param stems    어간 목록
     * @return 하나라도 있으면 true
     */
    private static boolean matchesPrefix(String haystack, String... stems) {
        for (final String stem : stems) {
            final Pattern pattern = Pattern.compile("\\b" + Pattern.quote(stem));
            if (pattern.matcher(haystack).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 본문에서 타임스탬프를 찾습니다.
     *
     * <p>ISO → 커널 → syslog 순으로 시도합니다. 가장 정보가 많은 형식을
     * 먼저 보는 편이 정확합니다.
     *
     * <h3>⚠️ {@code replaceAll("Z?$", "Z")} 를 쓰면 안 되는 이유</h3>
     * <p>이 패턴은 끝의 <b>0폭 위치</b>에서도 매치되어 {@code Z} 를 <b>덧붙입니다.</b>
     * {@code "…33Z"} 가 {@code "…33ZZ"} 가 되어 {@code Instant.parse} 가 실패하고,
     * 결과적으로 항상 폴백(수집 시각)을 쓰게 됩니다.
     * <b>증상이 조용합니다</b> — 예외도 없고 로그도 남지 않는데 정렬만 시간순이
     * 아니게 됩니다. 테스트로 발견했습니다.
     *
     * @param line         로그 원문
     * @param fallbackTime 못 찾았을 때 쓸 시각
     * @return ISO-8601 문자열
     */
    private String extractTimestamp(String line, String fallbackTime) {
        final Matcher iso = ISO_TIMESTAMP.matcher(line);
        if (iso.find()) {
            final String parsed = parseIsoSafely(iso.group(1));
            if (parsed != null) {
                return parsed;
            }
        }

        // syslog 는 연도가 없습니다. 올해로 가정합니다.
        // (연말/연초 경계에서 오차가 생길 수 있지만, 원문은 보존되므로 추적 가능)
        final Matcher syslog = SYSLOG_TIMESTAMP.matcher(line);
        if (syslog.find()) {
            try {
                final String text = syslog.group(1) + " " + java.time.Year.now().getValue();
                final java.time.format.DateTimeFormatter formatter =
                        java.time.format.DateTimeFormatter.ofPattern("MMM d HH:mm:ss yyyy",
                                Locale.ENGLISH);
                final java.time.LocalDateTime local =
                        java.time.LocalDateTime.parse(text, formatter);
                return local.atZone(java.time.ZoneId.systemDefault()).toInstant().toString();
            } catch (RuntimeException ignored) {
                // 아래 폴백으로 넘어갑니다.
            }
        }

        // 커널 타임스탬프는 부팅 이후 경과 시간이라 절대 시각이 아닙니다.
        // 절대 시각으로 오해하면 정렬이 완전히 망가지므로 폴백을 씁니다.
        if (KERNEL_TIMESTAMP.matcher(line).find()) {
            return fallbackTime;
        }

        return fallbackTime;
    }

    /**
     * ISO 후보 문자열을 {@link Instant} 로 바꿉니다. 실패하면 null 입니다.
     *
     * <p>공백 구분({@code 2026-09-19 08:12:33})과 타임존 없는 형태
     * ({@code 2026-09-19T08:12:33})를 모두 처리합니다.
     *
     * @param candidate ISO 후보
     * @return ISO-8601 문자열 (실패 시 null)
     */
    private String parseIsoSafely(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        // 공백 구분자를 T 로 바꿉니다. (이때 끝에 Z 를 덧붙이지 않습니다)
        String normalized = candidate.trim().replace(' ', 'T');

        try {
            // 타임존 표기가 있으면 그대로 파싱합니다.
            if (normalized.endsWith("Z") || normalized.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                return Instant.parse(normalized).toString();
            }

            // 타임존이 없으면 시스템 기본 시간대로 해석합니다.
            // (UTC 로 강제하면 서버 로컬 시간과 어긋나 정렬이 뒤집힙니다)
            final java.time.LocalDateTime local = java.time.LocalDateTime.parse(normalized);
            return local.atZone(java.time.ZoneId.systemDefault()).toInstant().toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 본문에서 facility 를 유추합니다.
     *
     * <p>없으면 null 을 돌려줍니다. 추측해서 채우면 잘못된 분류로 필터가
     * 오작동하므로, 모르면 비워 두는 편이 안전합니다.
     */
    private String guessFacility(String line) {
        final String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("kernel:")) return "kernel";
        if (lower.contains("sshd")) return "sshd";
        if (lower.contains("bgpd") || lower.contains("bgp")) return "bgp";
        if (lower.contains("ospfd") || lower.contains("ospf")) return "ospf";
        if (lower.contains("nftables") || lower.contains("nft")) return "nftables";
        if (lower.contains("systemd")) return "systemd";
        return null;
    }

    /**
     * 정규화된 로그입니다. (엔티티에 매핑하기 전의 값 객체)
     *
     * <p>엔티티를 직접 만들지 않는 이유: 정규화 로직을 DB 없이 테스트하기
     * 위함입니다.
     */
    public static class NormalizedLog {
        public String agentId;
        public String projectKey;
        public String product;
        public String loggedAt;
        public String collectedAt;
        public int severityNum = 6;
        public String severity = "info";
        public String facility;
        public String messageId;
        public String message;
        public String raw;
    }
}
