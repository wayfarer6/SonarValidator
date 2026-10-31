package org.sonar.sonarvalidator_backend.Service.notification;

import java.util.Locale;

public record NotificationFilter(NotificationCategory category,
                                 NotificationSeverity severity,
                                 String projectKey,
                                 String agentId,
                                 String term,
                                 String readState,
                                 int limit) {

    public static final int DEFAULT_LIMIT = 100;

    public static final int MAX_LIMIT = 500;

    public static NotificationFilter of(String category,
                                        String severity,
                                        String readState,
                                        String projectKey,
                                        String agentId,
                                        String search,
                                        int limit) {
        return new NotificationFilter(
                // ⚠️ 필터이므로 parse() 를 씁니다. 모르는 값은 null(=전체)입니다.
                NotificationCategory.parse(category),
                NotificationSeverity.parse(severity),
                blankToNull(projectKey),
                blankToNull(agentId),
                blankToNull(search),
                normalizeReadState(readState),
                clampLimit(limit));
    }

    public static NotificationFilter recent(int limit) {
        return of(null, null, null, null, null, null, limit);
    }

    public String likePattern() {
        return term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";
    }

    public String categoryValue() {
        return category == null ? null : category.value();
    }

    public String severityValue() {
        return severity == null ? null : severity.value();
    }

    public boolean hasTerm() {
        return term != null;
    }

    private static String normalizeReadState(String readState) {
        if (readState == null || readState.isBlank()) {
            return null;
        }
        final String lower = readState.trim().toLowerCase(Locale.ROOT);
        return ("unread".equals(lower) || "read".equals(lower)) ? lower : null;
    }

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
