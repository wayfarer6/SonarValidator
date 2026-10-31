package org.sonar.sonarvalidator_backend.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OfflineSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(OfflineSnapshotService.class);

    public static final String SCHEMA_NAME = "sonar.offline.snapshot";

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public static final long MAX_SNAPSHOT_BYTES = 8L * 1024 * 1024;

    private final DeviceConfigService deviceConfigService;
    private final AgentMessageRouterService router;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public OfflineSnapshotService(DeviceConfigService deviceConfigService,
                                  AgentMessageRouterService router) {
        this.deviceConfigService = deviceConfigService;
        this.router = router;
    }

    public record SnapshotResult(
            String file_name,
            boolean accepted,
            String agent_id,
            String format,
            int interface_count,
            int route_count,
            int vlan_count,
            int firewall_rule_count,
            List<String> warnings,
            List<String> errors) {
    }

    public record ImportResult(
            int received,
            int accepted,
            int rejected,
            List<SnapshotResult> snapshots,
            List<String> server_warnings) {
    }

    public ImportResult importSnapshots(Map<String, InputStream> files) {
        final List<SnapshotResult> results = new ArrayList<>();
        final List<String> serverWarnings = new ArrayList<>();

        int accepted = 0;
        int rejected = 0;

        for (final Map.Entry<String, InputStream> entry : files.entrySet()) {
            final SnapshotResult result = importOne(entry.getKey(), entry.getValue());
            results.add(result);
            if (result.accepted()) {
                accepted++;
            } else {
                rejected++;
            }
        }

        if (files.isEmpty()) {
            serverWarnings.add("업로드된 파일이 없습니다. (form 필드 이름은 'files' 여야 합니다)");
        }

        log.info("offline snapshot import: received={} accepted={} rejected={}",
                files.size(), accepted, rejected);

        return new ImportResult(files.size(), accepted, rejected, results, serverWarnings);
    }

    public SnapshotResult importOne(String fileName, InputStream stream) {
        final List<String> warnings = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        try {
            final JsonNode document = readDocument(stream, errors);
            if (document == null) {
                return rejected(fileName, errors, warnings);
            }

            // 1) 스키마 확인 — 다른 JSON 을 올렸을 때 명확히 알려 줍니다.
            final String schema = document.path("schema").asString("");
            if (!SCHEMA_NAME.equals(schema)) {
                errors.add("schema 불일치: 기대 '" + SCHEMA_NAME + "', 실제 '" + schema + "'");
                return rejected(fileName, errors, warnings);
            }

            final int version = document.path("schema_version").asInt(0);
            if (version > SUPPORTED_SCHEMA_VERSION) {
                // 거부하지 않고 계속합니다. 아는 필드만 쓰면 되기 때문입니다.
                warnings.add("schema_version " + version + " 은 이 서버(" + SUPPORTED_SCHEMA_VERSION
                        + ")보다 높습니다. 아는 필드만 반영했습니다.");
            }

            // 2) payload 확인 — 실제 설정이 들어 있는 곳입니다.
            final JsonNode payload = document.path("payload");
            if (payload.isMissingNode() || payload.isNull() || !payload.isObject()) {
                errors.add("payload 가 없거나 객체가 아닙니다.");
                return rejected(fileName, errors, warnings);
            }
            if (payload.isEmpty()) {
                errors.add("payload 가 비어 있습니다. 수집에 성공한 항목이 없는 스냅샷입니다.");
                return rejected(fileName, errors, warnings);
            }




            final String agentId = firstNonBlank(
                    document.path("agent_id").asString(""),
                    document.path("agent_name").asString(""),
                    fileName);
            final String product = firstNonBlank(
                    payload.path("product").asString(""),
                    payload.path("vendor").asString(""),
                    document.path("product").asString(""),
                    document.path("vendor").asString(""),
                    document.path("device_type").asString(""));

            if (product.isBlank()) {
                // 제품명이 없으면 어떤 파서를 쓸지 알 수 없어 설정이 빈 채로 남습니다.
                errors.add("제품명(product/vendor)이 없어 설정 파서를 선택할 수 없습니다.");
                return rejected(fileName, errors, warnings);
            }

            final NeutralDeviceConfig config =
                    router.acceptOfflineTelemetry(agentId, product, payload);
            warnings.addAll(config.getWarnings());

            // 5) 변환 결과가 완전히 비었으면 실패로 알립니다.
            //    (파서가 선택됐지만 payload 키가 전부 낯설었던 경우)
            final boolean empty = config.getInterfaces().isEmpty()
                    && config.getRoutes().isEmpty()
                    && config.getVlans().isEmpty()
                    && config.getFirewallRules().isEmpty()
                    && config.getBridges().isEmpty();
            if (empty) {
                errors.add("파서 '" + config.getFormat() + "' 가 아무 항목도 해석하지 못했습니다. "
                        + "payload 키가 Agent 버전과 맞는지 확인하세요. keys=" + payload.size());
                return rejected(fileName, errors, warnings);
            }

            log.info("offline snapshot accepted: file={} agent={} product={} format={} "
                            + "ifaces={} routes={} vlans={} rules={}",
                    fileName, agentId, product, config.getFormat(),
                    config.getInterfaces().size(), config.getRoutes().size(),
                    config.getVlans().size(), config.getFirewallRules().size());

            return new SnapshotResult(
                    fileName,
                    true,
                    agentId,
                    config.getFormat(),
                    config.getInterfaces().size(),
                    config.getRoutes().size(),
                    config.getVlans().size(),
                    config.getFirewallRules().size(),
                    List.copyOf(warnings),
                    List.of());

        } catch (RuntimeException ex) {
            // 파싱/변환 중 어떤 예외도 밖으로 나가면 안 됩니다.
            // 한 파일 때문에 업로드 전체가 500 이 되면 나머지 장비 설정을 잃습니다.
            log.warn("offline snapshot failed: file={} error={}", fileName, ex.getMessage());
            errors.add("처리 중 오류: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " — " + ex.getMessage()));
            return rejected(fileName, errors, warnings);
        }
    }

    private JsonNode readDocument(InputStream stream, List<String> errors) {
        if (stream == null) {
            errors.add("파일 내용이 비어 있습니다.");
            return null;
        }

        final byte[] bytes;
        try {
            bytes = stream.readAllBytes();
        } catch (IOException ex) {
            errors.add("파일을 읽지 못했습니다: " + ex.getMessage());
            return null;
        }

        if (bytes.length == 0) {
            errors.add("빈 파일입니다.");
            return null;
        }
        if (bytes.length > MAX_SNAPSHOT_BYTES) {
            errors.add("파일이 너무 큽니다: " + bytes.length + " bytes (최대 "
                    + MAX_SNAPSHOT_BYTES + ")");
            return null;
        }

        try {
            return mapper.readTree(bytes);
        } catch (RuntimeException ex) {
            errors.add("JSON 파싱 실패: " + ex.getMessage());
            return null;
        }
    }

    public SnapshotResult importContent(String fileName, String content) {
        return importOne(fileName, new java.io.ByteArrayInputStream(
                content == null ? new byte[0] : content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public Map<String, Object> describeSchema() {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("schema", SCHEMA_NAME);
        body.put("schema_version", SUPPORTED_SCHEMA_VERSION);
        body.put("max_bytes", MAX_SNAPSHOT_BYTES);
        body.put("supported_formats", deviceConfigService.supportedFormats());
        body.put("server_time", Instant.now().toString());
        return body;
    }

    private SnapshotResult rejected(String fileName, List<String> errors, List<String> warnings) {
        return new SnapshotResult(fileName, false, null, null, 0, 0, 0, 0,
                List.copyOf(warnings), List.copyOf(errors));
    }

    private String firstNonBlank(String... candidates) {
        for (final String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
