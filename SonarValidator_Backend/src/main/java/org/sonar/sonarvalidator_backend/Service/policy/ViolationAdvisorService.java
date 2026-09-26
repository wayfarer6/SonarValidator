package org.sonar.sonarvalidator_backend.Service.policy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.entity.AiProvider;
import org.sonar.sonarvalidator_backend.Model.entity.PolicyAdvice;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine;
import org.sonar.sonarvalidator_backend.Policy.advice.PolicyAdviceAnswer;
import org.sonar.sonarvalidator_backend.Policy.advice.PolicyAdviceContext;
import org.sonar.sonarvalidator_backend.Policy.advice.PolicyAdviceOption;
import org.sonar.sonarvalidator_backend.Policy.advice.PolicyAdviceParser;
import org.sonar.sonarvalidator_backend.Policy.advice.PolicyAdvicePromptBuilder;
import org.sonar.sonarvalidator_backend.Policy.advice.ViolationBrief;
import org.sonar.sonarvalidator_backend.Repository.PolicyAdviceRepository;
import org.sonar.sonarvalidator_backend.Service.ProjectService;
import org.sonar.sonarvalidator_backend.Service.ai.AiProviderService;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient.Message;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient.Result;
import org.sonar.sonarvalidator_backend.Util.Timestamps;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 망분리 위반에 대한 <b>AI 정책 조언</b>을 만듭니다. (SONAR-43)
 *
 * <h2>무엇을 하는가</h2>
 * <p>운영자가 위반 메시지 카드를 누르면, 그 위반을 <b>정책 관점에서 어떻게
 * 해결할지</b> AI 에게 묻고 결과를 화면에 돌려줍니다.
 *
 * <pre>
 *   위반 카드 클릭 (프론트)
 *        │  POST /api/v1/policy/advice/{projectId}
 *        │  { rule_id, src_subnet, dst_subnet, prompt }
 *        ▼
 *   ProjectService.validateStored()        ← 판정은 항상 서버가 다시 한다
 *        │
 *        ▼
 *   PolicyAdviceContext.of()               ← 등급·서브넷·위반을 프롬프트용으로 정리
 *        │
 *        ▼
 *   PolicyAdvicePromptBuilder.build()      ← 시스템 지침 + 맥락 + 질문
 *        │
 *        ▼
 *   OpenAiCompatibleClient.chat()          ← 공급자/모델은 DB 설정에서
 *        │
 *        ▼
 *   PolicyAdviceParser.parse()             ← JSON 3가지 모양 + 스키마 이탈 흡수
 *        │
 *        ▼
 *   policy_advice 테이블에 저장 (실패도 기록)
 * </pre>
 *
 * <h2>⚠️ 왜 위반 목록을 프론트에서 받지 않고 서버가 다시 판정하는가</h2>
 * <p>프론트가 보낸 위반 텍스트를 그대로 프롬프트에 넣으면, 화면이 조작되거나
 * 낡은 상태일 때 <b>존재하지 않는 위반에 대한 조언</b>이 나옵니다. 조언은
 * 정책 변경의 근거가 되므로, 근거가 되는 판정을 서버가 확정해야 합니다.
 * 프론트가 보내는 것은 <b>"어느 위반을 물었는가"</b>(rule_id/src/dst) 뿐입니다.
 *
 * <h2>⚠️ 실패도 저장한다</h2>
 * <p>{@code LogAnalysisEngine} 과 같은 원칙입니다. 키 만료·모델명 오타·서버
 * 미기동을 저장하지 않으면 화면에 흔적이 없어 운영자가 같은 실수를 반복합니다.
 *
 * <h2>⚠️ 위반이 0건이어도 물어볼 수 있다</h2>
 * <p>"지금 구성이 안전한가 / 더 강화할 점은 없는가" 는 준수 상태에서도 유효한
 * 질문입니다. 그래서 위반 0건을 오류로 만들지 않고, 프롬프트가 <b>평가 요청</b>
 * 으로 바뀝니다. ({@code PolicyAdvicePromptBuilder})
 */
