package org.sonar.sonarvalidator_backend.Controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.OfflineSnapshotService;
import org.springframework.http.MediaType;
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
 * 오프라인 스냅샷 가져오기 API 입니다.
 *
 * <h2>두 가지 입력 경로를 모두 지원하는 이유</h2>
 * <ol>
 *   <li><b>파일 업로드</b> ({@code multipart/form-data}) — 프론트엔드의
 *       드래그앤드롭 카드가 쓰는 정상 경로입니다. 파일 이름이 남아
 *       "어느 장비의 스냅샷인지" 를 결과에서 바로 알 수 있습니다.</li>
 *   <li><b>본문 텍스트</b> ({@code application/json}) — 파일을 꺼내기 어려운
 *       환경(원격 콘솔 복사/붙여넣기, 스크립트)을 위한 보조 경로입니다.
 *       Agent 의 {@code --export-stdout} 과 짝을 이룹니다.</li>
 * </ol>
 *
 * <h2>왜 200 을 돌려주면서 실패를 담는가</h2>
 * <p>여러 파일을 한 번에 올릴 수 있으므로 "일부만 성공" 이 정상적인 결과입니다.
 * HTTP 상태로 표현할 수 없으므로 항상 200 과 함께 파일별
 * {@code accepted}/{@code errors} 를 돌려줍니다. 프론트엔드는 이 구조로
 * "3건 중 2건 반영, 1건 실패(사유: …)" 를 그대로 보여줄 수 있습니다.
 *
 * <p>단, 요청 자체가 잘못된 경우(파일이 하나도 없음)는 400 이 자연스럽지만,
 * 프론트가 사유를 표시할 수 있도록 같은 구조로 200 을 주고
 * {@code server_warnings} 에 남깁니다. 사용자 입력 실수를 서버 오류처럼
 * 보이게 하지 않기 위한 선택입니다.
 */
@RestController
@RequestMapping("/api/v1/offline")
public class OfflineImportController {

    private static final Logger log = LoggerFactory.getLogger(OfflineImportController.class);

    private final OfflineSnapshotService snapshotService;
    private final AgentMessageRouterService router;

    /**
     * @param snapshotService 스냅샷 파싱/반영 서비스
     * @param router          오프라인 출처 판정용 보관소
     */
    public OfflineImportController(OfflineSnapshotService snapshotService,
                                   AgentMessageRouterService router) {
        this.snapshotService = snapshotService;
        this.router = router;
    }

    /**
     * 서버가 기대하는 스냅샷 스키마를 안내합니다.
     *
     * <p>프론트엔드가 업로드 카드 옆에 "어떤 파일을 올려야 하는지" 를 표시할 때
     * 씁니다. 스키마를 프론트에 하드코딩하면 서버와 어긋나므로 서버가 알려 줍니다.
     *
     * @return 스키마 이름/버전/최대 크기/지원 파서 목록
     */
    @GetMapping("/schema")
    public Map<String, Object> schema() {
        return snapshotService.describeSchema();
    }

    /**
     * 오프라인 스냅샷 파일을 업로드받아 반영합니다.
     *
     * <p>form 필드 이름은 {@code files} 입니다. 여러 파일을 동시에 올릴 수 있고,
     * 하나가 실패해도 나머지는 반영됩니다.
     *
     * @param files 업로드된 파일들 (없으면 빈 목록)
     * @return 전체/성공/실패 건수와 파일별 상세
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importFiles(
            @RequestParam(name = "files", required = false) List<MultipartFile> files) {

        final Map<String, InputStream> streams = new LinkedHashMap<>();
        final Map<String, String> readErrors = new LinkedHashMap<>();

        for (final MultipartFile file : files == null ? List.<MultipartFile>of() : files) {
            final String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "unnamed.json"
                    : file.getOriginalFilename();
            try {
                // 파일이 크면 아래에서 서비스가 크기 제한으로 거부합니다.
                streams.put(name, new ByteArrayInputStream(file.getBytes()));
            } catch (IOException ex) {
                readErrors.put(name, "업로드 스트림을 읽지 못했습니다: " + ex.getMessage());
            }
        }

        final OfflineSnapshotService.ImportResult result = snapshotService.importSnapshots(streams);
        return toResponse(result, readErrors);
    }

    /**
     * 스냅샷 JSON 을 본문으로 직접 받아 반영합니다.
     *
     * <p>Agent 의 {@code --export-stdout} 출력을 그대로 붙여넣는 경로입니다.
     * 파일 이름이 없으므로 결과의 {@code file_name} 은 {@code "(request-body)"} 입니다.
     *
     * @param body 스냅샷 JSON
     * @return 처리 결과
     */
    @PostMapping(value = "/import", consumes = {MediaType.APPLICATION_JSON_VALUE, "text/plain"})
    public Map<String, Object> importBody(@RequestBody JsonNode body) {
        final String content = body == null ? "" : body.toString();
        final OfflineSnapshotService.SnapshotResult single =
                snapshotService.importContent("(request-body)", content);

        final OfflineSnapshotService.ImportResult result = new OfflineSnapshotService.ImportResult(
                single == null ? 0 : 1,
                single != null && single.accepted() ? 1 : 0,
                single != null && !single.accepted() ? 1 : 0,
                single == null ? List.of() : List.of(single),
                List.of());

        return toResponse(result, Map.of());
    }

