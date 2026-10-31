package org.sonar.sonarvalidator_backend.Controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Service.cli.CliIngestRequest;
import org.sonar.sonarvalidator_backend.Service.cli.CliIngestService;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/cli")
public class CliIngestController {

    private final CliIngestService ingestService;

    public CliIngestController(CliIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody(required = false) JsonNode body,
                                      @RequestParam(name = "config", defaultValue = "false") boolean config) {
        return ingestService.ingest(toRequest(body), config);
    }

    @org.springframework.web.bind.annotation.GetMapping("/targets")
    public Map<String, Object> targets() {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("targets", java.util.List.of(
                Map.of("name", "route", "key", "route_status", "description", "라우팅 테이블 (show ip route / ip route show)"),
                Map.of("name", "nic", "key", "nic_status", "description", "인터페이스와 주소 (ip a / ip addr)"),
                Map.of("name", "brief", "key", "nic_status", "description", "인터페이스 요약 (ip -br addr / show ip int brief)"),
                Map.of("name", "arp", "key", "arp_table", "description", "이웃 테이블 (ip neigh / show arp)"),
                Map.of("name", "vlan", "key", "vlan_status", "description", "VLAN (show vlan brief)"),
                Map.of("name", "port", "key", "trunk_status", "description", "스위치 포트 (show interfaces switchport)"),
                Map.of("name", "ruleset", "key", "firewall_rules", "description", "nftables 규칙 (nft list ruleset)"),
                Map.of("name", "topology", "key", "ovs_topology", "description", "OVS 브리지 (ovs-vsctl show)")));

        final java.util.List<String> vendors = new java.util.ArrayList<>();
        for (final CliVendor vendor : CliVendor.values()) {
            vendors.add(vendor.name());
        }
        body.put("vendors", vendors);
        body.put("example", Map.of(
                "product", "Ubuntu",
                "target", "route",
                "raw", "default via 10.99.10.1 dev eth0 proto static"));
        return body;
    }

    private static CliIngestRequest toRequest(JsonNode body) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            return CliIngestRequest.empty();
        }
        final JsonNode inner = body.path("payload");
        final JsonNode source = inner.isObject() ? inner : body;
        return new CliIngestRequest(
                text(source, "product", "vendor"),
                text(source, "target", "query", "command"),
                text(source, "raw", "raw_output", "output", "cli_output"));
    }

    private static String text(JsonNode source, String... keys) {
        for (final String key : keys) {
            final JsonNode value = source.path(key);
            if (value.isString() && !value.asString("").isBlank()) {
                return value.asString("");
            }
        }
        return null;
    }
}
