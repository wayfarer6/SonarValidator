package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Service.telemetry.SnapshotPayloadGuard;
import tools.jackson.databind.json.JsonMapper;

class SnapshotPayloadGuardTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void missingPreviouslyObservedSectionIsNotDeletion() {
        final var previous = json.readTree("{\"nic_status\":{\"interfaces\":[]},\"route_status\":{\"routes\":[]}}");
        assertThrows(IllegalArgumentException.class, () -> SnapshotPayloadGuard.requireComplete(
                json.readTree("{\"route_status\":{\"routes\":[]}}"), previous));
    }
    @Test void explicitEmptySuccessIsValid() {
        final var payload = json.readTree("{\"nic_status\":{\"interfaces\":[]},\"route_status\":{\"routes\":[],\"parsed\":true,\"parse_error\":\"\"}}");
        assertDoesNotThrow(() -> SnapshotPayloadGuard.requireComplete(payload, payload));
    }
    @Test void failedAndMalformedSectionsAreNotEmptySuccess() {
        for (String body : java.util.List.of(
                "{\"route_status\":{\"routes\":[],\"parsed\":false}}",
                "{\"route_status\":{\"routes\":[],\"parse_error\":\"invalid syntax\"}}",
                "{\"nic_status\":{\"interfaces\":\"error\"}}"))
            assertThrows(IllegalArgumentException.class, () -> SnapshotPayloadGuard.requireComplete(json.readTree(body), null));
    }
}
