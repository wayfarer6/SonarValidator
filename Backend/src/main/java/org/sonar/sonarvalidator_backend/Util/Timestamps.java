package org.sonar.sonarvalidator_backend.Util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 날짜/시각 변환 헬퍼입니다.
 *
 * <h2>왜 별도 클래스가 필요한가</h2>
 * <p>DB 스키마 변경(SONAR-19)으로 엔티티의 날짜 필드가
 * <b>문자열(ISO-8601) → {@link Date}</b> 로 바뀌었습니다.
 * 그런데 외부 계약(HTTP JSON, 프론트엔드)은 여전히 <b>ISO-8601 문자열</b>을
 * 씁니다. 그래서 경계마다 두 표현을 오갈 일이 생겼고, 변환을 곳곳에
 * 흩뿌리면 한 곳만 빠뜨려도 조용히 숫자(epoch millis)가 내려가 화면이
 * 깨집니다. 변환 규칙을 여기 한 곳에 모읍니다.
 *
 * <h2>경계 원칙</h2>
 * <ul>
 *   <li><b>엔티티/DB</b>: {@link Date} (H2 {@code TIMESTAMP} 컬럼)</li>
 *   <li><b>서비스 파라미터·JSON 응답</b>: ISO-8601 문자열
 *       ({@link Instant#toString()} 형식, 예: {@code 2026-09-19T08:12:33Z})</li>
 * </ul>
 *
 * <p>모든 메서드는 {@code null} 을 안전하게 통과시킵니다. 날짜가 없는 행
 * (예: 아직 연결 확인을 안 한 자격증명)이 정상 상태이기 때문입니다.
 */
public final class Timestamps {

    private Timestamps() {
        // 유틸리티 클래스는 인스턴스화하지 않습니다.
    }

    /**
     * 현재 시각을 돌려줍니다.
     *
     * @return 현재 시각
     */
    public static Date now() {
        return new Date();
    }

    /**
     * 시각을 ISO-8601 문자열로 바꿉니다. (JSON 응답용)
     *
     * @param value 시각 (null 허용)
     * @return ISO-8601 문자열, 입력이 null 이면 null
     */
    public static String iso(Date value) {
        return value == null ? null : value.toInstant().toString();
    }

    /**
     * ISO-8601 문자열을 {@link Date} 로 바꿉니다. (요청 파싱용)
     *
     * <p>해석할 수 없으면 {@code null} 을 돌려줍니다. 예외를 던지지 않는
     * 이유: 기간 필터는 <b>선택</b> 입력이라 형식이 틀렸다고 요청 전체를
     * 실패시키기보다, 필터를 적용하지 않고 결과를 보여 주는 편이
     * 사용자에게 유용합니다. (LogService 의 관례와 같습니다)
     *
     * <p>타임존 표기가 없으면 서버 로컬 시간대로 해석합니다. UTC 로 강제하면
     * 서버 로컬 시각과 어긋나 기간 경계가 밀립니다.
     *
     * @param value ISO-8601 문자열 (null/공백 허용)
     * @return 시각, 해석 실패 시 null
     */
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