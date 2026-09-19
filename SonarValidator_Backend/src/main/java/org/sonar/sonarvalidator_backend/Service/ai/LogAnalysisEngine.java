package org.sonar.sonarvalidator_backend.Service.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.entity.AiProvider;
import org.sonar.sonarvalidator_backend.Model.entity.DeviceLog;
import org.sonar.sonarvalidator_backend.Model.entity.LogAnalysis;
import org.sonar.sonarvalidator_backend.Repository.LogAnalysisRepository;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient.Message;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient.Result;
import org.sonar.sonarvalidator_backend.Service.log.LogNormalizer;
import org.sonar.sonarvalidator_backend.Service.log.LogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 로그 분석 엔진입니다.
 *
 * <h2>무엇을 하는가</h2>
 * <p>사용자가 고른 로그(또는 필터 결과)를 프롬프트로 묶어 OpenAI 호환 API 에
 * 보내고, 응답을 <b>구조화</b>해 화면에 돌려줍니다.
 *
 * <pre>
 *   장비 로그 (warning 이상 / 사용자 지정)
 *        │  필터: 기간 · 프로젝트 · agent · 심각도
 *        ▼
 *   LogService.resolveForAnalysis()   ← 시간순 정렬 (인과 파악용)
 *        │
 *        ▼
 *   프롬프트 조립 (시스템 지침 + 로그 본문)
 *        │
 *        ▼
 *   OpenAiCompatibleClient.chat()     ← 공급자/모델은 DB 설정에서
 *        │
 *        ▼
 *   JSON 파싱 → summary / root_cause / recommendations / risk_level
 *        │
 *        ▼
 *   log_analysis 테이블에 저장 (실패도 기록)
 * </pre>
 *
 * <h2>⚠️ 프롬프트 설계에서 중요한 3가지</h2>
 *
 * <h3>1. 입력 크기 상한</h3>
 * <p>로그를 전부 넣으면 컨텍스트 한도를 넘겨 400 이 나거나, 통과해도 비용이
 * 폭증합니다. 그래서 <b>줄 수와 글자 수 둘 다</b> 자릅니다. 줄 수만 제한하면
 * 한 줄이 매우 긴 경우(설정 덤프 등)를 막지 못합니다.
 *
 * <h3>2. 잘렸다는 사실을 모델과 사용자 모두에게 알린다</h3>
 * <p>일부만 보고 "문제 없음" 이라고 답하면 위험합니다. 그래서 (a) 프롬프트에
 * 잘렸다고 명시하고, (b) 응답에 전달/전체 건수를 함께 담아 화면이 표시합니다.
 *
 * <h3>3. 근거 없는 추측 금지</h3>
 * <p>모델은 로그에 없는 원인을 그럴듯하게 지어내는 경향이 있습니다. 그래서
 * "로그에 근거가 없으면 모른다고 답하라" 를 시스템 프롬프트에 명시하고,
 * 응답 스키마에 {@code evidence} (근거가 된 로그 줄)를 요구합니다.
 *
 * <h2>⚠️ 실패도 저장한다</h2>
 * <p>분석 실패(키 만료, 모델명 오타, 서버 미기동)를 저장하지 않으면 화면에
 * 아무 기록이 남지 않아 사용자가 같은 실수를 반복합니다. 그래서
 * {@code succeeded=false} 로 사유를 남깁니다.
 */
@Service
public class LogAnalysisEngine {

    private static final Logger log = LoggerFactory.getLogger(LogAnalysisEngine.class);

    /**
     * 프롬프트에 넣을 최대 로그 줄 수입니다.
     *
     * <p>너무 크게 잡으면 컨텍스트 한도 초과와 비용 문제가 생기고, 너무 작게
     * 잡으면 사건의 흐름을 볼 수 없습니다. 로그 한 줄이 평균 150자라면
     * 300줄 ≈ 45,000자 ≈ 약 12,000 토큰으로, 대부분 모델의 한도 안에 들어갑니다.
     */
    public static final int MAX_PROMPT_LOGS = 300;

