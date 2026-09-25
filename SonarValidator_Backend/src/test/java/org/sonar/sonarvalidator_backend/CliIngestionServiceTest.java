package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.Config.Vendors.LinuxVmConfigParser;
import org.sonar.sonarvalidator_backend.Service.DeviceConfigService;
import org.sonar.sonarvalidator_backend.Service.cli.CliIngestionService;
import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 원문 CLI 문자열이 <b>기존 중립 설정 파이프라인까지</b> 도달하는지 검증합니다.
 *
 * <h2>왜 이 테스트가 필요한가</h2>
 * <p>파서 단위 테스트({@link CliOutputParserTest})는 "JSON 이 맞게 나왔다" 까지만
 * 봅니다. 그런데 실제로 화면에 나타나려면 그 JSON 이
 * <i>payload 키 → {@code AbstractDeviceConfigParser} → {@code NeutralDeviceConfig}</i>
 * 경로를 통과해야 합니다. 이 테스트는 그 <b>이음매</b>를 확인합니다.
 *
 * <pre>
 *   payload["nic_status"] = "&lt;원문 ip a 출력&gt;"      (문자열)
 *        │  CliIngestionService.extractParsed
 *        ▼  ANTLR 문법으로 파싱
 *   payload["nic_status"] = {"interfaces":[...]}       (구조화)
 *        │  DeviceConfigService → LinuxVmConfigParser
 *        ▼
 *   NeutralDeviceConfig.interfaces = { lo, eth1.131, eth4 }
 * </pre>
 *
 * <p>즉 <b>Prober 가 파싱하지 않고 원문만 보낸 경우에도</b> 설정이 채워지는지를
 * 봅니다.
 */
class CliIngestionServiceTest {

    private final CliOutputParser parser = new CliOutputParser();
    private final CliIngestionService ingestion = new CliIngestionService(parser);
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** {@code ip a} 원문. (Prober 가 파싱하지 않고 그대로 보낸 형태) */
    private static final String IP_ADDR_RAW = """
            1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN qlen 1000
                link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
                inet 127.0.0.1/8 scope host lo
            2: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UP qlen 1000
                link/ether 02:42:7c:24:78:01 brd ff:ff:ff:ff:ff:ff
                inet 10.10.131.1/24 scope global eth0
            """;

    /** {@code ip route show} 원문. (커널 라우팅) */
    private static final String KERNEL_ROUTE_RAW = """
            default via 10.99.10.1 dev eth0 proto static
            10.10.128.0/21 via 10.99.143.2 dev eth1 proto ospf metric 20
            10.99.10.0/24 dev eth0 proto kernel scope link src 10.99.10.2
            """;

    /** {@code ip neigh show} 원문. */
    private static final String ARP_RAW = """
            172.18.10.1 dev eth0 lladdr 0c:ae:21:dd:00:01 REACHABLE
            172.18.10.8 dev eth1 lladdr 0c:ae:21:dd:00:08 STALE
            """;

    @Test
    @DisplayName("원문 문자열 payload → 내부 계약 키로 변환된다")
    void extractParsedFromRaw() {
        final ObjectNode payload = mapper.createObjectNode();
        payload.put("nic_status", IP_ADDR_RAW);
        payload.put("route_status", KERNEL_ROUTE_RAW);
        payload.put("arp_table", ARP_RAW);

        final ObjectNode extra = ingestion.extractParsed(payload, CliVendor.LINUX);

        // 세 대상 모두 구조화됐는지 확인합니다.
        assertEquals(2, extra.path("nic_status").path("interfaces").size(),
                () -> "nic_status: " + extra);
        assertEquals(3, extra.path("route_status").path("routes").size(),
                () -> "route_status: " + extra);
        assertEquals(2, extra.path("arp_table").path("entries").size(),
                () -> "arp_table: " + extra);
    }

    @Test
    @DisplayName("이미 구조화된 payload 는 건드리지 않는다")
    void alreadyStructuredIsUntouched() {
        final ObjectNode payload = mapper.createObjectNode();
        final ObjectNode nic = payload.putObject("nic_status");
        nic.putArray("interfaces");

        final ObjectNode extra = ingestion.extractParsed(payload, CliVendor.LINUX);

        assertTrue(extra.isEmpty(),
                () -> "이미 구조화된 payload 는 변환 대상이 아니어야 합니다: " + extra);
    }

    @Test
    @DisplayName("raw 키에 담긴 원문도 알아본다")
    void rawKeyIsRecognized() {
        final ObjectNode payload = mapper.createObjectNode();
        payload.putObject("route_status").put("raw", KERNEL_ROUTE_RAW);

        final ObjectNode extra = ingestion.extractParsed(payload, CliVendor.LINUX);

        assertEquals(3, extra.path("route_status").path("routes").size(),
                () -> "raw 키의 원문: " + extra);
    }

