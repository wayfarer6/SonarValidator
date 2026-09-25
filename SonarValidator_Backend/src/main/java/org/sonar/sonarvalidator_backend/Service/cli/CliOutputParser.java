package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.FrrRouterVisitor;
import org.sonar.sonarvalidator_backend.Service.cli.IpAddrVisitor;
import org.sonar.sonarvalidator_backend.Service.cli.NftablesVisitor;
import org.sonar.sonarvalidator_backend.Service.cli.OvsVisitor;
import org.sonar.sonarvalidator_backend.Service.cli.SwitchVisitor;
import org.sonar.sonarvalidator_backend.grammar.FrrRouterLexer;
import org.sonar.sonarvalidator_backend.grammar.FrrRouterParser;
import org.sonar.sonarvalidator_backend.grammar.IpAddrLexer;
import org.sonar.sonarvalidator_backend.grammar.IpAddrParser;
import org.sonar.sonarvalidator_backend.grammar.NftablesRuleLexer;
import org.sonar.sonarvalidator_backend.grammar.NftablesRuleParser;
import org.sonar.sonarvalidator_backend.grammar.OvsTopologyLexer;
import org.sonar.sonarvalidator_backend.grammar.OvsTopologyParser;
import org.sonar.sonarvalidator_backend.grammar.SwitchTopologyLexer;
import org.sonar.sonarvalidator_backend.grammar.SwitchTopologyParser;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * CLI 출력을 JSON 으로 바꾸는 단일 진입점입니다.
 *
 * <p>C++ Prober 의 {@code cli_parser::ParseQueryOutput} 과 같은 계약입니다.
 * 벤더별 {@link CliVendor} 와 조회 대상({@code target}) 을 받아, 그 장비가 실제로
 * 내보내는 문법 하나를 골라 파싱합니다.
 *
 * <h2>문법 선택은 "출력 모양"이 아니라 "명령"으로</h2>
 * Prober 는 같은 벤더라도 명령에 따라 출력이 달라집니다.
 * <pre>
 *   FRR 호스트 셸   ip route show    → 라우트 코드 없음  → IpAddr 문법
 *   FRR vtysh       show ip route    → `O>*` 코드 있음   → FrrRouter 문법
 * </pre>
 * 그래서 {@code vendor} 만으로는 부족하고 {@code target} 까지 봐야 합니다.
 * target 이 비어 있으면 벤더 기본 조회로 폴백합니다.
 *
 * <h2>실패 계약</h2>
 * 파서는 <b>예외를 던지지 않습니다</b>. 문법 오류가 있어도 뽑은 정보를 그대로
 * 담고 {@code parsed:false} 와 {@code parse_error} 를 붙여 돌려줍니다.
 * 텔레메트리 수집 경로에서 예외는 곧 데이터 유실이기 때문입니다.
 *
 * <h2>상태가 없는 컴포넌트</h2>
 * 모든 메서드가 인자만으로 결과를 만듭니다. 그래서 {@link Service} 로 등록해도
 * 동시 요청에서 공유 상태가 없고, 테스트에서는 {@code new CliOutputParser()}
 * 로 그냥 쓸 수 있습니다.
 */
@Service
public final class CliOutputParser {

    /* ==================== 개별 파서 ==================== */

    /**
     * {@code ip a} / {@code ip addr show} → {@code {"interfaces":[...]}}.
     *
     * @param raw 원문 CLI 출력
     * @return 본문 (실패 시 실패 노드)
     */
    public ObjectNode parseNicStatus(String raw) {
        return guard(raw, () -> {
            ParseSession<IpAddrLexer, IpAddrParser> session =
                    new ParseSession<>(IpAddrLexer::new, IpAddrParser::new, raw);
            IpAddrVisitor visitor = new IpAddrVisitor(session);
            visitor.visit(session.parser().document());
            ObjectNode body = CliJson.object();
            body.set("interfaces", visitor.interfaces);
            body.put("interface_count", visitor.interfaces.size());
            return CliJson.attachParseInfo(body, session);
        });
    }

