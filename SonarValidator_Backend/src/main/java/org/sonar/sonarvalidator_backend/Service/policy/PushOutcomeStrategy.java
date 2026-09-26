package org.sonar.sonarvalidator_backend.Service.policy;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Service.notification.NotificationCategory;
import org.sonar.sonarvalidator_backend.Service.notification.NotificationSeverity;

/**
 * 정책 푸시 결과를 <b>사람이 읽는 알림</b>으로 바꿉니다.
 *
 * <h2>⚠️ 왜 전략 패턴인가 — "완료" 라고 뭉뚱그리면 안 된다</h2>
 * <p>푸시 결과는 최소 세 갈래이고, 운영자가 취할 조치가 각각 다릅니다.
 *
 * <pre>
 *   (A) 대상 없음    서브넷에 agent_id 가 없음  → 매핑하세요
 *   (B) 전달 0대     프로버가 꺼져 있음         → 장치를 켜세요
 *   (C) 일부/전부 전달                          → 강제면 심각도를 올려야 함
 * </pre>
 *
 * <p>이전에는 컨트롤러에 {@code if/else if/else} 세 갈래가 있었고, 각 갈래가
 * {@code notifyQuietly} 를 <b>여덟 인자</b>로 호출했습니다. 갈래를 하나 더
 * 넣으려면 170줄짜리 {@code push} 안에서 비슷한 호출을 복사해야 했습니다.
 *
 * <p>지금은 결과 하나가 구현체 하나입니다. {@link #notification} 이
 * {@code Optional.empty()} 를 돌려주면 알림을 만들지 않습니다 — 성공 케이스에
 * 알림을 끄고 싶을 때 구현체만 바꾸면 됩니다.
 *
 * <h2>⚠️ 심각도를 결과가 정한다</h2>
 * <p>호출자가 심각도를 넘기면 "강제 전송은 warning" 같은 규칙이 호출부로
 * 새어 나갑니다. 위반이 남은 채로 정책이 내려갔다는 사실은 <b>결과의
 * 성질</b>이므로 여기서 정합니다.
 */
public interface PushOutcomeStrategy {

    /**
     * 이 결과에 해당하는지 판단합니다.
     *
     * @param context 푸시 결과 요약
     * @return 해당하면 true
     */
    boolean matches(PushOutcome context);

    /** @return 로그·진단용 결과 이름 */
    String name();

    /**
     * 이 결과에 대해 남길 알림입니다.
     *
     * @param context 푸시 결과 요약
     * @return 알림 (남기지 않으면 empty)
     */
    Optional<Notification> notification(PushOutcome context);

    /**
     * 푸시 결과 요약입니다.
     *
     * <h2>⚠️ 왜 컨트롤러의 지역 변수를 그대로 안 쓰는가</h2>
     * <p>{@code push} 안에는 {@code deliveries} · {@code sent} · {@code skipped} ·
     * {@code force} · {@code report} 가 흩어져 있습니다. 전략이 이들을
     * 각자 읽으면 "무엇이 결과를 결정하는가" 가 다시 흩어집니다.
     * 한 객체로 모아 두면 전략의 판단 조건이 시그니처에 드러납니다.
     *
     * @param projectId      프로젝트 키
     * @param projectName    프로젝트 이름 (알림 제목용)
     * @param targetCount    배정된 서브넷 중 전송 시도한 수
     * @param deliveredCount 실제 전송 성공 수
     * @param skippedAgents  격리로 제외한 Agent 식별자
     * @param violationCount 남은 망분리 위반 건수
     * @param forced         위반이 남은 채로 강제 전송했는지
     */
    record PushOutcome(String projectId,
                       String projectName,
                       int targetCount,
                       int deliveredCount,
                       List<String> skippedAgents,
                       int violationCount,
                       boolean forced) {

        /** @return 전송 대상이 아예 없었는지 */
        public boolean hasNoTargets() {
            return targetCount == 0;
        }

        /** @return 대상은 있었지만 한 대도 못 보냈는지 */
        public boolean deliveredNothing() {
            return targetCount > 0 && deliveredCount == 0;
        }

        /** @return 최소 한 대 이상 전달됐는지 */
        public boolean deliveredSomething() {
            return deliveredCount > 0;
        }

        /** @return 격리 때문에 제외된 장치가 있는지 */
        public boolean hasSkipped() {
            return skippedAgents != null && !skippedAgents.isEmpty();
        }
    }

    /**
     * 알림 한 건의 내용입니다. ({@code notifyQuietly} 의 여덟 인자를 대신)
     *
     * <p>⚠️ 인자 여덟 개를 순서대로 넘기던 호출을 값 객체로 바꿉니다.
     * 모두 {@code String} 이라 순서를 바꿔도 컴파일러가 막지 못했고,
     * 실제로 링크와 출처를 바꿔 넣으면 <b>클릭하면 엉뚱한 화면</b>이 열립니다.
     *
     * @param severity   심각도 (문자열 아님 — 오타가 컴파일 오류가 됨)
     * @param category   분류
     * @param title      제목
     * @param message    본문
     * @param agentId    Agent 식별자 (없으면 null)
     * @param source     발생 주체
     * @param link       상세 화면 경로
     * @param dedupeKey  중복 방지 키 (없으면 null = 중복 검사 안 함)
     */
    record Notification(NotificationSeverity severity,
                        NotificationCategory category,
                        String title,
                        String message,
                        String agentId,
                        String source,
                        String link,
                        String dedupeKey) {

        /**
         * 정책 관련 알림을 만듭니다. (분류·출처·링크가 늘 같으므로 고정)
         *
         * @param severity  심각도
         * @param title     제목
         * @param message   본문
         * @param agentId   Agent 식별자 (없으면 null)
         * @param projectId 프로젝트 키 (링크 생성용)
         * @param linkPath  상세 경로
         * @param dedupeKey 중복 방지 키
         * @return 알림
         */
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