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

/**
 * {@code ovs-vsctl show} / {@code ovs-vsctl list port} / {@code ovs-ofctl dump-flows}
 * 출력을 JSON 으로 옮기는 방문자.
 *
 * <p>역할 분담은 C++ 기준 구현({@code components/parser/cli_output_parser.cpp})과 같다.
 * 다만 이 클래스는 <b>문자열을 다시 훑지 않는다</b>. 값의 종류(단일 VLAN 번호,
 * VLAN 번호 목록, 문자열)는 이미 문법이 확정해서 내려준다.
 *
 * <pre>
 *   attrLine : attrKey COLON attrValue NEWLINE
 *   attrValue: vlanList | scalarValue?
 * </pre>
 *
 * <p>따라서 이 방문자는
 * <ul>
 *   <li>{@code attrValue().vlanList()} 가 있으면 {@code NUMBER} 토큰을 그대로 번호로 읽고,</li>
 *   <li>{@code attrKey()} 의 <b>토큰 타입</b>으로 어느 필드인지 분기하며,</li>
 *   <li>남은 텍스트 조작은 {@link CliText} 의 순수 포맷 함수만 쓴다.</li>
 * </ul>
 *
 * <h2>계층 복원</h2>
 * {@code ovs-vsctl show} 는 들여쓰기로 계층을 표현한다. 문법이 들여쓰기 깊이를
 * 강제하면 탭/스페이스 혼용에서 깨지므로, 각 줄 <b>첫 토큰의 컬럼</b>을 읽어
 * 깊이를 비교한다. {@code WS} 를 skip 해도 토큰의 {@code charPositionInLine} 은
 * 보존되기 때문에 가능한 방법이다.
 *
 * <p>순회는 ANTLR 이 자동 생성한 {@code OvsTopologyBaseVisitor} 를 그대로 씁니다.
 */
public final class OvsVisitor extends OvsTopologyBaseVisitor<Void> {

    /** 포트 객체의 초기 {@code tag} 값(배열)과 실제 {@code tag: N} 이 만나는 지점을 맞추기 위한 표식. */
    private final ParseSession<OvsTopologyLexer, OvsTopologyParser> session;

    public final ArrayNode bridges = CliJson.array();
    public final ArrayNode ports = CliJson.array();
    public final ArrayNode flows = CliJson.array();

    // --- 들여쓰기 기반 현재 위치 ---
    private ObjectNode currentBridge;
    private ObjectNode currentPort;
    private ObjectNode currentIface;
    private int bridgeIndent;
    private int portIndent;
    private int ifaceIndent;

    // --- list port 레코드 누적 ---
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

    /**
     * 속성 줄 적용. 대상은 들여쓰기 깊이로 고른다.
     * 가장 안쪽(인터페이스)부터 검사해 조건에 맞는 첫 대상을 쓴다.
     */
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

    /**
     * 레코드 경계마다 새 객체를 시작한다.
     *
     * <p>{@code ovs-vsctl} 은 두 가지 구분 방식을 쓴다.
     * <ul>
     *   <li>{@code --} 로 구분 (구버전/일부 옵션)</li>
     *   <li><b>빈 줄</b>로 구분 (배포판 기본)</li>
     * </ul>
     * 빈 줄을 처리하지 않으면 모든 레코드가 한 객체로 합쳐진다.
     * 구분자가 아예 없고 {@code _uuid} 가 다시 나오는 출력 형태도 방어한다.
     */
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

    /**
     * {@code cookie=0x0, duration=1.2s, table=0, n_packets=0, ..., actions=drop}
     * 형태의 줄을 파싱한다.
     *
     * <p>문법에서 {@code FLOWTOKEN : [a-zA-Z_]+ '=' [^ \t\r\n,]+} 이므로
     * {@code key=value} 조각은 하나의 토큰이고, 쉼표는 별도 토큰이다.
     * 따라서 토큰을 순서대로 훑기만 하면 되고, 콤마 분해 문자열 처리가 필요 없다.
     */
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

    /**
     * {@code vlanList} 이면 VLAN 번호 목록을, 아니면 {@code null} 을 돌려준다.
     * {@code null} 과 "빈 목록" 을 구분하는 것이 중요하다.
     * {@code trunks: []} 는 빈 목록이고, {@code type: internal} 은 목록이 아니다.
     */
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

    /** 스칼라 값. 토큰 텍스트를 이어 붙인다(사이에 공백 없음). */
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

    /** {@code QUOTED} / {@code ATTRWORD} / {@code WORD} 이름 토큰에서 따옴표를 제거한다. */
    private static String nameOf(OvsTopologyParser.NameTokenContext ctx) {
        return ctx == null ? "" : CliText.unquote(ctx.getText());
    }
}