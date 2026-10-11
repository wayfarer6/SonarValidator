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

/**
 * 오프라인 스냅샷(Agent 가 파일로 남긴 JSON)을 읽어 <b>온라인 경로와 같은
 * 파이프라인</b>에 태우는 서비스입니다.
 *
 * <h2>왜 필요한가</h2>
 * <p>중앙 서버에 닿지 않는 장비(망분리, 관리망 미개통)에서는 텔레메트리가
 * WebSocket 으로 올라오지 않습니다. 그때 Agent 는 수집 결과를
 * {@code sonar_snapshot_<agent>_<시각>.json} 파일로 남기고, 운영자가 그것을
 * 프론트엔드에 끌어다 놓습니다.
 *
 * <h2>핵심 설계 — 같은 payload, 같은 파서</h2>
 * <p>이 서비스는 파일에서 {@code payload} 만 꺼내
 * {@link DeviceConfigService} 에 넘깁니다. 그 서비스는 WebSocket 텔레메트리와
 * <b>완전히 동일한</b> 벤더별 파서를 씁니다. 그래서 오프라인으로 올린 설정과
 * 온라인으로 올라온 설정이 화면에서 똑같이 보입니다 — 파서를 두 벌 만들지
 * 않기 위한 의도적 선택입니다.
 *
 * <h2>추측하지 않는 원칙</h2>
 * <p>스키마가 다르거나 필수 필드가 없으면 <b>거부하고 이유를 남깁니다.</b>
 * 조용히 절반만 반영하면 운영자는 "올렸는데 왜 안 보이지" 를 디버깅하게
 * 됩니다. 그래서 결과에 항상 {@code accepted}/{@code errors} 를 담습니다.
 */
@Service
public class OfflineSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(OfflineSnapshotService.class);

    /** Agent(offline_export.hpp)와 합의한 스키마 이름입니다. */
    public static final String SCHEMA_NAME = "sonar.offline.snapshot";

    /**
     * 이 서버가 이해하는 최대 스키마 버전입니다.
     *
     * <p>상위 버전이라도 아는 필드만 쓰고 나머지는 무시합니다(전방 호환).
     * 버전이 너무 높으면 경고만 남기고 계속 진행합니다 — 거부하면 새 Agent 가
     * 만든 파일을 구 서버가 못 읽게 되기 때문입니다.
     */
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /** 파일 하나의 최대 허용 크기(바이트). 무제한 업로드는 서버를 위험하게 만듭니다. */
    public static final long MAX_SNAPSHOT_BYTES = 8L * 1024 * 1024;

    private final DeviceConfigService deviceConfigService;
    private final AgentMessageRouterService router;
    private final JsonMapper mapper = JsonMapper.builder().build();

    /**
     * @param deviceConfigService 온라인 텔레메트리와 같은 벤더별 파서 선택기
     * @param router              수신 설정 보관소 (온라인/오프라인이 같은 곳을 씀)
     */
    public OfflineSnapshotService(DeviceConfigService deviceConfigService,
                                  AgentMessageRouterService router) {
        this.deviceConfigService = deviceConfigService;
        this.router = router;
    }

    /** 파일 하나를 처리한 결과입니다. */
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

    /** 여러 파일을 처리한 전체 결과입니다. */
    public record ImportResult(
            int received,
            int accepted,
            int rejected,
            List<SnapshotResult> snapshots,
            List<String> server_warnings) {
    }

    /**
     * 업로드된 파일 여러 개를 순서대로 처리합니다.
     *
     * <p>한 파일이 실패해도 나머지는 계속 처리합니다. 운영자가 여러 장비의
     * 스냅샷을 한 번에 올렸을 때, 하나가 깨졌다고 전부 잃으면 안 됩니다.
     *
     * @param files 파일 이름 → 내용 스트림
     * @return 전체/성공/실패 건수와 파일별 상세
     */
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

    /**
     * 파일 하나를 처리합니다.
     *
     * @param fileName 업로드된 파일 이름 (참고용)
     * @param stream   파일 내용
     * @return 처리 결과 (예외를 던지지 않음)
     */
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

            // 3) 식별자/제품명 결정.
            //    payload 의 product 를 우선하고, 없으면 문서 최상위 값을 씁니다.
            //    (Agent 는 둘 다 넣지만, 손으로 만든 파일은 한쪽만 있을 수 있다.)
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

            // 4) 온라인 경로와 **같은** 파서로 변환하고, 같은 보관소에 넣습니다.
            //    여기서 router 를 거치는 이유: 화면(/network/discovered, /agents)이
            //    보는 곳이 router 의 lastConfig 이기 때문입니다. 별도 저장소를 만들면
            //    업로드한 설정이 화면에 나타나지 않습니다.
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

    /**
     * 파일을 UTF-8 JSON 으로 읽습니다. 크기 제한을 넘으면 거부합니다.
     *
     * @param stream 입력 스트림
     * @param errors 실패 사유를 담을 목록 (호출자가 결과에 넣음)
     * @return 파싱된 문서, 실패하면 {@code null}
     */
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

    /**
     * 스냅샷 파일을 업로드 없이 문자열로 처리합니다. (테스트/진단용)
     *
     * @param fileName 참고용 파일 이름
     * @param content  JSON 문자열
     * @return 처리 결과
     */
    public SnapshotResult importContent(String fileName, String content) {
        return importOne(fileName, new java.io.ByteArrayInputStream(
                content == null ? new byte[0] : content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    /**
     * 서버가 지원하는 스키마 정보를 돌려줍니다. (프론트 안내문/진단용)
     *
     * @return 스키마 이름/버전/최대 크기와 지원 파서 목록
     */
    public Map<String, Object> describeSchema() {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("schema", SCHEMA_NAME);
        body.put("schema_version", SUPPORTED_SCHEMA_VERSION);
        body.put("max_bytes", MAX_SNAPSHOT_BYTES);
        body.put("supported_formats", deviceConfigService.supportedFormats());
        body.put("server_time", Instant.now().toString());
        return body;
    }

    /** 실패 결과를 만듭니다. */
    private SnapshotResult rejected(String fileName, List<String> errors, List<String> warnings) {
        return new SnapshotResult(fileName, false, null, null, 0, 0, 0, 0,
                List.copyOf(warnings), List.copyOf(errors));
    }

    /** 빈 값이 아닌 첫 인자를 돌려줍니다. */
    private String firstNonBlank(String... candidates) {
        for (final String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
