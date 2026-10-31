package org.sonar.sonarvalidator_backend.Util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public final class Timestamps {

    private Timestamps() {
        // 유틸리티 클래스는 인스턴스화하지 않습니다.
    }

    public static Date now() {
        return new Date();
    }

    public static String iso(Date value) {
        return value == null ? null : value.toInstant().toString();
    }

    public static Date parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String candidate = value.trim().replace(' ', 'T');
        try {
            if (candidate.endsWith("Z")
                    || candidate.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                return Date.from(Instant.parse(candidate));
            }
            final LocalDateTime local = LocalDateTime.parse(candidate);
            return Date.from(local.atZone(ZoneId.systemDefault()).toInstant());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
