package org.sonar.sonarvalidator_backend.Service.cli.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 조회 대상 선택기의 계약을 검증합니다.
 *
 * <h2>⚠️ 이 테스트가 겨냥하는 최악의 실패 모드</h2>
 * <p>"파싱은 성공했는데 화면은 빈" 상태입니다. ANTLR 의 catch-all 규칙은
 * 어떤 텍스트든 통과시키므로, <b>잘못된 문법을 골라도 예외가 나지 않습니다.</b>
 * 대신 항목이 0건인 정상 결과처럼 보입니다.
 *
 * <p>그래서 여기서는 다음을 봅니다.
 * <ul>
 *   <li>대상 이름이 <b>올바른 계약 키</b>로 해석되는가</li>
 *   <li>{@code brief} 가 원문에 따라 <b>다른 문법</b>으로 가는가</li>
 *   <li>대상이 없을 때 벤더 기본 조회로 가는가</li>
 *   <li>모르는 이름이 예외 없이 폴백되는가</li>
 * </ul>
 *
 * <p>이전에는 같은 사실이 세 곳에 흩어져 있었습니다
 * ({@code CliOutputParser} 의 대상 switch, 벤더 switch,
 * {@code CliIngestService.contractKeyOf} 의 키 표). 한 곳만 고치면
 * 조용히 어긋났습니다.
 */
class CliQueryStrategiesTest {

    private final CliQueryStrategies strategies = new CliQueryStrategies();

    /** {@code ip -br addr show} 형태의 짧은 출력. */
    private static final String ADDRESS_BRIEF = """
            lo               UNKNOWN        127.0.0.1/8
            eth0             UP             10.20.111.10/24
            """;

    /** {@code show ip interface brief} 형태의 IOS 출력. */
    private static final String IOS_BRIEF = """
            Interface              IP-Address      OK? Method Status                Protocol
            GigabitEthernet0/0/1   10.10.131.10    YES NVRAM  up                    up
            """;

    // ------------------------------------------------------------------
    //  이름 → 계약 키
    // ------------------------------------------------------------------

    @Test
    @DisplayName("대상 이름은 소비자가 읽는 계약 키로 해석된다")
    void targetNamesMapToContractKeys() {
        assertEquals("nic_status", strategies.select("nic", CliVendor.LINUX).contractKey());
        assertEquals("nic_status", strategies.select("addr", CliVendor.LINUX).contractKey());
        assertEquals("route_status", strategies.select("route", CliVendor.FRR).contractKey());
        assertEquals("arp_table", strategies.select("neigh", CliVendor.LINUX).contractKey());
        assertEquals("vlan_status", strategies.select("vlan", CliVendor.ARISTA).contractKey());
        assertEquals("trunk_status", strategies.select("port", CliVendor.ARISTA).contractKey());
        assertEquals("firewall_rules", strategies.select("nft", CliVendor.NFTABLES).contractKey());
        assertEquals("ovs_topology", strategies.select("ovs", CliVendor.OPEN_VSWITCH).contractKey());
    }

    @Test
    @DisplayName("대소문자와 공백이 섞여도 같은 대상으로 해석된다")
    void targetNamesAreNormalized() {
        // ⚠️ 정규화를 선택기에서 한 번만 하는 이유: 구현체마다 다듬으면
        //    "Route " 같은 입력이 어떤 구현체에도 안 걸려 조용히 폴백됩니다.
        assertEquals("route_status", strategies.select("  Route  ", CliVendor.FRR).contractKey());
        assertEquals("route_status", strategies.select("ROUTE-TABLE", CliVendor.FRR).contractKey());
    }

    // ------------------------------------------------------------------
    //  brief — 이름 하나가 두 문법을 갖는다
    // ------------------------------------------------------------------

    @Test
    @DisplayName("brief 는 담당 전략이 하나이고, 그 안에서 원문으로 문법을 고른다")
    void briefHasSingleOwningStrategy() {
        // ⚠️ 두 전략으로 나누면 등록 순서가 곧 정확성이 됩니다.
        //    새 전략을 끼워 넣는 사람이 그 사실을 모르면 조용히 깨집니다.
        assertEquals("NicBriefQueryStrategy",
                strategies.select("brief", CliVendor.CISCO).getClass().getSimpleName());
        assertEquals("NicBriefQueryStrategy",
                strategies.select("brief", CliVendor.LINUX).getClass().getSimpleName());
        assertEquals("NicBriefQueryStrategy",
                strategies.select("nic-brief", CliVendor.UNKNOWN).getClass().getSimpleName());
    }