    @Test
    @DisplayName("원문이 없으면 빈 노드를 돌려준다 (예외 없음)")
    void noRawYieldsEmpty() {
        final ObjectNode payload = mapper.createObjectNode();
        payload.put("kernel", "6.8.0");

        assertTrue(ingestion.extractParsed(payload, CliVendor.LINUX).isEmpty());
        assertTrue(ingestion.extractParsed(null, CliVendor.LINUX).isEmpty());
        assertTrue(ingestion.extractParsed(mapper.createArrayNode(), CliVendor.LINUX).isEmpty());
    }

    @Test
    @DisplayName("깨진 원문이어도 예외를 던지지 않고 계약 키를 만들지 않는다")
    void brokenRawDoesNotThrow() {
        final ObjectNode payload = mapper.createObjectNode();
        // 인터페이스 목록처럼 보이지만 문법에 맞지 않는 문자열입니다.
        payload.put("nic_status", "@@@ not a cli output @@@");
        payload.put("route_status", "\u0000\u0001 garbage");

        final ObjectNode extra = ingestion.extractParsed(payload, CliVendor.LINUX);

        // 실패하면 키를 넣지 않습니다 — 넣으면 소비자가 "빈 목록" 으로 오해합니다.
        assertFalse(extra.has("nic_status"), () -> "실패 결과를 넣으면 안 됩니다: " + extra);
        assertFalse(extra.has("route_status"), () -> "실패 결과를 넣으면 안 됩니다: " + extra);
    }

    @Test
    @DisplayName("제품명 → 벤더 판별이 CliVendor 규칙을 따른다")
    void vendorFromProductName() {
        assertEquals(CliVendor.CISCO, ingestion.vendorOf("Cisco IOS-XE"));
        assertEquals(CliVendor.ARISTA, ingestion.vendorOf("Arista EOS"));
        assertEquals(CliVendor.FRR, ingestion.vendorOf("FRR 8.5"));
        assertEquals(CliVendor.OPEN_VSWITCH, ingestion.vendorOf("OpenVSwitch"));
        assertEquals(CliVendor.NFTABLES, ingestion.vendorOf("nftables"));
        assertEquals(CliVendor.LINUX, ingestion.vendorOf("Ubuntu 24.04"));
        assertEquals(CliVendor.UNKNOWN, ingestion.vendorOf(null));
    }

    @Test
    @DisplayName("엔드투엔드: 원문 payload 가 NeutralDeviceConfig 까지 도달한다")
    void endToEndIntoNeutralConfig() {
        // 1) Prober 가 파싱하지 않고 원문만 보낸 payload.
        final ObjectNode payload = mapper.createObjectNode();
        payload.put("product", "Ubuntu 24.04");
        payload.put("nic_status", IP_ADDR_RAW);
        payload.put("route_status", KERNEL_ROUTE_RAW);
        payload.put("arp_table", ARP_RAW);

        // 2) 폴백 파싱 (라우터가 하는 일과 동일).
        final ObjectNode extra = ingestion.extractParsed(payload, ingestion.vendorOf("Ubuntu 24.04"));

        final ObjectNode merged = mapper.createObjectNode();
        merged.setAll(payload);
        merged.setAll(extra);

        // 3) 기존 중립 설정 파이프라인 (실제 벤더 파서).
        final DeviceConfigService service = new DeviceConfigService(java.util.List.of(new LinuxVmConfigParser()));
        final NeutralDeviceConfig config = service.parse("vm-1", "Ubuntu 24.04", merged);

        // 4) 화면/분석이 보게 되는 값.
        assertNotNull(config);
        assertEquals("Ubuntu 24.04", config.getProduct());
        assertTrue(config.getInterfaces().containsKey("eth0"),
                () -> "인터페이스가 채워져야 합니다: " + config.getInterfaces().keySet());
        assertEquals("10.10.131.1/24",
                String.join(",", config.getInterfaces().get("eth0").getAddresses()));
        assertEquals(3, config.getRoutes().size(), () -> "라우트: " + config.getRoutes());
        assertEquals(2, config.getArpEntries().size(), () -> "ARP: " + config.getArpEntries());

        final NeutralDeviceConfig.RouteConfig defaultRoute = config.getRoutes().get(0);
        assertEquals("0.0.0.0/0", defaultRoute.getPrefix());
        assertEquals("10.99.10.1", defaultRoute.getNextHop());
        assertEquals("eth0", defaultRoute.getNextHopInterface());
        assertEquals("static", defaultRoute.getProtocol());
        assertEquals(Boolean.TRUE, defaultRoute.getDefaultRoute(),
                "기본 경로로 판정돼야 합니다");
    }

    @Test
    @DisplayName("payload 는 제자리에서 수정되지 않는다")
    void payloadIsNotMutated() {
        final ObjectNode payload = mapper.createObjectNode();
        payload.put("route_status", KERNEL_ROUTE_RAW);

        final JsonNode before = payload.deepCopy();
        ingestion.extractParsed(payload, CliVendor.LINUX);

        // 보관용 원본(lastTelemetry)의 의미가 깨지면 조회 API 가 달라집니다.
        assertEquals(before.toString(), payload.toString(),
                "extractParsed 는 전달받은 payload 를 고치면 안 됩니다");
    }
}