    /**
     * {@code ip -br addr show} → {@code {"brief":[...]}}.
     *
     * @param raw 원문 CLI 출력
     * @return 본문
     */
    public ObjectNode parseNicBrief(String raw) {
        return guard(raw, () -> {
            ParseSession<IpAddrLexer, IpAddrParser> session =
                    new ParseSession<>(IpAddrLexer::new, IpAddrParser::new, raw);
            IpAddrVisitor visitor = new IpAddrVisitor(session);
            visitor.visit(session.parser().briefDocument());
            ObjectNode body = CliJson.object();
            body.set("brief", visitor.brief);
            body.put("brief_count", visitor.brief.size());
            return CliJson.attachParseInfo(body, session);
        });
    }

    /**
     * 이웃/ARP 테이블 → {@code {"entries":[...]}}.
     *
     * <p>{@code ip neigh show}, {@code show arp}(Arista), {@code show ip arp}(Cisco)
     * 세 형식을 모두 IpAddr 문법의 {@code arpDocument} 가 받습니다.
     *
     * @param raw   원문 CLI 출력
     * @param vendor 벤더 (현재 사용하지 않지만 C++ 계약과 시그니처를 맞춘다)
     * @return 본문
     */
    public ObjectNode parseArpTable(String raw, CliVendor vendor) {
        return guard(raw, () -> {
            ParseSession<IpAddrLexer, IpAddrParser> session =
                    new ParseSession<>(IpAddrLexer::new, IpAddrParser::new, raw);
            IpAddrVisitor visitor = new IpAddrVisitor(session);
            visitor.visit(session.parser().arpDocument());
            ObjectNode body = CliJson.object();
            body.set("entries", visitor.arpEntries);
            body.put("entry_count", visitor.arpEntries.size());
            return CliJson.attachParseInfo(body, session);
        });
    }

    /**
     * 라우팅 테이블 → {@code {"routes":[...]}}.
     *
     * <p>출력에 라우트 코드가 있으면({@code O>*}, {@code C}, {@code S}) FRR 문법,
     * 없으면({@code default via ... dev ...}) 커널 {@code ip route} 로 보고
     * IpAddr 문법을 씁니다.
     *
     * <p>코드 유무는 {@link RouteCodes#isRouteCodeToken} 으로 판정합니다.
     * 즉 이 분기도 문자열 눈대중이 아니라 <b>토큰 분류</b>입니다.
     *
     * @param raw    원문 CLI 출력
     * @param vendor 벤더 (리눅스 호스트 여부 판단용)
     * @return 본문
     */
    public ObjectNode parseRouteStatus(String raw, CliVendor vendor) {
        if (vendor != null && vendor.isLinuxHost() && !looksLikeVtyshRouteTable(raw)) {
            return guard(raw, () -> {
                ParseSession<IpAddrLexer, IpAddrParser> session =
                        new ParseSession<>(IpAddrLexer::new, IpAddrParser::new, raw);
                IpAddrVisitor visitor = new IpAddrVisitor(session);
                visitor.visit(session.parser().routeDocument());
                ObjectNode body = CliJson.object();
                body.set("routes", visitor.routes);
                body.put("route_count", visitor.routes.size());
                body.set("protocols", distinctProtocols(visitor.routes));
                return CliJson.attachParseInfo(body, session);
            });
        }

        return guard(raw, () -> {
            ParseSession<FrrRouterLexer, FrrRouterParser> session =
                    new ParseSession<>(FrrRouterLexer::new, FrrRouterParser::new, raw);
            FrrRouterVisitor visitor = new FrrRouterVisitor(session);
            visitor.visit(session.parser().routeDocument());
            ObjectNode body = CliJson.object();
            body.set("routes", visitor.routes);
            body.put("route_count", visitor.routes.size());
            body.set("protocols", distinctProtocols(visitor.routes));
            return CliJson.attachParseInfo(body, session);
        });
    }

    /**
     * {@code show ip interface brief} → {@code {"interfaces":[...]}}.
     *
     * <p>FRR 문법의 {@code ifaceDocument} 가 이 출력 전용입니다
     * ({@code briefEntry : ifname addrOrUnassigned? briefField* elem*}).
     *
     * @param raw    원문 CLI 출력
     * @param vendor 벤더 (현재 사용하지 않음)
     * @return 본문
     */
    public ObjectNode parseInterfaceStatus(String raw, CliVendor vendor) {
        return guard(raw, () -> {
            ParseSession<FrrRouterLexer, FrrRouterParser> session =
                    new ParseSession<>(FrrRouterLexer::new, FrrRouterParser::new, raw);
            FrrRouterVisitor visitor = new FrrRouterVisitor(session);
            visitor.visit(session.parser().ifaceDocument());
            ObjectNode body = CliJson.object();
            body.set("interfaces", visitor.brief);
            body.put("interface_count", visitor.brief.size());
            return CliJson.attachParseInfo(body, session);
        });
    }

