package org.sonar.sonarvalidator_backend.Service.log;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class LogNormalizer {

    private static final Pattern CISCO_STYLE =
            Pattern.compile("%([A-Z0-9_]+)-(\\d)-([A-Z0-9_]+)\\s*:?\\s*(.*)", Pattern.DOTALL);

    private static final Pattern SYSLOG_TIMESTAMP =
            Pattern.compile("([A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})");

    private static final Pattern ISO_TIMESTAMP =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z?(?:[+-]\\d{2}:?\\d{2})?)");

    private static final Pattern KERNEL_TIMESTAMP =
            Pattern.compile("^\\[(\\s*\\d+\\.\\d+)\\]");

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

    private static int clampSeverity(int value) {
        return Math.max(0, Math.min(7, value));
    }

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




        if (matchesPrefix(head, "warn")) return 4;
        if (matches(head, "notice")) return 5;
        if (matchesPrefix(head, "debug", "trace")) return 7;




        return 5;
    }

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

    private static boolean matchesPrefix(String haystack, String... stems) {
        for (final String stem : stems) {
            final Pattern pattern = Pattern.compile("\\b" + Pattern.quote(stem));
            if (pattern.matcher(haystack).find()) {
                return true;
            }
        }
        return false;
    }

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
