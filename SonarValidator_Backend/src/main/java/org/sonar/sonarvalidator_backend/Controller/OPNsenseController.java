package org.sonar.sonarvalidator_backend.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.entity.OPNsenseCredential;
import org.sonar.sonarvalidator_backend.Repository.OPNsenseCredentialRepository;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseApiClient;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseConnection;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseCredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPNsense 연동 설정 API 입니다.
 *
 * <h2>프론트엔드 흐름과의 대응</h2>
 * <p>Agent 배포 화면({@code ProjectCreation})에서 <b>OPNsense 카드를 누르면</b>
 * 별도 모달이 열리고, 여기서 주소/API Key/Secret 을 입력합니다.
 *
 * <pre>
 *   GET  /api/v1/opnsense/credentials            등록된 설정 목록
 *   GET  /api/v1/opnsense/credentials/{agentId}  Agent 별 설정 1건
 *   PUT  /api/v1/opnsense/credentials/{agentId}  저장 (생성/수정)
 *   DELETE /api/v1/opnsense/credentials/{agentId} 삭제
 *   POST /api/v1/opnsense/credentials/{agentId}/verify  연결 확인
 *   POST /api/v1/opnsense/verify-all              전체 연결 확인
 *   POST /api/v1/opnsense/credentials/{agentId}/probe   원문 조회 (진단)
 * </pre>
 *
 * <h2>⚠️ 실장비 테스트 불가</h2>
 * <p>현재 랩에 OPNsense 가 없어 <b>실제 호출 결과는 검증되지 않았습니다.</b>
 * 그래서 {@code /probe} 를 두었습니다. 실장비가 붙으면 이 엔드포인트로
 * 응답 원문을 보고 파서 매핑을 맞출 수 있습니다.
 * (응답 구조를 추측해 코드에 박아 두지 않기 위한 장치입니다.)
 *
 * <h2>시크릿 취급</h2>
 * <p>요청 본문의 {@code api_secret} 은 저장 시 암호화되며, <b>어떤 응답에도
 * 평문으로 나가지 않습니다.</b> 응답에는 {@code has_secret} 플래그와
 * 마스킹된 키만 포함됩니다.
 */
@RestController
@RequestMapping("/api/v1/opnsense")
public class OPNsenseController {

    private static final Logger log = LoggerFactory.getLogger(OPNsenseController.class);

    private final OPNsenseCredentialService credentialService;
    private final OPNsenseCredentialRepository repository;
    private final OPNsenseApiClient apiClient;
    private final AgentSessionRegistry registry;
    private final AgentMessageRouterService router;

    /**
     * 진단 대상 선택기입니다.
     *
     * <p>이 컨트롤러가 API 클라이언트의 모든 메서드를 알 필요가 없게 합니다.
     * 이전에는 {@code probe} 안에 대상 {@code switch} 가 있어서 HTTP 계층이
     * "인터페이스/규칙/NAT/별칭" 이라는 도메인 개념을 갖고 있었습니다.
     */
    private final org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseProbeStrategies probeStrategies;

    /**
     * @param credentialService 자격증명 서비스
     * @param repository        자격증명 저장소 (목록 조회용)
     * @param apiClient         OPNsense API 클라이언트 (probe 용)
     * @param registry          Agent 세션 레지스트리 (대상 Agent 목록)
     * @param router            Agent 설정 보관소 (OPNsense 장치 탐지)
     * @param probeStrategies   진단 대상 선택기
     */
    public OPNsenseController(OPNsenseCredentialService credentialService,
                              OPNsenseCredentialRepository repository,
                              OPNsenseApiClient apiClient,
                              AgentSessionRegistry registry,
                              AgentMessageRouterService router,
                              org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseProbeStrategies probeStrategies) {
        this.credentialService = credentialService;
        this.repository = repository;
        this.apiClient = apiClient;
        this.registry = registry;
        this.router = router;
        this.probeStrategies = probeStrategies;
    }

    /**
     * OPNsense 설정 요청 본문입니다.
     *
     * <p>{@code api_secret} 을 비워 보내면 <b>기존 값 유지</b>로 해석합니다.
     * 매번 다시 입력하게 하면 운영자가 평문을 여기저기 붙여 넣게 됩니다.
     *
     * @param displayName      표시 이름
     * @param baseUrl          기준 URL (예: {@code https://10.99.143.2})
     * @param apiKey           API Key (비우면 기존 유지)
     * @param apiSecret        API Secret (비우면 기존 유지)
     * @param allowInsecureTls 자체 서명 인증서 허용 여부
     * @param verifyNow        저장 직후 연결 확인 여부
     */
    public record CredentialRequest(
            String displayName,
            String baseUrl,
            String apiKey,
            String apiSecret,
            Boolean allowInsecureTls,
            Boolean verifyNow) {
    }

