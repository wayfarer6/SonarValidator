package org.sonar.sonarvalidator_backend.Service.policy;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Service.notification.NotificationSeverity;

final class PushOutcomes {

    private PushOutcomes() {
    }

    static final class SkipNotice {

        Optional<PushOutcomeStrategy.Notification> notification(
                PushOutcomeStrategy.PushOutcome context) {
            if (!context.hasSkipped()) {
                return Optional.empty();
            }
            return Optional.of(PushOutcomeStrategy.Notification.policy(
                    NotificationSeverity.WARNING,
                    "격리된 장치 제외: " + context.projectName(),
                    "격리 중인 장치 " + String.join(", ", context.skippedAgents())
                            + " 를 정책 푸시 대상에서 제외했습니다. 격리를 먼저 해제하세요.",
                    null,
                    context.projectId(),
                    "/agent",
                    "policy-push-quarantined:" + context.projectId()));
        }
    }

    static final class NoTargets implements PushOutcomeStrategy {

        @Override
        public boolean matches(PushOutcomeStrategy.PushOutcome context) {
            return context.hasNoTargets();
        }

        @Override
        public String name() {
            return "no-targets";
        }

        @Override
        public Optional<PushOutcomeStrategy.Notification> notification(
                PushOutcomeStrategy.PushOutcome context) {
            return Optional.of(PushOutcomeStrategy.Notification.policy(
                    NotificationSeverity.WARNING,
                    "정책 푸시 대상 없음: " + context.projectName(),
                    "서브넷에 연결된 Agent 가 없어 아무 장치에도 전달되지 않았습니다. "
                            + "Agent 배포 후 서브넷에 매핑하세요.",
                    null,
                    context.projectId(),
                    "/project/editor/" + context.projectId(),
                    "policy-push-no-target:" + context.projectId()));
        }
    }

    static final class NothingDelivered implements PushOutcomeStrategy {

        @Override
        public boolean matches(PushOutcomeStrategy.PushOutcome context) {
            return context.deliveredNothing();
        }

        @Override
        public String name() {
            return "nothing-delivered";
        }

        @Override
        public Optional<PushOutcomeStrategy.Notification> notification(
                PushOutcomeStrategy.PushOutcome context) {
            return Optional.of(PushOutcomeStrategy.Notification.policy(
                    NotificationSeverity.WARNING,
                    "정책 푸시 실패: " + context.projectName(),
                    "대상 " + context.targetCount() + "대 중 0대에 전달되었습니다. "
                            + "장치의 프로버가 연결되어 있는지 확인하세요.",
                    null,
                    context.projectId(),
                    "/project/editor/" + context.projectId(),
                    "policy-push-none-delivered:" + context.projectId()));
        }
    }

    static final class Delivered implements PushOutcomeStrategy {

        @Override
        public boolean matches(PushOutcomeStrategy.PushOutcome context) {
            return context.deliveredSomething();
        }

        @Override
        public String name() {
            return "delivered";
        }

        @Override
        public Optional<PushOutcomeStrategy.Notification> notification(
                PushOutcomeStrategy.PushOutcome context) {
            final boolean forced = context.forced();
            return Optional.of(PushOutcomeStrategy.Notification.policy(
                    forced ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
                    "정책 푸시 " + (forced ? "강제 완료: " : "완료: ") + context.projectName(),
                    "대상 " + context.targetCount() + "대 중 " + context.deliveredCount()
                            + "대에 전달되었습니다."
                            + (forced ? " (망분리 위반이 남은 상태로 강제 전송됨)" : ""),
                    null,
                    context.projectId(),
                    "/project/editor/" + context.projectId(),
                    null));
        }
    }

    static List<PushOutcomeStrategy> defaults() {
        return List.of(new NoTargets(), new NothingDelivered(), new Delivered());
    }
}
