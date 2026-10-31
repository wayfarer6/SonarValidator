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

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private static final Logger log = LoggerFactory.getLogger(LogController.class);

    private final LogService logService;
    private final LogAnalysisEngine analysisEngine;

    private final LogLineReader lineReader;

    public LogController(LogService logService, LogAnalysisEngine analysisEngine,
                         LogLineReader lineReader) {
        this.logService = logService;
        this.analysisEngine = analysisEngine;
        this.lineReader = lineReader;
    }

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

    @GetMapping("/filters")
    public Map<String, Object> filters() {
        return logService.filterOptions();
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", logService.severitySummary());
        return body;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, Object> body) {
        final String agentId = text(body.get("agent_id"));
        if (agentId == null) {
            throw new BadLogRequest("agent_id 는 필수입니다.");
        }




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

    @GetMapping("/analyses/{analysisId}")
    public Map<String, Object> analysisDetail(@PathVariable String analysisId) {
        final Map<String, Object> detail = analysisEngine.detail(analysisId);
        if (detail == null) {
            throw new LogNotFoundRequest("분석을 찾을 수 없습니다: " + analysisId);
        }
        return detail;
    }

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

    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class LogNotFoundRequest extends RuntimeException {
        public LogNotFoundRequest(String message) {
            super(message);
        }
    }

    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.BAD_REQUEST)
    public static class BadLogRequest extends RuntimeException {
        public BadLogRequest(String message) {
            super(message);
        }
    }
}
