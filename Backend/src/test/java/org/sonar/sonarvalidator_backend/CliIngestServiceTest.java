package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Service.DeviceConfigService;
import org.sonar.sonarvalidator_backend.Service.cli.CliIngestRequest;
import org.sonar.sonarvalidator_backend.Service.cli.CliIngestService;
import org.sonar.sonarvalidator_backend.Service.cli.CliIngestionService;
import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 원문 CLI 수집 서비스({@link CliIngestService})를 검증합니다.
 *
 * <h2>왜 이 테스트가 필요한가</h2>
 * <p>파서 단위 테스트는 "JSON 이 맞게 나왔다" 까지만 봅니다. 이 경로의 실패
 * 모드는 그게 아닙니다.
 *
 * <ol>
 *   <li><b>계약 키가 틀리는 경우</b> — 파싱은 성공했는데 payload 키 이름이
 *       {@code AbstractDeviceConfigParser} 가 읽는 것과 다르면 화면이 빕니다.
 *       (조용한 실패)</li>
 *   <li><b>0건을 성공으로 넘기는 경우</b> — 문법의 catch-all 규칙 때문에 엉뚱한
 *       텍스트도 {@code parsed:true} 가 됩니다. 계약 키로 넣으면 소비자가
 *       "조회했는데 인터페이스가 없다" 로 해석해 기존 데이터를 덮어씁니다.</li>
 *   <li><b>빈 입력</b> — 붙여넣기 실패를 파싱 실패와 구분해 사유를 알려야
 *       합니다.</li>
 * </ol>
 *
 * <p>즉 이 테스트는 <i>파서 → 계약 키 → 중립 설정</i> 이음매를 봅니다.
 */
class CliIngestServiceTest {

    private final CliOutputParser parser = new CliOutputParser();
    private final CliIngestionService ingestion = new CliIngestionService(parser);
    private final DeviceConfigService deviceConfigService = new DeviceConfigService(
            List.of(new org.sonar.sonarvalidator_backend.Model.Config.Vendors.LinuxVmConfigParser()));
    private final CliIngestService service =
            new CliIngestService(parser, ingestion, deviceConfigService);

    /** 커널 iproute2 라우팅 출력 — `metric 20` 키워드 형태. */
    private static final String KERNEL_ROUTE = """
            default via 10.99.10.1 dev eth0 proto static metric 1
            10.10.128.0/21 via 10.99.143.2 dev eth1 proto ospf metric 20
            """;

    /** FRR vtysh 라우팅 출력 — `[110/200]` 브래킷 형태. */
    private static final String FRR_ROUTE = """
            Codes: K - kernel route, C - connected, S - static, O - OSPF,
            C>* 10.99.10.0/24 is directly connected, eth0, 00:00:12
            O>* 10.10.128.0/21 [110/20] via 10.99.143.2, eth1, 00:00:05
            """;

    /* ==================== 계약 키 ==================== */

    @Test
    @DisplayName("route 원문 → route_status 키와 항목 수가 채워진다")
    void routeIntoContractKey() {
        final Map<String, Object> result = service.ingest(
                new CliIngestRequest("Ubuntu", "route", KERNEL_ROUTE), false);

        assertEquals(Boolean.TRUE, result.get("accepted"), () -> "실패: " + result);
        assertEquals("route_status", result.get("target_key"));
        assertEquals("LINUX", result.get("vendor"));
        assertEquals(2, (int) result.get("item_count"), () -> "파싱 결과: " + result.get("parsed"));
        assertTrue(((List<?>) result.get("errors")).isEmpty());
    }

    @Test
    @DisplayName("target 생략 시 벤더 기본 조회로 폴백한다")
    void targetFallsBackToVendorDefault() {
        // FRR 벤더의 기본 조회는 라우팅 테이블입니다.
        final Map<String, Object> result = service.ingest(
                new CliIngestRequest("FRR", null, FRR_ROUTE), false);

        assertEquals(Boolean.TRUE, result.get("accepted"), () -> "실패: " + result);
        assertEquals("route_status", result.get("target_key"));
        assertEquals("FRR", result.get("vendor"));
    }

    /* ==================== 중립 설정까지 ==================== */

    @Test
    @DisplayName("config=true 면 중립 설정의 라우트까지 채워진다 (조용한 실패 방지)")
    void ingestIntoNeutralConfig() {
        final Map<String, Object> result = service.ingest(
                new CliIngestRequest("Ubuntu", "route", KERNEL_ROUTE), true);

        final Object config = result.get("config");
        assertNotNull(config, "config 가 담겨야 합니다");
        final NeutralDeviceConfig neutral = (NeutralDeviceConfig) config;

        assertEquals(2, neutral.getRoutes().size(),
                () -> "라우트가 비면 계약 키가 틀린 것입니다: " + result.get("target_key"));
    }