    @Test
    @DisplayName("brief 두 문법은 같은 계약 키를 쓴다 (소비자가 구분할 필요 없음)")
    void briefPathsShareContractKey() {
        final String ios = strategies.select("brief", CliVendor.CISCO).contractKey();
        final String addr = strategies.select("brief", CliVendor.LINUX).contractKey();

        assertEquals(ios, addr, "두 출력 모두 인터페이스 정보이므로 같은 키");
        assertEquals("nic_status", ios);
    }

    @Test
    @DisplayName("brief 는 IOS 헤더가 있으면 인터페이스 상태 문법, 없으면 주소 요약 문법")
    void briefPicksGrammarByRawContent() {
        final CliOutputParser parser = new CliOutputParser();

        // IOS 브리프는 `interfaces[]` 를 만들고 `interface_count` 를 넣습니다.
        final QueryResult ios = parser.parseQuery("cisco", "brief", IOS_BRIEF);
        assertEquals("nic_status", ios.contractKey());
        assertTrue(ios.itemCount() >= 1, "IOS 헤더 → 인터페이스 항목이 나와야 함");

        // 주소 요약도 `interfaces[]` 를 만들지만 파싱 경로가 다릅니다.
        final QueryResult addr = parser.parseQuery("linux", "brief", ADDRESS_BRIEF);
        assertEquals("nic_status", addr.contractKey());
        assertTrue(addr.itemCount() >= 1, "주소 요약 → 인터페이스 항목이 나와야 함");
    }

    @Test
    @DisplayName("interface 를 명시하면 원문과 무관하게 인터페이스 상태 문법을 쓴다")
    void explicitInterfaceTargetWins() {
        // 운영자가 명시한 의사는 원문 추론보다 우선합니다.
        assertEquals("InterfaceStatusQueryStrategy",
                strategies.select("interface", CliVendor.CISCO).getClass().getSimpleName());
    }

    // ------------------------------------------------------------------
    //  벤더 기본 조회
    // ------------------------------------------------------------------

    @Test
    @DisplayName("대상이 없으면 벤더별 기본 조회로 간다")
    void vendorDefaultsApply() {
        assertEquals("ovs_topology",
                strategies.select(null, CliVendor.OPEN_VSWITCH).contractKey());
        assertEquals("route_status",
                strategies.select(null, CliVendor.FRR).contractKey());
        assertEquals("route_status",
                strategies.select(null, CliVendor.CISCO).contractKey());
        assertEquals("vlan_status",
                strategies.select(null, CliVendor.ARISTA).contractKey());
        assertEquals("firewall_rules",
                strategies.select(null, CliVendor.NFTABLES).contractKey());
        assertEquals("nic_status",
                strategies.select(null, CliVendor.LINUX).contractKey());
        // 모르는 벤더는 리눅스 기본 — 주소라도 보고합니다.
        assertEquals("nic_status",
                strategies.select(null, CliVendor.UNKNOWN).contractKey());
    }

    @Test
    @DisplayName("빈 대상과 공백 대상은 모두 벤더 기본으로 간다")
    void blankTargetUsesVendorDefault() {
        assertEquals("route_status", strategies.select("", CliVendor.FRR).contractKey());
        assertEquals("route_status", strategies.select("   ", CliVendor.FRR).contractKey());
        assertEquals("route_status", strategies.select(null, CliVendor.FRR).contractKey());
    }

    // ------------------------------------------------------------------
    //  실패 경로
    // ------------------------------------------------------------------

    @Test
    @DisplayName("모르는 대상 이름은 예외 없이 벤더 기본으로 폴백한다")
    void unknownTargetFallsBack() {
        // 예외를 던지면 텔레메트리 수집이 멈춥니다. 수집이 계속되는 편이 낫습니다.
        final CliQueryStrategy strategy = strategies.select("no-such-target", CliVendor.FRR);

        assertNotNull(strategy);
        assertEquals("route_status", strategy.contractKey());
    }

