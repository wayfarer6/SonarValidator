package org.sonar.sonarvalidator_backend.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Service.ai.AiProviderService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 공급자 설정 API 입니다.
 *
 * <h2>왜 설정을 DB + REST 로 두는가</h2>
 * <p>AI 공급자는 배포 환경마다 다르고(사내 vLLM, Ollama, OpenAI 등), 운영 중에
 * 자주 바뀝니다. 프로퍼티로 두면 바꿀 때마다 재기동이 필요하고, 여러 공급자를
 * 오가며 쓸 수 없습니다.
 *
 * <h2>⚠️ API Key 노출 방지</h2>
 * <p>조회 응답에는 키 원문이 <b>절대</b> 포함되지 않습니다.
 * {@code api_key_masked} 는 고정 문자열이고, {@code has_api_key} 로
 * "설정돼 있는지" 만 알려 줍니다.
 *
 * <p>수정 시 키를 비워 보내면 기존 값이 유지됩니다. 이 규칙이 없으면
 * "키를 바꾸지 않으려고 비웠더니 키가 지워지는" 사고가 납니다.
 *
 * <h2>엔드포인트</h2>
 * <pre>
 *   GET    /api/v1/ai/providers                   전체 목록 (마스킹)
 *   GET    /api/v1/ai/providers/enabled           사용 중인 것만 (분석 대상 선택용)
 *   GET    /api/v1/ai/providers/{id}              단건
 *   POST   /api/v1/ai/providers                   생성/수정 (id 없으면 생성)
 *   DELETE /api/v1/ai/providers/{id}              삭제
 *   POST   /api/v1/ai/providers/{id}/check        연결 확인
 *   POST   /api/v1/ai/providers/{id}/enabled      사용 여부 토글
 *   POST   /api/v1/ai/providers/{id}/default      기본 공급자 지정
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ai/providers")
public class AiProviderController {

    private final AiProviderService providerService;

    /**
     * @param providerService AI 공급자 서비스
     */
    public AiProviderController(AiProviderService providerService) {
        this.providerService = providerService;
    }

    /**
     * 모든 AI 공급자를 조회합니다.
     *
     * @return {@code {"total": n, "providers": [...]}}
     */
    @GetMapping
    public Map<String, Object> list() {
        final List<Map<String, Object>> providers = providerService.list();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", providers.size());
        body.put("providers", providers);
        body.put("default_base_url", AiProviderService.DEFAULT_BASE_URL);
        return body;
    }

    /**
     * 사용 중인 AI 공급자만 조회합니다.
     *
     * <p>로그 분석 화면의 공급자 선택 드롭다운이 씁니다.
     *
     * @return {@code {"total": n, "providers": [...]}}
     */
    @GetMapping("/enabled")
    public Map<String, Object> listEnabled() {
        final List<Map<String, Object>> providers = providerService.listEnabled();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", providers.size());
        body.put("providers", providers);
        return body;
    }

