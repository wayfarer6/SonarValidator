package org.sonar.sonarvalidator_backend.Service.telemetry;

import java.time.Instant;
import java.util.Objects;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;

public record TelemetrySnapshot(String agentId, NeutralDeviceConfig config, Source source, Instant receivedAt) {
    public enum Source { LIVE, OFFLINE }
    public TelemetrySnapshot {
        Objects.requireNonNull(agentId);
        Objects.requireNonNull(config);
        Objects.requireNonNull(source);
        Objects.requireNonNull(receivedAt);
    }
}
