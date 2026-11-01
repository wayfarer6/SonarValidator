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

@Service
public class ViolationAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(ViolationAdvisorService.class);

    private static final int MAX_STORED_RESPONSE = 30_000;

    private static final int DEFAULT_HISTORY = 30;
    private static final int MAX_HISTORY = 200;

    private final ProjectService projectService;
    private final AiProviderService providerService;
    private final OpenAiCompatibleClient client;
    private final PolicyAdviceParser parser;
    private final PolicyAdviceRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();
    private AdviceCommandContext commandContext;
    @org.springframework.beans.factory.annotation.Autowired
    public void setCommandContext(AdviceCommandContext context) { this.commandContext = context; }


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

    public Map<String, Object> advise(String projectKey,
                                      String ruleId,
                                      String srcSubnet,
                                      String dstSubnet,
                                      Long providerId,
                                      String userPrompt,
                                      String requestedBy) {

        return advise(projectKey, ruleId, srcSubnet, dstSubnet, providerId, userPrompt, requestedBy, null);
    }

    public Map<String, Object> advise(String projectKey, String ruleId, String srcSubnet, String dstSubnet,
                                      Long providerId, String userPrompt, String requestedBy, String targetAgent) {
        final long startedAt = System.currentTimeMillis();

        // 1) 판정은 항상 서버가 다시 한다. (프론트가 보낸 텍스트를 신뢰하지 않음)
        final Project project = projectService.getByKey(projectKey);
        final SegmentationBddEngine.Report report = projectService.validateStored(projectKey);

        final PolicyAdviceContext context = PolicyAdviceContext.of(project, report);
        final AdviceCommandContext.Snapshot commandSnapshot = commandContext == null ? null : commandContext.snapshot(project, targetAgent);
        final ViolationBrief focus = context.findBrief(ruleId, srcSubnet, dstSubnet);

        final String scope = (ruleId == null || ruleId.isBlank()) ? "project" : "violation";

        final PolicyAdvice record = new PolicyAdvice();
        record.setAdviceId("PADV-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase(Locale.ROOT));
        record.setProjectKey(projectKey);
        record.setProjectName(project.getName());
        record.setTargetAgent(targetAgent);
        record.setContextFingerprint(commandSnapshot == null ? null : commandSnapshot.fingerprint());
        record.setRuleId(ruleId == null || ruleId.isBlank() ? null : ruleId.trim());
        record.setScope(scope);
        record.setViolationCount(context.totalViolationCount());
        record.setCompliant(context.compliant());
        record.setIncludedViolationCount(context.briefs().size());
        record.setTruncated(context.truncated());
        record.setRequestedBy(requestedBy == null || requestedBy.isBlank() ? "system" : requestedBy);
        record.setCreatedAt(new java.util.Date());

        final java.util.Optional<AiProvider> resolved =
                providerService.resolveOrEmpty(providerId);

        if (resolved.isEmpty()) {
            record.setSucceeded(false);
            record.setErrorMessage(providerId == null
                    ? "사용 가능한 AI 공급자가 없습니다. 설정에서 AI 공급자를 등록하고 '사용' 을 켜세요."
                    : "지정한 AI 공급자를 찾을 수 없습니다: " + providerId);
            record.setElapsedMs(System.currentTimeMillis() - startedAt);

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
                PolicyAdvicePromptBuilder.build(provider, context, focus, userPrompt, commandSnapshot == null ? null : commandSnapshot.prompt());

        log.info("policy advice started: id={} project={} rule={} scope={} violations={}/{} provider={}",
                record.getAdviceId(), projectKey, record.getRuleId(), scope,
                context.briefs().size(), context.totalViolationCount(), provider.getName());

        final Result result = client.chat(providerService.toConnection(provider), messages, true);

        record.setElapsedMs(result.elapsedMs());
        record.setRawResponse(truncate(result.text(), MAX_STORED_RESPONSE));

        if (!result.ok()) {

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
        String commandsJson = mapper.writeValueAsString(answer.commands().stream().map(c -> c.toView()).toList());
        if (commandsJson.length() > 28000) {
            record.setCommandsJson("[]"); record.setNeedsMoreData(true);
        } else record.setCommandsJson(commandsJson);
        record.setEvidenceJson(writeJsonList(answer.evidence()));
        record.setNeedsMoreData(Boolean.TRUE.equals(record.getNeedsMoreData()) || answer.needsMoreData());

        final PolicyAdvice saved = repository.save(record);

        log.info("policy advice completed: id={} risk={} structured={} options={} elapsed={}ms",
                saved.getAdviceId(), saved.getRiskLevel(), saved.getStructured(),
                answer.options().size(), saved.getElapsedMs());

        return toView(saved);
    }

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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historyForAgent(String projectKey, String agentId, Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_HISTORY : Math.min(MAX_HISTORY, limit);
        return repository.findByProjectKeyAndTargetAgentOrderByCreatedAtDesc(projectKey, agentId, PageRequest.of(0,size)).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String adviceId) {
        return repository.findByAdviceId(adviceId)
                .map(this::toView)
                .orElse(null);
    }

    // 직렬화 / 유틸

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
        view.put("target_agent", record.getTargetAgent());
        if (commandContext != null && record.getCommandsJson() != null) {
            var commands = parser.readCommands(mapper.readTree(record.getCommandsJson()));
            view.put("command_proposals", commandContext.views(projectService.getByKey(record.getProjectKey()), record, commands));
        }
        return view;
    }

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
