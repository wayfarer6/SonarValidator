package org.sonar.sonarvalidator_backend.Service.notification;

import java.util.Locale;
import java.util.Set;

/**
 * 알림 분류({@code category})의 <b>허용 값</b>입니다.
 *
 * <h2>⚠️ 왜 enum + 파서인가</h2>
 * <p>이전에는 {@code NotificationService} 안에
 * <ul>
 *   <li>{@code Set<String> CATEGORIES} (허용 값),</li>
 *   <li>{@code normalizeCategory(String)} (정규화),</li>
 *   <li>그리고 호출부의 문자열 리터럴 {@code "SECURITY"}, {@code "POLICY"} …</li>
 * </ul>
 * 이 따로 있었습니다. 그래서 오타 난 리터럴이 <b>컴파일은 통과</b>하고
 * 조용히 {@code SYSTEM} 으로 강등됐습니다 — 운영자는 "보안 경고가 SYSTEM
 * 으로 보인다" 를 한참 뒤에 발견합니다.
 *
 * <p>지금은 호출부가 상수를 쓰므로 오타가 컴파일 오류가 됩니다.
 * 외부 입력(필터 파라미터)은 {@link #parse} 로 해석하고, 모르는 값은
 * {@code null}(= 전체) 로 두어 잘못된 필터가 결과를 비우지 않게 합니다.
 *
 * <h2>⚠️ 표시 이름을 여기 두는 이유</h2>
 * <p>{@link #label()} 이 UI 문구를 들고 있습니다. 화면에서 분류 이름을
 * 따로 매핑하면, 분류가 늘었을 때 화면에 원시 값이 노출됩니다.
 */
public enum NotificationCategory {

    POLICY("정책"),
    AGENT("에이전트"),
    PROJECT("프로젝트"),
    SECURITY("보안"),
    SYSTEM("시스템");

    /** 허용 값 집합 (입력 검증용). */
    private static final Set<String> NAMES = Set.of(
            "POLICY", "AGENT", "PROJECT", "SECURITY", "SYSTEM");

    private final String label;

    NotificationCategory(String label) {
        this.label = label;
    }

    /** @return 화면 표시 이름 (예: {@code 보안}) */
    public String label() {
        return label;
    }

    /** @return 저장 값 (예: {@code SECURITY}) */
    public String value() {
        return name();
    }

    /**
     * 외부 입력을 분류로 해석합니다.
     *
     * <p>모르는 값은 <b>{@code null} 을 돌려줍니다.</b>
     * {@link NotificationCategory#SYSTEM} 으로 강등하지 않는 이유: 이 메서드는
     * <b>기록</b>과 <b>필터</b> 양쪽에 쓰입니다. 필터에서 모르는 값을
     * {@code SYSTEM} 으로 바꾸면 "SYSTEM 알림만 보기" 가 되어 화면이 비고,
     * 운영자는 알림이 없는 줄 압니다. {@code null} 은 저장소에서 "조건 없음"
     * 으로 처리됩니다.
     *
     * @param value 입력 (null/빈 문자열 허용)
     * @return 분류 (모르면 null)
     */
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

    /**
     * 기록용 해석입니다. 모르는 값도 반드시 하나로 확정해야 합니다.
     *
     * <p>기록 경로에서 {@code null} 을 저장하면 조회가 그 행을 못 찾습니다.
     * 그래서 기록은 {@link #SYSTEM} 으로 강등합니다 — 기록과 필터의 요구가
     * 다르므로 메서드를 나눕니다.
     *
     * @param value 입력 (null/빈 문자열 허용)
     * @return 분류 (모르면 {@link #SYSTEM})
     */
    public static NotificationCategory parseForWrite(String value) {
        final NotificationCategory parsed = parse(value);
        return (parsed == null) ? SYSTEM : parsed;
    }

    /**
     * 이름이 허용 값인지 봅니다. (저장 값 기준)
     *
     * @param value 확인할 값
     * @return 허용 값이면 true
     */
    public static boolean isKnown(String value) {
        return value != null && NAMES.contains(value.trim().toUpperCase(Locale.ROOT));
    }
}