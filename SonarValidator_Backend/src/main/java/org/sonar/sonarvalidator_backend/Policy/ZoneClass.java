package org.sonar.sonarvalidator_backend.Policy;

import java.util.Locale;

/**
 * 망분리 정책이 쓰는 보안 등급(존)입니다.
 *
 * <p>프론트엔드의 CSO 등급과 1:1 로 맞춥니다.
 * ({@code ProjectWizardContext.tsx} 의 {@code SubnetClass})
 *
 * <table border="1">
 *   <caption>등급과 성질</caption>
 *   <tr><th>등급</th><th>레벨</th><th>성질</th></tr>
 *   <tr><td>{@link #CONFIDENTIAL}</td><td>3</td><td>가장 민감. 외부(Open)와 직접 연결 금지</td></tr>
 *   <tr><td>{@link #SENSITIVE}</td><td>2</td><td>내부 업무망. 양쪽 모두와 연결 가능</td></tr>
 *   <tr><td>{@link #OPEN}</td><td>1</td><td>공개망. Confidential 과 직접 연결 금지</td></tr>
 * </table>
 *
 * <p>레벨은 <b>인접 판정</b>에 씁니다. 등급 차이가 2 이상이면 한 단계를 건너뛰는
 * 것이므로 위반 후보가 됩니다. 이 규칙 덕분에 새 등급을 추가하더라도
 * 위반 판정 코드를 고칠 필요가 없습니다.
 */
public enum ZoneClass {

    /** 공개망. 인터넷과 직접 연동되는 구역입니다. */
    OPEN("Open", 1),

    /** 내부 업무망. 공개망과 기밀망 사이의 완충 구역입니다. */
    SENSITIVE("Sensitive", 2),

    /** 기밀망. 가장 민감한 구역이며 외부와 직접 연결하면 안 됩니다. */
    CONFIDENTIAL("Confidential", 3);

    private final String label;
    private final int level;

    ZoneClass(String label, int level) {
        this.label = label;
        this.level = level;
    }

    /** @return 사람이 읽는 표시 이름 (프론트엔드와 동일한 표기) */
    public String label() {
        return label;
    }

    /** @return 등급 레벨 (클수록 민감) */
    public int level() {
        return level;
    }

    /**
     * 문자열을 등급으로 바꿉니다. 대소문자와 앞뒤 공백을 무시합니다.
     *
     * @param value 입력 (예: {@code "Confidential"}, {@code "open"})
     * @return 대응 등급, 알 수 없으면 {@code null}
     */
    public static ZoneClass fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (final ZoneClass zone : values()) {
            if (zone.name().equals(normalized) || zone.label.toUpperCase(Locale.ROOT).equals(normalized)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * 두 등급의 직접 연결이 허용되는지 알려줍니다.
     *
     * <p>등급 차이가 1 이하면 허용합니다. 즉 인접 등급끼리만 연결할 수 있고,
     * 한 단계를 건너뛰는 연결({@code Confidential ↔ Open})은 금지됩니다.
     *
     * @param source 출발 등급
     * @param target 도착 등급
     * @return 연결이 허용되면 {@code true}
     */
    public static boolean allowsDirectConnection(ZoneClass source, ZoneClass target) {
        if (source == null || target == null) {
            return true;
        }
        return Math.abs(source.level - target.level) <= 1;
    }

    /**
     * 두 등급의 직접 연결이 금지되는지 알려줍니다.
     *
     * @param source 출발 등급
     * @param target 도착 등급
     * @return 금지되면 {@code true}
     */
    public static boolean forbidsDirectConnection(ZoneClass source, ZoneClass target) {
        return !allowsDirectConnection(source, target);
    }
}
