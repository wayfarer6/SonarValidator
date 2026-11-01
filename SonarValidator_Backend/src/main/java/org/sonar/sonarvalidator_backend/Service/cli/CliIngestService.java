package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Service.DeviceConfigService;
import org.springframework.stereotype.Service;

import tools.jackson.databind.node.ObjectNode;

@Service
public class CliIngestService {

    private static final Logger log = LoggerFactory.getLogger(CliIngestService.class);

    private final CliOutputParser parser;
    private final CliIngestionService ingestion;
    private final DeviceConfigService deviceConfigService;

    private static final org.sonar.sonarvalidator_backend.Service.cli.query.CliQueryStrategies
            QUERY_STRATEGIES =
            new org.sonar.sonarvalidator_backend.Service.cli.query.CliQueryStrategies();

    public CliIngestService(CliOutputParser parser,
                            CliIngestionService ingestion,
                            DeviceConfigService deviceConfigService) {
        this.parser = parser;
        this.ingestion = ingestion;
        this.deviceConfigService = deviceConfigService;
    }

    public Map<String, Object> ingest(CliIngestRequest request, boolean includeConfig) {
        final CliIngestRequest req = request == null ? CliIngestRequest.empty() : request;
        final Map<String, Object> result = new LinkedHashMap<>();

        if (!req.hasRaw()) {



            result.put("accepted", false);
            result.put("errors", List.of("raw 출력이 비어 있습니다"));
            return result;
        }

        final CliVendor vendor = ingestion.vendorOf(req.product());
        final String query = normalizeTarget(req.target(), vendor);
        final String contractKey = contractKeyOf(query, vendor);

        final ObjectNode parsed = parser.parseQueryOutput(vendor, query, req.raw());

        result.put("accepted", parsed.path("parsed").asBoolean(false));
        result.put("vendor", vendor.name());
        result.put("vendor_name", vendor.displayName());
        result.put("query", query);
        result.put("target_key", contractKey);
        result.put("item_count", countOf(parsed));
        result.put("parsed", parsed);

        final List<String> errors = new java.util.ArrayList<>();
        if (!parsed.path("parsed").asBoolean(false)) {
            errors.add("파싱 실패: " + parsed.path("parse_error").asString("unknown"));
        } else if (countOf(parsed) == 0) {
            // 문법의 catch-all 규칙은 어떤 텍스트든 통과시킵니다. 항목이 0건이면
            // "성공" 을 믿으면 안 됩니다.
            errors.add("파싱은 됐지만 항목이 0건입니다. 대상(" + query + ")이 맞는지 확인하세요");
        }
        result.put("errors", errors);
        result.put("accepted", errors.isEmpty());

        if (includeConfig) {
            // 계약 키를 씌운 payload 로 중립 설정까지 만듭니다. 소비자가 실제로 보게
            // 될 결과를 그대로 돌려주어 "파싱은 맞는데 화면은 빈" 상황을 빨리 찾습니다.
            final ObjectNode payload = CliJson.object();
            if (errors.isEmpty()) {
                payload.set(contractKey, parsed);
            }
            final NeutralDeviceConfig config =
                    deviceConfigService.parse("(cli-ingest)", req.product(), payload);
            result.put("config", config);
        }

        log.info("cli ingest: product={} vendor={} target={} key={} count={} accepted={}",
                req.product(), vendor.name(), req.target(), contractKey,
                countOf(parsed), errors.isEmpty());
        return result;
    }

    private static String normalizeTarget(String target, CliVendor vendor) {
        if (target == null || target.isBlank()) {
            log.debug("cli ingest target 미지정 → 벤더 기본 조회 사용: {}", vendor.displayName());
            return null;
        }
        return target.trim();
    }

    private static String contractKeyOf(String query, CliVendor vendor) {
        return QUERY_STRATEGIES.select(query, vendor).contractKey();
    }

    private static int countOf(ObjectNode parsed) {
        for (final String key : new String[]{
                "interface_count", "route_count", "entry_count", "vlan_count",
                "port_count", "bridge_count", "table_count"}) {
            if (parsed.path(key).isNumber()) {
                return parsed.path(key).asInt(0);
            }
        }
        return 0;
    }
}
