package org.sonar.sonarvalidator_backend.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.sonar.sonarvalidator_backend.Model.entity.Notification;
import org.sonar.sonarvalidator_backend.Repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림의 기록·조회·읽음 처리를 담당합니다.
 *
 * <h2>기록 지점</h2>
 * <p>이 서비스는 스스로 알림을 만들지 않습니다. 변경 이력
 * ({@link ComplianceService}) 과 같은 원칙으로, <b>실제로 사건이 일어난
 * 지점</b>에서 호출해 남깁니다. 이렇게 두면 알림이 실제 상태와 어긋날 수
 * 없습니다.
 *
 * <p>기록 실패가 본 작업을 막아서는 안 됩니다. {@link #notifyQuietly} 는
 * 예외를 흡수하고 경고만 남깁니다. (알림 기록 때문에 정책 푸시가 실패하는
 * 것이 훨씬 나쁩니다)
 *
 * <h2>⚠️ 반복 알림 합치기 (dedupe)</h2>
 * <p>30초 주기 검증이 계속 실패하면 알림이 하루 수천 건 쌓입니다. 그러면
 * 목록이 무의미해지고, 정작 <b>새로운 문제</b>가 그 속에 묻힙니다.
 *
 * <p>그래서 {@code dedupeKey} 가 같은 알림이 {@link #DEDUPE_WINDOW} 안에
 * 있으면 새 행을 만들지 않고 {@code repeatCount} 를 올리고 발생 시각을
 * 갱신합니다. 시각을 갱신하는 이유는 "지금도 진행 중" 이라는 사실이
 * "언젠가 한 번 있었다" 보다 중요하기 때문입니다.
 *
 * <p>창(window)을 두는 이유: 어제 실패한 것과 오늘 실패한 것은 별개로
 * 봐야 합니다. 영구히 합치면 문제가 해결됐다가 다시 생겨도 알림이 안 옵니다.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * 같은 중복 키를 하나로 볼 최대 시간 간격입니다.
     *
     * <p>5분으로 둔 근거는 수집/검증 주기(30초)보다 충분히 크고, 운영자가
     * "아까 그 문제" 로 인식하는 범위 안입니다. 너무 짧으면 여전히 쌓이고,
     * 너무 길면 서로 다른 시점의 문제가 합쳐집니다.
     */
    private static final Duration DEDUPE_WINDOW = Duration.ofMinutes(5);

    /** 알림 분류 값 집합. 오타로 생긴 분류를 걸러냅니다. */
    private static final java.util.Set<String> CATEGORIES =
            java.util.Set.of("POLICY", "AGENT", "PROJECT", "SECURITY", "SYSTEM");

    /** 심각도 값 집합. */
    private static final java.util.Set<String> SEVERITIES =
            java.util.Set.of("critical", "warning", "info");

    private final NotificationRepository repository;

    /**
     * @param repository 알림 저장소
     */
    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    // ------------------------------------------------------------------
    //  기록
    // ------------------------------------------------------------------

    /**
     * 알림 한 건을 기록합니다.
     *
     * @param category   분류 ({@code POLICY}/{@code AGENT}/{@code PROJECT}/{@code SECURITY}/{@code SYSTEM})
     * @param severity   심각도 ({@code critical}/{@code warning}/{@code info})
     * @param title      한 줄 제목
     * @param message    본문 요약
     * @param projectKey 프로젝트 키 (없으면 null)
     * @param agentId    장치 식별자 (없으면 null)
     * @param source     발생 주체
     * @param link       상세 화면 경로 (없으면 null)
     * @param dedupeKey  중복 방지 키 (없으면 null — 중복 검사 안 함)
     * @return 저장되거나 갱신된 알림
     */
    @Transactional
    public Notification notify(String category,
                               String severity,
                               String title,
                               String message,
                               String projectKey,
                               String agentId,
                               String source,
                               String link,
                               String dedupeKey) {
        final java.util.Date now = new java.util.Date();

        // 같은 원인이 최근에 있었다면 새로 만들지 않고 횟수만 올립니다.
        final Notification existing = findRecentDuplicate(dedupeKey, now);
        if (existing != null) {
            existing.setRepeatCount(existing.getRepeatCount() + 1);
            existing.setOccurredAt(now);
            // 본문이 갱신될 수 있으므로(예: 위반 건수가 바뀜) 최신 내용으로 교체합니다.
            if (message != null && !message.isBlank()) {
                existing.setMessage(message);
            }
            // 반복되면서 심각해질 수 있습니다. (warning → critical)
            existing.setSeverity(normalizeSeverity(severity));
            return repository.save(existing);
        }

        final Notification notification = new Notification();
        notification.setNotificationId("NTF-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase(Locale.ROOT));
        notification.setCategory(normalizeCategory(category));
        notification.setSeverity(normalizeSeverity(severity));
        notification.setTitle(title == null || title.isBlank() ? "(제목 없음)" : title);
        notification.setMessage(message);
        notification.setProjectKey(projectKey);
        notification.setAgentId(agentId);
        notification.setSource(source == null || source.isBlank() ? "system" : source);
        notification.setOccurredAt(now);
        notification.setRead(false);
        notification.setLink(link);
        notification.setDedupeKey(dedupeKey);
        notification.setRepeatCount(1);
        return repository.save(notification);
    }

    /**
     * 알림을 기록하되 예외를 흡수합니다.
     *
     * <p>정책 푸시/검증/에이전트 처리 경로에서 호출합니다. 알림 기록이
     * 실패해도 본 작업은 성공해야 하므로 예외를 삼키고 경고만 남깁니다.
     *
     * @param category   분류
     * @param severity   심각도
     * @param title      제목
     * @param message    본문
     * @param projectKey 프로젝트 키
     * @param agentId    장치 식별자
     * @param source     발생 주체
     * @param link       상세 경로
     * @param dedupeKey  중복 방지 키
     */
    public void notifyQuietly(String category,
                              String severity,
                              String title,
                              String message,
                              String projectKey,
                              String agentId,
                              String source,
                              String link,
                              String dedupeKey) {
        try {
            notify(category, severity, title, message, projectKey, agentId, source, link, dedupeKey);
        } catch (RuntimeException ex) {
            log.warn("failed to record notification '{}': {}", title, ex.getMessage());
        }
    }

    /**
     * 최근 중복 알림을 찾습니다.
     *
     * @param dedupeKey 중복 키 (null/빈 값이면 검사하지 않음)
     * @param now       현재 시각
     * @return 합칠 대상, 없으면 null
     */
    private Notification findRecentDuplicate(String dedupeKey, java.util.Date now) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            return null;
        }
        final Optional<Notification> found = repository.findFirstByDedupeKeyOrderByOccurredAtDesc(dedupeKey);
        if (found.isEmpty()) {
            return null;
        }
        final Notification candidate = found.get();
        // 시각 컬럼이 문자열이던 시절에는 형식이 깨진 행을 방어해야 했습니다.
        // 이제 날짜 타입이므로 저장소가 돌려주는 값은 항상 유효하거나 null 입니다.
        if (candidate.getOccurredAt() != null && now != null
                && Duration.between(candidate.getOccurredAt().toInstant(), now.toInstant())
                        .compareTo(DEDUPE_WINDOW) <= 0) {
            return candidate;
        }
        return null;
    }

    /** 분류를 허용 값으로 정규화합니다. 모르는 값은 {@code SYSTEM} 으로 둡니다. */
    private String normalizeCategory(String category) {
        if (category == null) {
            return "SYSTEM";
        }
        final String upper = category.trim().toUpperCase(Locale.ROOT);
        return CATEGORIES.contains(upper) ? upper : "SYSTEM";
    }

    /** 심각도를 허용 값으로 정규화합니다. */
    private String normalizeSeverity(String severity) {
        if (severity == null) {
            return "info";
        }
        final String lower = severity.trim().toLowerCase(Locale.ROOT);
        return SEVERITIES.contains(lower) ? lower : "info";
    }

    // ------------------------------------------------------------------
    //  조회
    // ------------------------------------------------------------------

    /**
     * 필터 조합으로 알림을 조회합니다.
     *
     * @param category   분류 (null 이면 전체)
     * @param severity   심각도 (null 이면 전체)
     * @param readState  null 전체 / {@code "unread"} 안읽음 / {@code "read"} 읽음
     * @param projectKey 프로젝트 키 (null 이면 전체)
     * @param agentId    장치 식별자 (null 이면 전체)
     * @param search     검색어 (null 이면 전체)
     * @param limit      최대 건수
     * @return 알림 목록 (최신순)
     */
    @Transactional(readOnly = true)
    public List<Notification> search(String category,
                                     String severity,
                                     String readState,
                                     String projectKey,
                                     String agentId,
                                     String search,
                                     int limit) {
        final Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
        final String term = blankToNull(search);
        // ⚠️ LIKE 패턴을 Java 에서 미리 만듭니다.
        //
        // JPQL 에서 LOWER(CONCAT('%', :search, '%')) 로 쓰면, 검색어가 없을 때
        // 파라미터가 untyped null 로 바인딩되어 PostgreSQL 이 LOWER(bytea) 로
        // 해석하고 "function lower(bytea) does not exist" 로 조회가 500 이 됩니다.
        // (H2 에서는 통과하고 PostgreSQL 에서만 터지므로 놓치기 쉬운 차이입니다)
        //
        // 패턴을 먼저 만들어 넘기면 파라미터가 항상 VARCHAR 로 타이핑되고,
        // 비교 대상 컬럼에만 LOWER 가 적용됩니다.
        final String pattern = term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";
        return repository.search(
                blankToNull(category),
                blankToNull(severity),
                normalizeReadState(readState),
                blankToNull(projectKey),
                blankToNull(agentId),
                pattern,
                pageable);
    }

    /**
     * 읽음 상태 문자열을 허용 값으로 정규화합니다.
     *
     * <p>모르는 값은 {@code null}(= 전체)로 둡니다. 잘못된 필터로 결과가
     * 비어 보이는 것보다 전체를 보여주는 편이 안전합니다.
     *
     * @param readState {@code "unread"}/{@code "read"}/null
     * @return 정규화된 값
     */
    private static String normalizeReadState(String readState) {
        if (readState == null || readState.isBlank()) {
            return null;
        }
        final String lower = readState.trim().toLowerCase(Locale.ROOT);
        return ("unread".equals(lower) || "read".equals(lower)) ? lower : null;
    }

    /**
     * 안읽음 알림만 조회합니다. (드롭다운 미리보기)
     *
     * @param limit 최대 건수
     * @return 안읽음 알림 (최신순)
     */
    @Transactional(readOnly = true)
    public List<Notification> unread(int limit) {
        return repository.findByReadFalseOrderByOccurredAtDesc(
                PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    /**
     * 안읽음 개수를 셉니다. (헤더 배지용)
     *
     * @return 안읽음 건수
     */
    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByReadFalse();
    }

    // ------------------------------------------------------------------
    //  읽음 처리
    // ------------------------------------------------------------------

    /**
     * 알림 한 건을 읽음으로 표시합니다.
     *
     * @param notificationId 외부 식별자 (예: {@code NTF-3F9A21B4})
     * @return 변경된 알림
     * @throws NotificationNotFoundException 없을 때
     */
    @Transactional
    public Notification markRead(String notificationId) {
        final Notification notification = findByPublicId(notificationId);
        notification.setRead(true);
        return repository.save(notification);
    }

    /**
     * 알림 한 건을 안읽음으로 되돌립니다.
     *
     * <p>"실수로 읽음 처리했다" 를 되돌릴 수 있어야 합니다. 되돌릴 수 없으면
     * 운영자는 중요한 알림을 놓칠까 봐 읽음 처리를 미루게 됩니다.
     *
     * @param notificationId 외부 식별자
     * @return 변경된 알림
     * @throws NotificationNotFoundException 없을 때
     */
    @Transactional
    public Notification markUnread(String notificationId) {
        final Notification notification = findByPublicId(notificationId);
        notification.setRead(false);
        return repository.save(notification);
    }

    /**
     * 안읽음 알림을 모두 읽음으로 표시합니다.
     *
     * @return 변경된 건수
     */
    @Transactional
    public int markAllRead() {
        final List<Notification> unread = repository.findByReadFalseOrderByOccurredAtDesc(
                PageRequest.of(0, 1000));
        for (final Notification notification : unread) {
            notification.setRead(true);
        }
        repository.saveAll(unread);
        log.info("marked {} notifications as read", unread.size());
        return unread.size();
    }

    /**
     * 외부 식별자로 알림을 찾습니다.
     *
     * @param notificationId 외부 식별자
     * @return 알림
     * @throws NotificationNotFoundException 없을 때
     */
    @Transactional(readOnly = true)
    public Notification findByPublicId(String notificationId) {
        return repository.findByNotificationId(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    /**
     * 알림 한 건을 삭제합니다.
     *
     * @param notificationId 외부 식별자
     * @throws NotificationNotFoundException 없을 때
     */
    @Transactional
    public void delete(String notificationId) {
        final Notification notification = findByPublicId(notificationId);
        repository.delete(notification);
        log.info("notification deleted: {}", notificationId);
    }

    /**
     * 알림을 모두 삭제합니다.
     *
     * @return 삭제 건수
     */
    @Transactional
    public int deleteAll() {
        final long count = repository.count();
        repository.deleteAll();
        log.info("deleted all {} notifications", count);
        return (int) count;
    }

    // ------------------------------------------------------------------
    //  응답 변환
    // ------------------------------------------------------------------

    /**
     * 알림을 응답 맵으로 바꿉니다.
     *
     * <p>엔티티를 그대로 반환하지 않는 이유는 다른 컨트롤러와 같습니다.
     * 내부 {@code id} 와 {@code dedupeKey} 는 클라이언트가 알 필요가 없고,
     * 응답에 섞이면 화면이 그 값에 의존하게 됩니다.
     *
     * @param notifications 알림 목록
     * @return 응답 맵 목록
     */
    public static List<Map<String, Object>> toResponse(List<Notification> notifications) {
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final Notification notification : notifications) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", notification.getNotificationId());
            entry.put("category", notification.getCategory());
            entry.put("severity", notification.getSeverity());
            entry.put("title", notification.getTitle());
            entry.put("message", notification.getMessage());
            entry.put("project_id", notification.getProjectKey());
            entry.put("agent_id", notification.getAgentId());
            entry.put("source", notification.getSource());
            entry.put("occurred_at", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(notification.getOccurredAt()));
            entry.put("read", notification.isRead());
            entry.put("link", notification.getLink());
            entry.put("repeat_count", notification.getRepeatCount());
            result.add(entry);
        }
        return result;
    }

    /** 빈 문자열을 null 로 바꿉니다. (필터 미지정과 구분) */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 분류별 건수를 셉니다. (필터 탭 숫자 표시용)
     *
     * @return 분류 → 건수
     */
    @Transactional(readOnly = true)
    public Map<String, Long> countByCategory() {
        final Map<String, Long> counts = new LinkedHashMap<>();
        for (final Object[] row : repository.countByCategory()) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    /**
     * 심각도별 건수를 셉니다.
     *
     * @return 심각도 → 건수
     */
    @Transactional(readOnly = true)
    public Map<String, Long> countBySeverity() {
        final Map<String, Long> counts = new LinkedHashMap<>();
        for (final Object[] row : repository.countBySeverity()) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    /** 알림을 찾지 못했을 때 던지는 예외입니다. (404 로 매핑) */
    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class NotificationNotFoundException extends RuntimeException {
        /**
         * @param notificationId 찾지 못한 식별자
         */
        public NotificationNotFoundException(String notificationId) {
            super("notification not found: " + notificationId);
        }
    }
}
