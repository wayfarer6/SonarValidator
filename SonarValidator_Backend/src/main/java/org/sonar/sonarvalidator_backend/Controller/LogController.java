package org.sonar.sonarvalidator_backend.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Service.ai.LogAnalysisEngine;
import org.sonar.sonarvalidator_backend.Service.log.LogLineReader;
import org.sonar.sonarvalidator_backend.Service.log.LogService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.JsonNode;

/**
 * 장비 로그 조회/수집과 AI 분석 API 입니다.
 *
 * <h2>엔드포인트</h2>
 * <pre>
 *   GET  /api/v1/logs                              로그 조회 (필터 조합)
 *   GET  /api/v1/logs/filters                      필터 선택지 (장비 목록 등)
 *   GET  /api/v1/logs/summary                      심각도별 집계
 *   POST /api/v1/logs/ingest                       로그 적재 (JSON 본문)
 *   POST /api/v1/logs/upload                       로그 파일 업로드 (multipart)
 *   POST /api/v1/logs/{id}/flags                   표시/메모 수정
 *   POST /api/v1/logs/analyze                      AI 분석 실행
 *   GET  /api/v1/logs/analyses                     분석 이력
 *   GET  /api/v1/logs/analyses/{analysisId}        분석 상세
 * </pre>
 *
 * <h2>필터를 한 엔드포인트로 통합한 이유</h2>
 * <p>요구사항이 "날짜별 / 프로젝트별 / 특정 agent" 로 필터하는 것입니다.
 * 세 축을 각각 별도 엔드포인트로 만들면 조합(프로젝트 + 기간 + 장비)을 표현할
 * 수 없습니다. 그래서 <b>모두 선택적 파라미터</b>로 받고, 안 준 축은 제한하지
 * 않습니다.
 *
 * <h2>⚠️ 로그는 반드시 상한을 둔다</h2>
 * <p>로그는 빠르게 늘어납니다. 상한 없는 조회는 브라우저를 멈추게 하므로
 * {@link LogService#MAX_PAGE_SIZE} 로 자르고, 잘렸는지 응답에 표시합니다.
 */
