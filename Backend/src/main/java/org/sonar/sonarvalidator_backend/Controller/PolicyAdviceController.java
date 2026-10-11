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

/**
 * 망분리 위반에 대한 <b>AI 정책 조언</b> API 입니다. (SONAR-43)
 *
 * <h2>무엇을 하는가</h2>
 * <p>정책 관리 화면의 <b>위반 메시지 카드를 누르면</b> 그 위반을 어떻게
 * 해결할지 AI 에게 묻습니다. 위반 현황 요약 카드(프로젝트 단위)에서도
 * 물을 수 있습니다.
 *
 * <h2>엔드포인트</h2>
 * <pre>
 *   POST /api/v1/policy/advice/{projectId}          조언 요청
 *        { "rule_id": "Rule-9001",                  // 카드를 누른 경우
 *          "src_subnet": "Subnet-0131",              // (선택) 같은 규칙의 다른 쌍 구분
 *          "dst_subnet": "Subnet-0141",
 *          "provider_id": 3,                         // (선택) 없으면 기본 공급자
 *          "prompt": "군사 규정 관점에서 봐줘" }       // (선택) 추가 질문
 *
 *   GET  /api/v1/policy/advice/{projectId}          조언 이력
 *        ?rule_id=Rule-9001                         (선택) 그 위반의 과거 조언만
 *        &limit=30
 *
 *   GET  /api/v1/policy/advice/{projectId}/{adviceId}   조언 상세
 * </pre>
 *
 * <h2>⚠️ 실패해도 200 을 돌려준다</h2>
 * <p>AI 호출 실패(키 만료·모델명 오타·서버 미기동)는 <b>사유가 담긴 정상
 * 응답</b>({@code succeeded=false}, {@code error_message})으로 돌려줍니다.
 * 5xx 로 만들면 화면이 사유를 보여주기 어렵고, 이력에도 남기기 어렵습니다.
 * {@code LogController.analyze} 와 같은 규칙입니다.
 *
 * <h2>⚠️ 왜 PUT 이 아니라 POST 인가</h2>
 * <p>같은 요청을 두 번 보내면 <b>AI 호출이 두 번</b> 일어나고 비용이 두 번
 * 나갑니다. PUT 은 "여러 번 보내도 같은 결과" 를 약속하는 의미이므로,
 * 이 동작에는 맞지 않습니다. 대신 GET 이력으로 <b>재사용</b>할 수 있게 했습니다.
 *
 * <h2>⚠️ 알림을 남기는 이유</h2>
 * <p>조언은 <b>정책을 바꾸는 근거</b>가 됩니다. "언제 누가 어떤 위반에 대해
 * 무엇을 조언받았나" 는 감사(audit)에서 필요해집니다. 조언이 성공하면
 * 기록하고, 위험도가 {@code CRITICAL}/{@code HIGH} 면 알림으로도 남깁니다.
 */
@RestController
@RequestMapping("/api/v1/policy/advice")
public class PolicyAdviceController {

    private static final Logger log = LoggerFactory.getLogger(PolicyAdviceController.class);

    private final ViolationAdvisorService advisorService;

    /**
     * 알림 기록기입니다.
     *
     * <p>조언 자체는 이력 테이블에 남지만, <b>위험도가 높은 조언</b>은
     * 운영자가 즉시 알아야 합니다. 이력은 찾아봐야 보이지만 알림은 먼저
     * 보입니다.
     */
    private final NotificationService notificationService;

    /**
     * @param advisorService     정책 조언 서비스
     * @param notificationService 알림 서비스
     */
    public PolicyAdviceController(ViolationAdvisorService advisorService,
                                  NotificationService notificationService) {
        this.advisorService = advisorService;
        this.notificationService = notificationService;
    }

    /**
     * 위반에 대한 정책 조언을 요청합니다.
     *
     * @param projectId      프로젝트 키
     * @param body           요청 본문 (rule_id / src_subnet / dst_subnet / provider_id / prompt)
     * @param authentication 현재 사용자 (요청자 기록)
     * @return 조언 결과 또는 실패 사유
     */
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

    /**
     * 프로젝트의 조언 이력을 조회합니다.
     *
     * <p>⚠️ 이 API 가 있는 이유: 같은 위반을 다시 물으면 AI 호출 비용이 두 번
     * 나갑니다. 화면은 카드를 열 때 <b>이력을 먼저 보여주고</b>, 운영자가
     * "다시 물어보기" 를 눌렀을 때만 POST 합니다.
     *
     * @param projectId 프로젝트 키
     * @param ruleId    규칙 식별자 (선택)
     * @param limit     최대 건수 (기본 30)
     * @return {@code {"total": n, "advices": [...]}}
     */
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

    /**
     * 조언 한 건을 상세 조회합니다.
     *
     * @param projectId 프로젝트 키 (경로 일관성용)
     * @param adviceId  조언 식별자 (예: {@code PADV-1A2B3C4D})
     * @return 조언 상세 (없으면 404)
     */
    @GetMapping("/{projectId}/{adviceId}")
    public Map<String, Object> detail(@PathVariable String projectId,
                                      @PathVariable String adviceId) {
        final Map<String, Object> detail = advisorService.detail(adviceId);
        if (detail == null) {
            throw new AdviceNotFoundRequest("조언을 찾을 수 없습니다: " + adviceId);
        }
        return detail;
    }

    /**
     * 위험도가 높은 조언을 알림으로 남깁니다.
     *
     * <p>⚠️ <b>실패해도 조언 응답을 막지 않습니다.</b> 조언은 이미 만들어졌고
     * 이력에도 남았으므로, 알림 실패가 사용자 응답을 500 으로 바꾸면
     * "조언은 성공했는데 화면은 오류" 라는 모순이 생깁니다.
     *
     * @param projectId 프로젝트 키
     * @param result    조언 결과
     */
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

    // ---------------------------------------------------------------------------
    // 본문 파싱 유틸
    // ---------------------------------------------------------------------------

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

    /**
     * 조언을 찾지 못했을 때 404 를 내기 위한 예외입니다.
     *
     * @param message 사유
     */
    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class AdviceNotFoundRequest extends RuntimeException {
        /**
         * @param message 사유
         */
        public AdviceNotFoundRequest(String message) {
            super(message);
        }
    }
}