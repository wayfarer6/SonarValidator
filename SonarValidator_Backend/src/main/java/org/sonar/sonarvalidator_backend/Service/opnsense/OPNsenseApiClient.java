package org.sonar.sonarvalidator_backend.Service.opnsense;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.SSLEngine;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class OPNsenseApiClient {
    private final java.util.concurrent.atomic.AtomicBoolean insecureTlsReported = new java.util.concurrent.atomic.AtomicBoolean();

    private static final Logger log = LoggerFactory.getLogger(OPNsenseApiClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper objectMapper;
    private final OPNsenseTransportPolicy transportPolicy;

    public OPNsenseApiClient(ObjectMapper objectMapper) {
        this(objectMapper, new OPNsenseTransportPolicy(""));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OPNsenseApiClient(ObjectMapper objectMapper, OPNsenseTransportPolicy transportPolicy) {
        this.objectMapper = objectMapper;
        this.transportPolicy = transportPolicy;
    }

    public record Result(boolean ok,
                         int statusCode,
                         JsonNode body,
                         String rawBody,
                         String error) {

        public static Result success(int status, JsonNode body, String raw) {
            return new Result(true, status, body, raw, null);
        }

        public static Result failure(int status, String raw, String error) {
            return new Result(false, status, null, raw, error);
        }
    }

    public Result checkConnection(OPNsenseConnection connection) {
        return getFirstAvailable(connection, "/api/core/firmware/status", "/api/core/firmware/info");
    }

    public String extractVersion(JsonNode body) {
        if (body == null || body.isNull()) {
            return null;
        }
        // 후보 경로들: firmware.status.product / product.version / product_name ...
        final List<String[]> candidates = List.of(
                new String[] {"product", "product_version"},
                new String[] {"product", "product_name"},
                new String[] {"product", "version"},
                new String[] {"status", "product_version"},
                new String[] {"status", "version"});
        for (final String[] path : candidates) {
            JsonNode node = body;
            for (final String segment : path) {
                node = node.path(segment);
            }
            if (node.isString() && !node.asString("").isBlank()) {
                return node.asString(null);
            }
        }
        return null;
    }

    public Result fetchInterfaces(OPNsenseConnection connection) {
        return getFirstAvailable(connection,
                "/api/interfaces/overview/interfacesInfo",
                "/api/diagnostics/interface/getInterfaceConfig",
                "/api/diagnostics/interface/get");
    }

    public Result fetchFirewallRules(OPNsenseConnection connection) {
        return get(connection, "/api/firewall/filter/search_rule?rowCount=-1");
    }

    public Result fetchNatRules(OPNsenseConnection connection) {
        final Result all = get(connection, "/api/firewall/filter/get");
        if (all.statusCode() == 404) {
            return get(connection, "/api/firewall/filter/search_nat?rowCount=-1");
        }
        if (!all.ok() || all.body() == null) return all;
        final JsonNode filter = all.body().path("filter");
        final ArrayNode rows = objectMapper.createArrayNode();
        rows.addAll(collectRules(filter.path("snatrules")));
        rows.addAll(collectRules(filter.path("npt")));
        rows.addAll(collectRules(filter.path("onetoone")));

        final ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", rows);
        wrapper.put("rowCount", rows.size());
        wrapper.put("total", rows.size());
        return Result.success(all.statusCode(), wrapper, wrapper.toString());
    }

    private ArrayNode collectRules(JsonNode node) {
        if (node.isArray()) {
            return (ArrayNode) node;
        }
        final JsonNode inner = node.path("rule");
        return inner.isArray() ? (ArrayNode) inner : objectMapper.createArrayNode();
    }

    public Result getFirstAvailable(OPNsenseConnection connection, String... paths) {
        Result last = null;
        for (final String path : paths) {
            last = get(connection, path);
            if (last.ok() || last.statusCode() != 404) {
                return last;
            }
        }
        return last == null
                ? Result.failure(0, null, "시도한 API 경로가 없습니다.")
                : last;
    }

    public Result fetchAliases(OPNsenseConnection connection) {
        return get(connection, "/api/firewall/alias/search_item?rowCount=-1");
    }

    public Result get(OPNsenseConnection connection, String path) {
        if (connection != null && !transportPolicy.allows(connection)) {
            return Result.failure(0, null,
                    "이 주소로는 요청을 보낼 수 없습니다. 평문 HTTP 가 금지되어 있거나"
                    + "(sonar.opnsense.allow-http=false) 허용 목록에 없습니다"
                    + "(sonar.opnsense.http-origins). HTTPS 를 쓰거나 서버 설정을 확인하세요.");
        }
        if (connection == null || connection.apiKey() == null || connection.apiKey().isBlank()
                || connection.apiSecret() == null || connection.apiSecret().isBlank()) {
            return Result.failure(0, null,
                    "OPNsense 접속 정보가 불완전합니다. URL/API Key/Secret 을 모두 입력하세요.");
        }
        final String url = connection.normalizedBaseUrl() + path;
        try (final HttpClient client = buildClient(connection)) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", basicAuthHeader(connection))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            final HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            final int status = response.statusCode();
            final String raw = response.body();

            if (status >= 200 && status < 300) {
                final JsonNode body = parseJson(raw);
                if (body == null || body.isNull() || !(body.isObject() || body.isArray())) {
                    return Result.failure(status, raw, "OPNsense API가 JSON 객체/배열을 반환하지 않았습니다. "
                            + "API 주소와 로그인 페이지 응답 여부를 확인하세요.");
                }
                return Result.success(status, body, raw);
            }
            // 실패 사유를 사람이 읽을 수 있게 바꿉니다.
            return Result.failure(status, raw, describeFailure(status, connection));
        } catch (java.net.http.HttpConnectTimeoutException ex) {
            return Result.failure(0, null, "연결 시간 초과: " + connection.normalizedBaseUrl()
                    + " 에 도달할 수 없습니다. (방화벽/라우팅 확인)");
        } catch (java.net.ConnectException ex) {
            return Result.failure(0, null, "연결 거부: " + connection.normalizedBaseUrl()
                    + " 에서 포트가 열려 있지 않습니다.");
        } catch (java.net.UnknownHostException ex) {
            return Result.failure(0, null, "호스트를 찾을 수 없습니다: " + connection.normalizedBaseUrl());
        } catch (javax.net.ssl.SSLHandshakeException ex) {
            return Result.failure(0, null, "TLS 핸드셰이크 실패: 자체 서명 인증서라면 "
                    + "'자체 서명 인증서 허용' 을 켜세요. (" + ex.getMessage() + ")");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Result.failure(0, null, "요청이 중단되었습니다.");
        } catch (Exception ex) {
            log.warn("OPNsense call failed: url={} error={}", url, ex.toString());
            return Result.failure(0, null, "요청 실패: " + ex.getClass().getSimpleName()
                    + " - " + ex.getMessage());
        }
    }

    private String describeFailure(int status, OPNsenseConnection connection) {
        return switch (status) {
            case 301, 302, 303, 307, 308 -> "리다이렉트 응답(" + status + "). "
                    + "OPNsense API의 직접 주소와 API Key/Secret을 확인하세요. 로그인 페이지는 API 응답이 아닙니다.";
            case 401 -> "인증 실패(401). API Key 와 Secret 을 확인하세요. "
                    + "키는 사용자 이름 자리에 들어갑니다. "
                    + "OPNsense 의 System > Access > Users 에서 키를 발급했는지 확인하세요.";
            case 403 -> "권한 없음(403). 해당 API 사용자에게 권한이 없습니다. "
                    + "OPNsense 의 System > Access > Users 에서 필요한 권한을 부여하세요.";
            case 404 -> "경로 없음(404). URL 에 경로가 포함되었거나 OPNsense 버전이 다를 수 있습니다. "
                    + "현재 base URL: " + connection.normalizedBaseUrl();
            case 500, 502, 503 -> "서버 오류(" + status + "). "
                    + "OPNsense 의 해당 서비스(예: filter)가 실행 중인지 확인하세요.";
            default -> "요청 실패: HTTP " + status;
        };
    }

    private String basicAuthHeader(OPNsenseConnection connection) {
        final String pair = connection.apiKey() + ":" + connection.apiSecret();
        return "Basic " + Base64.getEncoder()
                .encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }

    private HttpClient buildClient(OPNsenseConnection connection) {
        final HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // Never follow a redirect: it could downgrade HTTPS and expose Basic auth.
                .followRedirects(HttpClient.Redirect.NEVER);

        if (connection.allowInsecureTls()) {
            if (insecureTlsReported.compareAndSet(false, true))
                log.warn("OPNsense API 연결 설정: TLS 인증서 검증 생략 옵션 사용 중");
            try {
                final SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[] {TRUST_ALL}, new SecureRandom());
                builder.sslContext(context);
            } catch (Exception ex) {
                log.error("failed to build insecure SSL context: {}", ex.getMessage());
            }
        }
        return builder.build();
    }

    private static final X509ExtendedTrustManager TRUST_ALL = new X509ExtendedTrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // 의도적으로 검증하지 않습니다. (allowInsecureTls 전용)
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // 의도적으로 검증하지 않습니다. (allowInsecureTls 전용)
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            checkServerTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (RuntimeException ex) {
            log.debug("OPNsense response is not JSON: {}", ex.getMessage());
            return null;
        }
    }

    public Map<String, Object> summarize(JsonNode body) {
        final Map<String, Object> summary = new LinkedHashMap<>();
        if (body == null || body.isNull()) {
            summary.put("type", "null");
            return summary;
        }
        summary.put("type", body.isObject() ? "object" : body.isArray() ? "array" : "scalar");
        if (body.isObject()) {
            summary.put("keys", body.size());
            final StringBuilder names = new StringBuilder();
            body.propertyNames().forEach(name -> {
                if (names.length() < 300) {
                    names.append(name).append(' ');
                }
            });
            summary.put("key_names", names.toString().trim());
        } else if (body.isArray()) {
            summary.put("items", body.size());
        }
        return summary;
    }
}
