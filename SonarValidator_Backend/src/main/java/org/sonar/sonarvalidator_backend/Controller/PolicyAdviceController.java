package org.sonar.sonarvalidator_backend.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Service.NotificationService;
import org.sonar.sonarvalidator_backend.Service.policy.ViolationAdvisorService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policy/advice")
public class PolicyAdviceController {

    private static final Logger log = LoggerFactory.getLogger(PolicyAdviceController.class);

    private final ViolationAdvisorService advisorService;

    private final NotificationService notificationService;

    public PolicyAdviceController(ViolationAdvisorService advisorService,
                                  NotificationService notificationService) {
        this.advisorService = advisorService;
        this.notificationService = notificationService;
    }

    @PostMapping("/{projectId}")
    public Map<String, Object> advise(@PathVariable String projectId,
                                      @RequestBody(required = false) Map<String, Object> body,
                                      Authentication authentication) {

        final Map<String, Object> safeBody = body == null ? Map.of() : body;

        final Long providerId = safeBody.get("provider_id") == null
                ? null
                : parseLong(safeBody.get("provider_id"));

        final String requestedBy = authentication == null
                ? "system"
                : authentication.getName();

        final Map<String, Object> result = advisorService.advise(
                projectId,
                text(safeBody.get("rule_id")),
                text(safeBody.get("src_subnet")),
                text(safeBody.get("dst_subnet")),
                providerId,
                text(safeBody.get("prompt")),
                requestedBy);

        notifyIfNoteworthy(projectId, result);
        return result;
    }

    @GetMapping("/{projectId}")
    public Map<String, Object> history(@PathVariable String projectId,
                                       @RequestParam(value = "rule_id", required = false) String ruleId,
                                       @RequestParam(value = "limit", required = false) Integer limit) {

        final List<Map<String, Object>> rows = advisorService.history(projectId, ruleId, limit);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("total", rows.size());
        body.put("advices", rows);
        return body;
    }

    @GetMapping("/{projectId}/{adviceId}")
    public Map<String, Object> detail(@PathVariable String projectId,
                                      @PathVariable String adviceId) {
        final Map<String, Object> detail = advisorService.detail(adviceId);
        if (detail == null) {
            throw new AdviceNotFoundRequest("조언을 찾을 수 없습니다: " + adviceId);
        }
        return detail;
    }

    private void notifyIfNoteworthy(String projectId, Map<String, Object> result) {
        if (!Boolean.TRUE.equals(result.get("succeeded"))) {
            // 실패는 이력에 남습니다. 알림은 "점검 필요" 신호용이므로 실패에는 보내지 않습니다.
            return;
        }

        final Object risk = result.get("risk_level");
        final String riskText = risk == null ? "" : String.valueOf(risk);
        if (!"CRITICAL".equals(riskText) && !"HIGH".equals(riskText)) {
            return;
        }

        try {
            final String ruleId = result.get("rule_id") == null
                    ? "프로젝트 전체"
                    : String.valueOf(result.get("rule_id"));
            final String summary = result.get("summary") == null
                    ? "정책 점검이 필요합니다."
                    : String.valueOf(result.get("summary"));

            notificationService.notifyQuietly(
                    "POLICY",
                    "CRITICAL".equals(riskText) ? "critical" : "warning",
                    "AI 정책 조언 — 위험도 " + riskText + ": " + ruleId,
                    truncate(summary, 300),
                    projectId,
                    null,
                    "ai",
                    "/policy?project_id=" + projectId,
                    // ⚠️ 같은 프로젝트/규칙/위험도면 합칩니다. 조언을 반복해서
                    //    물으면 알림이 계속 쌓여 목록이 의미를 잃습니다.
                    "policy-advice:" + projectId + ":" + ruleId + ":" + riskText);
        } catch (RuntimeException ex) {
            // 알림 실패가 조언 응답을 막지 않아야 합니다.
            log.warn("failed to notify policy advice: project={} cause={}",
                    projectId, ex.getMessage());
        }
    }

    // 본문 파싱 유틸

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        final String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Long parseLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String truncate(String value, int limit) {
        if (value == null) return null;
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class AdviceNotFoundRequest extends RuntimeException {
        public AdviceNotFoundRequest(String message) {
            super(message);
        }
    }
}
