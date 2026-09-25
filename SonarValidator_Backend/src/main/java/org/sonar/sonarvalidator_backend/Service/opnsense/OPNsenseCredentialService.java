package org.sonar.sonarvalidator_backend.Service.opnsense;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.entity.OPNsenseCredential;
import org.sonar.sonarvalidator_backend.Repository.OPNsenseCredentialRepository;
import org.sonar.sonarvalidator_backend.Service.secret.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

/**
 * OPNsense 접속 정보의 저장/조회와 연결 확인을 담당합니다.
 *
 * <h2>시크릿 취급 원칙</h2>
 * <ol>
 *   <li>저장할 때는 <b>암호화</b>합니다. ({@link SecretCipher}, AES-GCM)</li>
 *   <li>응답에는 <b>마스킹된 값만</b> 넣습니다. 저장된 비밀을 다시 읽어
 *       보여줄 이유가 없고, 보여주면 그 자체가 유출 경로가 됩니다.</li>
 *   <li>수정 시 시크릿을 <b>비워 두면 기존 값 유지</b>합니다.
 *       매번 다시 입력하게 하면 운영자가 평문을 여기저기 붙여 넣게 됩니다.</li>
 *   <li>평문 시크릿은 {@link OPNsenseConnection} 안에서만 존재하고,
 *       그 객체는 호출 직후 버려집니다.</li>
 * </ol>
 *
 * <h2>연결 확인(테스트)이 중요한 이유</h2>
 * <p>URL 과 키가 맞는지 <b>저장 시점에</b> 알려주지 않으면, 운영자는
 * "저장됐다" 는 화면을 믿고 넘어갔다가 정책 푸시 단계에서 처음 실패를
 * 봅니다. 그때는 원인 후보가 너무 많습니다. 그래서 저장 직후
 * {@link #verify} 로 확인하고 결과를 DB 에 남깁니다.
 */
@Service
public class OPNsenseCredentialService {

    private static final Logger log = LoggerFactory.getLogger(OPNsenseCredentialService.class);

    private final OPNsenseCredentialRepository repository;
    private final SecretCipher secretCipher;
    private final OPNsenseApiClient apiClient;

    /**
     * @param repository   자격증명 저장소
     * @param secretCipher 시크릿 암호화기
     * @param apiClient    OPNsense API 클라이언트
     */
    public OPNsenseCredentialService(OPNsenseCredentialRepository repository,
                                     SecretCipher secretCipher,
                                     OPNsenseApiClient apiClient) {
        this.repository = repository;
        this.secretCipher = secretCipher;
        this.apiClient = apiClient;
    }

