package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseApiClient;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseConnection;
import tools.jackson.databind.ObjectMapper;

class OPNsenseConnectionSecurityTest {

    @Test
    @DisplayName("implicit scheme uses HTTPS while explicit HTTP is rejected")
    void requiresHttpsTransport() {
        assertTrue(new OPNsenseConnection("fw.example/", "key", "secret", false).isUsable());
        assertFalse(new OPNsenseConnection("http://fw.example", "key", "secret", false).isUsable());
        assertFalse(new OPNsenseConnection("https://user@fw.example", "key", "secret", false).isUsable());
    }

    @Test
    @DisplayName("API client refuses to send Basic credentials over HTTP")
    void clientRefusesHttpBeforeNetworkRequest() {
        final OPNsenseApiClient client = new OPNsenseApiClient(mock(ObjectMapper.class));

        final var result = client.checkConnection(
                new OPNsenseConnection("http://127.0.0.1:1", "key", "secret", false));

        assertFalse(result.ok());
        assertEquals(0, result.statusCode());
    }
}
