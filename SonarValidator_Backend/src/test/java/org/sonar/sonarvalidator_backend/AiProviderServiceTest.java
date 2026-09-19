package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.entity.AiProvider;
import org.sonar.sonarvalidator_backend.Repository.AiProviderRepository;
import org.sonar.sonarvalidator_backend.Service.ai.AiProviderService;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient;
import org.sonar.sonarvalidator_backend.Service.secret.SecretCipher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * AI 공급자 설정 저장 규칙을 검증합니다.
 *
 * <h2>여기서 잡으려는 실패 모드</h2>
 * <ul>
 *   <li><b>부분 수정이 기본 플래그를 지우는 문제</b> — 수정 화면이 일부 필드만
 *       보냈을 때 기본 공급자가 하나도 없게 되면, 분석이 알파벳순 첫 공급자를
 *       골라 엉뚱한 곳으로 요청을 보냅니다. 실제로 E2E 검증에서 이 문제로
 *       "Bad Provider" 에 요청이 가 연결 거부가 났습니다.</li>
 *   <li><b>API Key 유실</b> — 키를 비워 보내면 기존 값을 유지해야 합니다.</li>
 *   <li><b>키 평문 노출</b> — 저장·응답 어디에도 원문이 담기면 안 됩니다.</li>
 * </ul>
 *
 * <h2>왜 {@code @Import} 로 빈을 가져오는가</h2>
 * <p>{@link SecretCipher} 는 {@code @PostConstruct} 에서 키를 준비합니다.
 * 테스트에서 직접 {@code new SecretCipher("")} 하면 그 초기화가 실행되지 않아
 * {@code InvalidKeyException} 이 납니다. Spring 이 빈으로 만들게 해야
 * 실제 운영과 같은 경로를 검증합니다.
 *
 * <p>H2 인메모리 DB 로 실제 SQL 을 실행합니다. 목을 쓰면 저장 규칙의 미묘한
 * 버그를 잡지 못합니다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({AiProviderService.class, SecretCipher.class, OpenAiCompatibleClient.class})
class AiProviderServiceTest {

    @Autowired
    private AiProviderRepository repository;

    @Autowired
    private AiProviderService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    /**
     * 테스트용 공급자를 만듭니다.
     *
     * @param asDefault {@code null} 이면 "플래그를 보내지 않음" 을 뜻합니다.
     *                  (화면이 일부 필드만 보내는 상황)
     */
    private Map<String, Object> create(String name, String model, String apiKey, Boolean asDefault) {
        return service.save(null, name, "http://localhost:11434/v1", apiKey, model,
                "bearer", null, 60, null, 0.2, false, asDefault, true);
    }

    @Test
    @DisplayName("플래그를 주지 않은 첫 공급자는 자동으로 기본이 된다")
    void firstProviderBecomesDefault() {
        // 설정 직후 바로 쓸 수 있도록 첫 공급자는 기본이어야 합니다.
        final Map<String, Object> saved = create("Ollama", "llama3.1:8b", null, null);

        assertEquals(Boolean.TRUE, saved.get("is_default"));
    }

    @Test
    @DisplayName("기본 플래그는 하나만 유지된다")
    void onlyOneDefaultAtATime() {
        create("First", "model-a", null, true);
        create("Second", "model-b", null, true);

        final List<AiProvider> defaults = repository.findByIsDefaultTrue();
        assertEquals(1, defaults.size(), "기본 공급자는 하나여야 함");
        assertEquals("Second", defaults.get(0).getName());
    }

    @Test
    @DisplayName("부분 수정이 기본 플래그를 지우지 않는다 (실제로 겪은 버그)")
    void partialUpdateKeepsDefaultFlag() {
        final Map<String, Object> created = create("Mock LLM", "mock-model", "sk-key", true);
        final Long id = ((Number) created.get("id")).longValue();

        assertTrue(repository.findById(id).orElseThrow().getIsDefault(),
                "생성 시 기본으로 지정되어야 함");

        // 화면이 일부 필드만 보내는 상황을 흉내냅니다.
        // (타임아웃과 모델만 바꾸고 is_default 는 보내지 않음)
        service.save(id, "Mock LLM", "http://localhost:11434/v1", "", "mock-model-renamed",
                "bearer", null, 90, null, 0.2, false, null, true);

        final AiProvider reloaded = repository.findById(id).orElseThrow();
        assertTrue(reloaded.getIsDefault(),
                "is_default 를 보내지 않았으면 기존 값을 유지해야 함"
                        + " (지워지면 분석이 엉뚱한 공급자를 고릅니다)");
        assertEquals("mock-model-renamed", reloaded.getModel(), "다른 필드는 반영되어야 함");
        assertEquals(90, reloaded.getTimeoutSeconds());
    }

    @Test
    @DisplayName("is_default=false 를 명시하면 해제된다")
    void explicitFalseClearsDefault() {
        final Map<String, Object> created = create("Mock", "m1", null, true);
        final Long id = ((Number) created.get("id")).longValue();

        service.save(id, "Mock", "http://localhost:11434/v1", null, "m1",
                "bearer", null, 60, null, 0.2, false, false, true);

        assertFalse(repository.findById(id).orElseThrow().getIsDefault(),
                "명시적 해제는 반영되어야 함");
    }