@Service
public class ViolationAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(ViolationAdvisorService.class);

    /** 저장할 원문 응답의 최대 길이입니다. (컬럼 크기와 맞춤) */
    private static final int MAX_STORED_RESPONSE = 30_000;

    /** 조회 이력의 기본/최대 건수입니다. */
    private static final int DEFAULT_HISTORY = 30;
    private static final int MAX_HISTORY = 200;

    private final ProjectService projectService;
    private final AiProviderService providerService;
    private final OpenAiCompatibleClient client;
    private final PolicyAdviceParser parser;
    private final PolicyAdviceRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param projectService  프로젝트/판정 접근
     * @param providerService AI 공급자 선택
     * @param client          OpenAI 호환 클라이언트
     * @param parser          응답 파서
     * @param repository      조언 기록 저장소
     */
    public ViolationAdvisorService(ProjectService projectService,
                                   AiProviderService providerService,
                                   OpenAiCompatibleClient client,
                                   PolicyAdviceParser parser,
                                   PolicyAdviceRepository repository) {
        this.projectService = projectService;
        this.providerService = providerService;
        this.client = client;
        this.parser = parser;
        this.repository = repository;
    }

    /**
     * 위반에 대한 정책 조언을 요청합니다.
     *
     * @param projectKey  프로젝트 키
     * @param ruleId      초점 위반의 규칙 식별자 (null 이면 프로젝트 전체)
     * @param srcSubnet   초점 위반의 출발 서브넷 (선택)
     * @param dstSubnet   초점 위반의 도착 서브넷 (선택)
     * @param providerId  사용할 AI 공급자 (null 이면 기본)
     * @param userPrompt  사용자 추가 질문 (선택)
     * @param requestedBy 요청자
     * @return 조언 결과 (화면용)
     */
    @Transactional
    public Map<String, Object> advise(String projectKey,
                                      String ruleId,
                                      String srcSubnet,
                                      String dstSubnet,
                                      Long providerId,
                                      String userPrompt,
                                      String requestedBy) {

        final long startedAt = System.currentTimeMillis();

        // 1) 판정은 항상 서버가 다시 한다. (프론트가 보낸 텍스트를 신뢰하지 않음)
        final Project project = projectService.getByKey(projectKey);
        final SegmentationBddEngine.Report report = projectService.validateStored(projectKey);

        final PolicyAdviceContext context = PolicyAdviceContext.of(project, report);
        final ViolationBrief focus = context.findBrief(ruleId, srcSubnet, dstSubnet);

        final String scope = (ruleId == null || ruleId.isBlank()) ? "project" : "violation";

        final PolicyAdvice record = new PolicyAdvice();
        record.setAdviceId("PADV-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase(Locale.ROOT));
        record.setProjectKey(projectKey);
        record.setProjectName(project.getName());
        record.setRuleId(ruleId == null || ruleId.isBlank() ? null : ruleId.trim());
        record.setScope(scope);
        record.setViolationCount(context.totalViolationCount());
        record.setCompliant(context.compliant());
        record.setIncludedViolationCount(context.briefs().size());
        record.setTruncated(context.truncated());
        record.setRequestedBy(requestedBy == null || requestedBy.isBlank() ? "system" : requestedBy);
        record.setCreatedAt(new java.util.Date());

        // 2) 공급자를 고릅니다.
        //    ⚠️ 예외 없는 조회(resolveOrEmpty)를 씁니다.
        //    resolve() 를 쓰면 "공급자 없음" 예외가 이 메서드의 트랜잭션을
        //    rollback-only 로 마킹해, 실패를 기록하려는 save() 까지 롤백되고
        //    500 이 나갑니다. (실측으로 확인한 결함)
        final java.util.Optional<AiProvider> resolved =
                providerService.resolveOrEmpty(providerId);

        if (resolved.isEmpty()) {
            record.setSucceeded(false);
            record.setErrorMessage(providerId == null
                    ? "사용 가능한 AI 공급자가 없습니다. 설정에서 AI 공급자를 등록하고 '사용' 을 켜세요."
                    : "지정한 AI 공급자를 찾을 수 없습니다: " + providerId);
            record.setElapsedMs(System.currentTimeMillis() - startedAt);
            // 실패도 저장합니다. 사유가 남아야 운영자가 다음에 무엇을 고칠지 압니다.
            final PolicyAdvice saved = repository.save(record);
            log.warn("policy advice rejected: id={} reason={}",
                    saved.getAdviceId(), saved.getErrorMessage());
            return toView(saved);
        }

        final AiProvider provider = resolved.get();

        record.setProviderName(provider.getName());
        record.setModel(provider.getModel());

        // 3) 프롬프트를 조립하고 호출합니다.
        final List<Message> messages =
                PolicyAdvicePromptBuilder.build(provider, context, focus, userPrompt);

        log.info("policy advice started: id={} project={} rule={} scope={} violations={}/{} provider={}",
                record.getAdviceId(), projectKey, record.getRuleId(), scope,
                context.briefs().size(), context.totalViolationCount(), provider.getName());

        final Result result = client.chat(providerService.toConnection(provider), messages, true);

        record.setElapsedMs(result.elapsedMs());
        record.setRawResponse(truncate(result.text(), MAX_STORED_RESPONSE));

        if (!result.ok()) {
            // 실패도 저장합니다. 사유가 남아야 운영자가 다음에 무엇을 고칠지 압니다.
            record.setSucceeded(false);
            record.setErrorMessage(truncate(result.text(), 4000));
            final PolicyAdvice saved = repository.save(record);
            log.warn("policy advice failed: id={} reason={}", saved.getAdviceId(), result.text());
            return toView(saved);
        }

        // 4) 구조화 파싱을 시도합니다. 실패해도 원문은 남깁니다.
        final PolicyAdviceAnswer answer = parser.parse(result.text());
        record.setSucceeded(true);
        record.setStructured(answer.structured());
        record.setRiskLevel(answer.riskLevel());
        record.setSummary(truncate(answer.summary(), 4000));
        record.setRootCause(truncate(answer.rootCause(), 8000));
        record.setPolicyAdvice(truncate(answer.policyAdvice(), 4000));
        record.setOptionsJson(writeOptions(answer.options()));
        record.setEvidenceJson(writeJsonList(answer.evidence()));
        record.setNeedsMoreData(answer.needsMoreData());

        final PolicyAdvice saved = repository.save(record);

        log.info("policy advice completed: id={} risk={} structured={} options={} elapsed={}ms",
                saved.getAdviceId(), saved.getRiskLevel(), saved.getStructured(),
                answer.options().size(), saved.getElapsedMs());

        return toView(saved);
    }

    /**
     * 조언 이력을 조회합니다.
     *
     * @param projectKey 프로젝트 키 (필수)
     * @param ruleId     규칙 식별자 (null 이면 프로젝트 전체 이력)
     * @param limit      최대 건수
     * @return 조언 이력
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(String projectKey, String ruleId, Integer limit) {
        final int size = limit == null || limit <= 0
                ? DEFAULT_HISTORY
                : Math.min(MAX_HISTORY, limit);
        final var pageable = PageRequest.of(0, size);

        final List<PolicyAdvice> rows =
                (ruleId != null && !ruleId.isBlank())
                        ? repository.findByProjectKeyAndRuleIdOrderByCreatedAtDesc(
                                projectKey, ruleId.trim(), pageable)
                        : (projectKey != null && !projectKey.isBlank())
                                ? repository.findByProjectKeyOrderByCreatedAtDesc(projectKey, pageable)
                                : repository.findAllByOrderByCreatedAtDesc(pageable);

        return rows.stream().map(this::toView).toList();
    }

    /**
     * 조언 한 건을 상세 조회합니다.
     *
     * @param adviceId 조언 식별자
     * @return 조언 (없으면 null)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(String adviceId) {
        return repository.findByAdviceId(adviceId)
                .map(this::toView)
                .orElse(null);
    }

    // ---------------------------------------------------------------------------
    // 직렬화 / 유틸
    // ---------------------------------------------------------------------------

    /**
     * 조언 레코드를 화면용 맵으로 바꿉니다.
     *
     * @param record 조언 레코드
     * @return 화면용 맵
     */
    private Map<String, Object> toView(PolicyAdvice record) {
        final PolicyAdviceAnswer answer = new PolicyAdviceAnswer(
                record.getRiskLevel(),
                record.getSummary(),
                record.getRootCause(),
                record.getPolicyAdvice(),
                readOptions(record.getOptionsJson()),
                readJsonList(record.getEvidenceJson()),
                Boolean.TRUE.equals(record.getNeedsMoreData()),
                Boolean.TRUE.equals(record.getStructured()),
                record.getRawResponse());

        final Map<String, Object> view = answer.toView();
        // answer 의 키를 먼저 깔고, 기록 메타데이터를 덧붙입니다.
        view.put("advice_id", record.getAdviceId());
        view.put("project_id", record.getProjectKey());
        view.put("project_name", record.getProjectName());
        view.put("rule_id", record.getRuleId());
        view.put("scope", record.getScope());
        view.put("violation_count", record.getViolationCount());
        view.put("compliant", Boolean.TRUE.equals(record.getCompliant()));
        view.put("included_violation_count", record.getIncludedViolationCount());
        view.put("truncated", Boolean.TRUE.equals(record.getTruncated()));
        view.put("provider_name", record.getProviderName());
        view.put("model", record.getModel());
        view.put("elapsed_ms", record.getElapsedMs());
        view.put("requested_by", record.getRequestedBy());
        view.put("succeeded", Boolean.TRUE.equals(record.getSucceeded()));
        view.put("error_message", record.getErrorMessage());
        view.put("created_at", Timestamps.iso(record.getCreatedAt()));
        view.put("structured", Boolean.TRUE.equals(record.getStructured()));
        return view;
    }

    /** 선택지 목록을 JSON 으로 씁니다. */
    private String writeOptions(List<PolicyAdviceOption> options) {
        final List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (final PolicyAdviceOption option : options) {
            final Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("title", option.title());
            row.put("approach", option.approach());
            row.put("security_impact", option.securityImpact());
            row.put("operational_cost", option.operationalCost());
            row.put("recommended", option.recommended());
            rows.add(row);
        }
        return writeJson(rows);
    }

    /** 저장된 선택지 JSON 을 다시 읽습니다. */
    private List<PolicyAdviceOption> readOptions(String json) {
        final List<PolicyAdviceOption> options = new java.util.ArrayList<>();
        if (json == null || json.isBlank()) {
            return options;
        }
        try {
            final JsonNode node = mapper.readTree(json);
            if (!node.isArray()) {
                return options;
            }
            for (final JsonNode item : node) {
                options.add(new PolicyAdviceOption(
                        item.path("title").asString(""),
                        item.path("approach").asString(""),
                        item.path("security_impact").asString(""),
                        item.path("operational_cost").asString(""),
                        item.path("recommended").asBoolean(false)));
            }
        } catch (RuntimeException ex) {
            // 저장된 값이 깨져도 화면은 나머지를 보여줘야 합니다.
            log.warn("failed to read stored advice options: {}", ex.getMessage());
        }
        return options;
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (RuntimeException ex) {
            return "{}";
        }
    }

    private String writeJsonList(List<String> values) {
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
            final List<String> values = new java.util.ArrayList<>();
            for (final JsonNode item : node) {
                values.add(item.asString(""));
            }
            return values;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static String truncate(String value, int limit) {
        if (value == null) return null;
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}