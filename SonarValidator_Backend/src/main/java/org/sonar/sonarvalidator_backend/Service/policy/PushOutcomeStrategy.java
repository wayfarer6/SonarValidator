package org.sonar.sonarvalidator_backend.Service.policy;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Service.notification.NotificationCategory;
import org.sonar.sonarvalidator_backend.Service.notification.NotificationSeverity;

public interface PushOutcomeStrategy {

    boolean matches(PushOutcome context);

    String name();

    Optional<Notification> notification(PushOutcome context);

    record PushOutcome(String projectId,
                       String projectName,
                       int targetCount,
                       int deliveredCount,
                       List<String> skippedAgents,
                       int violationCount,
                       boolean forced) {

        public boolean hasNoTargets() {
            return targetCount == 0;
        }

        public boolean deliveredNothing() {
            return targetCount > 0 && deliveredCount == 0;
        }

        public boolean deliveredSomething() {
            return deliveredCount > 0;
        }

        public boolean hasSkipped() {
            return skippedAgents != null && !skippedAgents.isEmpty();
        }
    }

    record Notification(NotificationSeverity severity,
                        NotificationCategory category,
                        String title,
                        String message,
                        String agentId,
                        String source,
                        String link,
                        String dedupeKey) {

        public static Notification policy(NotificationSeverity severity,
                                          String title,
                                          String message,
                                          String agentId,
                                          String projectId,
                                          String linkPath,
                                          String dedupeKey) {
            return new Notification(severity, NotificationCategory.POLICY, title,
                    message, agentId, "system", linkPath, dedupeKey);
        }
    }
}
