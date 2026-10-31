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

@Component
public class OpenAiCompatibleClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ObjectMapper mapper = new ObjectMapper();

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

    // 요청 조립

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

    private static final String AZURE_API_VERSION = "2024-08-01-preview";

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

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

    // 값 타입

    public record Message(String role, String content) {
        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }
    }

    public record Connection(String baseUrl,
                             String apiKey,
                             String model,
                             String authStyle,
                             int timeoutSeconds,
                             Integer maxTokens,
                             Double temperature,
                             boolean allowInsecureTls) {

        public boolean isUsable() {
            return baseUrl != null && !baseUrl.isBlank()
                    && model != null && !model.isBlank();
        }
    }

    public record Result(boolean ok, String text, long elapsedMs, Map<String, Object> usage) {

        public static Result success(String text, long elapsedMs) {
            return new Result(true, text, elapsedMs, Map.of());
        }

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

        public static Result failure(String message) {
            return new Result(false, message, 0L, Map.of());
        }

        public static Result failure(String message, long elapsedMs) {
            return new Result(false, message, elapsedMs, Map.of());
        }
    }

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