    /**
     * 오프라인으로 반영된 장치 목록을 돌려줍니다.
     *
     * <p>{@code /api/v1/agents} 는 <b>현재 WebSocket 세션</b>만 보여주므로
     * 파일로 올린 장비는 나타나지 않습니다. 업로드가 실제로 반영됐는지
     * 확인할 지점이 필요해서 이 엔드포인트를 둡니다.
     *
     * @return 오프라인 출처 장치 목록
     */
    @GetMapping("/imported")
    public Map<String, Object> imported() {
        final List<Map<String, Object>> devices = new java.util.ArrayList<>();

        router.allConfigs().forEach((agentId, config) -> {
            if (!router.isOfflineOrigin(agentId)) {
                return;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("format", config.getFormat());
            entry.put("product", config.getProduct());
            entry.put("vendor", config.getVendor());
            entry.put("interfaces", config.getInterfaces().size());
            entry.put("routes", config.getRoutes().size());
            entry.put("vlans", config.getVlans().size());
            entry.put("firewall_rules", config.getFirewallRules().size());
            entry.put("bridges", List.copyOf(config.getBridges()));
            entry.put("warnings", List.copyOf(config.getWarnings()));
            devices.add(entry);
        });

        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("imported_devices", devices.size());
        response.put("devices", devices);
        response.put("server_time", java.time.Instant.now().toString());
        return response;
    }

    /**
     * 특정 Agent 의 현재 설정을 <b>오프라인 스냅샷 형식으로 내보냅니다</b>.
     *
     * <h2>왜 역방향도 필요한가</h2>
     * <p>오프라인 장비의 설정을 손으로 정리해 다른 랩에 옮기거나, 백업을 남기거나,
     * 다른 사람에게 전달할 때 같은 형식이 필요합니다. Agent 가 만드는 파일과
     * 이 응답이 같은 스키마여야 <b>다시 업로드해서 복원</b>할 수 있습니다.
     * (왕복이 되지 않는 내보내기는 백업이 아닙니다.)
     *
     * <h2>⚠️ 원본 payload 를 그대로 싣는 이유</h2>
     * <p>중립 설정({@link NeutralDeviceConfig})을 직렬화해 payload 로 넣으면
     * <b>다시 업로드해도 복원되지 않습니다.</b> 파서가 읽는 키는
     * {@code nic_status.interfaces} / {@code route_status.routes} 처럼
     * <b>원본 텔레메트리 구조</b>이기 때문입니다. 그래서 여기서는 보관 중인
     * 원본 payload({@code lastTelemetryOf})를 그대로 씁니다.
     *
     * <p>원본이 없으면(서버 재기동 등) 스냅샷을 만들 수 없으므로 404 대신
     * 명확한 안내와 함께 거부합니다 — 빈 껍데기를 내려주면 운영자가 그것을
     * 백업으로 믿게 됩니다.
     *
     * @param agentId Agent 식별자
     * @return 오프라인 스냅샷 문서
     */
    @GetMapping("/export/{agentId}")
    public Map<String, Object> exportAgent(@PathVariable String agentId) {
        final NeutralDeviceConfig config = router.lastConfigOf(agentId);
        final JsonNode payload = router.lastTelemetryOf(agentId);

        if (config == null || payload == null) {
            throw new AgentStatusController.AgentNotFoundException(agentId);
        }

        final Map<String, Object> document = new LinkedHashMap<>();
        document.put("schema", OfflineSnapshotService.SCHEMA_NAME);
        document.put("schema_version", OfflineSnapshotService.SUPPORTED_SCHEMA_VERSION);
        document.put("agent_id", agentId);
        document.put("agent_name", config.getHostname());
        document.put("device_type", config.getDeviceType());
        document.put("product", config.getProduct());
        document.put("vendor", config.getVendor());
        document.put("kernel", config.getKernel());
        document.put("collected_at", router.lastSeenOf(agentId) == null
                ? java.time.Instant.now().toString()
                : router.lastSeenOf(agentId).toString());
        document.put("exported_at", java.time.Instant.now().toString());
        document.put("reason", "server-export");
        // 중립 설정 요약은 사람이 파일만 봐도 내용을 알 수 있게 돕는 참고 정보입니다.
        // (파서는 읽지 않으므로 값이 틀려도 복원에는 영향이 없습니다.)
        document.put("summary", Map.of(
                "format", config.getFormat() == null ? "" : config.getFormat(),
                "interfaces", config.getInterfaces().size(),
                "routes", config.getRoutes().size(),
                "vlans", config.getVlans().size(),
                "firewall_rules", config.getFirewallRules().size()));
        document.put("offline_origin", router.isOfflineOrigin(agentId));
        // 복원의 유일한 근거입니다. 원본 텔레메트리 구조 그대로.
        document.put("payload", payload);
        return document;
    }

    /**
     * 서비스 결과를 프론트엔드가 그대로 쓸 수 있는 형태로 변환합니다.
     *
     * <p>필드 이름을 snake_case 로 맞추는 이유: 다른 API 와 동일한 관례를
     * 유지해야 프론트의 타입 정의가 한 가지 스타일로 유지됩니다.
     *
     * @param result     서비스 처리 결과
     * @param readErrors 업로드 스트림을 읽지 못한 파일 (파일 이름 → 사유)
     * @return 응답 맵
     */
    private Map<String, Object> toResponse(OfflineSnapshotService.ImportResult result,
                                           Map<String, String> readErrors) {
        final List<Map<String, Object>> snapshots = new java.util.ArrayList<>();
        for (final OfflineSnapshotService.SnapshotResult snapshot : result.snapshots()) {
            final Map<String, Object> item = new LinkedHashMap<>();
            item.put("file_name", snapshot.file_name());
            item.put("accepted", snapshot.accepted());
            item.put("agent_id", snapshot.agent_id());
            item.put("format", snapshot.format());
            item.put("interfaces", snapshot.interface_count());
            item.put("routes", snapshot.route_count());
            item.put("vlans", snapshot.vlan_count());
            item.put("firewall_rules", snapshot.firewall_rule_count());
            item.put("warnings", snapshot.warnings());
            item.put("errors", snapshot.errors());
            snapshots.add(item);
        }

        // 읽지 못한 파일도 결과에 포함해야 운영자가 "왜 3건 중 2건만 처리됐지" 를
        // 스스로 알 수 있습니다. 조용히 빠뜨리지 않습니다.
        readErrors.forEach((name, reason) -> {
            final Map<String, Object> item = new LinkedHashMap<>();
            item.put("file_name", name);
            item.put("accepted", false);
            item.put("agent_id", null);
            item.put("format", null);
            item.put("interfaces", 0);
            item.put("routes", 0);
            item.put("vlans", 0);
            item.put("firewall_rules", 0);
            item.put("warnings", List.of());
            item.put("errors", List.of(reason));
            snapshots.add(item);
        });

        final int rejected = result.rejected() + readErrors.size();

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("received", result.received() + readErrors.size());
        body.put("accepted", result.accepted());
        body.put("rejected", rejected);
        body.put("snapshots", snapshots);
        body.put("server_warnings", result.server_warnings());
        body.put("accepted_device_ids", result.snapshots().stream()
                .filter(OfflineSnapshotService.SnapshotResult::accepted)
                .map(OfflineSnapshotService.SnapshotResult::agent_id)
                .toList());

        log.info("offline import response: received={} accepted={} rejected={}",
                body.get("received"), body.get("accepted"), rejected);
        return body;
    }
}
