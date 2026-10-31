package org.sonar.sonarvalidator_backend.Controller;

import java.util.Map;

import org.sonar.sonarvalidator_backend.Service.AgentBundleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping({"/info"})
    public Map<String, Object> info(@RequestParam("agent_id") String agentId,
                                    @RequestParam(value = "node_type", required = false) String nodeType,
                                    @RequestParam(value = "server_ip", required = false) String serverIp,
                                    @RequestParam(value = "server_port", required = false) String serverPort) {
        return bundleService.describe(agentId, nodeType, serverIp, serverPort);
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<byte[]> download(@PathVariable String agentId,
                                           @RequestParam(value = "node_type", required = false) String nodeType,
                                           @RequestParam(value = "server_ip", required = false) String serverIp,
                                           @RequestParam(value = "server_port", required = false) String serverPort,
                                           @RequestParam(value = "data_directory", required = false) String dataDirectory) {
        final byte[] body = bundleService.build(
                agentId, nodeType, serverIp, serverPort, dataDirectory);
        final String fileName = bundleService.fileNameFor(agentId);

        log.info("agent download: agent={} type={} bytes={}",
                agentId, AgentBundleService.normalizeNodeType(nodeType), body.length);

        final HttpHeaders headers = new HttpHeaders();
        // ⚠️ octet-stream 이 아니라 gzip 입니다. 화면·프록시가 형식을 알 수 있어야

        headers.setContentType(APPLICATION_GZIP);
        // 파일 이름에 하이픈/점이 들어갈 수 있어 ContentDisposition 으로 안전하게 인코딩합니다.
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
}
