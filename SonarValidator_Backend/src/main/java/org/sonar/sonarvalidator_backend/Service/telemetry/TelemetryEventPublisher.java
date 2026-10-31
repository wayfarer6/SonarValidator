package org.sonar.sonarvalidator_backend.Service.telemetry;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class TelemetryEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(TelemetryEventPublisher.class);
    private final List<TelemetryObserver> observers;

    public TelemetryEventPublisher(List<TelemetryObserver> observers) {
        this.observers = List.copyOf(observers);
    }

    public void publish(TelemetrySnapshot snapshot) {
        for (final TelemetryObserver observer : observers) {
            try { observer.onTelemetry(snapshot); }
            catch (RuntimeException ex) {
                log.warn("telemetry observer {} failed for agent={}: {}",
                        observer.getClass().getSimpleName(), snapshot.agentId(), ex.getMessage());
            }
        }
    }
}
