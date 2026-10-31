package org.sonar.sonarvalidator_backend.Service.policy;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PolicyPushNotifier {

    private static final Logger log = LoggerFactory.getLogger(PolicyPushNotifier.class);

    private final List<PushOutcomeStrategy> strategies;

    private final PushOutcomes.SkipNotice skipNotice = new PushOutcomes.SkipNotice();

    public PolicyPushNotifier() {
        this(PushOutcomes.defaults());
    }

    public PolicyPushNotifier(List<PushOutcomeStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public List<PushOutcomeStrategy.Notification> classify(
            PushOutcomeStrategy.PushOutcome context) {
        if (context == null) {
            return List.of();
        }

        final List<PushOutcomeStrategy.Notification> notifications = new java.util.ArrayList<>();


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

    public List<PushOutcomeStrategy> strategies() {
        return strategies;
    }

    public static void dispatch(PushOutcomeStrategy.Notification notification,
                                java.util.function.Consumer<PushOutcomeStrategy.Notification> sink) {
        if (notification == null) {
            return;
        }
        sink.accept(notification);
    }

    public static String unclassifiedName() {
        return "unclassified";
    }

    public static Optional<PushOutcomeStrategy.Notification> none() {
        return Optional.empty();
    }
}
