package org.sonar.sonarvalidator_backend.Service.notification;

import java.util.Locale;

/**
 * 알림 조회 필터입니다. (읽기 전용 값 객체)
 *
 * <h2>⚠️ 왜 값 객체인가 — 정규화가 네 곳에 흩어져 있었다</h2>
 * <p>{@code NotificationService.search} 는 인자 일곱 개를 받았고, 그 안에서
 * 네 가지를 각각 다듬었습니다.
 *
 * <pre>
 *   category    → blankToNull + 저장소가 다시 검증
 *   severity    → blankToNull + 저장소가 다시 검증
 *   readState   → normalizeReadState   (unread/read 만 허용)
 *   search      → blankToNull + LIKE 패턴 생성 (PostgreSQL bytea 사고의 원인)
 * </pre>
 *
 * <p>컨트롤러가 같은 인자를 순서대로 넘겨야 했기 때문에, 순서를 하나
 * 바꾸면 <b>검색어가 프로젝트 키로 들어가는</b> 식의 사고가 났습니다.
 * 같은 타입({@code String})이 일곱 개 연달아 있으면 컴파일러가 막지 못합니다.
 *
 * <p>지금은 {@link #of} 하나로 만들고, 정규화는 <b>생성 시점에 한 번</b>
 * 일어납니다. 잘못된 값은 그 자리에서 {@code null}(= 조건 없음) 로
 * 정리되므로, 저장소는 이미 정리된 값만 받습니다.
 *
 * <h2>⚠️ 모르는 값을 {@code null} 로 두는 이유</h2>
 * <p>잘못된 필터로 결과가 비어 보이는 것보다 전체를 보여주는 편이
 * 안전합니다. "알림이 없다" 와 "필터가 틀렸다" 를 운영자가 구분할 수 없기
 * 때문입니다.
 *
 * @param category   분류 (null 이면 전체)
 * @param severity   심각도 (null 이면 전체)
 * @param projectKey 프로젝트 키 (null 이면 전체)
 * @param agentId    Agent 식별자 (null 이면 전체)
 * @param term       검색어 (null/빈 문자열이면 전체)
 * @param readState  null 전체 / {@code "unread"} / {@code "read"}
 * @param limit      최대 건수 (1~500 으로 자름)
 */
public record NotificationFilter(NotificationCategory category,
                                 NotificationSeverity severity,
                                 String projectKey,
                                 String agentId,
                                 String term,
                                 String readState,
                                 int limit) {

    /** 조회 한 건의 기본 최대치입니다. */
    public static final int DEFAULT_LIMIT = 100;

    /** 조회 한 건의 절대 상한입니다. */
    public static final int MAX_LIMIT = 500;

    /**
     * 외부 입력 문자열로 필터를 만듭니다.
     *
     * <p>컨트롤러가 받은 {@code @RequestParam} 을 그대로 넘기는 경로입니다.
     * 이 시점에 모든 정규화가 끝나므로 이후 계층은 원시 문자열을 보지 않습니다.
     *
     * @param category   분류 문자열 (모르면 무시)
     * @param severity   심각도 문자열 (모르면 무시)
     * @param readState  읽음 상태 문자열 (모르면 무시)
     * @param projectKey 프로젝트 키 (빈 값이면 null)
     * @param agentId    Agent 식별자 (빈 값이면 null)
     * @param search     검색어 (빈 값이면 null)
     * @param limit      최대 건수 (0 이하면 기본값)
     * @return 필터
     */
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

    /**
     * 조건 없이 최근 알림만 보는 필터입니다.
     *
     * @param limit 최대 건수
     * @return 필터
     */
    public static NotificationFilter recent(int limit) {
        return of(null, null, null, null, null, null, limit);
    }

    /**
     * 검색어의 SQL LIKE 패턴입니다.
     *
     * <h2>⚠️ Java 에서 미리 만드는 이유 (실측 사고)</h2>
     * <p>JPQL 에서 {@code LOWER(CONCAT('%', :search, '%'))} 로 쓰면, 검색어가
     * 없을 때 파라미터가 <b>untyped null</b> 로 바인딩되어 PostgreSQL 이
     * {@code LOWER(bytea)} 로 해석하고
     * {@code function lower(bytea) does not exist} 로 조회가 500 이 됩니다.
     *
     * <p>H2 에서는 통과하고 PostgreSQL 에서만 터지므로 놓치기 쉽습니다.
     * 패턴을 먼저 만들어 넘기면 파라미터가 항상 VARCHAR 로 타이핑되고,
     * 비교 대상 컬럼에만 {@code LOWER} 가 적용됩니다.
     *
     * @return LIKE 패턴 (검색어가 없으면 null)
     */
    public String likePattern() {
        return term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";
    }

    /** @return 저장 값 문자열 (없으면 null) — 저장소 인자용 */
    public String categoryValue() {
        return category == null ? null : category.value();
    }

    /** @return 저장 값 문자열 (없으면 null) — 저장소 인자용 */
    public String severityValue() {
        return severity == null ? null : severity.value();
    }

    /** @return 검색어가 있는지 */
    public boolean hasTerm() {
        return term != null;
    }

    /**
     * 읽음 상태 문자열을 허용 값으로 정규화합니다.
     *
     * @param readState 입력 (null 허용)
     * @return {@code "unread"} / {@code "read"} / null
     */
    private static String normalizeReadState(String readState) {
        if (readState == null || readState.isBlank()) {
            return null;
        }
        final String lower = readState.trim().toLowerCase(Locale.ROOT);
        return ("unread".equals(lower) || "read".equals(lower)) ? lower : null;
    }

    /** @return 범위 안으로 자른 최대 건수 */
    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 빈 값을 null 로, 앞뒤 공백은 제거합니다.
     *
     * <h2>⚠️ 공백을 남기면 조용히 아무것도 못 찾는다</h2>
     * <p>식별자 비교는 저장소에서 {@code =} 입니다. {@code "  PRJ-1  "} 는
     * {@code "PRJ-1"} 과 같지 않으므로 <b>결과가 비고</b>, 운영자는
     * "알림이 없다" 로 해석합니다. 오류가 나지 않는 것이 이 버그의 무서운 점입니다.
     *
     * @param value 입력
     * @return trim 된 값 (빈 값이면 null)
     */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}