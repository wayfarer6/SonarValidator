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
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OPNsense REST API 클라이언트입니다.
 *
 * <h2>OPNsense 인증 방식 (중요)</h2>
 * <p>OPNsense 는 사용자 이름/비밀번호 대신 <b>API Key + API Secret</b> 을
 * HTTP Basic 인증 형식으로 보냅니다.
 *
 * <pre>
 *   Authorization: Basic base64(apiKey + ":" + apiSecret)
 * </pre>
 *
 * <p>즉 <b>키가 사용자 이름 자리에</b> 들어갑니다. 이 부분을 반대로 넣으면
 * 401 이 나는데, 메시지가 "Authentication Failed" 뿐이라 원인을 찾기
 * 어렵습니다. (OPNsense 는 실패 사유를 구체적으로 알려주지 않습니다.)
 *
 * <p>API 를 쓰려면 OPNsense 에서 <b>System &gt; Access &gt; Users</b> 의 해당
 * 사용자에 권한을 주고, <b>API keys</b> 에서 키를 발급해야 합니다.
 * 키만 발급하고 권한이 없으면 403 이 납니다.
 *
 * <h2>사용하는 엔드포인트</h2>
 * <table border="1">
 *   <caption>OPNsense API</caption>
 *   <tr><th>용도</th><th>경로</th></tr>
 *   <tr><td>버전/연결 확인</td><td>{@code /api/core/firmware/status}</td></tr>
 *   <tr><td>인터페이스 목록</td><td>{@code /api/diagnostics/interface/get}</td></tr>
 *   <tr><td>방화벽 규칙</td><td>{@code /api/firewall/filter/search_rule?rowCount=-1}</td></tr>
 *   <tr><td>NAT 규칙</td><td>{@code /api/firewall/filter/search_nat?rowCount=-1}</td></tr>
 *   <tr><td>별칭(주소 그룹)</td><td>{@code /api/firewall/alias/search_item?rowCount=-1}</td></tr>
 * </table>
 *
 * <h2>⚠️ 테스트 불가 안내</h2>
 * <p>현재 랩에 OPNsense 실장비가 없어 <b>실제 호출은 검증되지 않았습니다.</b>
 * 그래서 이 클래스는 다음 원칙을 지킵니다.
 * <ul>
 *   <li>응답 구조를 <b>추측하지 않습니다.</b> 키가 없으면 null/빈 값을 돌려줍니다.</li>
 *   <li>파싱 실패를 예외로 던지지 않고 {@code raw} 를 함께 남깁니다.
 *       → 실장비가 붙으면 원문을 보고 매핑을 맞출 수 있습니다.</li>
 *   <li>호출 실패는 {@link Result} 로 감싸 사유와 함께 돌려줍니다.</li>
 * </ul>
 */
@Component
public class OPNsenseApiClient {

    private static final Logger log = LoggerFactory.getLogger(OPNsenseApiClient.class);

    /** 연결 타임아웃. 랩 장비는 응답이 느릴 수 있어 넉넉히 둡니다. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** 요청 타임아웃. 규칙 목록은 클 수 있어 조금 길게 둡니다. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper JSON 매퍼 (Spring 이 주입)
     */
    public OPNsenseApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 호출 결과입니다.
     *
     * <p>예외 대신 이 래퍼를 돌려주는 이유: 자격증명이 틀린 것과 네트워크가
     * 막힌 것을 <b>설정 화면에서 구분해 보여줘야</b> 하기 때문입니다.
     * 예외를 던지면 상위에서 메시지를 다시 해석해야 합니다.
     *
     * @param ok         성공 여부
     * @param statusCode HTTP 상태 코드 (네트워크 실패면 0)
     * @param body       파싱된 본문 (실패 시 null)
     * @param rawBody    원문 (진단용)
     * @param error      오류 메시지 (성공 시 null)
     */
    public record Result(boolean ok,
                         int statusCode,
                         JsonNode body,
                         String rawBody,
                         String error) {

        /** @return 성공 결과 */
        public static Result success(int status, JsonNode body, String raw) {
            return new Result(true, status, body, raw, null);
        }

        /** @return 실패 결과 */
        public static Result failure(int status, String raw, String error) {
            return new Result(false, status, null, raw, error);
        }
    }

    /**
     * 연결을 확인합니다. (설정 모달의 "연결 테스트" 버튼)
     *
     * <p>가장 가벼운 엔드포인트를 씁니다. 이 호출이 성공하면 URL 과
     * 자격증명이 모두 유효합니다.
     *
     * @param connection 접속 정보
     * @return 호출 결과
     */
    public Result checkConnection(OPNsenseConnection connection) {
        return get(connection, "/api/core/firmware/status");
    }

