package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.sonarvalidator_backend.Service.opnsense.*;
import tools.jackson.databind.ObjectMapper;

class OPNsenseTlsIntegrationTest {
    @TempDir Path temp;

    @Test void insecureOptInHandlesWrongCertificateNameWithoutChangingStrictClients() throws Exception {
        Path keyStorePath = temp.resolve("fixture.p12");
        String fixturePassword = "fixture-only-password";
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "fixture", "-keyalg", "RSA",
                "-dname", "CN=wrong.invalid", "-ext", "SAN=dns:wrong.invalid",
                "-validity", "1", "-storetype", "PKCS12", "-keystore", keyStorePath.toString(),
                "-storepass", fixturePassword, "-keypass", fixturePassword, "-noprompt")
                .redirectErrorStream(true).redirectOutput(temp.resolve("keytool.log").toFile()).start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keyStorePath)) {
            store.load(input, fixturePassword.toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, fixturePassword.toCharArray());
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(keys.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.createContext("/", exchange -> {
            byte[] body = "{\"product\":{\"product_version\":\"fixture\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String propertyBefore = System.getProperty("jdk.internal.httpclient.disableHostnameVerification");
            String origin = "https://127.0.0.1:" + server.getAddress().getPort();
            var client = new OPNsenseApiClient(new ObjectMapper());
            var strict = new OPNsenseConnection(origin, "fixture-key", "fixture-secret", false);
            var insecure = new OPNsenseConnection(origin, "fixture-key", "fixture-secret", true);
            assertTimeout(Duration.ofSeconds(15), () -> {
                assertFalse(client.checkConnection(strict).ok());
                assertTrue(client.checkConnection(insecure).ok());
                assertFalse(client.checkConnection(strict).ok());
            });
            assertEquals(propertyBefore, System.getProperty("jdk.internal.httpclient.disableHostnameVerification"));
        } finally {
            server.stop(0);
        }
    }
}