    /**
     * {@code ovs-vsctl show} 또는 {@code ovs-vsctl list port} → {@code {"bridges":[...]}} /
     * {@code {"ports":[...]}}.
     *
     * <p>두 출력은 진입 규칙이 다르므로 내용으로 고릅니다. 속성 레코드 구분자
     * {@code --} 나 {@code _uuid} 가 보이면 {@code list port} 로 봅니다.
     *
     * @param raw 원문 CLI 출력
     * @return 본문
     */
    public ObjectNode parseOvsTopology(String raw) {
        boolean isList = looksLikeOvsListOutput(raw);

        return guard(raw, () -> {
            ParseSession<OvsTopologyLexer, OvsTopologyParser> session =
                    new ParseSession<>(OvsTopologyLexer::new, OvsTopologyParser::new, raw);
            OvsVisitor visitor = new OvsVisitor(session);
            ObjectNode body = CliJson.object();

            if (isList) {
                visitor.visit(session.parser().listDocument());
                body.set("ports", visitor.ports);
                body.put("port_count", visitor.ports.size());
            } else {
                visitor.visit(session.parser().showDocument());
                body.set("bridges", visitor.bridges);
                body.put("bridge_count", visitor.bridges.size());
            }
            return CliJson.attachParseInfo(body, session);
        });
    }

    /**
     * {@code show vlan brief} → {@code {"vlans":[...]}}.
     *
     * @param raw 원문 CLI 출력
     * @return 본문
     */
    public ObjectNode parseSwitchVlan(String raw) {
        return guard(raw, () -> {
            ParseSession<SwitchTopologyLexer, SwitchTopologyParser> session =
                    new ParseSession<>(SwitchTopologyLexer::new, SwitchTopologyParser::new, raw);
            SwitchVisitor visitor = new SwitchVisitor(session);
            visitor.visit(session.parser().vlanDocument());
            ObjectNode body = CliJson.object();
            body.set("vlans", visitor.vlans);
            body.put("vlan_count", visitor.vlans.size());
            return CliJson.attachParseInfo(body, session);
        });
    }

    /**
     * {@code show interfaces switchport} → {@code {"ports":[...]}}.
     *
     * @param raw 원문 CLI 출력
     * @return 본문
     */
    public ObjectNode parseSwitchPorts(String raw) {
        return guard(raw, () -> {
            ParseSession<SwitchTopologyLexer, SwitchTopologyParser> session =
                    new ParseSession<>(SwitchTopologyLexer::new, SwitchTopologyParser::new, raw);
            SwitchVisitor visitor = new SwitchVisitor(session);
            visitor.visit(session.parser().portDocument());
            ObjectNode body = CliJson.object();
            body.set("ports", visitor.ports);
            body.put("port_count", visitor.ports.size());
            return CliJson.attachParseInfo(body, session);
        });
    }

    /**
     * {@code show running-config} → {@code {"ports":[...]}}.
     *
     * @param raw 원문 CLI 출력
     * @return 본문
     */
    public ObjectNode parseRunningConfig(String raw) {
        return guard(raw, () -> {
            ParseSession<SwitchTopologyLexer, SwitchTopologyParser> session =
                    new ParseSession<>(SwitchTopologyLexer::new, SwitchTopologyParser::new, raw);
            SwitchVisitor visitor = new SwitchVisitor(session);
            visitor.visit(session.parser().runningDocument());
            ObjectNode body = CliJson.object();
            body.set("ports", visitor.ports);
            body.put("port_count", visitor.ports.size());
            return CliJson.attachParseInfo(body, session);
        });
    }

    /**
     * {@code nft list ruleset} → {@code {"tables":[...]}}.
     *
     * @param raw 원문 CLI 출력
     * @return 본문
     */
    public ObjectNode parseFirewallRules(String raw) {
        return guard(raw, () -> {
            ParseSession<NftablesRuleLexer, NftablesRuleParser> session =
                    new ParseSession<>(NftablesRuleLexer::new, NftablesRuleParser::new, raw);
            NftablesVisitor visitor = new NftablesVisitor(session);
            visitor.visit(session.parser().rulesetDocument());
            return finishFirewall(visitor, session);
        });
    }