    /**
     * 저장된 접속 정보 목록을 조회합니다.
     *
     * @return 목록 (시크릿 제외)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAll() {
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final OPNsenseCredential credential : repository.findAllByOrderByIdAsc()) {
            result.add(toResponse(credential));
        }
        return result;
    }

    /**
     * 특정 Agent 의 접속 정보를 조회합니다.
     *
     * @param agentId Agent 식별자
     * @return 응답 맵 (없으면 null)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> get(String agentId) {
        return repository.findByAgentId(agentId).map(this::toResponse).orElse(null);
    }

    /**
     * 접속 정보를 저장합니다. (없으면 생성, 있으면 수정)
     *
     * <p>시크릿을 비워 두면 기존 값을 유지합니다. (위 클래스 주석 참고)
     *
     * @param agentId          Agent 식별자
     * @param displayName      표시 이름
     * @param baseUrl          기준 URL
     * @param apiKey           API Key (null/빈 값이면 기존 유지)
     * @param apiSecret        API Secret 평문 (null/빈 값이면 기존 유지)
     * @param allowInsecureTls 자체 서명 인증서 허용 여부
     * @param verifyNow        저장 직후 연결 확인을 수행할지 여부
     * @return 저장 결과 (연결 확인 결과 포함)
     * @throws IllegalArgumentException agentId 또는 baseUrl 이 비었을 때
     */
    @Transactional
    public Map<String, Object> save(String agentId,
                                    String displayName,
                                    String baseUrl,
                                    String apiKey,
                                    String apiSecret,
                                    boolean allowInsecureTls,
                                    boolean verifyNow) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agent_id 는 필수입니다.");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("OPNsense 주소(base_url)는 필수입니다.");
        }

        final OPNsenseCredential credential = repository.findByAgentId(agentId)
                .orElseGet(() -> {
                    final OPNsenseCredential created = new OPNsenseCredential();
                    created.setAgentId(agentId.trim());
                    created.setCreatedAt(new java.util.Date());
                    return created;
                });

        credential.setBaseUrl(baseUrl.trim());
        credential.setDisplayName(displayName == null || displayName.isBlank()
                ? agentId.trim()
                : displayName.trim());
        credential.setAllowInsecureTls(allowInsecureTls);

        // 빈 값이면 "변경 없음" 으로 해석합니다.
        if (apiKey != null && !apiKey.isBlank()) {
            credential.setApiKey(apiKey.trim());
        }
        if (apiSecret != null && !apiSecret.isBlank()) {
            credential.setSecret(secretCipher.encrypt(apiSecret.trim()));
        }
        credential.setUpdatedAt(new java.util.Date());

        OPNsenseCredential saved = repository.save(credential);

        if (verifyNow) {
            saved = verifyAndRecord(saved);
        }
        log.info("opnsense credential saved: agent={} url={} status={}",
                saved.getAgentId(), saved.getBaseUrl(), saved.getStatus());
        return toResponse(saved);
    }

    /**
     * 저장된 접속 정보를 삭제합니다.
     *
     * @param agentId Agent 식별자
     * @return 삭제했으면 {@code true}
     */
    @Transactional
    public boolean delete(String agentId) {
        final var found = repository.findByAgentId(agentId);
        if (found.isEmpty()) {
            return false;
        }
        repository.delete(found.get());
        log.info("opnsense credential deleted: agent={}", agentId);
        return true;
    }

    /**
     * 연결을 확인하고 결과를 DB 에 기록합니다.
     *
     * @param agentId Agent 식별자
     * @return 확인 결과 (응답 맵)
     * @throws IllegalArgumentException 등록되지 않은 Agent 인 경우
     */
    @Transactional
    public Map<String, Object> verify(String agentId) {
        final OPNsenseCredential credential = repository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "OPNsense 접속 정보가 등록되지 않았습니다: " + agentId));
        return toResponse(verifyAndRecord(credential));
    }

    /**
     * 모든 등록 항목의 연결을 확인합니다.
     *
     * @return 항목별 결과 목록
     */
    @Transactional
    public List<Map<String, Object>> verifyAll() {
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final OPNsenseCredential credential : repository.findAllByOrderByIdAsc()) {
            result.add(toResponse(verifyAndRecord(credential)));
        }
        return result;
    }

    /**
     * 실제 호출로 연결을 확인하고 엔티티에 결과를 반영합니다.
     *
     * @param credential 자격증명 엔티티
     * @return 갱신된 엔티티
     */
    private OPNsenseCredential verifyAndRecord(OPNsenseCredential credential) {
        final OPNsenseConnection connection = toConnection(credential);
        if (connection == null) {
            credential.markChecked(false, null, "API Key 또는 Secret 이 없습니다.");
            return repository.save(credential);
        }

        final OPNsenseApiClient.Result result = apiClient.checkConnection(connection);
        if (result.ok()) {
            final String version = apiClient.extractVersion(result.body());
            credential.markChecked(true, version, null);
            log.info("opnsense connection verified: agent={} version={}",
                    credential.getAgentId(), version == null ? "(unknown)" : version);
        } else {
            credential.markChecked(false, null, result.error());
            log.warn("opnsense connection failed: agent={} reason={}",
                    credential.getAgentId(), result.error());
        }
        return repository.save(credential);
    }

    /**
     * 엔티티를 호출용 접속 정보로 바꿉니다. (시크릿 복호화 포함)
     *
     * @param credential 자격증명 엔티티
     * @return 접속 정보, 시크릿 복호화 실패 시 null
     */
    public OPNsenseConnection toConnection(OPNsenseCredential credential) {
        if (credential == null) {
            return null;
        }
        String secret = null;
        if (secretCipher.isPresent(credential.getSecret())) {
            try {
                secret = secretCipher.decrypt(credential.getSecret());
            } catch (IllegalStateException ex) {
                // 키가 바뀌었거나 값이 손상된 경우입니다. 재입력이 필요합니다.
                log.error("cannot decrypt OPNsense secret for agent={}: {}",
                        credential.getAgentId(), ex.getMessage());
                return null;
            }
        }
        return new OPNsenseConnection(
                credential.getBaseUrl(),
                credential.getApiKey(),
                secret,
                credential.isAllowInsecureTls());
    }

    /**
     * 엔티티를 응답 맵으로 바꿉니다.
     *
     * <p>시크릿은 <b>마스킹된 값</b>만 넣고, 값이 있는지 여부를 별도
     * 플래그로 알려줍니다. 프론트엔드는 그 플래그로 "저장됨" 을 표시하고
     * 입력란은 비워 둡니다.
     *
     * @param credential 자격증명 엔티티
     * @return 응답 맵
     */
    public Map<String, Object> toResponse(OPNsenseCredential credential) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", credential.getAgentId());
        body.put("display_name", credential.getDisplayName());
        body.put("base_url", credential.getBaseUrl());
        body.put("api_key_masked", OPNsenseConnection.mask(credential.getApiKey()));
        // 시크릿 값 자체는 절대 내려보내지 않습니다.
        body.put("has_api_key", credential.getApiKey() != null && !credential.getApiKey().isBlank());
        body.put("has_secret", secretCipher.isPresent(credential.getSecret()));
        body.put("allow_insecure_tls", credential.isAllowInsecureTls());
        body.put("status", credential.getStatus() == null ? null : credential.getStatus().name());
        body.put("last_checked_at", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(credential.getLastCheckedAt()));
        body.put("last_error", credential.getLastError());
        body.put("detected_version", credential.getDetectedVersion());
        body.put("created_at", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(credential.getCreatedAt()));
        body.put("updated_at", credential.getUpdatedAt());
        return body;
    }

    /**
     * 아직 등록되지 않은 Agent 목록을 만듭니다. (설정 모달의 안내용)
     *
     * @param knownAgentIds 전체 Agent 식별자
     * @return 설정이 필요한 Agent 식별자 목록
     */
    @Transactional(readOnly = true)
    public List<String> agentsWithoutCredential(List<String> knownAgentIds) {
        final List<String> result = new ArrayList<>();
        if (knownAgentIds == null) {
            return result;
        }
        for (final String agentId : knownAgentIds) {
            if (agentId != null && !agentId.isBlank() && !repository.existsByAgentId(agentId)) {
                result.add(agentId);
            }
        }
        return result;
    }

    /**
     * 디버그용으로 응답 본문 요약을 만듭니다.
     *
     * @param body 응답 본문
     * @return 요약
     */
    public Map<String, Object> summarize(JsonNode body) {
        return apiClient.summarize(body);
    }
}
