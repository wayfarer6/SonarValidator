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

@RestController
@RequestMapping("/api/v1/ai/providers")
public class AiProviderController {

    private final AiProviderService providerService;

    public AiProviderController(AiProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public Map<String, Object> list() {
        final List<Map<String, Object>> providers = providerService.list();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", providers.size());
        body.put("providers", providers);
        body.put("default_base_url", AiProviderService.DEFAULT_BASE_URL);
        return body;
    }

    @GetMapping("/enabled")
    public Map<String, Object> listEnabled() {
        final List<Map<String, Object>> providers = providerService.listEnabled();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", providers.size());
        body.put("providers", providers);
        return body;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        return providerService.get(id).orElseThrow(
                () -> new ProviderNotFoundException("AI 공급자를 찾을 수 없습니다: " + id));
    }

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

    @PostMapping("/{id}/check")
    public Map<String, Object> check(@PathVariable Long id) {
        try {
            return providerService.check(id);
        } catch (IllegalArgumentException ex) {
            throw new ProviderNotFoundException(ex.getMessage());
        }
    }

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

    @PostMapping("/{id}/default")
    public Map<String, Object> setDefault(@PathVariable Long id) {
        try {
            return providerService.setDefault(id);
        } catch (IllegalArgumentException ex) {
            throw new ProviderNotFoundException(ex.getMessage());
        }
    }


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

    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class ProviderNotFoundException extends RuntimeException {
        public ProviderNotFoundException(String message) {
            super(message);
        }
    }

    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.BAD_REQUEST)
    public static class InvalidProviderRequest extends RuntimeException {
        public InvalidProviderRequest(String message) {
            super(message);
        }
    }
}
