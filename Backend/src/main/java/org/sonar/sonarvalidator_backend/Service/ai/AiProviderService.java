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

/**
 * AI 공급자 설정을 DB 에 저장/조회합니다.
 *
 * <h2>⚠️ API Key 취급 원칙</h2>
 * <ol>
 *   <li>저장은 항상 <b>AES-256-GCM 암호화</b> 후 합니다
 *       ({@link SecretCipher}). 평문으로 DB 에 남기지 않습니다.</li>
 *   <li>조회 응답에는 <b>마스킹된 값만</b> 담습니다 ({@code sk-…AbCd}).
 *       원문을 내보내면 화면/로그/브라우저 기록 어디에든 남을 수 있습니다.</li>
 *   <li>수정 시 키를 <b>비워서</b> 보내면 기존 키를 유지합니다.
 *       "키를 바꾸지 않으려면 다시 입력해야 하는" 불편을 없애기 위함입니다.
 *       (키가 필요한데 빈 문자열을 보내면 지워지는 것으로 오해하면
 *        설정이 조용히 깨집니다)</li>
 * </ol>
 */
@Service
public class AiProviderService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderService.class);

    /** OpenAI 호환 기본 base URL 입니다. (사용자가 바꿀 수 있음) */
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final AiProviderRepository repository;
    private final SecretCipher secretCipher;
    private final OpenAiCompatibleClient client;

    /**
     * @param repository   공급자 저장소
     * @param secretCipher 시크릿 암호화기
     * @param client       OpenAI 호환 클라이언트
     */
    public AiProviderService(AiProviderRepository repository,
                             SecretCipher secretCipher,
                             OpenAiCompatibleClient client) {
        this.repository = repository;
        this.secretCipher = secretCipher;
        this.client = client;
    }

    /**
     * 모든 공급자를 조회합니다. (API Key 는 마스킹)
     *
     * @return 화면용 공급자 목록
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toView).toList();
    }

    /**
     * 사용 가능한 공급자만 조회합니다. (분석 대상 선택용)
     *
     * @return 화면용 목록
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEnabled() {
        return repository.findByEnabledTrueOrderByNameAsc().stream().map(this::toView).toList();
    }

    /**
     * 공급자 한 건을 조회합니다.
     *
     * @param id 공급자 키
     * @return 화면용 정보 (없으면 빈 값)
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> get(Long id) {
        return repository.findById(id).map(this::toView);
    }

    /**
     * 공급자를 생성하거나 수정합니다.
     *
     * @param id               null 이면 생성, 값이 있으면 수정
     * @param name             표시 이름
     * @param baseUrl          기준 URL (비우면 OpenAI 기본값)
     * @param apiKey           API Key (null/빈 값이면 유지, 로컬 모델은 비워 둠)
     * @param model            모델 이름
     * @param authStyle        {@code bearer} / {@code azure}
     * @param systemPrompt     추가 시스템 프롬프트
     * @param timeoutSeconds   타임아웃(초)
     * @param maxTokens        최대 토큰 (null 허용)
     * @param temperature      창의성
     * @param allowInsecureTls TLS 검증 건너뛰기
     * @param isDefault        기본 공급자 여부
     * @param enabled          사용 여부
     * @return 저장된 공급자 (화면용)
     * @throws IllegalArgumentException 필수 값이 비었을 때
     */
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

        // ★ 키를 비워 보내면 기존 값을 유지합니다.
        //   (수정 화면이 키를 표시하지 않으므로, 매번 다시 입력하게 만들면
        //    사용자가 그냥 비우고 저장해 키가 사라지는 사고가 납니다)
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

    /**
     * 공급자를 삭제합니다.
     *
     * @param id 공급자 키
     * @throws IllegalArgumentException 없을 때
     */
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("AI 공급자를 찾을 수 없습니다: " + id);
        }
        repository.deleteById(id);
        log.info("AI provider deleted: id={}", id);
    }

    /**
     * 공급자의 활성 상태만 토글합니다.
     *
     * <p>삭제 대신 끄는 경로를 제공하는 이유: 키를 지우지 않고 잠시
     * 비활성화하고 싶은 경우가 많습니다.
     *
     * @param id      공급자 키
     * @param enabled 사용 여부
     * @return 수정된 공급자 (화면용)
     */
    @Transactional
    public Map<String, Object> setEnabled(Long id, boolean enabled) {
        final AiProvider provider = repository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("AI 공급자를 찾을 수 없습니다: " + id));
        provider.setEnabled(enabled);
        provider.setUpdatedAt(new java.util.Date());
        return toView(repository.save(provider));
    }

    /**
     * 기본 공급자로 지정합니다.
     *
     * @param id 공급자 키
     * @return 수정된 공급자 (화면용)
     */
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

    /**
     * 공급자 연결을 확인하고 결과를 기록합니다.
     *
     * <p>저장만 하고 끝내면 운영자가 "등록했다" 고 믿고 넘어갔다가 분석
     * 단계에서야 처음 실패를 봅니다. 그래서 확인 결과를 DB 에 남기고
     * 화면에 바로 보여줍니다.
     *
     * @param id 공급자 키
     * @return 확인 결과 {@code {ok, message, elapsed_ms}}
     */
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

    /**
     * 분석에 쓸 공급자를 고릅니다.
     *
     * <p>선택 순서:
     * <ol>
     *   <li>요청에 지정한 {@code providerId}</li>
     *   <li>기본 플래그가 켜진 공급자</li>
     *   <li>사용 가능한 첫 공급자</li>
     * </ol>
     * <p>아무것도 없으면 예외를 던집니다. 분석은 공급자 없이 진행할 수 없고,
     * 조용히 실패하면 사용자가 이유를 알 수 없기 때문입니다.
     *
     * <h2>⚠️ 트랜잭션 비참여 ({@code NOT_SUPPORTED}) — 실제 겪은 결함</h2>
     * <p>이 메서드는 <b>호출자 트랜잭션에 참여하지 않습니다.</b> 이유가 있습니다.
     *
     * <p>호출자(로그 분석·정책 조언)는 {@code @Transactional} 이고, 이 메서드가
     * 예외를 던져 <b>그 예외를 잡아 실패를 기록</b>하려 합니다. 그런데 같은
     * 트랜잭션에 참여한 내부 메서드가 {@code RuntimeException} 을 던지면,
     * Spring 은 그 트랜잭션을 <b>{@code rollback-only} 로 마킹</b>합니다.
     * 호출자가 예외를 삼켜도 <b>마킹은 되돌릴 수 없고</b>, 커밋 시점에
     * {@code UnexpectedRollbackException} 이 터집니다.
     *
     * <p>증상: "사용 가능한 AI 공급자가 없습니다" 라는 <b>친절한 안내가
     * 500 Internal Server Error 로</b> 나갑니다. 실패 기록도 롤백되어
     * 화면에 아무 흔적이 남지 않습니다.
     *
     * <p>트랜잭션 밖에서 실행하면 마킹할 트랜잭션이 없으므로 이 문제가
     * 사라집니다. 읽기 전용 조회이고 엔티티에 지연 로딩 연관이 없어
     * 부작용이 없습니다. (호출자가 <b>예외 없는</b> 조회를 원하면
     * {@link #resolveOrEmpty} 를 쓰세요 — 그쪽이 더 견고합니다)
     *
     * @param providerId 지정 공급자 (null 이면 자동)
     * @return 선택된 공급자
     * @throws IllegalStateException 쓸 수 있는 공급자가 없을 때
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public AiProvider resolve(Long providerId) {
        return resolveOrEmpty(providerId).orElseThrow(() -> new IllegalStateException(
                providerId == null
                        ? "사용 가능한 AI 공급자가 없습니다. 설정에서 AI 공급자를 등록하고 '사용' 을 켜세요."
                        : "지정한 AI 공급자를 찾을 수 없습니다: " + providerId));
    }

    /**
     * 쓸 수 있는 공급자를 찾되, <b>예외를 던지지 않습니다.</b>
     *
     * <h2>⚠️ 왜 예외 없는 조회가 필요한가</h2>
     * <p>호출자가 {@code @Transactional} 인 상태에서 "공급자 없음" 을
     * <b>정상 응답으로 알려야</b> 하는 경우가 있습니다. 로그 분석과 정책 조언은
     * 실패도 저장하고 200 으로 돌려주는 것이 계약입니다.
     *
     * <p>그때 {@link #resolve} 를 쓰면 예외가 호출자 트랜잭션을 롤백 전용으로
     * 마킹해 <b>실패 기록까지 사라지고 500 이 나갑니다.</b> 이 메서드는
     * 예외를 만들지 않으므로 호출자가 안전하게 실패를 기록할 수 있습니다.
     *
     * @param providerId 지정 공급자 (null 이면 자동)
     * @return 선택된 공급자, 없으면 빈 값
     */
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

    /**
     * 엔티티를 클라이언트 접속 정보로 바꿉니다. (키를 복호화)
     *
     * @param provider 공급자
     * @return 접속 정보
     */
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

    /** 지정한 공급자를 제외한 나머지의 기본 플래그를 끕니다. */
    private void clearDefaultExcept(Long keepId) {
        for (final AiProvider other : repository.findByIsDefaultTrue()) {
            if (keepId != null && keepId.equals(other.getId())) {
                continue;
            }
            other.setIsDefault(false);
            repository.save(other);
        }
    }

    /**
     * 엔티티를 화면용 맵으로 바꿉니다.
     *
     * <p>⚠️ {@code apiKeyEncrypted} 는 <b>절대 포함하지 않습니다.</b>
     * 마스킹된 표시용 값만 넣습니다.
     */
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

    /**
     * 저장된 암호문에서 키의 앞뒤 일부만 보여 줄 마스킹 문자열을 만듭니다.
     *
     * <p>⚠️ 키가 암호화되어 있으므로 원문의 일부를 보여줄 수 없습니다.
     * 클라이언트가 {@code ****} 로 길이만 알려주는 형태로 응답합니다.
     */
    private String maskApiKey(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return "";
        }
        return "********";
    }

    /** 끝 슬래시를 제거합니다. (경로 조합 시 중복 방지) */
    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
