package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseApiClient;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseConnection;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseTransportPolicy;
import tools.jackson.databind.ObjectMapper;

class OPNsenseConnectionSecurityTest {

    @Test
    @DisplayName("HTTP와 HTTPS 주소 모두 사용 가능하고, user-info 가 붙은 주소는 거부한다")
    void acceptsBothSchemesButNotUserInfo() {
        assertTrue(new OPNsenseConnection("fw.example/", "key", "secret", false).isUsable());
        assertTrue(new OPNsenseConnection("http://fw.example", "key", "secret", false).isUsable());
        assertTrue(new OPNsenseConnection("https://fw.example", "key", "secret", false).isUsable());
        assertFalse(new OPNsenseConnection("https://user@fw.example", "key", "secret", false).isUsable());
        assertFalse(new OPNsenseConnection("", "key", "secret", false).isUsable());
        assertFalse(new OPNsenseConnection("https://fw.example", "", "secret", false).isUsable());
    }

    @Test
    @DisplayName("전송 정책은 기본적으로 HTTP/HTTPS 를 모두 허용한다")
    void defaultPolicyAllowsBothSchemes() {
        final var policy = new OPNsenseTransportPolicy("");

        assertTrue(policy.allows(new OPNsenseConnection("https://fw.example", "key", "secret", false)));
        assertTrue(policy.allows(new OPNsenseConnection("http://10.20.0.2", "key", "secret", false)));
    }

    @Test
    @DisplayName("allow-http=false 면 평문 HTTP 를 거부한다")
    void canDisablePlainHttp() {
        final var policy = new OPNsenseTransportPolicy(false, "");

        assertTrue(policy.allows(new OPNsenseConnection("https://fw.example", "key", "secret", false)));
        assertFalse(policy.allows(new OPNsenseConnection("http://10.20.0.2", "key", "secret", false)));
    }

    @Test
    @DisplayName("허용 목록을 지정하면 그 주소만 평문 HTTP 로 통과한다")
    void originAllowlistRestrictsHttp() {
        final var policy = new OPNsenseTransportPolicy("http://10.20.0.2");

        assertTrue(policy.allows(new OPNsenseConnection("http://10.20.0.2", "key", "secret", false)));
        assertFalse(policy.allows(new OPNsenseConnection("http://10.20.0.9", "key", "secret", false)));
    }

    @Test
    @DisplayName("API 클라이언트는 정책이 막은 주소로 요청을 보내지 않는다")
    void clientRefusesDisallowedTransportBeforeNetworkRequest() {
        final var client = new OPNsenseApiClient(mock(ObjectMapper.class),
                new OPNsenseTransportPolicy(false, ""));

        final var result = client.checkConnection(
                new OPNsenseConnection("http://127.0.0.1:1", "key", "secret", false));

        assertFalse(result.ok());
        assertEquals(0, result.statusCode());
        assertTrue(result.error().contains("HTTP"));
    }
}