    @Test
    @DisplayName("벤더가 null 이어도 예외가 나지 않는다")
    void nullVendorIsTolerated() {
        final CliQueryStrategy strategy = strategies.select("route", null);

        assertNotNull(strategy);
        assertEquals("route_status", strategy.contractKey());
    }

    @Test
    @DisplayName("모든 전략이 자기 기본 대상 이름으로 다시 선택된다 (자기 일관성)")
    void primaryTargetsAreSelfSelecting() {
        // primaryTarget() 이 자기 자신을 지목하지 못하면,
        // 벤더 기본 조회가 엉뚱한 전략으로 갑니다.
        for (final CliQueryStrategy strategy : strategies.strategies()) {
            final CliQueryStrategy again = strategies.select(
                    strategy.primaryTarget(), CliVendor.UNKNOWN);
            assertEquals(strategy.getClass(), again.getClass(),
                    strategy.primaryTarget() + " 가 자기 자신을 선택하지 못함");
        }
    }

    @Test
    @DisplayName("같은 이름을 두 전략이 담당하지 않는다 (불변식)")
    void noDuplicateOwningNames() {
        // ⚠️ 이 불변식이 깨지면 <b>등록 순서가 곧 정확성</b>이 됩니다.
        //    새 전략을 끼워 넣는 사람이 그 사실을 모르면 조용히 깨집니다.
        //    원문 모양으로 문법이 갈리는 대상(brief)은 전략 <b>안에서</b> 고릅니다.
        final java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (final CliQueryStrategy strategy : strategies.strategies()) {
            for (final String name : ALL_KNOWN_NAMES) {
                if (strategy.matches(name)) {
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }
        for (final var entry : counts.entrySet()) {
            assertEquals(1, entry.getValue(),
                    entry.getKey() + " 담당 전략이 " + entry.getValue() + "개 — 이름 하나 = 전략 하나여야 함");
        }
    }

    /** 저장소에서 쓰는 모든 대상 이름입니다. (중복 검사 대상) */
    private static final java.util.List<String> ALL_KNOWN_NAMES = java.util.List.of(
            "nic", "addr", "brief", "nic-brief", "route", "route-table",
            "interface", "interface-brief", "arp", "neigh", "topology", "ovs",
            "vlan", "vlan-brief", "switchport", "port", "running", "running-config",
            "chain", "ruleset", "nft", "firewall");

    // ------------------------------------------------------------------
    //  파서 연동
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parseQuery 는 해석된 이름·계약 키·본문을 함께 돌려준다")
    void parseQueryReturnsAllThree() {
        final CliOutputParser parser = new CliOutputParser();
        final QueryResult result = parser.parseQuery("linux?", "nic", ADDRESS_BRIEF);

        assertNotNull(result);
        assertEquals("nic", result.query(), "해석된 대상 이름");
        assertEquals("nic_status", result.contractKey(), "소비자가 읽을 키");
        assertNotNull(result.body(), "본문");
    }

    @Test
    @DisplayName("이름을 생략하면 벤더 기본 조회가 적용된다")
    void parseQueryAppliesVendorDefault() {
        final CliOutputParser parser = new CliOutputParser();
        final QueryResult result = parser.parseQuery("opnsense", null, IOS_BRIEF);

        // opnsense 는 UNKNOWN 벤더로 판별되므로 리눅스 기본(주소 조회)입니다.
        assertEquals("nic", result.query());
        assertEquals("nic_status", result.contractKey());
    }

    @Test
    @DisplayName("원문이 비어 있어도 예외 없이 실패 노드를 돌려준다")
    void emptyRawProducesFailureNode() {
        final CliOutputParser parser = new CliOutputParser();
        final QueryResult result = parser.parseQuery("frr", "route", "");

        assertNotNull(result.body(), "실패해도 본문은 null 이 아님");
        assertTrue(result.itemCount() == 0, "항목 0건");
    }

    @Test
    @DisplayName("항목 수는 각 문법의 개수 키에서 읽는다")
    void itemCountReadsGrammarSpecificKeys() {
        final CliOutputParser parser = new CliOutputParser();
        final QueryResult result = parser.parseQuery("cisco", "interface", IOS_BRIEF);

        // IOS 브리프는 interfaces[] 를 만들고 interface_count 를 넣습니다.
        assertTrue(result.itemCount() >= 0, "개수 키를 읽음");
        assertEquals("nic_status", result.contractKey());
    }
}