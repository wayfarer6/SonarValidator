package org.sonar.sonarvalidator_backend.Service.quarantine;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;

public interface QuarantineMethod {

    enum Mode {
        DEVICE,
        SUBNET,
        UNSUPPORTED
    }

    boolean supports(DeviceType type);

    Mode mode();

    default String exclusionReason() {
        return null;
    }

    default List<String> warnings(QuarantineContext context) {
        return List.of();
    }
}