    /**
     * {@code nft -a list chain <family> <table> <chain>} → {@code {"tables":[...]}}.
     *
     * <p>테이블 선언 없이 체인만 오는 출력이라 family/table 을 알 수 없습니다.
     *
     * @param raw 원문 CLI 출력
     * @return 본문
     */
    public ObjectNode parseFirewallChain(String raw) {
        return guard(raw, () -> {
            ParseSession<NftablesRuleLexer, NftablesRuleParser> session =
                    new ParseSession<>(NftablesRuleLexer::new, NftablesRuleParser::new, raw);
            NftablesVisitor visitor = new NftablesVisitor(session);
            visitor.visit(session.parser().chainDocument());
            return finishFirewall(visitor, session);
        });
    }

    /* ==================== 통합 진입점 ==================== */

    /**
     * 벤더와 조회 대상으로 알맞은 파서를 고릅니다.
     *
     * @param vendor 벤더 (null 이면 {@link CliVendor#UNKNOWN})
     * @param target 조회 대상 (null/빈 문자열이면 벤더 기본 조회)
     * @param raw    원문 CLI 출력
     * @return 본문
     */
    public ObjectNode parseQueryOutput(CliVendor vendor, String target, String raw) {
        CliVendor resolved = vendor == null ? CliVendor.UNKNOWN : vendor;
        String name = target == null ? "" : target.trim().toLowerCase(java.util.Locale.ROOT);

        switch (name) {
            case "nic":
            case "addr":
                return parseNicStatus(raw);

            case "brief":
            case "nic-brief":
                // `show ip interface brief` 와 `ip -br addr show` 를 모두 지원한다.
                // IOS 스타일 헤더(`OK?`)가 보이면 인터페이스 상태 출력이다.
                if (raw != null && raw.contains("Interface") && raw.contains("OK?")) {
                    return parseInterfaceStatus(raw, resolved);
                }
                return parseNicBrief(raw);

            case "route":
            case "route-table":
                return parseRouteStatus(raw, resolved);

            case "interface":
            case "interface-brief":
                return parseInterfaceStatus(raw, resolved);

            case "topology":
            case "ovs":
                return parseOvsTopology(raw);

            case "vlan":
                return parseSwitchVlan(raw);

            case "switchport":
            case "port":
                return parseSwitchPorts(raw);

            case "running":
            case "running-config":
                return parseRunningConfig(raw);

            case "chain":
                return parseFirewallChain(raw);

            case "ruleset":
            case "nft":
                return parseFirewallRules(raw);

            case "arp":
            case "neigh":
                return parseArpTable(raw, resolved);

            default:
                break;
        }

        // target 미지정 → 벤더별 기본 조회로 폴백
        return switch (resolved) {
            case OPEN_VSWITCH -> parseOvsTopology(raw);
            case FRR, CISCO -> parseRouteStatus(raw, resolved);
            case ARISTA -> parseSwitchVlan(raw);
            case NFTABLES -> parseFirewallRules(raw);
            case LINUX, UNKNOWN -> parseNicStatus(raw);
        };
    }

    /* ==================== 내부 도우미 ==================== */

    /** 파서 본문을 만드는 부분. 예외를 밖으로 내보내지 않기 위한 함수형 인터페이스. */
    @FunctionalInterface
    private interface BodySupplier {
        ObjectNode get();
    }