    @Test
    @DisplayName("키를 비워 저장하면 기존 키가 유지된다")
    void blankApiKeyKeepsExisting() {
        final Map<String, Object> created = create("Mock", "m1", "sk-original-key", true);
        final Long id = ((Number) created.get("id")).longValue();

        final String encryptedBefore = repository.findById(id).orElseThrow().getApiKeyEncrypted();
        assertTrue(encryptedBefore != null && !encryptedBefore.isBlank(), "키가 저장되어야 함");

        // 빈 문자열로 수정합니다.
        service.save(id, "Mock", "http://localhost:11434/v1", "", "m1",
                "bearer", null, 60, null, 0.2, false, null, true);

        final String encryptedAfter = repository.findById(id).orElseThrow().getApiKeyEncrypted();
        assertEquals(encryptedBefore, encryptedAfter,
                "빈 키로 수정해도 기존 키가 유지되어야 함");
    }

    @Test
    @DisplayName("저장한 키가 그대로 복호화된다 (E2E 에서 키가 살아있는지)")
    void decryptedKeyMatchesOriginal() {
        create("Mock", "m1", "sk-round-trip-key", true);

        final AiProvider provider = repository.findAll().get(0);
        final var connection = service.toConnection(provider);

        // 복호화가 실패하면 null 이 되고, 그러면 401 이 나 원인을 찾기 어렵습니다.
        assertEquals("sk-round-trip-key", connection.apiKey(),
                "저장한 키가 그대로 복호화되어야 함");
    }

    @Test
    @DisplayName("API Key 는 평문으로 저장되지 않는다")
    void apiKeyIsEncrypted() {
        final String plainKey = "sk-super-secret-12345";
        create("Mock", "m1", plainKey, true);

        final String stored = repository.findAll().get(0).getApiKeyEncrypted();
        assertTrue(stored != null && !stored.contains(plainKey),
                "평문 키가 DB 에 남으면 안 됩니다");
        assertFalse(stored.isBlank());
    }

    @Test
    @DisplayName("조회 응답에 API Key 원문이 담기지 않는다")
    void listNeverExposesKey() {
        final String plainKey = "sk-super-secret-12345";
        create("Mock", "m1", plainKey, true);

        final List<Map<String, Object>> providers = service.list();
        assertEquals(1, providers.size());

        final Map<String, Object> view = providers.get(0);
        final String serialized = view.toString();

        assertFalse(serialized.contains(plainKey), "응답에 평문 키가 있으면 안 됩니다");
        assertEquals(Boolean.TRUE, view.get("has_api_key"), "키 존재 여부는 알려야 함");
        assertEquals("********", view.get("api_key_masked"), "마스킹 값만 노출");
    }

    @Test
    @DisplayName("모델 이름이 없으면 거부한다")
    void rejectsMissingModel() {
        // 모델명이 없으면 호출 자체가 불가능하므로 저장 전에 막습니다.
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(null, "Mock", "http://x/v1", null, "  ",
                        "bearer", null, 60, null, 0.2, false, true, true));

        assertTrue(ex.getMessage().contains("모델"), ex.getMessage());
    }

    @Test
    @DisplayName("같은 이름의 공급자를 중복 생성할 수 없다")
    void rejectsDuplicateName() {
        create("Duplicate", "m1", null, null);

        // 같은 이름이 둘이면 화면에서 구분이 불가능합니다.
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> create("Duplicate", "m2", null, null));

        assertTrue(ex.getMessage().contains("같은 이름"), ex.getMessage());
    }

    @Test
    @DisplayName("base URL 이 비면 OpenAI 기본값을 쓴다")
    void defaultsBaseUrl() {
        final Map<String, Object> saved = service.save(null, "Default", "", null, "gpt-4o-mini",
                "bearer", null, 60, null, 0.2, false, true, true);

        assertEquals(AiProviderService.DEFAULT_BASE_URL, saved.get("base_url"));
    }

    @Test
    @DisplayName("base URL 의 끝 슬래시를 제거한다")
    void stripsTrailingSlash() {
        // 저장 시 정리해 두면 호출 경로 조합에서 이중 슬래시를 피할 수 있습니다.
        final Map<String, Object> saved = service.save(null, "Slash", "http://x:8000/v1///",
                null, "m1", "bearer", null, 60, null, 0.2, false, true, true);

        assertEquals("http://x:8000/v1", saved.get("base_url"));
    }

    @Test
    @DisplayName("resolve 는 기본 공급자를 우선 고른다")
    void resolvePrefersDefault() {
        // 알파벳순으로 앞선 공급자를 만들고,
        create("Aaa First", "m1", null, false);
        final Map<String, Object> second = create("Zzz Second", "m2", null, false);

        // 뒤쪽 공급자를 기본으로 지정합니다.
        service.setDefault(((Number) second.get("id")).longValue());

        final AiProvider resolved = service.resolve(null);
        assertEquals("Zzz Second", resolved.getName(),
                "알파벳순이 아니라 기본 플래그를 따라야 함");
    }

    @Test
    @DisplayName("사용 가능한 공급자가 없으면 사유와 함께 예외가 난다")
    void resolveFailsWithClearMessage() {
        // 조용히 실패하면 사용자가 이유를 알 수 없습니다.
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.resolve(null));

        assertTrue(ex.getMessage().contains("AI 공급자"), ex.getMessage());
    }

    @Test
    @DisplayName("비활성 공급자는 resolve 대상에서 제외된다")
    void resolveSkipsDisabled() {
        final Map<String, Object> created = create("Disabled", "m1", null, true);
        final Long id = ((Number) created.get("id")).longValue();

        service.setEnabled(id, false);

        // 기본 플래그가 켜져 있어도 사용 중이 아니면 고르면 안 됩니다.
        assertThrows(IllegalStateException.class, () -> service.resolve(null));
    }
}
