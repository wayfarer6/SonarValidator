package org.sonar.sonarvalidator_backend.Service.ai;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.entity.AiProvider;
import org.sonar.sonarvalidator_backend.Repository.AiProviderRepository;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient.Connection;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient.Result;
import org.sonar.sonarvalidator_backend.Service.secret.SecretCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProviderService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderService.class);

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final AiProviderRepository repository;
    private final SecretCipher secretCipher;
    private final OpenAiCompatibleClient client;

    public AiProviderService(AiProviderRepository repository,
                             SecretCipher secretCipher,
                             OpenAiCompatibleClient client) {
        this.repository = repository;
        this.secretCipher = secretCipher;
        this.client = client;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEnabled() {
        return repository.findByEnabledTrueOrderByNameAsc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> get(Long id) {
        return repository.findById(id).map(this::toView);
    }

    @Transactional
    public Map<String, Object> save(Long id,
                                    String name,
                                    String baseUrl,
                                    String apiKey,
                                    String model,
                                    String authStyle,
                                    String systemPrompt,
                                    Integer timeoutSeconds,
                                    Integer maxTokens,
                                    Double temperature,
                                    Boolean allowInsecureTls,
                                    Boolean isDefault,
                                    Boolean enabled) {

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("모델 이름(model)은 필수입니다.");
        }

        final AiProvider provider = id == null
                ? new AiProvider()
                : repository.findById(id).orElseThrow(
                        () -> new IllegalArgumentException("AI 공급자를 찾을 수 없습니다: " + id));

        final String resolvedName = (name == null || name.isBlank())
                ? "AI Provider"
                : name.trim();

        // 이름 중복을 막습니다. 같은 이름이 둘이면 화면에서 구분이 불가능합니다.
        repository.findByName(resolvedName).ifPresent((existing) -> {
            if (!existing.getId().equals(provider.getId())) {
                throw new IllegalArgumentException("같은 이름의 AI 공급자가 이미 있습니다: " + resolvedName);
            }
        });

        provider.setName(resolvedName);
        // base URL 이 비면 OpenAI 기본값을 씁니다. (가장 흔한 설정)
        provider.setBaseUrl((baseUrl == null || baseUrl.isBlank())
                ? DEFAULT_BASE_URL
                : stripTrailingSlash(baseUrl));
        provider.setModel(model.trim());
        provider.setAuthStyle((authStyle == null || authStyle.isBlank()) ? "bearer" : authStyle.trim());
        provider.setSystemPrompt(systemPrompt);

        if (timeoutSeconds != null && timeoutSeconds > 0) {
            provider.setTimeoutSeconds(Math.min(600, timeoutSeconds));
        }
        provider.setMaxTokens(maxTokens);
        if (temperature != null) {
            // 0~2 범위로 자릅니다. (일부 공급자는 범위 밖이면 400 을 냅니다)
            provider.setTemperature(Math.max(0.0, Math.min(2.0, temperature)));
        }
        provider.setAllowInsecureTls(allowInsecureTls != null && allowInsecureTls);
        provider.setEnabled(enabled == null || enabled);




        if (apiKey != null && !apiKey.isBlank()) {
            provider.setApiKeyEncrypted(secretCipher.encrypt(apiKey.trim()));
        }

        if (provider.getCreatedAt() == null) {
            provider.setCreatedAt(new java.util.Date());
        }
        provider.setUpdatedAt(new java.util.Date());

        // 기본 공급자는 항상 하나만 유지합니다.
        if (Boolean.TRUE.equals(isDefault)) {
            clearDefaultExcept(provider.getId());
            provider.setIsDefault(true);
        } else if (id != null) {
            // 수정에서 플래그를 끄는 경우를 반영합니다.
            provider.setIsDefault(false);
        } else if (provider.getIsDefault() == null) {
            // 첫 공급자는 자동으로 기본이 됩니다. (설정 직후 바로 쓸 수 있게)
            provider.setIsDefault(repository.count() == 0);
        }

        final AiProvider saved = repository.save(provider);
        log.info("AI provider saved: id={} name={} model={} enabled={} default={}",
                saved.getId(), saved.getName(), saved.getModel(),
                saved.getEnabled(), saved.getIsDefault());

        return toView(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("AI 공급자를 찾을 수 없습니다: " + id);
        }
        repository.deleteById(id);
        log.info("AI provider deleted: id={}", id);
    }

    @Transactional
    public Map<String, Object> setEnabled(Long id, boolean enabled) {
        final AiProvider provider = repository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("AI 공급자를 찾을 수 없습니다: " + id));
        provider.setEnabled(enabled);
        provider.setUpdatedAt(new java.util.Date());
        return toView(repository.save(provider));
    }

    @Transactional
    public Map<String, Object> setDefault(Long id) {
        final AiProvider provider = repository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("AI 공급자를 찾을 수 없습니다: " + id));

        clearDefaultExcept(provider.getId());
        provider.setIsDefault(true);
        provider.setUpdatedAt(new java.util.Date());

        final AiProvider saved = repository.save(provider);
        log.info("AI provider set as default: id={} name={}", saved.getId(), saved.getName());
        return toView(saved);
    }

    @Transactional
    public Map<String, Object> check(Long id) {
        final AiProvider provider = repository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("AI 공급자를 찾을 수 없습니다: " + id));

        final Result result = client.checkConnection(toConnection(provider));

        provider.setLastStatus(result.ok() ? "ok" : "failed");
        provider.setLastMessage(result.text());
        provider.setLastCheckedAt(new java.util.Date());
        provider.setUpdatedAt(new java.util.Date());
        repository.save(provider);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", result.ok());
        body.put("message", result.text());
        body.put("elapsed_ms", result.elapsedMs());
        body.put("provider_id", provider.getId());
        body.put("provider_name", provider.getName());
        return body;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public AiProvider resolve(Long providerId) {
        return resolveOrEmpty(providerId).orElseThrow(() -> new IllegalStateException(
                providerId == null
                        ? "사용 가능한 AI 공급자가 없습니다. 설정에서 AI 공급자를 등록하고 '사용' 을 켜세요."
                        : "지정한 AI 공급자를 찾을 수 없습니다: " + providerId));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED,
            readOnly = true)
    public Optional<AiProvider> resolveOrEmpty(Long providerId) {
        if (providerId != null) {
            return repository.findById(providerId);
        }

        // 기본 플래그가 켜진 것 중 사용 가능한 것
        final Optional<AiProvider> defaultProvider = repository.findByIsDefaultTrue().stream()
                .filter((p) -> Boolean.TRUE.equals(p.getEnabled()))
                .findFirst();
        if (defaultProvider.isPresent()) {
            return defaultProvider;
        }

        // 마지막 폴백: 사용 가능한 첫 공급자
        final List<AiProvider> enabled = repository.findByEnabledTrueOrderByNameAsc();
        return enabled.isEmpty() ? Optional.empty() : Optional.of(enabled.get(0));
    }

    public Connection toConnection(AiProvider provider) {
        String apiKey = null;
        if (provider.getApiKeyEncrypted() != null && !provider.getApiKeyEncrypted().isBlank()) {
            try {
                apiKey = secretCipher.decrypt(provider.getApiKeyEncrypted());
            } catch (RuntimeException ex) {
                // 복호화 실패(키가 바뀐 경우)를 삼키고 키 없이 시도합니다.
                // 그러면 401 이 나고, 그 메시지가 사용자에게 보입니다.
                log.warn("failed to decrypt api key for provider {}: {}",
                        provider.getId(), ex.getMessage());
            }
        }

        return new Connection(
                provider.getBaseUrl(),
                apiKey,
                provider.getModel(),
                provider.getAuthStyle(),
                provider.getTimeoutSeconds() == null ? 120 : provider.getTimeoutSeconds(),
                provider.getMaxTokens(),
                provider.getTemperature(),
                Boolean.TRUE.equals(provider.getAllowInsecureTls()));
    }

    private void clearDefaultExcept(Long keepId) {
        for (final AiProvider other : repository.findByIsDefaultTrue()) {
            if (keepId != null && keepId.equals(other.getId())) {
                continue;
            }
            other.setIsDefault(false);
            repository.save(other);
        }
    }

    private Map<String, Object> toView(AiProvider provider) {
        final Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", provider.getId());
        view.put("name", provider.getName());
        view.put("base_url", provider.getBaseUrl());
        view.put("model", provider.getModel());
        view.put("auth_style", provider.getAuthStyle());
        view.put("system_prompt", provider.getSystemPrompt());
        view.put("timeout_seconds", provider.getTimeoutSeconds());
        view.put("max_tokens", provider.getMaxTokens());
        view.put("temperature", provider.getTemperature());
        view.put("allow_insecure_tls", Boolean.TRUE.equals(provider.getAllowInsecureTls()));
        view.put("is_default", Boolean.TRUE.equals(provider.getIsDefault()));
        view.put("enabled", Boolean.TRUE.equals(provider.getEnabled()));
        view.put("has_api_key", provider.getApiKeyEncrypted() != null
                && !provider.getApiKeyEncrypted().isBlank());
        view.put("api_key_masked", maskApiKey(provider.getApiKeyEncrypted()));
        view.put("last_status", provider.getLastStatus());
        view.put("last_message", provider.getLastMessage());
        view.put("last_checked_at", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(provider.getLastCheckedAt()));
        view.put("created_at", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(provider.getCreatedAt()));
        view.put("updated_at", provider.getUpdatedAt());
        return view;
    }

    private String maskApiKey(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return "";
        }
        return "********";
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