    /**
     * 프롬프트 본문의 최대 글자 수입니다.
     *
     * <p>줄 수 제한만으로는 한 줄이 긴 경우를 막지 못합니다.
     * 400,000자 ≈ 약 100,000 토큰으로 넉넉한 상한입니다.
     */
    public static final int MAX_PROMPT_CHARS = 400_000;

    /**
     * 한 줄이 이보다 길면 잘라서 넣습니다.
     *
     * <p>설정 덤프나 스택 트레이스가 한 줄로 들어오는 경우가 있습니다.
     * 통째로 넣으면 그 한 줄이 전체 예산을 다 써버립니다.
     */
    private static final int MAX_LINE_CHARS = 2000;

    /** 저장할 원문 응답의 최대 길이입니다. (컬럼 크기와 맞춤) */
    private static final int MAX_STORED_RESPONSE = 30_000;

    private final LogService logService;
    private final AiProviderService providerService;
    private final OpenAiCompatibleClient client;
    private final LogAnalysisRepository analysisRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param logService         로그 조회
     * @param providerService    AI 공급자 선택
     * @param client             OpenAI 호환 클라이언트
     * @param analysisRepository 분석 결과 저장소
     */
    public LogAnalysisEngine(LogService logService,
                             AiProviderService providerService,
                             OpenAiCompatibleClient client,
                             LogAnalysisRepository analysisRepository) {
        this.logService = logService;
        this.providerService = providerService;
        this.client = client;
        this.analysisRepository = analysisRepository;
    }

