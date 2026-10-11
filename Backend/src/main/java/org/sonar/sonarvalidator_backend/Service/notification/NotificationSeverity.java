package org.sonar.sonarvalidator_backend.Service.notification;

import java.util.Locale;
import java.util.Set;

/**
 * 알림 심각도({@code severity})의 허용 값입니다.
 *
 * <h2>⚠️ 순서가 곧 의미다</h2>
 * <p>선언 순서를 <b>낮음 → 높음</b> 으로 두었습니다. 이 순서 덕분에
 * {@link #isAtLeast} 를 {@code ordinal()} 비교로 구현할 수 있고,
 * "critical 만 필터" 같은 판정을 문자열 집합 없이 표현합니다.
 *
 * <h2>⚠️ 분류와 정규화 규칙이 다르다</h2>
 * <p>분류는 대문자 저장({@code SECURITY}), 심각도는 <b>소문자</b>
 * 저장({@code critical})입니다. 이 불일치는 API 응답에 그대로 드러나므로
 * 바꿀 수 없습니다. 대신 규칙을 여기 한 곳에 모아, 호출부가
 * {@code toUpperCase}/{@code toLowerCase} 를 각자 고르지 않게 합니다.
 */
public enum NotificationSeverity {

    /** 참고 정보. 조치가 필요하지 않습니다. */
    INFO("info", "정보"),

    /** 주의. 곧 조치해야 합니다. */
    WARNING("warning", "경고"),

    /** 즉시 조치. 보안 위반 등. */
    CRITICAL("critical", "심각");

    /** 저장 값 집합. 소문자입니다 (기존 계약). */
    private static final Set<String> NAMES = Set.of("critical", "warning", "info");

    private final String value;
    private final String label;

    NotificationSeverity(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /** @return 저장 값 (예: {@code critical}) */
    public String value() {
        return value;
    }

    /** @return 화면 표시 이름 (예: {@code 심각}) */
    public String label() {
        return label;
    }

    /**
     * 외부 입력을 심각도로 해석합니다.
     *
     * @param value 입력 (null/빈 문자열 허용)
     * @return 심각도 (모르면 null)
     */
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

    /**
     * 기록용 해석입니다. 모르는 값은 {@link #INFO} 로 둡니다.
     *
     * <p>가장 낮은 심각도로 둡니다. 모르는 값을 {@code CRITICAL} 로 올리면
     * 잘못된 입력 하나가 전체 화면을 빨갛게 만들고, {@code WARNING} 으로
     * 두면 "조치 필요" 로 오해됩니다.
     *
     * @param value 입력
     * @return 심각도 (모르면 {@link #INFO})
     */
    public static NotificationSeverity parseForWrite(String value) {
        final NotificationSeverity parsed = parse(value);
        return (parsed == null) ? INFO : parsed;
    }

    /**
     * 이 심각도가 기준 이상인지 봅니다.
     *
     * <p>선언 순서가 곧 강도이므로 {@code ordinal} 비교로 충분합니다.
     *
     * @param threshold 기준
     * @return 이 심각도가 기준 이상이면 true
     */
    public boolean isAtLeast(NotificationSeverity threshold) {
        return threshold != null && ordinal() >= threshold.ordinal();
    }

    /**
     * 이름이 허용 값인지 봅니다. (저장 값 기준)
     *
     * @param value 확인할 값
     * @return 허용 값이면 true
     */
    public static boolean isKnown(String value) {
        return value != null && NAMES.contains(value.trim().toLowerCase(Locale.ROOT));
    }
}