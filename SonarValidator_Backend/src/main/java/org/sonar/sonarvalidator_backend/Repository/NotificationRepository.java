package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

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

    long countByReadFalse();

    List<Notification> findByReadFalseOrderByOccurredAtDesc(Pageable pageable);

    Optional<Notification> findFirstByDedupeKeyOrderByOccurredAtDesc(String dedupeKey);

    Optional<Notification> findByNotificationId(String notificationId);

    @Query("SELECT n.category, COUNT(n) FROM Notification n GROUP BY n.category")
    List<Object[]> countByCategory();

    @Query("SELECT n.severity, COUNT(n) FROM Notification n GROUP BY n.severity")
    List<Object[]> countBySeverity();
}
