package org.sonar.sonarvalidator_backend.Controller;

import java.util.Map;

import org.sonar.sonarvalidator_backend.Service.AgentBundleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/agents/download", "/api/v1/agents/bundle"})
public class AgentBundleController {

    private static final Logger log = LoggerFactory.getLogger(AgentBundleController.class);

    private static final MediaType APPLICATION_GZIP =
            MediaType.parseMediaType("application/gzip");

    private final AgentBundleService bundleService;

    public AgentBundleController(AgentBundleService bundleService) {
        this.bundleService = bundleService;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> requestError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("status", error.getStatusCode().value(),
                "message", error.getReason() == null ? "Agent 다운로드 요청을 확인하세요." : error.getReason()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> missingAssets(IllegalStateException error) {
        log.error("Agent bundle assets are unavailable", error);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", 503, "message", "Prober 배포 파일이 준비되지 않았습니다. 서버의 배포 자산을 확인하세요."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalidSettings() {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "message", "프로젝트 관리 대역 설정을 확인하세요."));
    }

    private static void rejectServerOverride(String ip, String port) {
        if (ip != null || port != null) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "서버 주소와 포트는 프로젝트 편집에서만 변경할 수 있습니다.");
    }

    @GetMapping({"/info"})
    public Map<String, Object> info(@RequestParam("agent_id") String agentId,
                                    @RequestParam("project_id") String projectId,
                                    @RequestParam(value = "node_type", required = false) String nodeType,
                                    @RequestParam(value = "server_ip", required = false) String serverIp,
                                    @RequestParam(value = "server_port", required = false) String serverPort) {
        rejectServerOverride(serverIp, serverPort);
        return bundleService.describe(agentId, nodeType, projectId);
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<byte[]> download(@PathVariable String agentId,
                                           @RequestParam("project_id") String projectId,
                                    @RequestParam(value = "node_type", required = false) String nodeType,
                                           @RequestParam(value = "server_ip", required = false) String serverIp,
                                           @RequestParam(value = "server_port", required = false) String serverPort,
                                           @RequestParam(value = "data_directory", required = false) String dataDirectory) {
        rejectServerOverride(serverIp, serverPort);
        final byte[] body = bundleService.build(agentId, nodeType, projectId, dataDirectory);
        final String fileName = bundleService.fileNameFor(agentId);

        log.info("Prober 다운로드: 노드={} 종류={} 크기={} bytes",
                agentId, AgentBundleService.normalizeNodeType(nodeType), body.length);

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APPLICATION_GZIP);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
