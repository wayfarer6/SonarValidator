package org.sonar.sonarvalidator_backend.Service.policy;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Service.notification.NotificationSeverity;

/**
 * 정책 푸시 결과 전략 4개를 한 파일에 모았습니다.
 *
 * <h2>⚠️ 순서가 곧 우선순위다</h2>
 * <p>{@link PushOutcomes} 가 <b>앞에서부터</b> 첫 매칭을 씁니다. 그래서
 * "대상 없음" 이 "전달 0대" 보다 먼저 와야 합니다.
 * <pre>
 *   대상 0대  →  hasNoTargets()   와  deliveredNothing()  이 <b>둘 다</b> 참? 
 *                 hasNoTargets 는 targetCount == 0
 *                 deliveredNothing 은 targetCount &gt; 0 조건이 있어 거짓
 * </pre>
 * 조건을 이렇게 갈라 둔 덕분에 순서가 바뀌어도 결과가 달라지지 않습니다.
 * (순서에 의존하는 전략 목록은 새 전략을 끼워 넣을 때 조용히 깨집니다)
 *
 * <h2>⚠️ 조용한 제외를 조용히 두지 않는다</h2>
 * <p>격리된 장치를 푸시에서 빼는 것은 <b>맞는 동작</b>이지만, 운영자가
 * 모르면 "푸시했는데 왜 안 되지" 가 됩니다. 그래서 제외가 있었으면
 * 결과와 무관하게 <b>별도 알림</b>을 남깁니다 — 이는 결과 분류가 아니라
 * 부가 정보이므로 {@link SkipNotice} 로 따로 둡니다.
 */
final class PushOutcomes {

    private PushOutcomes() {
    }

    /**
     * 격리로 제외된 장치가 있을 때 남기는 알림입니다.
     *
     * <p>결과 분류와 <b>독립</b>입니다. 전달이 성공했든 실패했든, 제외된
     * 장치가 있으면 알려야 합니다.
     */
    static final class SkipNotice {

        /** @param context 푸시 결과 @return 남길 알림 (제외가 없으면 empty) */
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

    /**
     * (A) 전송 대상이 아예 없었습니다.
     *
     * <p>가장 흔한 초기 상태입니다 — 프로젝트를 만들었지만 아직 서브넷에
     * Agent 를 매핑하지 않았습니다. "푸시 완료" 로 보이면 안 됩니다.
     */
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

    /**
     * (B) 대상은 있었지만 <b>한 대도</b> 전달되지 않았습니다.
     *
     * <p>서버는 보냈다고 생각했는데 장치가 없습니다. 격리와 달리 이건
     * <b>장애</b>이므로 서버가 반드시 알려야 합니다.
     */
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

    /**
     * (C) 최소 한 대에 전달되었습니다.
     *
     * <h2>⚠️ 강제 전송은 심각도를 올린다</h2>
     * <p>위반이 남은 채로 정책이 내려갔습니다. "완료" 로만 보이면 운영자는
     * 위반을 고쳤다고 오해합니다. 그래서 {@code warning} 으로 올리고
     * <b>제목에도</b> "강제" 를 넣습니다.
     *
     * <p>{@code dedupeKey} 를 null 로 두는 이유: 성공 알림은 정보성이고,
     * 같은 프로젝트에 반복 푸시하는 것이 정상 운영입니다. 중복으로 합치면
     * "언제 마지막으로 푸시했나" 를 잃습니다.
     */
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

    /** 등록 순서대로 시도할 기본 목록입니다. (조건이 겹치지 않게 설계됨) */
    static List<PushOutcomeStrategy> defaults() {
        return List.of(new NoTargets(), new NothingDelivered(), new Delivered());
    }
}