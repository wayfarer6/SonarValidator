package org.sonar.sonarvalidator_backend.Service.policy;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 정책 푸시 결과를 알림으로 바꿔 주는 <b>결과 분류기</b>입니다.
 *
 * <h2>⚠️ 컨트롤러에서 분기를 걷어낸다</h2>
 * <p>{@code PolicyManagement.push} 는 170줄이었고, 그 끝에 결과 분기
 * {@code if/else if/else} 와 {@code notifyQuietly} 여덟 인자 호출 네 번이
 * 있었습니다. 컨트롤러가 HTTP 계층이면서 <b>"어떤 결과에 어떤 문구를
 * 남길 것인가"</b> 라는 정책을 갖고 있었습니다.
 *
 * <p>지금 컨트롤러는 결과 요약을 만들어 넘기고, 이 클래스가 알림을
 * 만듭니다. 문구를 고치려면 이 파일만 열면 됩니다.
 *
 * <h2>⚠️ 분류 실패를 조용히 넘긴다</h2>
 * <p>어떤 전략도 매칭되지 않으면 알림을 만들지 않습니다. 예외를 던지면
 * <b>푸시 자체가 실패로 보입니다</b> — 정책은 이미 장치에 전달됐는데
 * 서버가 500 을 돌려주는 상황이 됩니다. 알림은 부가 정보이므로 실패해도
 * 본 작업을 막지 않아야 합니다.
 *
 * <h2>⚠️ 제외 알림은 분류와 독립</h2>
 * <p>"격리된 장치를 뺐다" 는 결과 종류가 아니라 <b>부가 정보</b>입니다.
 * 전달이 성공했든 실패했든 알려야 하므로 별도로 처리합니다.
 * ({@link PushOutcomes.SkipNotice})
 */
@Service
public class PolicyPushNotifier {

    private static final Logger log = LoggerFactory.getLogger(PolicyPushNotifier.class);

    private final List<PushOutcomeStrategy> strategies;

    private final PushOutcomes.SkipNotice skipNotice = new PushOutcomes.SkipNotice();

    /** 기본 전략 목록으로 만듭니다. */
    public PolicyPushNotifier() {
        this(PushOutcomes.defaults());
    }

    /**
     * 테스트/확장용 생성자입니다.
     *
     * @param strategies 순서대로 시도할 결과 전략
     */
    public PolicyPushNotifier(List<PushOutcomeStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    /**
     * 푸시 결과를 분류하고 남길 알림을 돌려줍니다.
     *
     * <p>제외 알림을 <b>먼저</b> 넣습니다. 알림 목록에서 "왜 대상 수가
     * 줄었나" 가 "전달 완료" 보다 위에 있어야 읽는 순서가 자연스럽습니다.
     *
     * @param context 푸시 결과 요약
     * @return 남길 알림 목록 (없으면 빈 목록)
     */
    public List<PushOutcomeStrategy.Notification> classify(
            PushOutcomeStrategy.PushOutcome context) {
        if (context == null) {
            return List.of();
        }

        final List<PushOutcomeStrategy.Notification> notifications = new java.util.ArrayList<>();

        // 부가 정보 먼저 — "왜 대상이 줄었는가" 가 결과보다 앞에 와야 합니다.
        skipNotice.notification(context).ifPresent(notifications::add);

        boolean matched = false;
        for (final PushOutcomeStrategy strategy : strategies) {
            if (strategy.matches(context)) {
                matched = true;
                strategy.notification(context).ifPresent(notifications::add);
                break;
            }
        }

        if (!matched) {
            // 알림을 못 만들었을 뿐, 푸시는 끝났습니다. 경고만 남깁니다.
            log.warn("policy push outcome not classified: targets={} delivered={} forced={}",
                    context.targetCount(), context.deliveredCount(), context.forced());
        }
        return notifications;
    }

    /**
     * 결과 이름만 돌려줍니다. (로그/테스트용)
     *
     * @param context 푸시 결과
     * @return 결과 이름 (분류 실패 시 {@code "unclassified"})
     */
    public String nameOf(PushOutcomeStrategy.PushOutcome context) {
        if (context != null) {
            for (final PushOutcomeStrategy strategy : strategies) {
                if (strategy.matches(context)) {
                    return strategy.name();
                }
            }
        }
        return "unclassified";
    }

    /** @return 등록된 전략 목록 (진단용) */
    public List<PushOutcomeStrategy> strategies() {
        return strategies;
    }

    /**
     * 알림을 저장소에 넘길 형태로 바꿉니다.
     *
     * <p>호출자가 {@code notifyQuietly} 의 여덟 인자를 순서대로 넘기지 않게
     * 합니다 — 모두 {@code String} 이라 순서를 바꿔도 컴파일러가 막지 못하고,
     * 실제로 링크와 출처를 바꿔 넣으면 <b>클릭하면 엉뚱한 화면</b>이 열립니다.
     *
     * @param notification 알림 내용
     * @param sink         저장 함수 ({@code notifyQuietly} 위임)
     */
    public static void dispatch(PushOutcomeStrategy.Notification notification,
                                java.util.function.Consumer<PushOutcomeStrategy.Notification> sink) {
        if (notification == null) {
            return;
        }
        sink.accept(notification);
    }

    /** @return 분류되지 않았을 때의 이름 (테스트 계약) */
    public static String unclassifiedName() {
        return "unclassified";
    }

    /** @return 매칭된 전략이 없을 수 있다는 사실을 명시한 빈 결과 */
    public static Optional<PushOutcomeStrategy.Notification> none() {
        return Optional.empty();
    }
}