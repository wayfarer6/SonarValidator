package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 알림 저장소입니다.
 *
 * <h2>왜 {@code @Query} 한 개로 통일했는가</h2>
 * <p>알림 조회는 필터 조합이 많습니다(분류/심각도/읽음/프로젝트/장치/검색어).
 * 파생 쿼리로 만들면 조합마다 메서드가 필요합니다. {@code device_log} 와 같은
 * 방식으로 <b>null 허용 파라미터</b>를 받는 쿼리 하나를 씁니다.
 *
 * <h2>⚠️ 읽음 필터의 3상태</h2>
 * <p>"전체 / 안읽음만 / 읽음만" 세 가지를 문자열 하나로 표현합니다.
 * <ul>
 *   <li>{@code readState = null} — 둘 다 (전체)</li>
 *   <li>{@code readState = "unread"} — 안읽음만</li>
 *   <li>{@code readState = "read"} — 읽음만</li>
 * </ul>
 *
 * <p>⚠️ {@link Boolean} 으로 받으면 <b>의미가 뒤집히기 쉽습니다.</b> 실제로
 * {@code unread_only=true} 를 그대로 {@code n.read = :readState} 에 넘겨
 * "안읽음만" 요청이 <b>읽은 것만</b> 돌려주는 버그가 있었습니다. 두 값이
 * 모두 boolean 이라 컴파일러가 잡아주지 않습니다.
 *
 * <p>그래서 화면의 선택지와 같은 <b>문자열</b>로 받고, 비교 대상
 * ({@code n.read})에는 리터럴 {@code FALSE}/{@code TRUE} 만 씁니다. 파라미터
 * 타입 추론 문제도 함께 피할 수 있습니다.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 필터 조합으로 알림을 최신순 조회합니다.
     *
     * <p>모든 파라미터는 null 을 허용하며, null 이면 그 조건을 적용하지 않습니다.
     *
     * @param category    분류 ({@code POLICY}/{@code AGENT} …), null 이면 전체
     * @param severity    심각도, null 이면 전체
     * @param readState   null 전체 / {@code "unread"} 안읽음 / {@code "read"} 읽음
     * @param projectKey  프로젝트 키, null 이면 전체
     * @param agentId     장치 식별자, null 이면 전체
     * @param searchPattern 이미 소문자로 만든 LIKE 패턴 (예: {@code %timeout%}), null 이면 전체
     * @param pageable    페이지 크기 제한
     * @return 알림 목록 (최신순)
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE (:category IS NULL OR n.category = :category)
              AND (:severity IS NULL OR n.severity = :severity)
              AND (:readState IS NULL
                   OR (:readState = 'unread' AND n.read = FALSE)
                   OR (:readState = 'read' AND n.read = TRUE))
              AND (:projectKey IS NULL OR n.projectKey = :projectKey)
              AND (:agentId IS NULL OR n.agentId = :agentId)
              AND (:searchPattern IS NULL
                   OR LOWER(n.title) LIKE :searchPattern
                   OR LOWER(n.message) LIKE :searchPattern)
            ORDER BY n.occurredAt DESC, n.id DESC
            """)
    List<Notification> search(@Param("category") String category,
                              @Param("severity") String severity,
                              @Param("readState") String readState,
                              @Param("projectKey") String projectKey,
                              @Param("agentId") String agentId,
                              @Param("searchPattern") String searchPattern,
                              Pageable pageable);

    /**
     * 안읽음 알림 개수를 셉니다. (헤더 배지용)
     *
     * @return 안읽음 건수
     */
    long countByReadFalse();

    /**
     * 안읽음 알림만 최신순으로 조회합니다. (드롭다운 미리보기용)
     *
     * @param pageable 페이지 크기 제한
     * @return 안읽음 알림 목록
     */
    List<Notification> findByReadFalseOrderByOccurredAtDesc(Pageable pageable);

    /**
     * 중복 방지 키로 가장 최근 알림을 찾습니다.
     *
     * <p>같은 원인이 반복될 때 새로 만들지 않고 횟수만 올리기 위해 씁니다.
     *
     * <p>⚠️ {@code Pageable} 을 함께 받으면 안 됩니다. Spring Data 는
     * {@code Pageable} 이 붙은 메서드의 반환형으로 {@code Optional} 을 허용하지
     * 않습니다({@code List}/{@code Page}/{@code Slice} 만 가능). 기동 시
     * 리포지토리 프록시 생성이 실패하므로, {@code findFirst} 파생 쿼리로
     * 한 건만 받습니다.
     *
     * @param dedupeKey 중복 방지 키
     * @return 가장 최근 알림 (없으면 빈 값)
     */
    Optional<Notification> findFirstByDedupeKeyOrderByOccurredAtDesc(String dedupeKey);

    /**
     * 외부 식별자로 알림을 찾습니다.
     *
     * <p>스캔한 행에서 {@code findAll()} 로 찾으면 알림이 쌓일수록 느려집니다.
     * 식별자는 {@code UNIQUE} 이므로 인덱스 조회로 처리합니다.
     *
     * @param notificationId 외부 식별자 (예: {@code NTF-3F9A21B4})
     * @return 알림 (없으면 빈 값)
     */
    Optional<Notification> findByNotificationId(String notificationId);

    /** 분류별 건수 (필터 탭의 숫자 표시용). */
    @Query("SELECT n.category, COUNT(n) FROM Notification n GROUP BY n.category")
    List<Object[]> countByCategory();

    /** 심각도별 건수. */
    @Query("SELECT n.severity, COUNT(n) FROM Notification n GROUP BY n.severity")
    List<Object[]> countBySeverity();
}