    @Test
    @DisplayName("metric 은 정수로 읽힌다 — 브래킷([110/20])과 키워드 모두")
    void metricIsInteger() {
        final Map<String, Object> kernel = service.ingest(
                new CliIngestRequest("Ubuntu", "route", KERNEL_ROUTE), false);
        final JsonNode kernelRoutes = ((JsonNode) kernel.get("parsed")).path("routes");
        assertTrue(kernelRoutes.get(1).path("metric").isNumber(),
                () -> "커널 metric 은 숫자여야 합니다: " + kernelRoutes.get(1));
        assertEquals(20, kernelRoutes.get(1).path("metric").asInt());

        final Map<String, Object> frr = service.ingest(
                new CliIngestRequest("FRR", "route", FRR_ROUTE), false);
        final JsonNode frrRoutes = ((JsonNode) frr.get("parsed")).path("routes");
        // `[110/20]` → distance=110, metric=20 (Java Backend 계약)
        final JsonNode ospf = frrRoutes.get(frrRoutes.size() - 1);
        assertEquals(110, ospf.path("distance").asInt(),
                () -> "distance 를 잃으면 안 됩니다: " + ospf);
        assertEquals(20, ospf.path("metric").asInt(),
                () -> "metric 은 정수여야 합니다: " + ospf);
    }

    /* ==================== 실패 계약 ==================== */

    @Test
    @DisplayName("빈 원문은 파싱하지 않고 사유를 돌려준다")
    void blankRawIsRejected() {
        final Map<String, Object> result = service.ingest(
                new CliIngestRequest("Ubuntu", "route", "   \n  "), false);

        assertEquals(Boolean.FALSE, result.get("accepted"));
        assertFalse(((List<?>) result.get("errors")).isEmpty(), "사유가 있어야 합니다");
        // 파싱 자체를 시도하지 않으므로 parsed 노드도 없습니다.
        assertFalse(result.containsKey("parsed"), () -> "불필요한 파싱: " + result);
    }

    @Test
    @DisplayName("null 요청도 예외 없이 사유를 돌려준다")
    void nullRequestDoesNotThrow() {
        final Map<String, Object> result = service.ingest(null, false);

        assertEquals(Boolean.FALSE, result.get("accepted"));
        assertFalse(((List<?>) result.get("errors")).isEmpty());
    }

    @Test
    @DisplayName("대상과 무관한 텍스트는 0건이므로 경고하고 계약 키를 넣지 않는다")
    void zeroItemResultIsNotWrittenIntoPayload() {
        // 라우트가 아닌 텍스트가 라우트로 파싱되면 안 됩니다. 예전 문법은
        // routeHead 에 IFNAME 을 허용해 아무 단어나 목적지로 삼켰고, `hello world`
        // 가 라우트 2건으로 통과했습니다(parsed:true, item_count:2). 소비자는 이를
        // "조회했는데 경로 없음" 이 아니라 "경로 2건" 으로 읽습니다.
        // 그래서 목적지 자리를 커널이 실제로 내는 토큰으로 좁혔습니다.
        final Map<String, Object> result = service.ingest(
                new CliIngestRequest("Ubuntu", "route", "hello world\nthis is not a route"), true);

        assertEquals(0, (int) result.get("item_count"), () -> "잡음이 라우트로 파싱됨: " + result.get("parsed"));
        assertFalse(((List<?>) result.get("errors")).isEmpty(),
                () -> "0건이면 경고가 있어야 합니다: " + result);

        final NeutralDeviceConfig neutral = (NeutralDeviceConfig) result.get("config");
        assertTrue(neutral.getRoutes().isEmpty(),
                "0건 결과로 기존 라우트를 덮으면 안 됩니다");
    }

    /* ==================== 오프라인/원문 키 관용 ==================== */

    @Test
    @DisplayName("대상 이름의 별칭(neigh/addr)도 인식한다")
    void targetAliasesAreRecognized() {
        final String neigh = """
                10.99.10.1 dev eth0 lladdr 52:54:00:11:22:33 REACHABLE
                10.99.10.5 dev eth1 lladdr 52:54:00:44:55:66 STALE
                """;
        final Map<String, Object> result = service.ingest(
                new CliIngestRequest("Ubuntu", "neigh", neigh), false);

        assertEquals("arp_table", result.get("target_key"), () -> "별칭 미인식: " + result);
        assertEquals(2, (int) result.get("item_count"), () -> "파싱: " + result.get("parsed"));
    }

    @Test
    @DisplayName("빈 원문은 파서가 아니라 수집 계층이 막는다 (2중 방어)")
    void blankRawIsRejectedByIngestionLayerNotParser() {
        // 파서는 "빈 입력" 을 스스로 판단하지 않습니다. 문법이 빈 문서를 정상으로
        // 받아들이므로 parsed:true / 0건 이 나옵니다. 파서의 책임은 원문이 주어졌을 때
        // 최선의 구조를 내는 것이고, "조회 결과가 없다" 는 판단은 수집 계층의 몫입니다.
        // 이 경계를 테스트로 못 박아 둡니다.
        final ObjectNode empty = parser.parseQueryOutput(CliVendor.UNKNOWN, "route", "");
        assertTrue(empty.path("parsed").asBoolean(false),
                () -> "빈 문서는 문법상 정상입니다: " + empty);
        assertEquals(0, empty.path("route_count").asInt(), () -> "0건: " + empty);

        // 그래서 수집 계층이 먼저 빈 원문을 걸러냅니다. 파싱을 시도하지 않고
        // parsed 노드도 만들지 않습니다. 만약 위 0건 결과를 계약 키로 넣으면
        // 소비자는 "조회했는데 경로가 없다" 로 오해해 기존 라우트를 지웁니다.
        final Map<String, Object> rejected = service.ingest(
                new CliIngestRequest("Ubuntu", "route", "   "), false);
        assertEquals(Boolean.FALSE, rejected.get("accepted"), () -> "빈 원문 거부: " + rejected);
        assertFalse(rejected.containsKey("parsed"), () -> "파싱 시도 금지: " + rejected);
    }
}