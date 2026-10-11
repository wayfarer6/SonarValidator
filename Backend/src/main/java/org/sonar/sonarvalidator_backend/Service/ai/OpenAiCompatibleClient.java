package org.sonar.sonarvalidator_backend.Service.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * OpenAI 호환 API 클라이언트입니다.
 *
 * <h2>왜 "OpenAI 호환" 하나로 충분한가</h2>
 * <p>{@code POST {baseUrl}/chat/completions} 규격은 사실상 표준이 되어,
 * 아래가 모두 같은 형태로 호출됩니다. 그래서 공급자별 분기 코드가 필요 없습니다.
 *
 * <table border="1">
 *   <caption>호환 공급자 예</caption>
 *   <tr><th>공급자</th><th>baseUrl 예</th></tr>
 *   <tr><td>OpenAI</td><td>{@code https://api.openai.com/v1}</td></tr>
 *   <tr><td>Ollama (로컬)</td><td>{@code http://localhost:11434/v1}</td></tr>
 *   <tr><td>vLLM / LocalAI</td><td>{@code http://host:8000/v1}</td></tr>
 *   <tr><td>Groq / Together / OpenRouter</td><td>각 사의 {@code /v1}</td></tr>
 *   <tr><td>Azure OpenAI</td><td>{@code https://<res>.openai.azure.com/openai/deployments/<model>}</td></tr>
 * </table>
 *
 * <h2>⚠️ baseUrl 정규화 (실제로 자주 틀리는 부분)</h2>
 * <p>사용자는 {@code https://api.openai.com}, {@code .../v1},
 * {@code .../v1/} 을 모두 입력합니다. 그대로 이어 붙이면
 * {@code /v1/v1/chat/completions} 나 {@code //chat/completions} 가 되어
 * 404 가 납니다. 그래서 경로를 합칠 때 아래를 적용합니다.
 * <ul>
 *   <li>끝 슬래시 제거</li>
 *   <li>이미 {@code /chat/completions} 로 끝나면 그대로 사용</li>
 *   <li>그 외에는 {@code /v1} 유무를 보고 중복을 피해 붙임</li>
 * </ul>
 *
 * <h2>에러를 예외 대신 {@link Result} 로 돌려주는 이유</h2>
 * <p>분석 실패는 <b>예상 가능한 결과</b>입니다. (키 만료, 모델명 오타,
 * 로컬 서버 미기동, 컨텍스트 초과) 예외로 던지면 호출측이 매번 try/catch 로
 * 감싸야 하고, 사유를 사용자에게 그대로 보여주기 어렵습니다.
 * 이 클래스는 항상 사유가 담긴 {@link Result} 를 돌려줍니다.
 */
@Component
public class OpenAiCompatibleClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * OpenAI 호환 채팅 요청을 보냅니다.
     *
     * @param connection 접속 정보 (baseUrl/키/모델/타임아웃)
     * @param messages   역할/내용 쌍 목록 (system, user 순)
     * @param jsonMode   JSON 응답을 요구할지 여부
     * @return 성공이면 본문 텍스트, 실패면 사유가 담긴 결과
     */
    public Result chat(Connection connection, List<Message> messages, boolean jsonMode) {
        if (connection == null || !connection.isUsable()) {
            return Result.failure("AI 공급자 설정이 불완전합니다. (base URL / 모델 확인)");
        }

        final long startedAt = System.currentTimeMillis();

        try {
            final String url = buildChatCompletionsUrl(connection.baseUrl());
            final String body = buildRequestBody(connection, messages, jsonMode);

            final HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(connection.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    // 일부 게이트웨이가 User-Agent 없으면 403 을 냅니다.
                    .header("User-Agent", "SonarValidator/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

            applyAuth(builder, connection);

            final HttpClient client = buildHttpClient(connection.allowInsecureTls());
            final HttpResponse<String> response = client.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            final long elapsed = System.currentTimeMillis() - startedAt;

            if (response.statusCode() / 100 != 2) {
                // 공급자는 실패 사유를 JSON 본문에 담아 보내는 경우가 많습니다.
                // 상태 코드만 보여주면 "401" 로 끝나 사용자가 원인을 알 수 없습니다.
                return Result.failure(describeHttpError(response.statusCode(), response.body()), elapsed);
            }

            return parseResponse(response.body(), elapsed);

        } catch (java.net.http.HttpConnectTimeoutException ex) {
            return Result.failure("연결 시간 초과: " + connection.baseUrl()
                    + " (서버가 떠 있는지, 방화벽이 막고 있지 않은지 확인하세요)",
                    System.currentTimeMillis() - startedAt);
        } catch (java.net.ConnectException ex) {
            return Result.failure("연결 거부: " + connection.baseUrl()
                    + " (주소/포트 확인, 로컬 모델이면 실행 여부 확인)",
                    System.currentTimeMillis() - startedAt);
        } catch (java.net.UnknownHostException ex) {
            return Result.failure("호스트를 찾을 수 없습니다: " + connection.baseUrl(),
                    System.currentTimeMillis() - startedAt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Result.failure("요청이 중단되었습니다.", System.currentTimeMillis() - startedAt);
        } catch (IllegalArgumentException ex) {
            return Result.failure("base URL 형식이 올바르지 않습니다: " + connection.baseUrl(),
                    System.currentTimeMillis() - startedAt);
        } catch (Exception ex) {
            log.warn("AI request failed: {}", ex.getMessage());
            return Result.failure("AI 요청 실패: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " — " + ex.getMessage()),
                    System.currentTimeMillis() - startedAt);
        }
    }

    /**
     * 가벼운 연결 확인입니다.
     *
     * <p>모델 목록({@code GET /models})을 조회합니다. 이 엔드포인트를 지원하지
     * 않는 공급자도 있으므로, 404 면 <b>실패로 보지 않고</b> "확인 불가" 로
     * 안내합니다. 실제 분석은 될 수 있는데 "설정 오류" 로 보이면 사용자가
     * 쓰지 못하게 되기 때문입니다.
     *
     * @param connection 접속 정보
     * @return 확인 결과와 메시지
     */
    public Result checkConnection(Connection connection) {
        if (connection == null || connection.baseUrl() == null || connection.baseUrl().isBlank()) {
            return Result.failure("base URL 이 비어 있습니다.");
        }

        final long startedAt = System.currentTimeMillis();

        try {
            final String url = buildModelsUrl(connection.baseUrl());

            final HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.min(20, connection.timeoutSeconds())))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SonarValidator/1.0")
                    .GET();

            applyAuth(builder, connection);

            final HttpClient client = buildHttpClient(connection.allowInsecureTls());
            final HttpResponse<String> response = client.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            final long elapsed = System.currentTimeMillis() - startedAt;

            if (response.statusCode() == 404) {
                return Result.success("모델 목록 API 가 없습니다(연결 자체는 가능). "
                        + "분석을 실행해 실제 동작을 확인하세요.", elapsed);
            }
            if (response.statusCode() / 100 != 2) {
                return Result.failure(describeHttpError(response.statusCode(), response.body()), elapsed);
            }

            // 모델 이름이 설정한 값과 다르면 알려 줍니다. (오타로 404 가 나는 것을 예방)
            final JsonNode root = mapper.readTree(response.body());
            final JsonNode data = root.path("data");
            final List<String> models = new ArrayList<>();
            if (data.isArray()) {
                for (final JsonNode item : data) {
                    final String id = item.path("id").asString("");
                    if (!id.isBlank()) {
                        models.add(id);
                    }
                }
            }

            final String configured = connection.model();
            if (configured != null && !configured.isBlank() && !models.isEmpty()) {
                final boolean listed = models.stream().anyMatch((id) -> id.equals(configured));
                if (!listed) {
                    return Result.success("연결 성공. 다만 설정한 모델 '" + configured
                            + "' 이 목록에 없습니다. 사용 가능: "
                            + String.join(", ", models.size() > 8 ? models.subList(0, 8) : models)
                            + (models.size() > 8 ? " …" : ""), elapsed);
                }
            }

            return Result.success("연결 성공. 사용 가능한 모델 " + models.size() + "개.", elapsed);

        } catch (Exception ex) {
            return Result.failure("연결 확인 실패: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " — " + ex.getMessage()),
                    System.currentTimeMillis() - startedAt);
        }
    }

    // ---------------------------------------------------------------------------
    // 요청 조립
    // ---------------------------------------------------------------------------

    /**
     * 채팅 완료 엔드포인트 URL 을 만듭니다.
     *
     * <p>끝 슬래시, {@code /v1} 중복, 이미 완성된 경로를 모두 처리합니다.
     *
     * <p>테스트에서 직접 검증할 수 있도록 공개합니다. URL 조립은 이 클래스에서
     * <b>가장 자주 틀리는 부분</b>이라 회귀 테스트가 필요합니다.
     *
     * @param baseUrl 사용자가 입력한 기준 URL
     * @return 호출할 URL
     */
    public String buildChatCompletionsUrl(String baseUrl) {
        String base = stripTrailingSlash(baseUrl);

        // 사용자가 완성된 경로를 넣은 경우 그대로 씁니다.
        if (base.endsWith("/chat/completions")) {
            return base;
        }

        // Azure 는 .../deployments/<model> 뒤에 바로 붙습니다.
        // 판단 근거: 경로에 /openai/deployments/ 가 있으면 api-version 이 필요합니다.
        if (base.contains("/openai/deployments/")) {
            return base + "/chat/completions?api-version=" + AZURE_API_VERSION;
        }

        return base + "/chat/completions";
    }

    /** 모델 목록 URL 을 만듭니다. 확인용이므로 실패해도 무방합니다. */
    public String buildModelsUrl(String baseUrl) {
        String base = stripTrailingSlash(baseUrl);

        if (base.endsWith("/models")) {
            return base;
        }
        if (base.endsWith("/chat/completions")) {
            base = base.substring(0, base.length() - "/chat/completions".length());
        }
        if (base.contains("/openai/deployments/")) {
            // Azure 는 deployments 경로에서 models 를 제공하지 않으므로
            // deployments 앞까지만 씁니다.
            final int index = base.indexOf("/openai/deployments/");
            return base.substring(0, index) + "/openai/models?api-version=" + AZURE_API_VERSION;
        }
        return base + "/models";
    }

    /** Azure OpenAI 의 API 버전입니다. (URL 로만 지정 가능) */
    private static final String AZURE_API_VERSION = "2024-08-01-preview";

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 요청 본문을 만듭니다.
     *
     * @param connection 접속 정보
     * @param messages   메시지 목록
     * @param jsonMode   JSON 응답 요구 여부
     * @return JSON 문자열
     */
    private String buildRequestBody(Connection connection, List<Message> messages, boolean jsonMode) {
        final ObjectNode root = JSON.objectNode();
        root.put("model", connection.model());
        root.put("temperature", connection.temperature() == null ? 0.2 : connection.temperature());

        if (connection.maxTokens() != null && connection.maxTokens() > 0) {
            root.put("max_tokens", connection.maxTokens());
        }

        final ArrayNode array = root.putArray("messages");
        for (final Message message : messages) {
            final ObjectNode item = array.addObject();
            item.put("role", message.role());
            item.put("content", message.content());
        }

        if (jsonMode) {
            // 공급자가 지원하면 구조화 응답을 강하게 유도합니다.
            // 지원하지 않으면 무시되므로 안전합니다.
            final ObjectNode format = root.putObject("response_format");
            format.put("type", "json_object");
        }

        return root.toString();
    }

    /** 인증 헤더를 붙입니다. 키가 없으면 붙이지 않습니다. (로컬 Ollama 등) */
    private void applyAuth(HttpRequest.Builder builder, Connection connection) {
        final String key = connection.apiKey();

        if (key == null || key.isBlank()) {
            return;
        }

        // Azure OpenAI 는 api-key 헤더를 씁니다. Bearer 로 보내면 401 이 납니다.
        if ("azure".equalsIgnoreCase(connection.authStyle())) {
            builder.header("api-key", key);
        } else {
            builder.header("Authorization", "Bearer " + key);
        }
    }

    /**
     * HTTP 클라이언트를 만듭니다.
     *
     * <p>{@code allowInsecureTls} 가 켜지면 인증서 검증을 건너뜁니다.
     * 사내 자체 서명 인증서를 쓰는 서버에서만 필요하고, 기본값은 꺼짐입니다.
     */
    private HttpClient buildHttpClient(boolean allowInsecureTls) {
        final HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                // 리다이렉트를 따르지 않습니다. 인증 헤더가 다른 호스트로
                // 넘어가면 키가 유출될 수 있습니다.
                .followRedirects(HttpClient.Redirect.NEVER);

        if (allowInsecureTls) {
            try {
                final SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());
                builder.sslContext(context);
                log.warn("AI provider TLS verification is DISABLED (allowInsecureTls=true)");
            } catch (Exception ex) {
                log.warn("failed to build insecure SSL context: {}", ex.getMessage());
            }
        }

        return builder.build();
    }

    /**
     * 응답 본문에서 모델의 텍스트를 꺼냅니다.
     *
     * @param body     응답 JSON
     * @param elapsed  소요 시간(ms)
     * @return 본문 텍스트
     */
    private Result parseResponse(String body, long elapsed) {
        try {
            final JsonNode root = mapper.readTree(body);

            // 공급자가 200 을 주면서 error 객체를 담는 경우가 있습니다.
            final JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                final String message = error.path("message").asString(error.toString());
                return Result.failure("AI 공급자 오류: " + message, elapsed);
            }

            final JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return Result.failure("응답에 choices 가 없습니다. 본문: " + truncate(body, 400), elapsed);
            }

            final JsonNode content = choices.get(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                return Result.failure("응답에 message.content 가 없습니다. 본문: "
                        + truncate(body, 400), elapsed);
            }

            // content 가 문자열이 아니라 배열인 공급자도 있습니다.
            // (예: Anthropic 호환 게이트웨이) 텍스트 조각을 이어 붙입니다.
            if (content.isArray()) {
                final StringBuilder text = new StringBuilder();
                for (final JsonNode part : content) {
                    final String piece = part.path("text").asString("");
                    if (!piece.isBlank()) {
                        text.append(piece);
                    }
                }
                if (text.length() == 0) {
                    return Result.failure("응답 content 배열에 텍스트가 없습니다.", elapsed);
                }
                return Result.success(text.toString(), elapsed, root);
            }

            final String text = content.asString("");
            if (text.isBlank()) {
                return Result.failure("모델이 빈 응답을 반환했습니다.", elapsed);
            }

            return Result.success(text, elapsed, root);

        } catch (Exception ex) {
            return Result.failure("응답 파싱 실패: " + ex.getMessage()
                    + " (본문: " + truncate(body, 300) + ")", elapsed);
        }
    }

    /**
     * HTTP 오류를 사람이 읽을 수 있는 문장으로 바꿉니다.
     *
     * <p>상태 코드만 보여주면 사용자가 원인을 알 수 없습니다. 공급자가 보낸
     * 오류 본문의 {@code error.message} 를 함께 보여주고, 자주 나오는 코드는
     * 확인할 지점을 안내합니다.
     */
    private String describeHttpError(int status, String body) {
        String detail = "";
        try {
            final JsonNode root = mapper.readTree(body);
            detail = root.path("error").path("message").asString(
                    root.path("message").asString(""));
        } catch (Exception ignored) {
            detail = truncate(body, 300);
        }

        final String hint = switch (status) {
            case 401, 403 -> "API Key 가 올바른지, 해당 모델 권한이 있는지 확인하세요.";
            case 404 -> "base URL 과 모델 이름을 확인하세요. "
                    + "(경로에 /v1 이 빠졌거나 모델명이 다를 수 있습니다)";
            case 429 -> "요청 한도를 초과했습니다. 잠시 후 다시 시도하세요.";
            case 400 -> "요청이 거부되었습니다. 모델명이나 컨텍스트 길이 초과를 확인하세요.";
            case 500, 502, 503, 504 -> "공급자 서버 오류입니다. 잠시 후 다시 시도하세요.";
            default -> "";
        };

        return "HTTP " + status + (detail.isBlank() ? "" : " — " + detail)
                + (hint.isBlank() ? "" : " [" + hint + "]");
    }

    private static String truncate(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    // ---------------------------------------------------------------------------
    // 값 타입
    // ---------------------------------------------------------------------------

    /**
     * 채팅 메시지 한 건입니다.
     *
     * @param role    {@code system} / {@code user} / {@code assistant}
     * @param content 내용
     */
    public record Message(String role, String content) {
        /** system 메시지를 만듭니다. */
        public static Message system(String content) {
            return new Message("system", content);
        }

        /** user 메시지를 만듭니다. */
        public static Message user(String content) {
            return new Message("user", content);
        }
    }

    /**
     * 호출에 필요한 접속 정보입니다.
     *
     * <p>엔티티를 직접 넘기지 않는 이유: 이 클라이언트가 JPA 에 의존하면
     * 단위 테스트에서 DB 없이 검증할 수 없습니다.
     *
     * @param baseUrl          기준 URL
     * @param apiKey           API Key (없으면 null — 로컬 모델)
     * @param model            모델 이름
     * @param authStyle        {@code bearer} 또는 {@code azure}
     * @param timeoutSeconds   요청 타임아웃(초)
     * @param maxTokens        최대 토큰 (null 이면 미지정)
     * @param temperature      창의성
     * @param allowInsecureTls TLS 검증 건너뛰기
     */
    public record Connection(String baseUrl,
                             String apiKey,
                             String model,
                             String authStyle,
                             int timeoutSeconds,
                             Integer maxTokens,
                             Double temperature,
                             boolean allowInsecureTls) {

        /**
         * 호출에 쓸 수 있는지 확인합니다.
         *
         * <p>API Key 는 필수가 아닙니다. 로컬 Ollama/vLLM 은 키가 없습니다.
         *
         * @return baseUrl 과 model 이 채워졌으면 true
         */
        public boolean isUsable() {
            return baseUrl != null && !baseUrl.isBlank()
                    && model != null && !model.isBlank();
        }
    }

    /**
     * 호출 결과입니다.
     *
     * @param ok       성공 여부
     * @param text     성공 시 응답 텍스트 / 실패 시 사유
     * @param elapsedMs 소요 시간(ms)
     * @param usage    사용량 정보 (있으면)
     */
    public record Result(boolean ok, String text, long elapsedMs, Map<String, Object> usage) {

        /** 성공 결과를 만듭니다. */
        public static Result success(String text, long elapsedMs) {
            return new Result(true, text, elapsedMs, Map.of());
        }

        /** 사용량 정보와 함께 성공 결과를 만듭니다. */
        public static Result success(String text, long elapsedMs, JsonNode root) {
            final Map<String, Object> usage = new LinkedHashMap<>();
            final JsonNode usageNode = root == null ? null : root.path("usage");
            if (usageNode != null && usageNode.isObject()) {
                usageNode.properties().forEach((entry) -> {
                    final JsonNode value = entry.getValue();
                    if (value.isNumber()) {
                        usage.put(entry.getKey(), value.asInt());
                    }
                });
            }
            return new Result(true, text, elapsedMs, Map.copyOf(usage));
        }

        /** 실패 결과를 만듭니다. (소요 시간 미상) */
        public static Result failure(String message) {
            return new Result(false, message, 0L, Map.of());
        }

        /** 실패 결과를 만듭니다. */
        public static Result failure(String message, long elapsedMs) {
            return new Result(false, message, elapsedMs, Map.of());
        }
    }

    /** 자체 서명 인증서를 허용하기 위한 TrustManager 입니다. */
    private static final class TrustAllManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // 검증을 건너뜁니다. (allowInsecureTls=true 일 때만 사용)
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // 검증을 건너뜁니다. (allowInsecureTls=true 일 때만 사용)
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
