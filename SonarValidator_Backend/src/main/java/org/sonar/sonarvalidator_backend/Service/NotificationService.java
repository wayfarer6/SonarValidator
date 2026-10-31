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
import org.sonar.sonarvalidator_backend.Service.notification.NotificationCategory;
import org.sonar.sonarvalidator_backend.Service.notification.NotificationFilter;
import org.sonar.sonarvalidator_backend.Service.notification.NotificationSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final Duration DEDUPE_WINDOW = Duration.ofMinutes(5);

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    //  기록

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

    private String normalizeCategory(String category) {
        return NotificationCategory.parseForWrite(category).value();
    }

    private String normalizeSeverity(String severity) {
        return NotificationSeverity.parseForWrite(severity).value();
    }

    //  조회

    @Transactional(readOnly = true)
    public List<Notification> search(NotificationFilter filter) {
        final NotificationFilter f =
                (filter == null) ? NotificationFilter.recent(NotificationFilter.DEFAULT_LIMIT) : filter;
        final Pageable pageable = PageRequest.of(0, f.limit());
        return repository.search(
                f.categoryValue(),
                f.severityValue(),
                f.readState(),
                f.projectKey(),
                f.agentId(),



                f.likePattern(),
                pageable);
    }

    @Transactional(readOnly = true)
    public List<Notification> search(String category,
                                     String severity,
                                     String readState,
                                     String projectKey,
                                     String agentId,
                                     String search,
                                     int limit) {
        return search(NotificationFilter.of(
                category, severity, readState, projectKey, agentId, search, limit));
    }

    @Transactional(readOnly = true)
    public List<Notification> unread(int limit) {
        return repository.findByReadFalseOrderByOccurredAtDesc(
                PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByReadFalse();
    }

    //  읽음 처리

    @Transactional
    public Notification markRead(String notificationId) {
        final Notification notification = findByPublicId(notificationId);
        notification.setRead(true);
        return repository.save(notification);
    }

    @Transactional
    public Notification markUnread(String notificationId) {
        final Notification notification = findByPublicId(notificationId);
        notification.setRead(false);
        return repository.save(notification);
    }

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

    @Transactional(readOnly = true)
    public Notification findByPublicId(String notificationId) {
        return repository.findByNotificationId(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    @Transactional
    public void delete(String notificationId) {
        final Notification notification = findByPublicId(notificationId);
        repository.delete(notification);
        log.info("notification deleted: {}", notificationId);
    }

    @Transactional
    public int deleteAll() {
        final long count = repository.count();
        repository.deleteAll();
        log.info("deleted all {} notifications", count);
        return (int) count;
    }

    //  응답 변환

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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countByCategory() {
        final Map<String, Long> counts = new LinkedHashMap<>();
        for (final Object[] row : repository.countByCategory()) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countBySeverity() {
        final Map<String, Long> counts = new LinkedHashMap<>();
        for (final Object[] row : repository.countBySeverity()) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class NotificationNotFoundException extends RuntimeException {
        public NotificationNotFoundException(String notificationId) {
            super("notification not found: " + notificationId);
        }
    }
}
