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

/**
 * Agent 설치 번들(설정이 미리 채워진 ZIP)을 내려주는 API 입니다.
 *
 * <h2>⚠️ 왜 다운로드 전에 서버가 설정을 채우는가</h2>
 * <p>프로버는 {@code default.conf} 의 네 값으로 동작하고, 그중
 * {@code AGENT_NAME} 이 <b>"배포 예정" 등록 이름과 같아야</b> 합니다.
 * 다르면 같은 장치가 두 줄로 나타나고, 등록한 장치는 영원히 <b>무응답</b>으로
 * 남습니다. 사람이 손으로 채우면 반드시 어긋나므로 서버가 채웁니다.
 *
 * <h2>프론트엔드 사용 흐름</h2>
 * <ol>
 *   <li>장치 유형과 이름을 고른다</li>
 *   <li>{@code GET /api/v1/agents/bundle/info?agent_id=&node_type=} 로 미리보기</li>
 *   <li>{@code GET /api/v1/agents/bundle/{agentId}?node_type=} 로 ZIP 다운로드</li>
 * </ol>
 *
 * <p>다운로드는 {@code <a href>} 로도 가능합니다(인증 쿠키가 실림).
 * 그래서 별도 토큰이 필요 없습니다.
 */
@RestController
@RequestMapping("/api/v1/agents/bundle")
public class AgentBundleController {

    private static final Logger log = LoggerFactory.getLogger(AgentBundleController.class);

    private final AgentBundleService bundleService;

    /**
     * @param bundleService 번들 생성 서비스
     */
    public AgentBundleController(AgentBundleService bundleService) {
        this.bundleService = bundleService;
    }

    /**
     * 번들 정보를 미리 봅니다. (다운로드 전 확인용)
     *
     * <p>서버 주소와 장치 유형이 어떻게 들어가는지, 스테이징된 자산이 무엇인지
     * 확인할 수 있습니다. 파일 크기를 받아보기 전에 알 수 있어 편리합니다.
     *
     * @param agentId  Agent 이름
     * @param nodeType 장치 유형 (Router/Switch/VM/Firewall)
     * @return 번들 요약
     */
    @GetMapping("/info")
    public Map<String, Object> info(@RequestParam("agent_id") String agentId,
                                    @RequestParam(value = "node_type", required = false) String nodeType) {
        return bundleService.describe(agentId, nodeType);
    }

    /**
     * Agent 설치 번들을 내려받습니다.
     *
     * <p>{@code default.conf} 에 서버 주소/포트/장치 유형/Agent 이름이
     * 채워져 있고, 이 장치에 맞춘 {@code README.txt} 가 함께 들어 있습니다.
     *
     * @param agentId        Agent 이름
     * @param nodeType       장치 유형 (기본 VM)
     * @param serverIp       장치별 서버 주소 (관리망만 있는 장치는 관리 주소를 넘김)
     * @param dataDirectory  DATA_DIRECTORY (예: Arista 의 {@code /mnt/flash})
     * @return ZIP 응답
     */
    @GetMapping("/{agentId}")
    public ResponseEntity<byte[]> download(@PathVariable String agentId,
                                           @RequestParam(value = "node_type", required = false) String nodeType,
                                           @RequestParam(value = "server_ip", required = false) String serverIp,
                                           @RequestParam(value = "data_directory", required = false) String dataDirectory) {
        final byte[] body = bundleService.build(agentId, nodeType, serverIp, dataDirectory);
        final String fileName = bundleService.fileNameFor(agentId);

        log.info("agent bundle download: agent={} type={} bytes={}",
                agentId, AgentBundleService.normalizeNodeType(nodeType), body.length);

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        // 파일 이름에 하이픈/점이 들어갈 수 있어 ContentDisposition 으로 안전하게 인코딩합니다.
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
}