    /**
     * 로그를 분석합니다.
     *
     * @param logIds      분석할 특정 로그 ID (비어 있으면 필터 조회)
     * @param agentId     장비 식별자 (null 이면 전체)
     * @param projectKey  프로젝트 키 (null 이면 전체)
     * @param from        기간 시작 (null 이면 제한 없음)
     * @param to          기간 끝 (null 이면 제한 없음)
     * @param severity    최소 심각도 (예: {@code warning})
     * @param scope       분석 범위 ({@code selected}/{@code filter}/{@code single})
     * @param providerId  사용할 AI 공급자 (null 이면 기본 공급자)
     * @param userPrompt  사용자가 덧붙이는 질문/지시 (선택)
     * @param requestedBy 요청자
     * @return 분석 결과 (화면용)
     */
    @Transactional
    public Map<String, Object> analyze(List<Long> logIds,
                                       String agentId,
                                       String projectKey,
                                       String from,
                                       String to,
                                       String severity,
                                       String scope,
                                       Long providerId,
                                       String userPrompt,
                                       String requestedBy) {

        final long startedAt = System.currentTimeMillis();

        // 1) 분석 대상 로그를 시간순으로 가져옵니다.
        final List<DeviceLog> logs = logService.resolveForAnalysis(
                logIds, agentId, projectKey, from, to, severity, MAX_PROMPT_LOGS);

        final long totalMatching = logIds != null && !logIds.isEmpty()
                ? logs.size()
                : logService.countFor(agentId, projectKey, from, to, severity);

        final LogAnalysis record = new LogAnalysis();
        record.setAnalysisId("AIA-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase(Locale.ROOT));
        record.setProjectKey(blankToNull(projectKey));
        record.setAgentId(blankToNull(agentId));
        record.setScope(scope == null || scope.isBlank() ? "filter" : scope);
        record.setSeverityFilter(blankToNull(severity));
        record.setPeriodFrom(blankToNull(from));
        record.setPeriodTo(blankToNull(to));
        record.setRequestedBy(requestedBy == null || requestedBy.isBlank() ? "system" : requestedBy);
        record.setCreatedAt(Instant.now().toString());
        record.setTotalLogCount((int) Math.min(Integer.MAX_VALUE, totalMatching));
        record.setIncludedLogCount(logs.size());
        record.setProviderName(null);
        record.setModel(null);
        record.setFilterJson(buildFilterJson(logIds, agentId, projectKey, from, to, severity, scope));
        record.setLogIdsJson(buildLogIdsJson(logs));

        // 2) 분석할 로그가 없으면 모델을 부르지 않습니다.
        //    (호출 비용과 시간을 낭비하고, 모델이 "로그가 없다" 는 무의미한 답을 합니다)
        if (logs.isEmpty()) {
            record.setSucceeded(false);
            record.setErrorMessage("분석할 로그가 없습니다. 기간/필터를 조정하거나 로그를 먼저 수집하세요.");
            record.setElapsedMs(System.currentTimeMillis() - startedAt);
            analysisRepository.save(record);
            return toView(record);
        }

        // 3) 공급자를 고릅니다. 없으면 여기서 사유가 담긴 예외가 납니다.
        final AiProvider provider;
        try {
            provider = providerService.resolve(providerId);
        } catch (IllegalStateException ex) {
            record.setSucceeded(false);
            record.setErrorMessage(ex.getMessage());
            record.setElapsedMs(System.currentTimeMillis() - startedAt);
            analysisRepository.save(record);
            return toView(record);
        }

        record.setProviderName(provider.getName());
        record.setModel(provider.getModel());

        // 4) 프롬프트를 조립하고 호출합니다.
        final List<Message> messages = buildMessages(provider, logs, totalMatching, userPrompt);

        log.info("log analysis started: id={} provider={} model={} logs={}/{}",
                record.getAnalysisId(), provider.getName(), provider.getModel(),
                logs.size(), totalMatching);

        final Result result = client.chat(providerService.toConnection(provider), messages, true);

        record.setElapsedMs(result.elapsedMs());
        record.setRawResponse(truncate(result.text(), MAX_STORED_RESPONSE));

        if (!result.ok()) {
            // 실패도 저장합니다. 사유가 남아야 사용자가 다음에 무엇을 고칠지 압니다.
            record.setSucceeded(false);
            record.setErrorMessage(truncate(result.text(), 4000));
            final LogAnalysis saved = analysisRepository.save(record);
            log.warn("log analysis failed: id={} reason={}", saved.getAnalysisId(), result.text());
            return toView(saved);
        }

        // 5) 구조화 파싱을 시도합니다. 실패해도 원문은 남깁니다.
        record.setSucceeded(true);
        parseStructured(record, result.text());

        final LogAnalysis saved = analysisRepository.save(record);

        log.info("log analysis completed: id={} risk={} elapsed={}ms",
                saved.getAnalysisId(), saved.getRiskLevel(), saved.getElapsedMs());

        return toView(saved);
    }

    /**
     * 분석 이력을 조회합니다.
     *
     * @param projectKey 프로젝트 키 (null 이면 전체)
     * @param agentId    장비 식별자 (null 이면 전체)
     * @param limit      최대 건수
     * @return 분석 이력 목록
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(String projectKey, String agentId, Integer limit) {
        final int size = limit == null || limit <= 0 ? 50 : Math.min(200, limit);
        final var pageable = org.springframework.data.domain.PageRequest.of(0, size);

        final List<LogAnalysis> rows;
        if (agentId != null && !agentId.isBlank() && projectKey != null && !projectKey.isBlank()) {
            rows = analysisRepository.findByProjectKeyAndAgentIdOrderByCreatedAtDesc(
                    projectKey, agentId, pageable);
        } else if (agentId != null && !agentId.isBlank()) {
            rows = analysisRepository.findByAgentIdOrderByCreatedAtDesc(agentId, pageable);
        } else if (projectKey != null && !projectKey.isBlank()) {
            rows = analysisRepository.findByProjectKeyOrderByCreatedAtDesc(projectKey, pageable);
        } else {
            rows = analysisRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return rows.stream().map(this::toView).toList();
    }

    /**
     * 분석 한 건을 상세 조회합니다.
     *
     * @param analysisId 분석 식별자
     * @return 분석 결과 (없으면 빈 값)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(String analysisId) {
        return analysisRepository.findByAnalysisId(analysisId)
                .map(this::toView)
                .orElse(null);
    }

    // ---------------------------------------------------------------------------
    // 프롬프트 조립
    // ---------------------------------------------------------------------------

    /**
     * 시스템 지침 + 로그 본문으로 메시지를 만듭니다.
     *
     * @param provider     공급자 (추가 시스템 프롬프트가 있으면 반영)
     * @param logs         로그 목록 (시간순)
     * @param totalMatching 필터에 걸린 전체 건수
     * @param userPrompt   사용자 추가 질문
     * @return 메시지 목록
     */
    private List<Message> buildMessages(AiProvider provider,
                                        List<DeviceLog> logs,
                                        long totalMatching,
                                        String userPrompt) {

        final List<Message> messages = new ArrayList<>();

        final StringBuilder system = new StringBuilder();
        system.append("""
                당신은 네트워크 장비 로그를 분석하는 숙련된 엔지니어입니다.
                Cisco IOS-XE, Arista EOS, FRRouting, Open vSwitch, Alpine/nftables,
                Linux 서버 로그를 다룹니다.

                다음 규칙을 반드시 지키세요.

                1. 로그에 실제로 나타난 내용만 근거로 판단하세요.
                   근거가 부족하면 추측하지 말고, 무엇을 더 확인해야 하는지 말하세요.
                2. 로그에 없는 장비/설정/토폴로지를 가정하지 마세요.
                3. 반복되는 로그(repeat)는 문제의 심각도 판단에 반영하세요.
                4. 타임스탬프 순서로 사건의 인과관계를 파악하세요.
                5. 반드시 아래 JSON 형식으로만 답하세요. 설명 문장이나 코드블록을 덧붙이지 마세요.

                {
                  "risk_level": "CRITICAL | HIGH | MEDIUM | LOW",
                  "summary": "한두 문장 요약 (한국어)",
                  "root_cause": "추정 원인. 근거가 약하면 '근거 부족'이라고 명시",
                  "evidence": ["근거가 된 로그 줄 (최대 5개, 원문 인용)"],
                  "recommendations": ["구체적 조치 1", "구체적 조치 2"],
                  "needs_more_data": false
                }
                """);

        if (provider.getSystemPrompt() != null && !provider.getSystemPrompt().isBlank()) {
            system.append("\n추가 지침 (운영자가 설정):\n")
                    .append(provider.getSystemPrompt().trim())
                    .append('\n');
        }

        messages.add(Message.system(system.toString()));

        final StringBuilder user = new StringBuilder();
        user.append("아래 네트워크 장비 로그를 분석해 주세요.\n\n");

        // ⚠️ 잘렸다는 사실을 모델에게 반드시 알립니다.
        //    일부만 보고 "문제 없음" 이라고 단정하면 위험합니다.
        user.append("로그 건수: 전체 ").append(totalMatching).append("건 중 ")
                .append(logs.size()).append("건을 제공합니다.");
        if (totalMatching > logs.size()) {
            user.append(" (컨텍스트 한도로 일부만 제공됨 — 전체를 보지 못했다는 점을 감안하세요)");
        }
        user.append("\n\n");

        user.append("=== 로그 (오래된 것부터) ===\n");
        int usedChars = 0;
        final StringBuilder body = new StringBuilder();

        for (final DeviceLog entry : logs) {
            final StringBuilder line = new StringBuilder();
            line.append('[').append(entry.getLoggedAt() == null ? "?" : entry.getLoggedAt()).append("] ");
            line.append("agent=").append(entry.getAgentId() == null ? "?" : entry.getAgentId()).append(' ');
            if (entry.getProduct() != null && !entry.getProduct().isBlank()) {
                line.append("product=").append(entry.getProduct()).append(' ');
            }
            line.append("severity=").append(entry.getSeverity()).append(' ');
            if (entry.getRepeatCount() != null && entry.getRepeatCount() > 1) {
                line.append("(repeat x").append(entry.getRepeatCount()).append(") ");
            }
            line.append('|');

            // 원문을 씁니다. 정규화된 message 만 쓰면 메시지 코드 등
            // 원본에만 있는 정보를 잃습니다.
            String content = entry.getRaw();
            if (content == null || content.isBlank()) {
                content = entry.getMessage();
            }
            line.append(' ').append(truncate(content, MAX_LINE_CHARS)).append('\n');

            // 글자 수 예산을 넘으면 중단합니다.
            if (usedChars + line.length() > MAX_PROMPT_CHARS) {
                body.append("… (글자 수 한도로 나머지 ")
                        .append(logs.size() - body.toString().split("\n").length + 1)
                        .append("건은 생략됨)\n");
                break;
            }

            body.append(line);
            usedChars += line.length();
        }

        user.append(body);
        user.append("\n=== 로그 끝 ===\n");

        if (userPrompt != null && !userPrompt.isBlank()) {
            user.append("\n추가로 답해 주세요: ").append(userPrompt.trim()).append('\n');
        }

        messages.add(Message.user(user.toString()));
        return messages;
    }

    // ---------------------------------------------------------------------------
    // 응답 파싱
    // ---------------------------------------------------------------------------

    /**
     * 모델 응답에서 구조화 필드를 꺼냅니다.
     *
     * <p>⚠️ <b>JSON 파싱 실패를 오류로 처리하지 않습니다.</b> 모델이 코드블록
     * ({@code ```json … ```})으로 감싸거나 앞뒤에 설명을 붙이는 일이 흔합니다.
     * 그때 분석 자체를 버리면 사용자는 "분석 실패" 만 보게 되는데, 원문에는
     * 쓸 만한 내용이 들어 있습니다. 그래서 원문은 항상 남기고,
     * 파싱에 성공하면 보기 좋게 채웁니다.
     *
     * @param record 분석 레코드 (여기에 채웁니다)
     * @param text   모델 응답
     */
    private void parseStructured(LogAnalysis record, String text) {
        final JsonNode root = tryParseJson(text);
        if (root == null || !root.isObject()) {
            // 파싱 실패: 원문만 남깁니다. 화면은 raw_response 를 그대로 보여줍니다.
            log.debug("AI response is not JSON; keeping raw text only");
            record.setRiskLevel(null);
            return;
        }

        record.setRiskLevel(normalizeRisk(root.path("risk_level").asString("")));
        record.setSummary(truncate(root.path("summary").asString(""), 4000));
        record.setRootCause(truncate(root.path("root_cause").asString(""), 8000));

        final JsonNode recommendations = root.path("recommendations");
        if (recommendations.isArray()) {
            final List<String> items = new ArrayList<>();
            for (final JsonNode item : recommendations) {
                final String value = item.asString("");
                if (!value.isBlank()) {
                    items.add(value);
                }
            }
            record.setRecommendationsJson(writeJsonList(items));
        }
    }

    /**
     * 응답에서 JSON 을 꺼냅니다.
     *
     * <p>모델이 코드블록으로 감싸는 경우가 흔하므로 그것을 먼저 벗겨냅니다.
     * 그다음 첫 {@code &#123;} 부터 마지막 {@code &#125;} 까지를 시도합니다.
     *
     * @param text 모델 응답
     * @return 파싱된 JSON (실패 시 null)
     */
    private JsonNode tryParseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String candidate = text.trim();

        // ```json … ``` 또는 ``` … ``` 를 벗깁니다.
        if (candidate.startsWith("```")) {
            final int firstNewline = candidate.indexOf('\n');
            if (firstNewline > 0) {
                candidate = candidate.substring(firstNewline + 1);
            }
            final int closing = candidate.lastIndexOf("```");
            if (closing >= 0) {
                candidate = candidate.substring(0, closing);
            }
            candidate = candidate.trim();
        }

        // 그래도 안 되면 첫 { 부터 마지막 } 까지를 시도합니다.
        if (!candidate.startsWith("{")) {
            final int start = candidate.indexOf('{');
            final int end = candidate.lastIndexOf('}');
            if (start >= 0 && end > start) {
                candidate = candidate.substring(start, end + 1);
            }
        }

        try {
            return mapper.readTree(candidate);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** 위험도 문자열을 표준 값으로 정규화합니다. */
    private String normalizeRisk(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String upper = value.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "CRITICAL", "SEVERE", "HIGH" -> upper.equals("HIGH") ? "HIGH" : "CRITICAL";
            case "MEDIUM", "MODERATE", "WARN", "WARNING" -> "MEDIUM";
            case "LOW", "INFO", "OK", "NONE" -> "LOW";
            default -> null;
        };
    }

    // ---------------------------------------------------------------------------
    // 유틸
    // ---------------------------------------------------------------------------

    /** 분석 레코드를 화면용 맵으로 바꿉니다. */
    private Map<String, Object> toView(LogAnalysis record) {
        final Map<String, Object> view = new LinkedHashMap<>();
        view.put("analysis_id", record.getAnalysisId());
        view.put("project_id", record.getProjectKey());
        view.put("agent_id", record.getAgentId());
        view.put("scope", record.getScope());
        view.put("severity_filter", record.getSeverityFilter());
        view.put("period_from", record.getPeriodFrom());
        view.put("period_to", record.getPeriodTo());
        view.put("provider_name", record.getProviderName());
        view.put("model", record.getModel());
        view.put("succeeded", Boolean.TRUE.equals(record.getSucceeded()));
        view.put("error_message", record.getErrorMessage());
        view.put("risk_level", record.getRiskLevel());
        view.put("summary", record.getSummary());
        view.put("root_cause", record.getRootCause());
        view.put("recommendations", readJsonList(record.getRecommendationsJson()));
        view.put("included_log_count", record.getIncludedLogCount());
        view.put("total_log_count", record.getTotalLogCount());
        // 잘렸는지 화면이 바로 알 수 있게 계산해서 넘깁니다.
        view.put("truncated", record.getTotalLogCount() != null
                && record.getIncludedLogCount() != null
                && record.getTotalLogCount() > record.getIncludedLogCount());
        view.put("log_ids", readJsonList(record.getLogIdsJson()));
        view.put("elapsed_ms", record.getElapsedMs());
        view.put("requested_by", record.getRequestedBy());
        view.put("created_at", record.getCreatedAt());
        // 파싱 성공 여부를 알려 화면이 원문을 보여줄지 판단하게 합니다.
        view.put("structured", record.getSummary() != null && !record.getSummary().isBlank());
        view.put("raw_response", record.getRawResponse());
        return view;
    }

    private String buildFilterJson(List<Long> logIds, String agentId, String projectKey,
                                   String from, String to, String severity, String scope) {
        final Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("log_ids", logIds == null ? List.of() : logIds);
        filter.put("agent_id", blankToNull(agentId));
        filter.put("project_id", blankToNull(projectKey));
        filter.put("from", blankToNull(from));
        filter.put("to", blankToNull(to));
        filter.put("severity", blankToNull(severity));
        filter.put("scope", scope);
        return writeJson(filter);
    }

    private String buildLogIdsJson(List<DeviceLog> logs) {
        final List<Long> ids = logs.stream().map(DeviceLog::getId).filter(java.util.Objects::nonNull).toList();
        return writeJsonList(ids.stream().map(String::valueOf).toList());
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (RuntimeException ex) {
            return "{}";
        }
    }

    private String writeJsonList(List<?> values) {
        return writeJson(values == null ? List.of() : values);
    }

    private List<String> readJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            final JsonNode node = mapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            final List<String> values = new ArrayList<>();
            for (final JsonNode item : node) {
                values.add(item.asString(""));
            }
            return values;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int limit) {
        if (value == null) return null;
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
