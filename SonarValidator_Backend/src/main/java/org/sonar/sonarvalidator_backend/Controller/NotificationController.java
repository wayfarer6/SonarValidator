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

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

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

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("unread", notificationService.unreadCount());
        body.put("by_category", notificationService.countByCategory());
        body.put("by_severity", notificationService.countBySeverity());
        return body;
    }

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

    @PatchMapping("/{notificationId}/read")
    public Map<String, Object> markRead(@PathVariable String notificationId) {
        final Notification notification = notificationService.markRead(notificationId);
        return readResponse(notification);
    }

    @PatchMapping("/{notificationId}/unread")
    public Map<String, Object> markUnread(@PathVariable String notificationId) {
        final Notification notification = notificationService.markUnread(notificationId);
        return readResponse(notification);
    }

    @PostMapping("/read-all")
    public Map<String, Object> markAllRead() {
        final int changed = notificationService.markAllRead();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("changed", changed);
        body.put("unread", notificationService.unreadCount());
        return body;
    }

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

    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> deleteAll() {
        final int deleted = notificationService.deleteAll();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", deleted);
        body.put("unread", 0);
        return body;
    }

    private Map<String, Object> readResponse(Notification notification) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("notification", NotificationService.toResponse(List.of(notification)).get(0));
        body.put("unread", notificationService.unreadCount());
        return body;
    }
}
