package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Controller.OPNsenseController;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseApiClient;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseCredentialService;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseProbeStrategies;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseTransportPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class OPNsenseAuthorizationTest {

    private final OPNsenseCredentialService credentials = mock(OPNsenseCredentialService.class);
    private final OPNsenseController controller = new OPNsenseController(
            credentials,
            mock(OPNsenseApiClient.class),
            mock(AgentSessionRegistry.class),
            mock(AgentMessageRouterService.class),
            new OPNsenseProbeStrategies(),
            new OPNsenseTransportPolicy(""));

    @Test
    @DisplayName("VIEWER cannot mutate OPNsense credentials")
    void viewerCannotSaveOrDeleteCredentials() {
        final var viewer = authentication("ROLE_VIEWER");

        assertEquals(HttpStatus.FORBIDDEN,
                controller.save(viewer, "42", null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                controller.delete(viewer, "42").getStatusCode());
        verify(credentials, never()).save(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("OPERATOR can manage credentials under existing edit-role convention")
    void operatorCanSaveCredentials() {
        final var operator = authentication("ROLE_OPERATOR");
        when(credentials.save("42", "fw", "https://fw.example", "key", "secret", false, true, "PRJ-1"))
                .thenReturn(java.util.Map.of("node_id", 42));

        assertEquals(HttpStatus.OK, controller.save(operator, "42",
                new OPNsenseController.CredentialRequest(
                        "fw", "https://fw.example", "key", "secret", false, true, "PRJ-1"))
                .getStatusCode());
    }

    @Test
    @DisplayName("OPERATOR cannot access the raw OPNsense probe response")
    void operatorCannotProbeRawResponses() {
        final var operator = authentication("ROLE_OPERATOR");

        assertEquals(HttpStatus.FORBIDDEN,
                controller.probe(operator, "42", "firmware").getStatusCode());
    }

    private static UsernamePasswordAuthenticationToken authentication(String role) {
        return new UsernamePasswordAuthenticationToken(
                "user", "password", List.of(new SimpleGrantedAuthority(role)));
    }
}
