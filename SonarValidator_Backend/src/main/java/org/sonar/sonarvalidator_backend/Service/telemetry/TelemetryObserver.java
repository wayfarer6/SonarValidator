package org.sonar.sonarvalidator_backend.Service.telemetry;

@FunctionalInterface
public interface TelemetryObserver {
    void onTelemetry(TelemetrySnapshot snapshot);
}
