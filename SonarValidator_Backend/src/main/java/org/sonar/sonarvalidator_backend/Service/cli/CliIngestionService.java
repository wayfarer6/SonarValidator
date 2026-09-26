package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 수집된 명령 출력을 <b>백엔드 내부 계약</b>(payload 키)으로 정규화합니다.
 *
 * <h2>왜 이 계층이 필요한가</h2>
 * <p>정상 경로에서 Prober 는 이미 C++ 파서({@code cli_parser::ParseQueryOutput})를
 * 거쳐 {@code nic_status} / {@code route_status} 같은 구조화 JSON 을 만들어 보냅니다.
 * 그 JSON 은 {@link org.sonar.sonarvalidator_backend.Model.Config.AbstractDeviceConfigParser}
 * 가 바로 소비할 수 있습니다.
 *
 * <p>그런데 다음 경우에는 <b>원문 CLI 문자열</b>이 그대로 들어옵니다.
 * <ul>
 *   <li>구버전 Prober 가 파싱 전 원문만 실어 보낼 때</li>
 *   <li>운영자가 진단을 위해 {@code show ip route} 결과를 그대로 붙여 넣을 때</li>
 *   <li>오프라인 스냅샷에 원문이 함께 저장됐을 때</li>
 *   <li>Agent 가 보낸 구조의 키 이름이 내부 계약과 다를 때(벤더 개별 키)</li>
 * </ul>
 *
 * <p>이때 그냥 버리면 화면에 아무것도 나타나지 않고, 잘못된 키로 넘기면
 * 조용히 빈 설정이 됩니다. 그래서 <b>원문이 보이면 여기서 ANTLR 문법으로 다시
 * 파싱</b>해 내부 계약 형태로 맞춰 둡니다.
 *
 * <h2>동작 규칙</h2>
 * <ol>
 *   <li>payload 에 이미 {@code *.interfaces}/{@code routes} 배열이 있으면 <b>손대지 않습니다</b>.
 *       (Prober 가 이미 파싱한 결과를 다시 만들면 오히려 손실)</li>
 *   <li>원문 문자열만 있으면 대상({@code target})을 추정해
 *       {@link CliOutputParser#parseQueryOutput} 로 파싱합니다.</li>
 *   <li>대상 추정은 <b>출력 모양</b>이 아니라 <b>문법의 첫 토큰</b>으로 합니다.
 *       (예: {@code Codes:} 헤더 → 라우팅, {@code Interface ... OK?} → 인터페이스 상태)</li>
 *   <li>파싱은 예외를 던지지 않습니다. 실패해도 원문을 보존하고 그대로 돌려줍니다.</li>
 * </ol>
 */
@Service
public class CliIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CliIngestionService.class);

    /**
     * 원문이 담겨 있을 수 있는 키 이름들입니다.
     *
     * <p>Prober 버전과 장치 유형에 따라 키가 다릅니다. 하나만 보면 장치에 따라
     * 조용히 원문을 잃습니다.
     */
    private static final List<String> RAW_KEYS = List.of(
            "raw", "raw_output", "output", "text", "cli_output");

    /**
     * payload 에서 원문을 찾을 때 살펴보는 컨테이너 키입니다.
     *
     * <p>{@code nic_status} 안에 문자열 하나만 들어 있는 경우처럼, 대상 키 안에
     * 원문이 들어오는 형태를 지원하기 위한 것입니다.
     */
    private static final List<String> TARGETS = List.of(
            "nic_status", "route_status", "arp_table", "vlan_status",
            "trunk_status", "firewall_rules", "ovs_topology", "ovs_ports");

    private final CliOutputParser parser;

    /**
     * @param parser 원문 문자열을 구조화하는 파서 (상태 없음)
     */
    public CliIngestionService(CliOutputParser parser) {
        this.parser = parser;
    }

    /**
     * 텔레메트리 payload 의 원문 CLI 필드를 찾아 내부 계약 형태로 정규화합니다.
     *
     * <p><b>payload 를 제자리에서 고치지 않습니다.</b> 같은 노드를 여러 계층이
     * 공유하면(예: {@code lastTelemetry} 에 그대로 보관) 한 계층의 수정이 다른
     * 계층에 예기치 않게 보입니다. 필요한 경우에만 새 노드를 만들어 돌려줍니다.
     *
     * @param  payload  텔레메트리 payload (null 허용)
     * @param  vendor   벤더 (제품명에서 판별한 값; 모르면 {@code null})
     * @return 원문을 구조화한 <b>추가분</b> 노드 (추가할 것이 없으면 빈 노드)
     */
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

            // 파싱과 계약 키 판정을 <b>같은 전략</b>이 합니다.
            // 이전에는 파서가 문법을 고르고 이 메서드가 개수 키를 따로
            // 계산했는데, 둘이 어긋나면 "파싱은 성공, 화면은 빈" 상태가
            // 됩니다. QueryResult 가 세 값을 함께 들고 오므로 어긋날 수 없습니다.
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
            // 문법이 "성공" 이라 해도 항목이 0건이면 믿지 않습니다.
            // 문법의 elem 규칙(`~NEWLINE`)은 어떤 줄이든 통과시키므로, 모양이
            // 전혀 다른 텍스트도 parsed:true 로 나옵니다. 그대로 넘기면
            // 소비자는 "조회했는데 인터페이스가 없다" 로 해석합니다.
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

    /**
     * 제품명 문자열에서 벤더를 판별합니다.
     *
     * <p>{@link CliVendor#fromProductName} 를 그대로 노출해 호출자가 열거형을
     * 직접 다루지 않게 합니다.
     *
     * @param  productName 제품명 (null 허용)
     * @return 벤더 (판별 실패 시 {@link CliVendor#UNKNOWN})
     */
    public CliVendor vendorOf(String productName) {
        return CliVendor.fromProductName(productName);
    }

    /* ==================== 내부 도우미 ==================== */

    /**
     * 노드가 이미 소비자가 기대하는 구조를 갖췄는지 봅니다.
     *
     * <p>배열 키가 있고 그 배열이 비어 있지 않거나, 배열 키가 있는 객체면
     * "이미 파싱됨" 으로 봅니다. 빈 배열은 Prober 가 "조회했지만 항목이 없음"
     * 을 뜻하므로 다시 파싱하면 안 됩니다.
     *
     * @param  node 대상 노드
     * @return 이미 구조화된 것으로 볼 수 있는지
     */
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

    /**
     * 노드 안에서 원문 CLI 문자열을 찾습니다.
     *
     * @param  node 대상 노드 (문자열 또는 객체)
     * @return 원문 (없으면 {@code null})
     */
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

    /**
     * 원문의 내용으로 파서 대상을 추정합니다.
     *
     * <p>호출자가 준 대상 키를 우선 신뢰하고, 그 키에 원문이 여러 종류로 섞여
     * 들어올 수 있는 경우에만 내용으로 좁힙니다. 판정은 문자열 눈대중이 아니라
     * 문법이 실제로 요구하는 선행 토큰을 봅니다.
     *
     * <pre>
     *   Codes: 헤더           → FRR vtysh 라우트 테이블   → route
     *   Interface … OK?       → IOS 스타일 인터페이스 상태 → interface
     *   vlan … / switchport   → 스위치                    → port
     *   chain … / table …     → nftables                  → ruleset
     * </pre>
     *
     * @param  raw    원문
     * @param  target payload 의 대상 키
     * @return {@link CliOutputParser#parseQueryOutput} 에 넘길 대상 이름
     */
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

    /**
     * 파싱 결과에서 대표 개수를 뽑습니다. (로그용)
     *
     * <p>⚠️ <b>"0건이면 버린다"</b> 는 판정은 {@code QueryResult.itemCount()} 만
     * 씁니다. 이 메서드는 로그 문구용이라 더 관대합니다(배열을 직접 세는
     * 폴백 포함). 두 기준이 다르면 로그에는 개수가 찍히는데 데이터는 버려지는
     * 상황이 됩니다.
     *
     * <p>개수 키 목록이 {@code QueryResult} 와 같아야 하는 이유도 같습니다 —
     * 한쪽에만 키가 있으면 판정이 갈립니다. ({@code brief_count} 누락 사례)
     *
     * @param  parsed 파싱 본문
     * @return 항목 수 (없으면 0)
     */
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