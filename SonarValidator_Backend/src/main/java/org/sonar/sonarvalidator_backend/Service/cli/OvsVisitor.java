package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.ArrayList;
import java.util.List;

import org.sonar.sonarvalidator_backend.Service.cli.CliJson;
import org.sonar.sonarvalidator_backend.Service.cli.CliText;
import org.sonar.sonarvalidator_backend.Service.cli.ParseSession;
import org.sonar.sonarvalidator_backend.grammar.OvsTopologyBaseVisitor;
import org.sonar.sonarvalidator_backend.grammar.OvsTopologyLexer;
import org.sonar.sonarvalidator_backend.grammar.OvsTopologyParser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class OvsVisitor extends OvsTopologyBaseVisitor<Void> {

    private final ParseSession<OvsTopologyLexer, OvsTopologyParser> session;

    public final ArrayNode bridges = CliJson.array();
    public final ArrayNode ports = CliJson.array();
    public final ArrayNode flows = CliJson.array();

    private ObjectNode currentBridge;
    private ObjectNode currentPort;
    private ObjectNode currentIface;
    private int bridgeIndent;
    private int portIndent;
    private int ifaceIndent;

    private ObjectNode currentPortList;

    public OvsVisitor(ParseSession<OvsTopologyLexer, OvsTopologyParser> session) {
        this.session = session;
    }

    /* ==================== ovs-vsctl show ==================== */

    @Override
    public Void visitShowItem(OvsTopologyParser.ShowItemContext ctx) {
        int indent = session.indentOf(ctx);

        if (ctx.bridgeLine() != null) {
            startBridge(ctx.bridgeLine(), indent);
            return null;
        }
        if (ctx.portLine() != null) {
            startPort(ctx.portLine(), indent);
            return null;
        }
        if (ctx.ifaceLine() != null) {
            startIface(ctx.ifaceLine(), indent);
            return null;
        }
        if (ctx.attrLine() != null) {
            applyAttr(ctx.attrLine(), indent);
        }
        return null;
    }

    private void startBridge(OvsTopologyParser.BridgeLineContext ctx, int indent) {
        ObjectNode bridge = CliJson.object();
        bridge.put("name", nameOf(ctx.nameToken()));
        bridge.putArray("ports");
        bridges.add(bridge);

        currentBridge = bridge;
        currentPort = null;
        currentIface = null;
        bridgeIndent = indent;
        portIndent = 0;
        ifaceIndent = 0;
    }

    private void startPort(OvsTopologyParser.PortLineContext ctx, int indent) {
        if (currentBridge == null) {
            return;
        }
        ObjectNode port = CliJson.object();
        port.put("name", nameOf(ctx.nameToken()));
        port.putArray("trunks");
        port.putArray("interfaces");
        // C++ 는 tag 를 배열로 초기화한 뒤 `tag: N` 이 오면 단일 정수로 덮어쓴다.
        port.putArray("tag");

        currentBridge.withArray("ports").add(port);
        currentPort = port;
        currentIface = null;
        portIndent = indent;
        ifaceIndent = 0;
    }

    private void startIface(OvsTopologyParser.IfaceLineContext ctx, int indent) {
        if (currentPort == null) {
            return;
        }
        ObjectNode iface = CliJson.object();
        iface.put("name", nameOf(ctx.nameToken()));
        currentPort.withArray("interfaces").add(iface);
        currentIface = iface;
        ifaceIndent = indent;
    }

    private void applyAttr(OvsTopologyParser.AttrLineContext ctx, int indent) {
        ObjectNode target = null;
        if (currentIface != null && indent >= ifaceIndent) {
            target = currentIface;
        } else if (currentPort != null && indent >= portIndent) {
            target = currentPort;
        } else if (currentBridge != null && indent >= bridgeIndent) {
            target = currentBridge;
        }
        if (target == null) {
            return;
        }

        OvsTopologyParser.AttrKeyContext keyCtx = ctx.attrKey();
        OvsTopologyParser.AttrValueContext valueCtx = ctx.attrValue();

        // 값의 종류는 문법이 이미 확정했다.
        List<Integer> vlans = vlanNumbersOf(valueCtx);
        String value = vlans != null ? "" : scalarOf(valueCtx);

        switch (keyCtx.getStart().getType()) {
            case OvsTopologyLexer.TAG -> {
                // `tag: 141` 은 단일 정수, `tag: []` 는 값 없음 → 배열 유지
                if (!vlans.isEmpty()) {
                    target.put("tag", vlans.get(0));
                }
            }
            case OvsTopologyLexer.TRUNKS -> target.set("trunks", vlanArrayOf(vlans));
            case OvsTopologyLexer.VLANMODE -> target.put("vlan_mode", CliText.unquote(value));
            case OvsTopologyLexer.TYPEWORD -> target.put("type", CliText.unquote(value));
            case OvsTopologyLexer.NAMEWORD -> target.put("name", CliText.unquote(value));
            case OvsTopologyLexer.FAILMODE -> target.put("fail_mode", CliText.unquote(value));
            case OvsTopologyLexer.STPENABLE ->
                    target.put("stp_enable", "true".equals(CliText.unquote(value)));
            default -> {
                // 승격되지 않은 일반 키. 키 이름은 attrKey 텍스트를 그대로 쓴다.
                String key = keyCtx.getText();
                if (vlans != null) {
                    target.set(key, vlanArrayOf(vlans));
                } else if (!value.isEmpty()) {
                    target.put(key, CliText.unquote(value));
                }
            }
        }
    }

    /* ==================== ovs-vsctl list port ==================== */

    @Override
    public Void visitListItem(OvsTopologyParser.ListItemContext ctx) {
        if (ctx.recordSep() != null || ctx.blank() != null) {
            currentPortList = null;
            return null;
        }
        OvsTopologyParser.ListRecordContext record = ctx.listRecord();
        if (record == null) {
            return null;
        }

        OvsTopologyParser.AttrKeyContext keyCtx = record.attrKey();
        String key = keyCtx.getText();

        if (OvsTopologyLexer.ATTRWORD == keyCtx.getStart().getType() && "_uuid".equals(key)
                && currentPortList != null) {
            currentPortList = null;
        }

        if (currentPortList == null) {
            currentPortList = CliJson.object();
            ports.add(currentPortList);
        }

        OvsTopologyParser.AttrValueContext valueCtx = record.attrValue();
        List<Integer> vlans = vlanNumbersOf(valueCtx);
        String value = vlans != null ? "" : scalarOf(valueCtx);

        if (OvsTopologyLexer.NAMEWORD == keyCtx.getStart().getType()) {
            currentPortList.put("name", CliText.unquote(value));
        } else if (OvsTopologyLexer.TAG == keyCtx.getStart().getType()) {
            if (!vlans.isEmpty()) {
                currentPortList.put("tag", vlans.get(0));
            }
        } else if (OvsTopologyLexer.TRUNKS == keyCtx.getStart().getType()) {
            currentPortList.set("trunks", vlanArrayOf(vlans));
        } else if (OvsTopologyLexer.VLANMODE == keyCtx.getStart().getType()) {
            currentPortList.put("vlan_mode", CliText.unquote(value));
        } else if ("_uuid".equals(key)) {
            // C++ 는 키 이름을 그대로 쓰지만 스키마상 uuid 로 노출한다.
            currentPortList.put("uuid", CliText.unquote(value));
        } else if (vlans != null) {
            currentPortList.set(key, vlanArrayOf(vlans));
        } else if (!value.isEmpty()) {
            currentPortList.put(key, CliText.unquote(value));
        }
        return null;
    }

    /* ==================== ovs-ofctl dump-flows ==================== */

    @Override
    public Void visitFlowItem(OvsTopologyParser.FlowItemContext ctx) {
        OvsTopologyParser.FlowLineContext line = ctx.flowLine();
        if (line == null) {
            return null;
        }

        ObjectNode flow = CliJson.object();
        flow.put("raw", CliText.trim(session.lineOf(line)));

        List<String> bareTerms = new ArrayList<>();

        for (int i = 0; i < line.getChildCount(); i++) {
            JsonNode ignored = null;
            var child = line.getChild(i);
            if (!(child instanceof org.antlr.v4.runtime.tree.TerminalNode token)) {
                continue;
            }
            int type = token.getSymbol().getType();
            if (type == OvsTopologyLexer.COMMA) {
                continue;
            }
            if (type != OvsTopologyLexer.FLOWTOKEN) {
                // `ip`, `tcp` 처럼 `=` 가 없는 매칭 조건
                String text = token.getText();
                if (!text.isBlank()) {
                    bareTerms.add(text);
                }
                continue;
            }

            String piece = token.getText();
            int eq = piece.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = piece.substring(0, eq);
            String value = piece.substring(eq + 1);
            switch (key) {
                case "actions" -> flow.put("actions", value);
                case "n_packets" -> flow.put("packets", value);
                case "n_bytes" -> flow.put("bytes", value);
                case "table" -> flow.put("table", value);
                case "priority" -> flow.put("priority", value);
                default -> {
                    ObjectNode pairs = flow.has("match_pairs")
                            ? (ObjectNode) flow.get("match_pairs")
                            : flow.withObject("match_pairs");
                    pairs.put(key, value);
                }
            }
        }

        if (!bareTerms.isEmpty()) {
            flow.put("match", String.join(" ", bareTerms));
        }

        // 목적지/입력 포트 기반 매칭을 꺼내기 쉽게 최상위로 승격한다.
        if (flow.has("match_pairs")) {
            ObjectNode pairs = (ObjectNode) flow.get("match_pairs");
            for (String key : new String[] { "nw_src", "nw_dst", "ip", "in_port", "dl_type" }) {
                if (pairs.has(key)) {
                    flow.set(key, pairs.get(key));
                }
            }
        }

        flows.add(flow);
        return null;
    }

    /* ==================== 값 추출 도우미 ==================== */

    private static List<Integer> vlanNumbersOf(OvsTopologyParser.AttrValueContext value) {
        if (value == null || value.vlanList() == null) {
            return null;
        }
        List<Integer> numbers = new ArrayList<>();
        for (var number : value.vlanList().NUMBER()) {
            numbers.add(Integer.parseInt(number.getText()));
        }
        return numbers;
    }

    private static String scalarOf(OvsTopologyParser.AttrValueContext value) {
        if (value == null || value.scalarValue() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var token : value.scalarValue().valueToken()) {
            sb.append(token.getText());
        }
        return sb.toString();
    }

    private static ArrayNode vlanArrayOf(List<Integer> vlans) {
        ArrayNode array = CliJson.array();
        if (vlans != null) {
            for (int vlan : vlans) {
                array.add(vlan);
            }
        }
        return array;
    }

    private static String nameOf(OvsTopologyParser.NameTokenContext ctx) {
        return ctx == null ? "" : CliText.unquote(ctx.getText());
    }
}
