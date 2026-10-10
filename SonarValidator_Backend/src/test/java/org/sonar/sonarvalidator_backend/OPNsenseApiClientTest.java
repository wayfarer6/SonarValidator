package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Service.opnsense.*;
import tools.jackson.databind.ObjectMapper;

class OPNsenseApiClientTest {
    @Test
    void rviEndpointsAndHttpOriginAreExplicit() throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final var paths = new CopyOnWriteArrayList<String>();
        server.createContext("/", exchange -> {
            final String path = exchange.getRequestURI().getPath();
            paths.add(path);
            final String body = switch (path) {
                case "/api/core/firmware/status" -> "{\"product\":{\"product_version\":\"26.1\"}}";
                case "/api/firewall/filter/get" -> "{\"filter\":{\"snatrules\":{\"rule\":[{\"id\":\"nat1\"}]},\"npt\":{},\"onetoone\":{}}}";
                case "/html" -> "<html>Login</html>";
                default -> "{}";
            };
            final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            final String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            final var connection = new OPNsenseConnection(origin, "test-key", "test-secret", false);
            final var policy = new OPNsenseTransportPolicy(origin);
            final var client = new OPNsenseApiClient(new ObjectMapper(), policy);
            // 허용 목록이 비어 있으면 모든 HTTP 를 허용한다. (기본값)
            assertTrue(new OPNsenseTransportPolicy("").allows(connection));
            // 허용 목록을 지정하면 그 주소만 통과한다.
            assertFalse(policy.allows(new OPNsenseConnection("http://127.0.0.1:1", "k", "s", true)));
            assertFalse(policy.allows(new OPNsenseConnection(origin + "/api", "k", "s", false)));
            assertFalse(policy.allows(new OPNsenseConnection(origin.replace("//", "//user@"), "k", "s", false)));
            assertTrue(client.checkConnection(connection).ok());
            assertEquals("/api/core/firmware/status", paths.getFirst());
            final var nat = client.fetchNatRules(connection);
            assertTrue(nat.ok());
            assertEquals(1, nat.body().path("total").asInt());
            assertFalse(paths.contains("/api/firewall/filter/search_nat"));
            assertFalse(client.get(connection, "/html").ok());
        } finally { server.stop(0); }
    }

    @Test
    void firmwareFallsBackOnlyWhenEndpointMissing() {
        final var connection = new OPNsenseConnection("https://fw.example", "k", "s", false);
        final var paths = new CopyOnWriteArrayList<String>();
        final var client = new OPNsenseApiClient(new ObjectMapper()) {
            @Override public Result get(OPNsenseConnection c, String path) {
                paths.add(path);
                return path.endsWith("/status") ? Result.failure(404, "", "missing")
                        : Result.success(200, new ObjectMapper().createObjectNode(), "{}");
            }
        };
        assertTrue(client.checkConnection(connection).ok());
        assertEquals(java.util.List.of("/api/core/firmware/status", "/api/core/firmware/info"), paths);
    }
}
