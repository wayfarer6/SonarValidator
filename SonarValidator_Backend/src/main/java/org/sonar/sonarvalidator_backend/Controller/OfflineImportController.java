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

@RestController
@RequestMapping("/api/v1/offline")
public class OfflineImportController {

    private static final Logger log = LoggerFactory.getLogger(OfflineImportController.class);

    private final OfflineSnapshotService snapshotService;
    private final AgentMessageRouterService router;

    public OfflineImportController(OfflineSnapshotService snapshotService,
                                   AgentMessageRouterService router) {
        this.snapshotService = snapshotService;
        this.router = router;
    }

    @GetMapping("/schema")
    public Map<String, Object> schema() {
        return snapshotService.describeSchema();
    }

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

        log.debug("offline import response: received={} accepted={} rejected={}",
                body.get("received"), body.get("accepted"), rejected);
        return body;
    }
}