    /**
     * 응답에서 OPNsense 버전을 추출합니다.
     *
     * <p>버전 위치가 응답 구조마다 다를 수 있어 <b>여러 후보 경로를
     * 순서대로</b> 확인합니다. 어디에도 없으면 null 을 돌려줍니다.
     * (실장비 없이 구조를 단정하지 않기 위함)
     *
     * @param body 응답 본문
     * @return 버전 문자열, 찾지 못하면 null
     */
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

    /**
     * 인터페이스 목록을 조회합니다.
     *
     * @param connection 접속 정보
     * @return 호출 결과
     */
    public Result fetchInterfaces(OPNsenseConnection connection) {
        return get(connection, "/api/diagnostics/interface/get");
    }

    /**
     * 방화벽 필터 규칙을 조회합니다.
     *
     * @param connection 접속 정보
     * @return 호출 결과
     */
    public Result fetchFirewallRules(OPNsenseConnection connection) {
        return get(connection, "/api/firewall/filter/search_rule?rowCount=-1");
    }

    /**
     * NAT 규칙을 조회합니다.
     *
     * @param connection 접속 정보
     * @return 호출 결과
     */
    public Result fetchNatRules(OPNsenseConnection connection) {
        return get(connection, "/api/firewall/filter/search_nat?rowCount=-1");
    }

    /**
     * 별칭(주소 그룹) 목록을 조회합니다.
     *
     * @param connection 접속 정보
     * @return 호출 결과
     */
    public Result fetchAliases(OPNsenseConnection connection) {
        return get(connection, "/api/firewall/alias/search_item?rowCount=-1");
    }

    /**
     * GET 요청을 보냅니다.
     *
     * @param connection 접속 정보
     * @param path       API 경로 ({@code /api/...} 로 시작)
     * @return 호출 결과
     */
    public Result get(OPNsenseConnection connection, String path) {
        if (connection == null || !connection.isUsable()) {
            return Result.failure(0, null,
                    "OPNsense 접속 정보가 불완전합니다. URL/API Key/Secret 을 모두 입력하세요.");
        }
        final String url = connection.normalizedBaseUrl() + path;
        final HttpClient client = buildClient(connection);

        try {
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
                return Result.success(status, parseJson(raw), raw);
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

    /**
     * HTTP 상태 코드를 사람이 읽는 사유로 바꿉니다.
     *
     * <p>OPNsense 는 인증 실패 시 본문에 {@code "Authentication Failed"} 만
     * 넣습니다. 그것만 보면 "키가 틀렸는지, 권한이 없는지" 구분이 안 되므로
     * 확인할 곳을 함께 안내합니다.
     *
     * @param status     상태 코드
     * @param connection 접속 정보 (URL 안내용)
     * @return 오류 메시지
     */
    private String describeFailure(int status, OPNsenseConnection connection) {
        return switch (status) {
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

    /**
     * Basic 인증 헤더를 만듭니다.
     *
     * <p><b>키가 사용자 이름 자리</b>입니다. ({@code key:secret})
     *
     * @param connection 접속 정보
     * @return {@code Basic ...} 헤더 값
     */
    private String basicAuthHeader(OPNsenseConnection connection) {
        final String pair = connection.apiKey() + ":" + connection.apiSecret();
        return "Basic " + Base64.getEncoder()
                .encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * HttpClient 를 만듭니다.
     *
     * <p>{@code allowInsecureTls} 가 켜져 있으면 모든 인증서를 신뢰합니다.
     * 랩 장비가 자체 서명 인증서를 쓰는 경우를 위한 것이며,
     * <b>운영에서는 절대 켜지 마세요.</b> 중간자 공격에 무방비가 됩니다.
     *
     * @param connection 접속 정보
     * @return HttpClient
     */
    private HttpClient buildClient(OPNsenseConnection connection) {
        final HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // OPNsense 는 자체 서명 인증서를 쓰는 경우가 많아 리다이렉트를 따릅니다.
                .followRedirects(HttpClient.Redirect.NORMAL);

        if (connection.allowInsecureTls()) {
            log.warn("OPNsense TLS verification is DISABLED for {} — "
                    + "this must not be used in production", connection.normalizedBaseUrl());
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

    /** 모든 인증서를 신뢰하는 관리자. 랩 전용입니다. */
    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // 의도적으로 검증하지 않습니다. (allowInsecureTls 전용)
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // 의도적으로 검증하지 않습니다. (allowInsecureTls 전용)
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    /**
     * 본문을 JSON 으로 파싱합니다. 실패하면 null 을 돌려줍니다.
     *
     * <p>OPNsense 는 콘텐츠 타입을 잘못 주는 경우가 있어 예외를 흡수합니다.
     * 원문은 {@code rawBody} 에 남아 있으므로 진단에 문제가 없습니다.
     *
     * @param raw 원문
     * @return 파싱된 노드 또는 null
     */
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

    /**
     * 응답 본문을 요약합니다. (설정 모달의 미리보기용)
     *
     * <p>본문이 클 수 있으므로 상위 키만 세어 개수를 알려줍니다.
     *
     * @param body 응답 본문
     * @return 요약 정보
     */
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
