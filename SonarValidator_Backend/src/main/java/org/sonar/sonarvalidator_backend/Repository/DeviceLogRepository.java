package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.DeviceLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 장비 로그 저장소입니다.
 *
 * <h2>왜 파생 쿼리 대신 {@code @Query} 를 쓰는가</h2>
 * <p>로그 조회는 <b>필터 조합</b>이 많습니다(장비/프로젝트/기간/심각도/검색어).
 * 모든 조합을 파생 쿼리 메서드로 만들면 수십 개가 필요하고, 조합이 하나
 * 늘 때마다 메서드를 추가해야 합니다.
 *
 * <p>그래서 <b>null 허용 파라미터</b>를 받는 한 개의 쿼리로 통일합니다.
 * {@code (:param IS NULL OR ...)} 패턴으로 조건을 선택적으로 적용합니다.
 * 인덱스({@code agent_id, logged_at})를 타도록 조건 순서도 그에 맞춥니다.
 *
 * <h2>⚠️ 심각도 필터의 방향</h2>
 * <p>syslog 는 <b>숫자가 낮을수록 심각</b>합니다. 그래서 "warning 이상" 은
 * {@code severity_num <= 4} 입니다. 부등호 방향을 반대로 쓰면 정반대 결과가
 * 나오는데, 결과가 그럴듯해 보여 알아채기 어렵습니다.
 */
public interface DeviceLogRepository extends JpaRepository<DeviceLog, Long> {

    /**
     * 필터 조합으로 로그를 최신순 조회합니다.
     *
     * <p>모든 파라미터는 null 을 허용하며, null 이면 그 조건을 적용하지 않습니다.
     *
     * @param agentId       장비 식별자 (null 이면 전체)
     * @param projectKey    프로젝트 키 (null 이면 전체)
     * @param from          기간 시작 ISO-8601 (null 이면 제한 없음)
     * @param to            기간 끝 ISO-8601 (null 이면 제한 없음)
     * @param maxSeverity   최대 심각도 번호 (0~7). "warning 이상"이면 4
     * @param search        본문/원문 부분 일치 검색어 (null 이면 전체)
     * @param highlightedOnly true 면 사용자가 표시한 로그만
     * @param pageable      페이지 크기 제한
     * @return 로그 목록 (최신순)
     */
    @Query("""
            SELECT l FROM DeviceLog l
            WHERE (:agentId IS NULL OR l.agentId = :agentId)
              AND (:projectKey IS NULL OR l.projectKey = :projectKey)
              AND (:from IS NULL OR l.loggedAt >= :from)
              AND (:to IS NULL OR l.loggedAt <= :to)
              AND (:maxSeverity IS NULL OR l.severityNum <= :maxSeverity)
              AND (:search IS NULL
                   OR LOWER(l.message) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(l.raw) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:highlightedOnly = FALSE OR l.highlighted = TRUE)
            ORDER BY l.loggedAt DESC, l.id DESC
            """)
    List<DeviceLog> search(@Param("agentId") String agentId,
                           @Param("projectKey") String projectKey,
                           @Param("from") String from,
                           @Param("to") String to,
                           @Param("maxSeverity") Integer maxSeverity,
                           @Param("search") String search,
                           @Param("highlightedOnly") boolean highlightedOnly,
                           Pageable pageable);

    /**
     * 위와 같은 조건의 전체 건수를 셉니다.
     *
     * <p>화면에 "N건 중 M건 표시" 를 보여주려면 총계가 필요합니다.
     * 목록 조회에 페이지 제한이 걸려 있어 {@code size()} 로는 알 수 없습니다.
     *
     * @param agentId       장비 식별자
     * @param projectKey    프로젝트 키
     * @param from          기간 시작
     * @param to            기간 끝
     * @param maxSeverity   최대 심각도 번호
     * @param search        검색어
     * @param highlightedOnly 표시된 로그만
     * @return 조건에 맞는 전체 건수
     */
    @Query("""
            SELECT COUNT(l) FROM DeviceLog l
            WHERE (:agentId IS NULL OR l.agentId = :agentId)
              AND (:projectKey IS NULL OR l.projectKey = :projectKey)
              AND (:from IS NULL OR l.loggedAt >= :from)
              AND (:to IS NULL OR l.loggedAt <= :to)
              AND (:maxSeverity IS NULL OR l.severityNum <= :maxSeverity)
              AND (:search IS NULL
                   OR LOWER(l.message) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(l.raw) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:highlightedOnly = FALSE OR l.highlighted = TRUE)
            """)
    long countMatching(@Param("agentId") String agentId,
                       @Param("projectKey") String projectKey,
                       @Param("from") String from,
                       @Param("to") String to,
                       @Param("maxSeverity") Integer maxSeverity,
                       @Param("search") String search,
                       @Param("highlightedOnly") boolean highlightedOnly);

    /**
     * 지문으로 기존 로그를 찾습니다. (중복 수집 방지)
     *
     * @param fingerprint 로그 지문
     * @return 기존 로그 (없으면 빈 값)
     */
    Optional<DeviceLog> findByFingerprint(String fingerprint);

    /**
     * 심각도별 건수를 집계합니다. (대시보드 요약용)
     *
     * @return {@code [severityNum, count]} 배열 목록
     */
    @Query("""
            SELECT l.severityNum, COUNT(l) FROM DeviceLog l
            GROUP BY l.severityNum
            ORDER BY l.severityNum
            """)
    List<Object[]> countBySeverity();

    /**
     * 장비별 로그 건수를 집계합니다. (필터 드롭다운의 건수 표시용)
     *
     * @return {@code [agentId, count]} 배열 목록
     */
    @Query("""
            SELECT l.agentId, COUNT(l) FROM DeviceLog l
            GROUP BY l.agentId
            ORDER BY COUNT(l) DESC
            """)
    List<Object[]> countByAgent();

    /**
     * 로그에 등장한 장비 식별자 목록입니다. (필터 드롭다운용)
     *
     * @return 장비 식별자
     */
    @Query("SELECT DISTINCT l.agentId FROM DeviceLog l ORDER BY l.agentId")
    List<String> distinctAgentIds();

    /** 오래된 로그를 정리할 때 씁니다. */
    List<DeviceLog> findByLoggedAtBefore(String cutoff);
}
