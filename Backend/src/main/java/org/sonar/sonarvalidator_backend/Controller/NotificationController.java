package org.sonar.sonarvalidator_backend.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sonar.sonarvalidator_backend.Model.entity.Notification;
import org.sonar.sonarvalidator_backend.Service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 조회/읽음 처리 API 입니다.
 *
 * <h2>프론트엔드 흐름과의 대응</h2>
 * <pre>
 *   헤더 종 아이콘        → GET  /api/v1/notifications?limit=5   (미리보기)
 *   안읽음 배지          → GET  /api/v1/notifications/summary    (개수만)
 *   View Notification    → GET  /api/v1/notifications            (필터 목록)
 *   항목 클릭            → PATCH /api/v1/notifications/{id}/read
 *   모두 읽음            → POST /api/v1/notifications/read-all
 * </pre>
 *
 * <h2>왜 summary 를 따로 두는가</h2>
 * <p>배지는 <b>모든 페이지에서</b> 보입니다. 목록 전체를 받아 프론트에서
 * 세면 페이지를 열 때마다 수십 KB 를 낭비합니다. 개수만 돌려주는 가벼운
 * 엔드포인트를 둡니다.
 *
 * <h2>읽음 처리를 PATCH 로 하는 이유</h2>
 * <p>"읽음" 은 리소스의 <b>일부 필드만 바꾸는</b> 동작입니다. PUT 은 전체
 * 교체를 뜻하므로 제목/본문을 함께 보내야 하는 것처럼 오해됩니다.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * @param notificationService 알림 서비스
     */
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 알림을 필터 조합으로 조회합니다.
     *
     * @param category   분류 ({@code POLICY}/{@code AGENT}/{@code PROJECT}/{@code SECURITY}/{@code SYSTEM})
     * @param severity   심각도 ({@code critical}/{@code warning}/{@code info})
     * @param readState  {@code "unread"} 안읽음만, {@code "read"} 읽음만, 생략하면 전체
     * @param projectId  프로젝트 키
     * @param agentId    장치 식별자
     * @param search     제목/본문 검색어
     * @param limit      최대 건수 (기본 100)
     * @return {@code {"total": n, "unread": n, "notifications": [...]}}
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "read_state", required = false) String readState,
            @RequestParam(value = "unread_only", required = false) Boolean unreadOnly,
            @RequestParam(value = "project_id", required = false) String projectId,
            @RequestParam(value = "agent_id", required = false) String agentId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {

        // unread_only 는 read_state 의 단축 표기입니다. 두 파라미터를 모두
        // 지원하는 이유: 화면(체크박스)은 unread_only 가 자연스럽고,
        // 3상태("읽음만" 포함)를 표현할 때는 read_state 가 필요합니다.
        //
        // ⚠️ unread_only=true 를 그대로 "읽음 여부" 로 쓰면 의미가 뒤집힙니다.
        // (true = 읽음, 이 아니라 true = 안읽음만) 그래서 문자열로 변환해 넘깁니다.
        final String effectiveReadState = readState != null && !readState.isBlank()
                ? readState
                : (unreadOnly == null ? null : (unreadOnly ? "unread" : "read"));

        final List<Notification> notifications = notificationService.search(
                category, severity, effectiveReadState, projectId, agentId, search, limit);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", notifications.size());
        body.put("unread", notificationService.unreadCount());
        body.put("notifications", NotificationService.toResponse(notifications));
        return body;
    }

    /**
     * 알림 요약(안읽음 개수 + 분류/심각도별 건수)을 반환합니다.
     *
     * <p>배지와 필터 탭의 숫자를 한 번에 받아갑니다. 탭마다 요청을 보내면
     * 화면을 열 때 요청이 5~6개가 됩니다.
     *
     * @return 요약 정보
     */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("unread", notificationService.unreadCount());
        body.put("by_category", notificationService.countByCategory());
        body.put("by_severity", notificationService.countBySeverity());
        return body;
    }

    /**
     * 안읽음 알림만 최신순으로 조회합니다. (헤더 드롭다운 미리보기)
     *
     * @param limit 최대 건수 (기본 5)
     * @return {@code {"total": n, "notifications": [...]}}
     */
    @GetMapping("/unread")
    public Map<String, Object> unread(
            @RequestParam(value = "limit", defaultValue = "5") int limit) {

        final List<Notification> notifications = notificationService.unread(limit);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", notifications.size());
        body.put("unread", notificationService.unreadCount());
        body.put("notifications", NotificationService.toResponse(notifications));
        return body;
    }

    /**
     * 알림 한 건을 읽음으로 표시합니다.
     *
     * @param notificationId 외부 식별자
     * @return 변경된 알림과 남은 안읽음 개수
     */
    @PatchMapping("/{notificationId}/read")
    public Map<String, Object> markRead(@PathVariable String notificationId) {
        final Notification notification = notificationService.markRead(notificationId);
        return readResponse(notification);
    }

    /**
     * 알림 한 건을 안읽음으로 되돌립니다.
     *
     * @param notificationId 외부 식별자
     * @return 변경된 알림과 남은 안읽음 개수
     */
    @PatchMapping("/{notificationId}/unread")
    public Map<String, Object> markUnread(@PathVariable String notificationId) {
        final Notification notification = notificationService.markUnread(notificationId);
        return readResponse(notification);
    }

    /**
     * 안읽음 알림을 모두 읽음으로 표시합니다.
     *
     * @return 변경된 건수와 남은 안읽음 개수(항상 0)
     */
    @PostMapping("/read-all")
    public Map<String, Object> markAllRead() {
        final int changed = notificationService.markAllRead();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("changed", changed);
        body.put("unread", notificationService.unreadCount());
        return body;
    }

    /**
     * 알림 한 건을 삭제합니다.
     *
     * <p>알림은 <b>이력</b>이라 원칙적으로 남기는 편이 좋지만, 잘못된 알림이나
     * 테스트로 만든 알림이 목록을 어지럽히면 지울 수 있어야 합니다.
     * (삭제 감사 기록은 별도로 남기지 않습니다 — 알림 자체가 이력이므로)
     *
     * @param notificationId 외부 식별자
     * @return 삭제 결과
     */
    @DeleteMapping("/{notificationId}")
    public Map<String, Object> delete(@PathVariable String notificationId) {
        // 없는 알림을 지우려 하면 404 를 냅니다. (조용히 성공하면 오타를 못 찾습니다)
        notificationService.findByPublicId(notificationId);
        notificationService.delete(notificationId);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", true);
        body.put("id", notificationId);
        body.put("unread", notificationService.unreadCount());
        return body;
    }

    /**
     * 알림을 모두 삭제합니다.
     *
     * @return 삭제 건수
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> deleteAll() {
        final int deleted = notificationService.deleteAll();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", deleted);
        body.put("unread", 0);
        return body;
    }

    /** 읽음/안읽음 처리의 공통 응답을 만듭니다. */
    private Map<String, Object> readResponse(Notification notification) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("notification", NotificationService.toResponse(List.of(notification)).get(0));
        body.put("unread", notificationService.unreadCount());
        return body;
    }
}
