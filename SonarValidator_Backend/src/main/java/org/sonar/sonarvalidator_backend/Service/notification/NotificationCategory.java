package org.sonar.sonarvalidator_backend.Service.notification;

import java.util.Locale;
import java.util.Set;

public enum NotificationCategory {

    POLICY("정책"),
    AGENT("에이전트"),
    PROJECT("프로젝트"),
    SECURITY("보안"),
    SYSTEM("시스템");

    private static final Set<String> NAMES = Set.of(
            "POLICY", "AGENT", "PROJECT", "SECURITY", "SYSTEM");

    private final String label;

    NotificationCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String value() {
        return name();
    }

    public static NotificationCategory parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String upper = value.trim().toUpperCase(Locale.ROOT);
        for (final NotificationCategory category : values()) {
            if (category.name().equals(upper)) {
                return category;
            }
        }
        return null;
    }

    public static NotificationCategory parseForWrite(String value) {
        final NotificationCategory parsed = parse(value);
        return (parsed == null) ? SYSTEM : parsed;
    }

    public static boolean isKnown(String value) {
        return value != null && NAMES.contains(value.trim().toUpperCase(Locale.ROOT));
    }
}