    /**
     * AI 공급자 한 건을 조회합니다.
     *
     * @param id 공급자 키
     * @return 공급자 정보 (없으면 404)
     */
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        return providerService.get(id).orElseThrow(
                () -> new ProviderNotFoundException("AI 공급자를 찾을 수 없습니다: " + id));
    }

    /**
     * AI 공급자를 생성하거나 수정합니다.
     *
     * <p>본문 필드:
     * <pre>
     *   {
     *     "id": 3,                          // 없으면 생성
     *     "name": "사내 vLLM",
     *     "base_url": "http://10.0.0.5:8000/v1",
     *     "api_key": "sk-...",              // 비우면 기존 유지
     *     "model": "Qwen2.5-14B-Instruct",
     *     "auth_style": "bearer",           // 또는 "azure"
     *     "system_prompt": "우리 조직 규칙…",
     *     "timeout_seconds": 180,
     *     "max_tokens": 2048,
     *     "temperature": 0.2,
     *     "allow_insecure_tls": false,
     *     "is_default": true,
     *     "enabled": true
     *   }
     * </pre>
     *
     * @param body 요청 본문
     * @return 저장된 공급자
     */
    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        final Long id = asLong(body.get("id"));

        try {
            return providerService.save(
                    id,
                    asString(body.get("name")),
                    asString(body.get("base_url")),
                    asString(body.get("api_key")),
                    asString(body.get("model")),
                    asString(body.get("auth_style")),
                    asString(body.get("system_prompt")),
                    asInt(body.get("timeout_seconds")),
                    asInt(body.get("max_tokens")),
                    asDouble(body.get("temperature")),
                    asBool(body.get("allow_insecure_tls")),
                    asBool(body.get("is_default")),
                    asBool(body.get("enabled")));
        } catch (IllegalArgumentException ex) {
            // 필수 값 누락/이름 중복은 사용자 입력 문제이므로 400 으로 돌려줍니다.
            throw new InvalidProviderRequest(ex.getMessage());
        }
    }

    /**
     * AI 공급자를 삭제합니다.
     *
     * @param id 공급자 키
     * @return 삭제 결과
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        try {
            providerService.delete(id);
        } catch (IllegalArgumentException ex) {
            throw new ProviderNotFoundException(ex.getMessage());
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", true);
        body.put("id", id);
        return body;
    }

    /**
     * 공급자 연결을 확인합니다.
     *
     * <p>저장만 하고 끝내면 운영자가 "등록했다" 고 믿고 넘어갔다가 분석
     * 단계에서야 처음 실패를 봅니다.
     *
     * @param id 공급자 키
     * @return {@code {ok, message, elapsed_ms}}
     */
    @PostMapping("/{id}/check")
    public Map<String, Object> check(@PathVariable Long id) {
        try {
            return providerService.check(id);
        } catch (IllegalArgumentException ex) {
            throw new ProviderNotFoundException(ex.getMessage());
        }
    }

    /**
     * 사용 여부를 토글합니다.
     *
     * @param id      공급자 키
     * @param enabled 사용 여부 (기본 true)
     * @return 수정된 공급자
     */
    @PostMapping("/{id}/enabled")
    public Map<String, Object> setEnabled(@PathVariable Long id,
                                          @RequestParam(name = "enabled", defaultValue = "true")
                                          boolean enabled) {
        try {
            return providerService.setEnabled(id, enabled);
        } catch (IllegalArgumentException ex) {
            throw new ProviderNotFoundException(ex.getMessage());
        }
    }

    /**
     * 기본 공급자로 지정합니다.
     *
     * <p>기본 공급자는 하나만 유지됩니다. (지정하면 나머지가 자동 해제)
     *
     * @param id 공급자 키
     * @return 수정된 공급자
     */
    @PostMapping("/{id}/default")
    public Map<String, Object> setDefault(@PathVariable Long id) {
        try {
            return providerService.setDefault(id);
        } catch (IllegalArgumentException ex) {
            throw new ProviderNotFoundException(ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------------
    // 본문 파싱 유틸
    //
    // Map<String,Object> 로 받는 이유: 프론트가 보내는 필드가 늘어나도
    // DTO 클래스를 매번 고치지 않아도 됩니다. 대신 여기서 타입을 안전하게
    // 변환합니다. (null / 문자열 숫자 / 불리언 문자열 모두 허용)
    // ---------------------------------------------------------------------------

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer asInt(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double asDouble(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Double.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Boolean asBool(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        final String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "on".equalsIgnoreCase(text);
    }

    /**
     * 공급자를 찾지 못했을 때 404 를 내기 위한 예외입니다.
     *
     * @param message 사유
     */
    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class ProviderNotFoundException extends RuntimeException {
        /**
         * @param message 사유
         */
        public ProviderNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * 입력이 잘못됐을 때 400 을 내기 위한 예외입니다.
     *
     * @param message 사유
     */
    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.BAD_REQUEST)
    public static class InvalidProviderRequest extends RuntimeException {
        /**
         * @param message 사유
         */
        public InvalidProviderRequest(String message) {
            super(message);
        }
    }
}
