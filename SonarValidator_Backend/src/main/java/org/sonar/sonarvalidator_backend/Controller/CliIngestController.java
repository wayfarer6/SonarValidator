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

/**
 * 원문 CLI 문자열을 직접 파싱하는 REST 엔드포인트입니다.
 *
 * <h2>무엇을 위한 것인가</h2>
 * <p>텔레메트리 정상 경로는 Agent 가 <b>이미 파싱한 JSON</b> 을 보내는 것입니다.
 * 하지만 다음 상황에서는 서버가 원문을 직접 받아야 합니다.
 *
 * <ol>
 *   <li><b>구버전 Prober</b> — 개별 장비의 바이너리를 한 번에 올리기 어려워,
 *       원문만 보내는 장비가 섞입니다.</li>
 *   <li><b>운영자 붙여넣기</b> — 진단 중 {@code show ip route} 결과를 그대로
 *       확인하고 싶을 때.</li>
 *   <li><b>오프라인 스냅샷 재파싱</b> — 저장된 원문만 다시 돌려 결과를 확인.</li>
 *   <li><b>계약 키 불일치 조사</b> — 벤더가 제 키로 보낸 JSON 을 원문 기준으로
 *       다시 확인.</li>
 * </ol>
 *
 * <p>이 경로가 없으면 위 경우에 서버가 할 수 있는 일이 없습니다 — 원문은
 * 파싱되지 않은 채로 버려지고 화면에는 아무것도 나타나지 않습니다.
 *
 * <h2>왜 200 을 돌려주면서 실패를 담는가</h2>
 * <p>{@link OfflineImportController} 와 같은 이유입니다. 원문이 비었거나 문법에
 * 맞지 않는 것은 <b>사용자 입력 문제</b>이고, 프론트엔드가 사유를 그대로
 * 표시할 수 있어야 합니다. 요청 자체는 정상 처리됐으므로 200 과 함께
 * {@code accepted} / {@code errors} 를 돌려줍니다.
 *
 * <h2>계약</h2>
 * <pre>
 *   POST /api/v1/cli/ingest
 *   { "product": "Ubuntu", "target": "route", "raw": "&lt;CLI 원문&gt;" }
 *
 *   → { "accepted": true, "vendor": "LINUX", "target_key": "route_status",
 *       "item_count": 3, "parsed": {...}, "errors": [] }
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/cli")
public class CliIngestController {

    private final CliIngestService ingestService;

    /**
     * @param ingestService 원문 파싱 + 중립 설정 변환 서비스
     */
    public CliIngestController(CliIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * 원문 CLI 한 건을 내부 계약 JSON 으로 바꿔 돌려줍니다.
     *
     * @param body   {@code {product, target, raw}} (또는 봉투 형태)
     * @param config {@code true} 면 중립 설정까지 함께 변환 (기본 {@code false})
     * @return 파싱 결과 (항상 200)
     */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody(required = false) JsonNode body,
                                      @RequestParam(name = "config", defaultValue = "false") boolean config) {
        return ingestService.ingest(toRequest(body), config);
    }

    /**
     * 서버가 인식하는 대상 이름과 벤더를 안내합니다.
     *
     * <p>프론트엔드가 대상 선택 상자(dropdown)를 그릴 때 씁니다. 목록을 프론트에
     * 하드코딩하면 서버와 어긋나므로 서버가 알려 줍니다.
     * ({@link OfflineImportController#schema()} 와 같은 이유)
     *
     * @return 지원 대상/벤더 목록과 사용 예
     */
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

    /**
     * REST 본문을 서비스 요청으로 바꿉니다.
     *
     * <p>{@code payload} 키가 있으면 그 안을, 없으면 본문 자체를 봅니다.
     * 그래야 완전한 봉투({@code {payload:{...}}})와 짧은 호출이 모두 동작합니다.
     * ({@link AgentStatusController#toCommandEnvelope} 와 같은 관용구)
     *
     * @param body REST 본문 (null 허용)
     * @return 서비스 요청 (필드가 비면 null 인 요청)
     */
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

    /**
     * 후보 키 중 처음 발견되는 문자열을 돌려줍니다.
     *
     * <p>Prober 버전과 스크립트에 따라 키 이름이 다르므로 후보를 여러 개 봅니다.
     * ({@link org.sonar.sonarvalidator_backend.Service.cli.CliIngestionService} 의
     * {@code RAW_KEYS} 와 같은 목록)
     *
     * @param  source 대상 노드
     * @param  keys   후보 키
     * @return 값 (없으면 {@code null})
     */
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