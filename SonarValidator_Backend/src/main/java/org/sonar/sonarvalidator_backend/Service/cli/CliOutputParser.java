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

@Service
public final class CliOutputParser {

    /* ==================== 개별 파서 ==================== */

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

    public ObjectNode parseFirewallRules(String raw) {
        return guard(raw, () -> {
            ParseSession<NftablesRuleLexer, NftablesRuleParser> session =
                    new ParseSession<>(NftablesRuleLexer::new, NftablesRuleParser::new, raw);
            NftablesVisitor visitor = new NftablesVisitor(session);
            visitor.visit(session.parser().rulesetDocument());
            return finishFirewall(visitor, session);
        });
    }

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

    private static final org.sonar.sonarvalidator_backend.Service.cli.query.CliQueryStrategies
            QUERY_STRATEGIES =
            new org.sonar.sonarvalidator_backend.Service.cli.query.CliQueryStrategies();

    public org.sonar.sonarvalidator_backend.Service.cli.query.QueryResult parseQuery(String vendor,
                                                                                    String target,
                                                                                    String raw) {
        final CliVendor resolved = CliVendor.parse(vendor);
        final org.sonar.sonarvalidator_backend.Service.cli.query.CliQueryStrategy strategy =
                QUERY_STRATEGIES.select(target, resolved);
        final ObjectNode body = strategy.parse(this, resolved, raw);
        return new org.sonar.sonarvalidator_backend.Service.cli.query.QueryResult(
                strategy.primaryTarget(), strategy.contractKey(), body);
    }

    public ObjectNode parseQueryOutput(CliVendor vendor, String target, String raw) {
        final CliVendor resolved = (vendor == null) ? CliVendor.UNKNOWN : vendor;
        final org.sonar.sonarvalidator_backend.Service.cli.query.CliQueryStrategy strategy =
                QUERY_STRATEGIES.select(target, resolved);
        return strategy.parse(this, resolved, raw);
    }

    public static String contractKeyOf(CliVendor vendor, String target) {
        final CliVendor resolved = (vendor == null) ? CliVendor.UNKNOWN : vendor;
        // ⚠️ 계약 키는 <b>이름만</b>으로 정해집니다. 원문 모양에 따라 키가
        //    달라지면 호출자가 소비 키를 알 수 없습니다.
        return QUERY_STRATEGIES.select(target, resolved).contractKey();
    }

    /* ==================== 내부 도우미 ==================== */

    @FunctionalInterface
    private interface BodySupplier {
        ObjectNode get();
    }

    private static ObjectNode guard(String raw, BodySupplier supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            return CliJson.failure(raw, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

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

    private static boolean isRouteMarker(char ch) {
        return ch == '*' || ch == '>' || ch == '<' || ch == '&';
    }

    static boolean looksLikeOvsListOutput(String raw) {
        if (raw == null) {
            return false;
        }
        String normalized = raw.replace("\r", "");
        return normalized.contains("\n--\n")
                || normalized.startsWith("--")
                || normalized.contains("_uuid");
    }

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