@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private static final Logger log = LoggerFactory.getLogger(LogController.class);

    private final LogService logService;
    private final LogAnalysisEngine analysisEngine;

    /**
     * 적재 요청의 줄 목록 해석기입니다.
     *
     * <p>이 컨트롤러가 "무엇을 한 줄로 볼 것인가" 를 알 필요가 없게 합니다.
     * 이전에는 {@code ingest} 와 {@code upload} 에 {@code split("\\R")} 이
     * 각각 있었습니다.
     */
    private final LogLineReader lineReader;

    /**
     * @param logService      로그 서비스
     * @param analysisEngine  AI 분석 엔진
     * @param lineReader      적재 줄 해석기
     */
    public LogController(LogService logService, LogAnalysisEngine analysisEngine,
                         LogLineReader lineReader) {
        this.logService = logService;
        this.analysisEngine = analysisEngine;
        this.lineReader = lineReader;
    }

    /**
     * 로그를 조회합니다.
     *
     * <p>모든 파라미터는 선택입니다. 조합해서 좁힐 수 있습니다.
     *
     * @param agentId       특정 장비 (예: {@code c8000v-1})
     * @param projectId     프로젝트 키
     * @param from          기간 시작 ISO-8601 (예: {@code 2026-09-19T00:00:00Z})
     * @param to            기간 끝 ISO-8601
     * @param severity      최소 심각도 ({@code warning} 이면 warning 이상)
     * @param search        본문 검색어
     * @param highlightedOnly 표시해 둔 로그만
     * @param limit         최대 건수 (기본 100, 최대 500)
     * @return {@code {total, returned, truncated, filters, logs}}
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(value = "agent_id", required = false) String agentId,
            @RequestParam(value = "project_id", required = false) String projectId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "highlighted_only", required = false, defaultValue = "false")
            boolean highlightedOnly,
            @RequestParam(value = "limit", required = false) Integer limit) {

        return logService.query(agentId, projectId, from, to, severity, search, highlightedOnly, limit);
    }

    /**
     * 필터 드롭다운에 쓸 선택지를 돌려줍니다.
     *
     * <p>장비 목록을 서버에서 주는 이유: 프론트가 별도 API 두 개를 조합하면
     * 로그가 있는 장비와 연결된 장비가 어긋나 사용자가 빈 결과를 보게 됩니다.
     *
     * @return {@code {agents, severities, total}}
     */
    @GetMapping("/filters")
    public Map<String, Object> filters() {
        return logService.filterOptions();
    }

    /**
     * 심각도별 건수를 돌려줍니다.
     *
     * @return {@code {"summary": [...]}}
     */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", logService.severitySummary());
        return body;
    }

    /**
     * 로그를 적재합니다.
     *
     * <p>본문 예:
     * <pre>
     *   {
     *     "agent_id": "c8000v-1",
     *     "product": "Cisco 8000v",
     *     "project_id": "PRJ-1",
     *     "source": "upload",
     *     "lines": ["%SYS-5-CONFIG_I: Configured from console", "..."]
     *   }
     * </pre>
     *
     * @param body 요청 본문
     * @return {@code {received, inserted, duplicated, skipped}}
     */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, Object> body) {
        final String agentId = text(body.get("agent_id"));
        if (agentId == null) {
            throw new BadLogRequest("agent_id 는 필수입니다.");
        }

        // ⚠️ 줄 쪼개기는 LogLineReader 가 소유합니다.
        //    이전에는 여기와 upload 에 split("\\R") 이 각각 있었고,
        //    같은 내용을 두 경로로 넣었을 때 줄 수가 달라질 수 있었습니다.
        final LogLineReader.LogLines read = lineReader.read(body, null);
        if (!read.recognized()) {
            // 모양을 모르는 요청과 "내용이 빈" 요청은 다른 답을 줘야 합니다.
            throw new BadLogRequest("lines 배열 또는 text 문자열이 필요합니다.");
        }

        final String explicitSource = text(body.get("source"));
        return logService.ingest(
                agentId,
                text(body.get("product")),
                text(body.get("project_id")),
                // 호출자가 출처를 명시했으면 그것을 씁니다. (예: "agent"/"offline")
                explicitSource == null ? read.source() : explicitSource,
                read.immutable().lines());
    }

    /**
     * 로그 파일을 업로드해 적재합니다.
     *
     * <p>장비에 접속할 수 없는 환경에서 로그 파일만 가져온 경우를 위한 경로입니다.
     * UTF-8 로 읽고 줄 단위로 저장합니다.
     *
     * @param file      로그 파일
     * @param agentId   장비 식별자
     * @param product   제품명 (선택, 심각도 해석 힌트)
     * @param projectId 프로젝트 키 (선택)
     * @return 적재 결과
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("agent_id") String agentId,
            @RequestParam(value = "product", required = false) String product,
            @RequestParam(value = "project_id", required = false) String projectId) {

        if (file == null || file.isEmpty()) {
            throw new BadLogRequest("빈 파일입니다.");
        }

        // 쪼개기·인코딩 처리는 LogLineReader 한 곳에 있습니다.
        // 구현체가 IllegalArgument 를 던지면 400 으로 바뀝니다.
        final LogLineReader.LogLines read;
        try {
            read = lineReader.read(null, file);
        } catch (IllegalArgumentException ex) {
            throw new BadLogRequest(ex.getMessage());
        }

        log.info("log file uploaded: name={} agent={} lines={}",
                file.getOriginalFilename(), agentId, read.count());

        return logService.ingest(agentId, product, projectId, read.source(), read.immutable().lines());
    }

    /**
     * 로그의 표시 여부/메모를 수정합니다.
     *
     * @param id   로그 키
     * @param body {@code {"highlighted": true, "note": "이 로그를 분석 대상으로"}}
     * @return 수정된 로그
     */
    @PostMapping("/{id}/flags")
    public Map<String, Object> updateFlags(@PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        final Boolean highlighted = body.get("highlighted") == null
                ? null
                : Boolean.parseBoolean(String.valueOf(body.get("highlighted")));
        final String note = body.containsKey("note") ? text(body.get("note")) : null;

        try {
            return logService.updateFlags(id, highlighted, note);
        } catch (IllegalArgumentException ex) {
            throw new LogNotFoundRequest(ex.getMessage());
        }
    }

    /**
     * AI 로그 분석을 실행합니다.
     *
     * <p>본문 예 (특정 로그만 분석):
     * <pre>
     *   {
     *     "log_ids": [12, 15, 18],
     *     "scope": "selected",
     *     "provider_id": 2,
     *     "prompt": "BGP 세션 끊김의 원인에 집중해 주세요"
     *   }
     * </pre>
     *
     * <p>본문 예 (필터 결과 전체 분석):
     * <pre>
     *   {
     *     "project_id": "PRJ-1",
     *     "agent_id": "c8000v-1",
     *     "from": "2026-09-19T00:00:00Z",
     *     "to": "2026-09-19T23:59:59Z",
     *     "severity": "warning",
     *     "scope": "filter"
     *   }
     * </pre>
     *
     * <p>⚠️ 실패해도 <b>200</b> 을 돌려주고 {@code succeeded=false} 와
     * {@code error_message} 를 담습니다. 실패를 5xx 로 만들면 화면이 사유를
     * 보여주기 어렵고, 이력에도 남기기 어렵기 때문입니다.
     *
     * @param body           요청 본문
     * @param authentication 현재 사용자 (분석 요청자 기록)
     * @return 분석 결과 또는 실패 사유
     */
    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody Map<String, Object> body,
                                      Authentication authentication) {

        final List<Long> logIds = new ArrayList<>();
        if (body.get("log_ids") instanceof List<?> list) {
            for (final Object item : list) {
                if (item instanceof Number number) {
                    logIds.add(number.longValue());
                } else if (item != null) {
                    try {
                        logIds.add(Long.valueOf(String.valueOf(item).trim()));
                    } catch (NumberFormatException ignored) {
                        // 숫자가 아닌 항목은 무시합니다.
                    }
                }
            }
        }

        final Long providerId = body.get("provider_id") == null
                ? null
                : parseLong(body.get("provider_id"));

        final String requestedBy = authentication == null
                ? "system"
                : authentication.getName();

        return analysisEngine.analyze(
                logIds,
                text(body.get("agent_id")),
                text(body.get("project_id")),
                text(body.get("from")),
                text(body.get("to")),
                text(body.get("severity")),
                text(body.get("scope")) == null
                        ? (logIds.isEmpty() ? "filter" : "selected")
                        : text(body.get("scope")),
                providerId,
                text(body.get("prompt")),
                requestedBy);
    }

    /**
     * 분석 이력을 조회합니다.
     *
     * @param projectId 프로젝트 키 (선택)
     * @param agentId   장비 식별자 (선택)
     * @param limit     최대 건수 (기본 50)
     * @return {@code {"total": n, "analyses": [...]}}
     */
    @GetMapping("/analyses")
    public Map<String, Object> analyses(
            @RequestParam(value = "project_id", required = false) String projectId,
            @RequestParam(value = "agent_id", required = false) String agentId,
            @RequestParam(value = "limit", required = false) Integer limit) {

        final List<Map<String, Object>> rows = analysisEngine.history(projectId, agentId, limit);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", rows.size());
        body.put("analyses", rows);
        return body;
    }

    /**
     * 분석 한 건을 상세 조회합니다.
     *
     * @param analysisId 분석 식별자 (예: {@code AIA-1A2B3C4D})
     * @return 분석 상세 (없으면 404)
     */
    @GetMapping("/analyses/{analysisId}")
    public Map<String, Object> analysisDetail(@PathVariable String analysisId) {
        final Map<String, Object> detail = analysisEngine.detail(analysisId);
        if (detail == null) {
            throw new LogNotFoundRequest("분석을 찾을 수 없습니다: " + analysisId);
        }
        return detail;
    }

    /**
     * 진단용: 받은 본문 구조를 그대로 돌려줍니다.
     *
     * <p>로그 형식이 낯설 때 어떤 필드가 들어오는지 확인하는 용도입니다.
     * (OPNsense 의 {@code /probe} 와 같은 목적)
     *
     * @param body 원문 JSON
     * @return 필드 요약
     */
    @PostMapping("/probe")
    public Map<String, Object> probe(@RequestBody JsonNode body) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", body == null ? "null" : body.getClass().getSimpleName());
        result.put("raw", body == null ? null : body.toString());
        return result;
    }

    private static String text(Object value) {
        if (value == null) return null;
        final String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    private static Long parseLong(Object value) {
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 로그를 찾지 못했을 때 404 를 내기 위한 예외입니다.
     *
     * @param message 사유
     */
    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class LogNotFoundRequest extends RuntimeException {
        /**
         * @param message 사유
         */
        public LogNotFoundRequest(String message) {
            super(message);
        }
    }

    /**
     * 잘못된 요청에 400 을 내기 위한 예외입니다.
     *
     * @param message 사유
     */
    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.BAD_REQUEST)
    public static class BadLogRequest extends RuntimeException {
        /**
         * @param message 사유
         */
        public BadLogRequest(String message) {
            super(message);
        }
    }
}