    /**
     * 파싱을 실행하고 예외를 실패 노드로 바꿉니다.
     *
     * <p>문법 오류는 예외가 아니라 {@link ParseSession} 의 오류 카운트로 들어오므로
     * 여기서 잡히는 것은 예상 밖의 런타임 오류(예: 숫자 변환 실패)뿐입니다.
     *
     * @param raw      진단용 원문
     * @param supplier 본문 생성
     * @return 본문 또는 실패 노드
     */
    private static ObjectNode guard(String raw, BodySupplier supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            return CliJson.failure(raw, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * 방화벽 결과에 체인/규칙 개수를 더합니다.
     *
     * @param visitor 방문자
     * @param session 파싱 세션
     * @return 본문
     */
    private static ObjectNode finishFirewall(NftablesVisitor visitor,
                                             ParseSession<NftablesRuleLexer, NftablesRuleParser> session) {
        int chainCount = 0;
        int ruleCount = 0;
        for (JsonNode table : visitor.tables) {
            JsonNode chains = table.get("chains");
            if (chains == null || !chains.isArray()) {
                continue;
            }
            chainCount += chains.size();
            for (JsonNode chain : chains) {
                JsonNode rules = chain.get("rules");
                if (rules != null && rules.isArray()) {
                    ruleCount += rules.size();
                }
            }
        }
        ObjectNode body = CliJson.object();
        body.set("tables", visitor.tables);
        body.put("table_count", visitor.tables.size());
        body.put("chain_count", chainCount);
        body.put("rule_count", ruleCount);
        return CliJson.attachParseInfo(body, session);
    }

    /**
     * 라우트 목록에서 등장한 프로토콜 이름을 중복 없이 모읍니다.
     *
     * @param routes 라우트 배열
     * @return 프로토콜 이름 배열
     */
    private static ArrayNode distinctProtocols(ArrayNode routes) {
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode route : routes) {
            JsonNode protocol = route.get("protocol");
            if (protocol != null && protocol.isString()) {
                String text = protocol.asString();
                if (!text.isEmpty()) {
                    seen.add(text);
                }
            }
        }
        ArrayNode array = CliJson.array();
        for (String protocol : seen) {
            array.add(protocol);
        }
        return array;
    }

    /**
     * FRR 의 라우팅 표 출력이 vtysh 스타일(라우트 코드 있음)인지 판단합니다.
     *
     * <p>이 판단은 <b>어느 문법을 쓸지 고르기 위한 사전 분기</b>라서 파싱 전에
     * 이뤄질 수밖에 없습니다. C++ Prober 의 {@code LooksLikeVtyshRouteTable} 과
     * 같은 규칙을 씁니다.
     *
     * <ol>
     *   <li>{@code Codes:} 머리말이 있으면 확정</li>
     *   <li>줄 선두 토큰이 <b>대문자 1~4자 + 선택적 {@code *><&amp;}</b> 로만
     *       구성되고, 그 뒤에 두 번째 토큰이 있으면 확정</li>
     * </ol>
     *
     * <p>커널 형식의 선두 토큰({@code default}, {@code 10.99.10.0/24})은 소문자나
     * 숫자로 시작하므로 2번에 걸리지 않습니다.
     *
     * @param raw 원문
     * @return vtysh 형식 여부
     */
    static boolean looksLikeVtyshRouteTable(String raw) {
        if (raw == null) {
            return false;
        }
        if (raw.contains("Codes:")) {
            return true;
        }
        for (String line : lines(raw)) {
            List<String> tokens = CliText.splitTokens(line);
            if (tokens.isEmpty()) {
                continue;
            }
            String first = tokens.get(0);

            int index = 0;
            while (index < first.length() && Character.isUpperCase(first.charAt(index))) {
                index++;
            }
            int letters = index;
            if (letters == 0 || letters > 4) {
                continue;
            }
            while (index < first.length() && isRouteMarker(first.charAt(index))) {
                index++;
            }
            if (index == first.length() && tokens.size() >= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * 라우트 코드 뒤에 붙는 선택/FIB/백업 마커인지 확인합니다.
     *
     * @param ch 문자
     * @return 마커 여부
     */
    private static boolean isRouteMarker(char ch) {
        return ch == '*' || ch == '>' || ch == '<' || ch == '&';
    }

    /**
     * {@code ovs-vsctl list port} 출력인지 판단합니다.
     *
     * @param raw 원문
     * @return list 출력 여부
     */
    static boolean looksLikeOvsListOutput(String raw) {
        if (raw == null) {
            return false;
        }
        String normalized = raw.replace("\r", "");
        return normalized.contains("\n--\n")
                || normalized.startsWith("--")
                || normalized.contains("_uuid");
    }

    /** 진단용: 문자열 목록을 줄 단위로 잘라 돌려줍니다. */
    static List<String> lines(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String line : raw.replace("\r", "").split("\n")) {
            out.add(line);
        }
        return out;
    }
}