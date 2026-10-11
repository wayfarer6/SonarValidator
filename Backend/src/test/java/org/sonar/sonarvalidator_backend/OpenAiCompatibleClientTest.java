package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient.Connection;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient.Message;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient.Result;

/**
 * OpenAI 호환 클라이언트의 URL 조립과 방어 로직을 검증합니다.
 *
 * <h2>여기서 잡으려는 실패 모드</h2>
 * <ul>
 *   <li><b>baseUrl 중복/누락</b> — 사용자는 {@code https://api.openai.com},
 *       {@code .../v1}, {@code .../v1/} 을 모두 입력합니다. 그대로 이어 붙이면
 *       {@code /v1/v1/chat/completions} 나 {@code //chat/completions} 가 되어
 *       404 가 나는데, 메시지가 불친절해 원인을 찾기 어렵습니다.</li>
 *   <li><b>키 없는 로컬 모델</b> — Ollama/vLLM 은 API Key 가 없습니다.
 *       키를 필수로 만들면 로컬 모델을 쓸 수 없습니다.</li>
 *   <li><b>설정 불완전 시 조용한 실패</b> — 호출 전에 검증해야
 *       무의미한 네트워크 요청을 보내지 않습니다.</li>
 * </ul>
 *
 * <p>실제 네트워크 호출은 하지 않습니다. (외부 의존 없이 CI 에서 실행)
 */
class OpenAiCompatibleClientTest {

    private final OpenAiCompatibleClient client = new OpenAiCompatibleClient();

    // ---------------------------------------------------------------------------
    // URL 조립 (가장 자주 틀리는 부분)
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("표준 baseUrl 에 경로를 붙인다")
    void buildsStandardUrl() {
        assertEquals("https://api.openai.com/v1/chat/completions",
                client.buildChatCompletionsUrl("https://api.openai.com/v1"));
    }

    @Test
    @DisplayName("끝 슬래시가 있어도 중복 슬래시가 생기지 않는다")
    void handlesTrailingSlash() {
        assertEquals("https://api.openai.com/v1/chat/completions",
                client.buildChatCompletionsUrl("https://api.openai.com/v1/"));

        assertEquals("https://api.openai.com/v1/chat/completions",
                client.buildChatCompletionsUrl("https://api.openai.com/v1///"));
    }

    @Test
    @DisplayName("공백이 섞여도 정리한다 (붙여넣기 실수)")
    void trimsWhitespace() {
        assertEquals("http://localhost:11434/v1/chat/completions",
                client.buildChatCompletionsUrl("  http://localhost:11434/v1  "));
    }

    @Test
    @DisplayName("이미 완성된 경로를 주면 그대로 쓴다")
    void keepsCompletePath() {
        // 일부 사용자는 전체 URL 을 붙여넣습니다. 그때 /v1 을 또 붙이면 404 입니다.
        final String complete = "https://api.openai.com/v1/chat/completions";
        assertEquals(complete, client.buildChatCompletionsUrl(complete));
    }

    @Test
    @DisplayName("Ollama 로컬 주소를 그대로 지원한다")
    void supportsLocalOllama() {
        assertEquals("http://192.168.122.58:11434/v1/chat/completions",
                client.buildChatCompletionsUrl("http://192.168.122.58:11434/v1"));
    }

    @Test
    @DisplayName("Azure 배포 경로에는 api-version 을 붙인다")
    void supportsAzureDeploymentPath() {
        final String url = client.buildChatCompletionsUrl(
                "https://my-res.openai.azure.com/openai/deployments/gpt-4o");

        // Azure 는 api-version 쿼리가 없으면 400 을 냅니다.
        assertTrue(url.endsWith("/chat/completions?api-version=2024-08-01-preview"), url);
    }

    @Test
    @DisplayName("모델 목록 URL 도 중복 없이 만든다")
    void buildsModelsUrl() {
        assertEquals("https://api.openai.com/v1/models",
                client.buildModelsUrl("https://api.openai.com/v1"));

        assertEquals("https://api.openai.com/v1/models",
                client.buildModelsUrl("https://api.openai.com/v1/models"));

        // 완성된 chat 경로에서 되돌아오는 경우
        assertEquals("https://api.openai.com/v1/models",
                client.buildModelsUrl("https://api.openai.com/v1/chat/completions"));
    }

    // ---------------------------------------------------------------------------
    // 설정 검증
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("baseUrl 과 model 이 없으면 네트워크 요청 없이 실패한다")
    void rejectsIncompleteConnection() {
        final Connection noBase = new Connection("", "key", "gpt-4o", "bearer",
                30, null, 0.2, false);
        final Result result = client.chat(noBase, java.util.List.of(Message.user("hi")), false);

        assertFalse(result.ok());
        assertTrue(result.text().contains("불완전"), result.text());
    }

    @Test
    @DisplayName("API Key 없는 로컬 모델은 유효한 설정으로 본다")
    void allowsKeylessLocalModel() {
        // Ollama/vLLM 은 키가 없습니다. 키를 필수로 만들면 로컬 모델을 못 씁니다.
        final Connection local = new Connection("http://localhost:11434/v1", null,
                "llama3.1:8b", "bearer", 30, null, 0.2, false);

        assertTrue(local.isUsable());
    }

    @Test
    @DisplayName("model 이 비면 사용할 수 없는 설정이다")
    void requiresModel() {
        final Connection noModel = new Connection("http://localhost:11434/v1", null,
                "  ", "bearer", 30, null, 0.2, false);

        assertFalse(noModel.isUsable());
    }

    // ---------------------------------------------------------------------------
    // 결과 타입
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("실패 결과는 사유를 담는다")
    void failureCarriesReason() {
        final Result result = Result.failure("키가 만료되었습니다.");

        assertFalse(result.ok());
        assertEquals("키가 만료되었습니다.", result.text());
    }

    @Test
    @DisplayName("성공 결과는 본문과 소요 시간을 담는다")
    void successCarriesText() {
        final Result result = Result.success("{\"risk_level\":\"LOW\"}", 1234L);

        assertTrue(result.ok());
        assertEquals("{\"risk_level\":\"LOW\"}", result.text());
        assertEquals(1234L, result.elapsedMs());
    }

    // ---------------------------------------------------------------------------
    // 메시지 팩토리
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("메시지 역할이 올바르게 만들어진다")
    void messageFactories() {
        assertEquals("system", Message.system("지침").role());
        assertEquals("user", Message.user("질문").role());
        assertEquals("지침", Message.system("지침").content());
    }

    @Test
    @DisplayName("null 연결은 예외 없이 실패 결과를 돌려준다")
    void handlesNullConnection() {
        // 예외를 던지면 호출측이 매번 try/catch 로 감싸야 합니다.
        final Result result = client.chat(null, java.util.List.of(), false);
        assertFalse(result.ok());
    }
}
