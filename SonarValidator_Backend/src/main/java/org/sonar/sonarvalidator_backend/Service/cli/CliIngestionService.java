package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class CliIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CliIngestionService.class);

    private static final List<String> RAW_KEYS = List.of(
            "raw", "raw_output", "output", "text", "cli_output");

    private static final List<String> TARGETS = List.of(
            "nic_status", "route_status", "arp_table", "vlan_status",
            "trunk_status", "firewall_rules", "ovs_topology", "ovs_ports");

    private final CliOutputParser parser;

    public CliIngestionService(CliOutputParser parser) {
        this.parser = parser;
    }

    public ObjectNode extractParsed(JsonNode payload, CliVendor vendor) {
        final ObjectNode result = CliJson.object();
        if (payload == null || payload.isNull() || !payload.isObject()) {
            return result;
        }

        final CliVendor resolved = vendor == null ? CliVendor.UNKNOWN : vendor;

        for (final String target : TARGETS) {
            final JsonNode node = payload.path(target);
            if (node.isMissingNode() || node.isNull()) {
                continue;
            }

            // (1) 이미 구조화된 형태면 그대로 둡니다.
            if (alreadyStructured(node)) {
                continue;
            }

            // (2) 원문 문자열(또는 원문을 담은 컨테이너)만 있으면 다시 파싱합니다.
            final String raw = findRawText(node);
            if (raw == null || raw.isBlank()) {
                continue;
            }

            final String query = inferTarget(raw, target);

            final org.sonar.sonarvalidator_backend.Service.cli.query.QueryResult outcome =
                    parser.parseQuery(resolved.name(), query, raw);
            final ObjectNode parsed = outcome.body();

            if (!outcome.parsed()) {
                // 실패해도 원문은 버리지 않습니다. 다만 계약 키로는 넣지 않습니다
                // — 넣으면 소비자가 "빈 인터페이스 목록" 으로 해석합니다.
                log.warn("cli fallback parse failed: target={} vendor={} key={} error={}",
                        target, resolved.displayName(), outcome.contractKey(),
                        parsed.path("parse_error").asString("?"));
                continue;
            }
            if (outcome.itemCount() == 0) {
                log.warn("cli fallback produced no items; ignoring: target={} vendor={} query={}",
                        target, resolved.displayName(), query);
                continue;
            }
            result.set(target, parsed);
            log.info("cli fallback parse ok: target={} vendor={} query={} key={} count={}",
                    target, resolved.displayName(), outcome.query(), outcome.contractKey(),
                    outcome.itemCount());
        }

        return result;
    }

    public CliVendor vendorOf(String productName) {
        return CliVendor.fromProductName(productName);
    }

    /* ==================== 내부 도우미 ==================== */

    private static boolean alreadyStructured(JsonNode node) {
        if (!node.isObject()) {
            return false;
        }
        for (final String arrayKey : new String[]{"interfaces", "routes", "entries", "vlans", "ports", "bridges", "tables"}) {
            final JsonNode array = node.path(arrayKey);
            if (array.isArray()) {
                return true;
            }
        }
        // 문자열도 배열도 아니면 원문일 수 있으므로 false 를 유지합니다.
        return false;
    }

    private static String findRawText(JsonNode node) {
        if (node.isString()) {
            return node.asString("");
        }
        if (node.isArray()) {
            // 여러 줄이 나뉘어 오는 형태를 하나로 붙입니다.
            final StringBuilder joined = new StringBuilder();
            for (final JsonNode item : node) {
                if (!item.isString()) {
                    return null;
                }
                if (!joined.isEmpty()) {
                    joined.append('\n');
                }
                joined.append(item.asString(""));
            }
            return joined.isEmpty() ? null : joined.toString();
        }
        if (node.isObject()) {
            for (final String key : RAW_KEYS) {
                final JsonNode value = node.path(key);
                if (value.isString() && !value.asString("").isBlank()) {
                    return value.asString("");
                }
                if (value.isArray()) {
                    final String joined = findRawText(value);
                    if (joined != null) {
                        return joined;
                    }
                }
            }
        }
        return null;
    }

    private static String inferTarget(String raw, String target) {
        final String lower = (target == null ? "" : target).toLowerCase(Locale.ROOT);

        // 라우팅 계열은 두 문법(FRR 코드 / 커널 iproute2)이 섞일 수 있어 내용을 봅니다.
        if (lower.contains("route")) {
            return "route";
        }
        if (lower.contains("arp")) {
            return "arp";
        }
        if (lower.contains("nic")) {
            return "nic";
        }
        if (lower.contains("vlan")) {
            return "vlan";
        }
        if (lower.contains("trunk") || lower.contains("port")) {
            return "port";
        }
        if (lower.contains("firewall") || lower.contains("rules")) {
            return "ruleset";
        }
        if (lower.contains("ovs")) {
            return "topology";
        }

        // 대상 키를 모를 때만 내용으로 추정합니다.
        if (raw.contains("Codes:") || raw.contains("Gateway of last resort")) {
            return "route";
        }
        if (raw.contains("OK?")) {
            return "interface";
        }
        return "brief";
    }

    private static int countOf(ObjectNode parsed) {
        for (final String key : new String[]{
                "interface_count", "brief_count", "route_count", "entry_count",
                "vlan_count", "port_count", "bridge_count", "table_count"}) {
            final JsonNode value = parsed.path(key);
            if (value.isNumber()) {
                return value.asInt(0);
            }
        }
        return parsed.size();
    }
}