    /**
     * 등록된 OPNsense 설정 목록을 반환합니다.
     *
     * @return {@code {"total": n, "credentials": [...]}}
     */
    @GetMapping("/credentials")
    public Map<String, Object> list() {
        final List<Map<String, Object>> credentials = credentialService.listAll();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", credentials.size());
        body.put("credentials", credentials);
        return body;
    }

    /**
     * Agent 별 설정 1건을 반환합니다.
     *
     * @param agentId Agent 식별자
     * @return 설정 또는 404
     */
    @GetMapping("/credentials/{agentId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String agentId) {
        final Map<String, Object> credential = credentialService.get(agentId);
        if (credential == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "OPNsense 설정이 없습니다: " + agentId));
        }
        return ResponseEntity.ok(credential);
    }

    /**
     * 설정을 저장합니다. (없으면 생성)
     *
     * @param agentId Agent 식별자
     * @param body    설정 본문
     * @return 저장 결과 (연결 확인 결과 포함)
     */
    @PutMapping("/credentials/{agentId}")
    public ResponseEntity<Map<String, Object>> save(@PathVariable String agentId,
                                                    @RequestBody(required = false) CredentialRequest body) {
        final CredentialRequest request = body == null
                ? new CredentialRequest(null, null, null, null, null, null)
                : body;
        try {
            final Map<String, Object> saved = credentialService.save(
                    agentId,
                    request.displayName(),
                    request.baseUrl(),
                    request.apiKey(),
                    request.apiSecret(),
                    Boolean.TRUE.equals(request.allowInsecureTls()),
                    request.verifyNow() == null || request.verifyNow());
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException ex) {
            // 입력 오류는 400 으로 돌려줘 화면이 그대로 보여줄 수 있게 합니다.
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * 설정을 삭제합니다.
     *
     * @param agentId Agent 식별자
     * @return {@code {"deleted": true}}
     */
    @DeleteMapping("/credentials/{agentId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String agentId) {
        final boolean deleted = credentialService.delete(agentId);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "OPNsense 설정이 없습니다: " + agentId));
        }
        return ResponseEntity.ok(Map.of("deleted", true, "agent_id", agentId));
    }

    /**
     * 연결을 확인합니다. (설정 모달의 "연결 테스트" 버튼)
     *
     * @param agentId Agent 식별자
     * @return 확인 결과
     */
    @PostMapping("/credentials/{agentId}/verify")
    public ResponseEntity<Map<String, Object>> verify(@PathVariable String agentId) {
        try {
            return ResponseEntity.ok(credentialService.verify(agentId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * 등록된 모든 설정의 연결을 확인합니다.
     *
     * @return 항목별 결과
     */
    @PostMapping("/verify-all")
    public Map<String, Object> verifyAll() {
        final List<Map<String, Object>> results = credentialService.verifyAll();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", results.size());
        body.put("results", results);
        return body;
    }

    /**
     * OPNsense 응답 <b>원문</b>을 조회합니다. (진단 전용)
     *
     * <h2>왜 필요한가</h2>
     * <p>OPNsense 버전마다 응답 구조가 다를 수 있습니다. 구조를 추측해
     * 파서를 먼저 쓰면 조용히 빈 값이 나옵니다. 이 엔드포인트로 원문을 본
     * 뒤 매핑을 확정하는 편이 안전합니다.
     *
     * @param agentId Agent 식별자
     * @param target  조회 대상 ({@code interfaces}, {@code rules}, {@code nat},
     *                {@code aliases}, {@code firmware})
     * @return 상태 코드, 요약, 원문(앞부분)
     */
    @PostMapping("/credentials/{agentId}/probe")
    public ResponseEntity<Map<String, Object>> probe(@PathVariable String agentId,
                                                     @org.springframework.web.bind.annotation.RequestParam(
                                                             value = "target", defaultValue = "firmware")
                                                     String target) {
        final OPNsenseCredential credential = repository.findByAgentId(agentId).orElse(null);
        if (credential == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "OPNsense 설정이 없습니다: " + agentId));
        }
        final OPNsenseConnection connection = credentialService.toConnection(credential);
        if (connection == null || !connection.isUsable()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "접속 정보가 불완전하거나 시크릿 복호화에 실패했습니다. "
                            + "Secret 을 다시 입력해 저장하세요."));
        }

        // ⚠️ 대상 분기는 전략 선택기가 합니다.
        //    이 컨트롤러는 "무엇을 조회하는가" 를 모릅니다 — 이름과 결과만
        //    다룹니다. 그래서 새 대상을 추가해도 이 파일을 고칠 필요가 없습니다.
        final org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseProbeStrategy strategy =
                probeStrategies.select(target);
        final OPNsenseApiClient.Result result = strategy.probe(apiClient, connection);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", agentId);
        body.put("target", strategy.name());
        // 운영자가 "이 조회가 무엇을 보는가" 를 숫자만 보고 추측하지 않게 합니다.
        body.put("description", strategy.description());
        body.put("base_url", connection.normalizedBaseUrl());
        body.put("ok", result.ok());
        body.put("status_code", result.statusCode());
        body.put("error", result.error());
        body.put("summary", result.body() == null
                ? Map.of()
                : apiClient.summarize(result.body()));
        // 원문은 앞부분만 돌려줍니다. 규칙 목록은 수백 KB 일 수 있습니다.
        body.put("raw_preview", truncate(result.rawBody(), 4000));
        log.info("opnsense probe: agent={} target={} ok={} status={}",
                agentId, strategy.name(), result.ok(), result.statusCode());
        return ResponseEntity.ok(body);
    }

    /**
     * 사용 가능한 진단 대상을 반환합니다.
     *
     * <p>프론트엔드가 대상 목록을 하드코딩하지 않게 합니다. 서버가 대상을
     * 늘렸을 때 화면이 뒤처지면, 운영자는 새 대상을 쓸 방법을 알 수 없습니다.
     *
     * @return 대상 이름·설명 목록
     */
    @GetMapping("/probe-targets")
    public Map<String, Object> probeTargets() {
        return Map.of("targets", probeStrategies.describe());
    }

    /**
     * OPNsense 장치로 보이는 Agent 목록을 반환합니다.
     *
     * <p>설정 모달이 "어떤 장치에 키를 넣어야 하는지" 를 알려주기 위한
     * 정보입니다. 수집된 제품명에 {@code opnsense} 가 들어가면 후보로 봅니다.
     *
     * @return 후보 Agent 목록 + 설정 여부
     */
    @GetMapping("/candidates")
    public Map<String, Object> candidates() {
        final List<Map<String, Object>> candidates = new ArrayList<>();

        // 수집된 설정에서 OPNsense 로 보이는 장치를 찾습니다.
        router.allConfigs().forEach((agentId, config) -> {
            final String product = config.getProduct() == null ? "" : config.getProduct();
            final String vendor = config.getVendor() == null ? "" : config.getVendor();
            final String format = config.getFormat() == null ? "" : config.getFormat();
            final boolean looksLikeOpnsense = (product + " " + vendor + " " + format)
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains("opnsense");
            if (!looksLikeOpnsense) {
                return;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("hostname", config.getHostname());
            entry.put("product", config.getProduct());
            entry.put("vendor", config.getVendor());
            entry.put("format", config.getFormat());
            entry.put("has_credential", repository.existsByAgentId(agentId));
            candidates.add(entry);
        });

        // 연결만 되고 아직 식별되지 않은 Agent 도 후보로 넣습니다.
        // (OPNsense 는 API 로만 접근하는 경우가 있어 수집이 안 될 수 있습니다)
        for (final String agentId : registry.connectedAgentIds()) {
            final boolean present = candidates.stream()
                    .anyMatch(item -> agentId.equals(item.get("agent_id")));
            if (present) {
                continue;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("hostname", agentId);
            entry.put("product", null);
            entry.put("vendor", null);
            entry.put("format", null);
            entry.put("has_credential", repository.existsByAgentId(agentId));
            entry.put("detected", false);
            candidates.add(entry);
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", candidates.size());
        body.put("candidates", candidates);
        return body;
    }

    /**
     * 문자열을 최대 길이로 자릅니다.
     *
     * @param value     원본
     * @param maxLength 최대 길이
     * @return 잘린 문자열
     */
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...(truncated)";
    }
}
