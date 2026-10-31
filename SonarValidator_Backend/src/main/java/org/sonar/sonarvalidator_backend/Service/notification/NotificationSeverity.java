package org.sonar.sonarvalidator_backend.Service.notification;

import java.util.Locale;
import java.util.Set;

public enum NotificationSeverity {

    INFO("info", "정보"),

    WARNING("warning", "경고"),

    CRITICAL("critical", "심각");

    private static final Set<String> NAMES = Set.of("critical", "warning", "info");

    private final String value;
    private final String label;

    NotificationSeverity(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public static NotificationSeverity parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String lower = value.trim().toLowerCase(Locale.ROOT);
        for (final NotificationSeverity severity : values()) {
            if (severity.value.equals(lower)) {
                return severity;
            }
        }
        return null;
    }

    public static NotificationSeverity parseForWrite(String value) {
        final NotificationSeverity parsed = parse(value);
        return (parsed == null) ? INFO : parsed;
    }

    public boolean isAtLeast(NotificationSeverity threshold) {
        return threshold != null && ordinal() >= threshold.ordinal();
    }

    public static boolean isKnown(String value) {
        return value != null && NAMES.contains(value.trim().toLowerCase(Locale.ROOT));
    }
}
