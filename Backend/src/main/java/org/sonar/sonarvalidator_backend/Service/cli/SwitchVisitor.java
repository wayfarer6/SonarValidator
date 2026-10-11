package org.sonar.sonarvalidator_backend.Service.cli;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.sonar.sonarvalidator_backend.Service.cli.CliJson;
import org.sonar.sonarvalidator_backend.Service.cli.CliText;
import org.sonar.sonarvalidator_backend.Service.cli.ParseSession;
import org.sonar.sonarvalidator_backend.grammar.SwitchTopologyBaseVisitor;
import org.sonar.sonarvalidator_backend.grammar.SwitchTopologyLexer;
import org.sonar.sonarvalidator_backend.grammar.SwitchTopologyParser;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 스위치 CLI 출력({@code show vlan brief}, {@code show ip interface brief},
 * {@code show interfaces switchport}, {@code show running-config})을 JSON 으로 옮기는 방문자.
 *
 * <h2>문법이 나눠 준 것</h2>
 * <pre>
 *   vlanDocument    : vlanEntry 하나 = VLAN 한 행 (id/name/status/ports)
 *   briefDocument   : briefEntry 하나 = 인터페이스 한 행
 *   portDocument    : portEntry = `key: value` 또는 `interface X`
 *   runningDocument : configLine = 임의 토큰 줄
 * </pre>
 *
 * <p>{@code show running-config} 의 {@code switchport ...} 줄은
 * 키워드를 전용 토큰으로 승격해 두었다({@code SWITCHPORT}, {@code MODEWORD},
 * {@code ACCESS}, {@code TRUNK}, {@code NATIVE}, {@code VLANWORD}).
 * 따라서 {@link #applySwitchportTokens} 는 <b>토큰 타입만 보고</b> 분기한다.
 *
 * <h2>키 이름 비교에 대해</h2>
 * {@code show interfaces switchport} 의 키는 {@code Access Mode VLAN},
 * {@code Administrative Mode} 처럼 <b>여러 단어 + 대문자</b>라서 전용 토큰으로
 * 승격하기 어렵다. 이런 곳은 문법이 "키는 콜론 앞부분" 이라고 이미 확정해 주므로
 * (keyWord+ COLON elem*), 남은 판단은 소문자 정규화 비교로 처리한다.
 *
 * <p>순회는 ANTLR 이 자동 생성한 {@code SwitchTopologyBaseVisitor} 를 그대로
 * 씁니다.
 */
public final class SwitchVisitor extends SwitchTopologyBaseVisitor<Void> {

    private final ParseSession<SwitchTopologyLexer, SwitchTopologyParser> session;

    public final ArrayNode vlans = CliJson.array();
    public final ArrayNode interfaces = CliJson.array();
    public final ArrayNode ports = CliJson.array();

    private ObjectNode currentPort;

    public SwitchVisitor(ParseSession<SwitchTopologyLexer, SwitchTopologyParser> session) {
        this.session = session;
    }

    /* ==================== show vlan brief ==================== */

    @Override
    public Void visitVlanItem(SwitchTopologyParser.VlanItemContext ctx) {
        if (ctx.vlanEntry() != null) {
            addVlan(ctx.vlanEntry());
            return null;
        }
        // `show vlan brief` 는 포트 목록이 다음 줄로 이어질 수 있다.
        if (ctx.genericLine() != null && vlans.size() > 0) {
            appendVlanContinuation(ctx.genericLine());
        }
        return null;
    }

    private void addVlan(SwitchTopologyParser.VlanEntryContext ctx) {
        ObjectNode vlan = CliJson.object();
        if (ctx.vlanId() != null) {
            vlan.put("vlan_id", Integer.parseInt(ctx.vlanId().getText()));
        }
        if (ctx.vlanName() != null) {
            vlan.put("name", ctx.vlanName().getText());
        }
        if (ctx.vlanStatus() != null) {
            vlan.put("status", ctx.vlanStatus().getText());
        }

        ArrayNode portList = CliJson.array();
        if (ctx.portList() != null) {
            for (var portToken : ctx.portList().portToken()) {
                String port = CliText.trimPunct(portToken.getText());
                if (!port.isEmpty()) {
                    portList.add(port);
                }
            }
        }
        if (!portList.isEmpty()) {
            vlan.set("ports", portList);
        }
        vlans.add(vlan);
    }

    /**
     * 이어지는 줄의 <b>첫 토큰 종류</b>로 포트 목록 continuation 인지 판단한다.
     *
     * <p>문법에서 {@code VLAN0003} / {@code Eth1/1} / {@code Gi0/1} 은 모두
     * {@code PORTNAME} 또는 {@code IFNAME} 이다. 즉 "포트 모양" 판별이 이미
     * 토큰 타입에 들어 있으므로 문자를 훑을 필요가 없다.
     */
    private void appendVlanContinuation(SwitchTopologyParser.GenericLineContext ctx) {
        TokenCursor<SwitchTopologyParser.ElemContext> cursor =
                TokenCursor.of(ctx, SwitchTopologyParser.ElemContext.class);
        if (!cursor.hasNext()) {
            return;
        }
        int type = cursor.type();
        if (type != SwitchTopologyLexer.PORTNAME
                && type != SwitchTopologyLexer.IFNAME
                && type != SwitchTopologyLexer.IDENT) {
            return;
        }

        ObjectNode last = (ObjectNode) vlans.get(vlans.size() - 1);
        ArrayNode portList = last.has("ports") ? (ArrayNode) last.get("ports") : last.putArray("ports");
        while (cursor.hasNext()) {
            String port = CliText.trimPunct(cursor.take());
            if (!port.isEmpty()) {
                portList.add(port);
            }
        }
    }

    /* ==================== show ip interface brief ==================== */

    @Override
    public Void visitBriefItem(SwitchTopologyParser.BriefItemContext ctx) {
        if (ctx.briefEntry() == null) {
            return null;
        }
        SwitchTopologyParser.BriefEntryContext entry = ctx.briefEntry();

        java.util.List<BriefEntry.Piece> pieces = new java.util.ArrayList<>();
        if (entry.ifname() != null) {
            pieces.add(new BriefEntry.Piece(BriefEntry.Kind.NAME, entry.ifname().getText()));
        }
        if (entry.addrOrUnassigned() != null) {
            boolean unassigned = entry.addrOrUnassigned().UNASSIGNED() != null;
            pieces.add(new BriefEntry.Piece(
                    unassigned ? BriefEntry.Kind.UNASSIGNED : BriefEntry.Kind.ADDRESS,
                    entry.addrOrUnassigned().getText()));
        }
        for (var field : entry.briefField()) {
            pieces.add(new BriefEntry.Piece(BriefEntry.Kind.STATUS, field.getText()));
        }

        // 남은 꼬리 조각은 문법이 알려 주는 토큰 타입으로 분류한다.
        TokenCursor<SwitchTopologyParser.ElemContext> cursor =
                TokenCursor.of(entry, SwitchTopologyParser.ElemContext.class);
        while (cursor.hasNext()) {
            pieces.add(new BriefEntry.Piece(
                    classifyTail(cursor.type()), CliText.trimPunct(cursor.take())));
        }

        ObjectNode parsed = BriefEntry.parse(pieces);

        // 컬럼 헤더(`Interface IP Address Status Protocol ...`)를 데이터로 오인하지 않는다.
        String name = parsed.path("name").asString("");
        if ("Interface".equals(name) || "Address".equals(name) || "Name".equals(name)) {
            return null;
        }
        interfaces.add(parsed);
        return null;
    }

    private static BriefEntry.Kind classifyTail(int type) {
        return switch (type) {
            case SwitchTopologyLexer.ADDR -> BriefEntry.Kind.ADDRESS;
            case SwitchTopologyLexer.UNASSIGNED -> BriefEntry.Kind.UNASSIGNED;
            case SwitchTopologyLexer.STATUSWORD -> BriefEntry.Kind.STATUS;
            default -> BriefEntry.Kind.EXTRA;
        };
    }

    /* ==================== show interfaces switchport ==================== */

    @Override
    public Void visitPortItem(SwitchTopologyParser.PortItemContext ctx) {
        if (ctx.portEntry() == null) {
            // `show running-config` 의 `switchport ...` 줄은 콜론이 없어 genericLine 이다.
            if (ctx.genericLine() != null && currentPort != null) {
                applySwitchportTokens(
                        TokenCursor.of(ctx.genericLine(), SwitchTopologyParser.ElemContext.class),
                        currentPort);
            }
            return null;
        }
        return visitPortEntry(ctx.portEntry());
    }

    @Override
    public Void visitPortEntry(SwitchTopologyParser.PortEntryContext ctx) {
        // (1) `interface Ethernet1` — IOS 스타일. 새 포트 레코드 시작.
        if (ctx.INTERFACE() != null) {
            ObjectNode port = CliJson.object();
            if (ctx.ifname() != null) {
                port.put("name", CliText.trimPunct(ctx.ifname().getText()));
            }
            port.putArray("trunk_vlans");
            ports.add(port);
            currentPort = port;
            return null;
        }

        String[] kv = splitAttr(session.lineOf(ctx));
        String key = kv[0];
        String value = kv[1];
        if (key.isEmpty()) {
            return null;
        }

        // (2) Arista 는 `Name: Et2` 로 포트 블록을 시작한다.
        String normalized = normalizeKey(key);
        if ("name".equals(normalized)) {
            ObjectNode port = CliJson.object();
            port.put("name", CliText.unquote(value));
            port.putArray("trunk_vlans");
            ports.add(port);
            currentPort = port;
            return null;
        }

        // 포트 블록 이전의 전역 설정 줄(`Default switchport mode ...` 등)은 버린다.
        if (currentPort == null) {
            return null;
        }

        applyPortAttribute(normalized, value, currentPort);
        return null;
    }

    /**
     * {@code show interfaces switchport} 의 키/값을 포트 레코드에 반영한다.
     *
     * <p>키 표기 차이를 흡수한다.
     * <pre>
     *   IOS    : Access Mode VLAN: 99 / Trunking VLANs Enabled: 111,112
     *   Arista : Access Mode VLAN: 8 (VLAN8) / Trunking VLANs Enabled: ALL
     *            Administrative Mode: static access
     * </pre>
     *
     * @param key   소문자로 정규화한 키
     * @param value 콜론 뒤 원문 값
     */
    private void applyPortAttribute(String key, String value, ObjectNode port) {
        if ("switchport".equals(key)) {
            port.put("admin_enabled", "Enabled".equals(CliText.unquote(value)));
            return;
        }
        if (key.contains("administrative mode") && !key.contains("native")) {
            String mode = CliText.unquote(value);
            if (mode.contains("trunk")) {
                port.put("mode", "trunk");
            } else if (mode.contains("access")) {
                port.put("mode", "access");
            } else {
                port.put("mode", mode);
            }
            return;
        }
        if ("operational mode".equals(key)) {
            port.put("operational_mode", CliText.unquote(value));
            return;
        }
        if (key.contains("access mode vlan")) {
            // `8 (VLAN8)` 에서 괄호 앞 숫자만 VLAN ID 다.
            // 괄호 안 이름의 숫자까지 이어 붙이면 88 이 되는 버그가 생긴다.
            int paren = value.indexOf('(');
            String idText = CliText.trim(paren < 0 ? value : value.substring(0, paren));
            if (CliText.isNumber(idText)) {
                port.put("access_vlan", Integer.parseInt(idText));
            }
            int close = paren < 0 ? -1 : value.indexOf(')', paren);
            if (paren >= 0 && close > paren + 1) {
                port.put("access_vlan_name", value.substring(paren + 1, close));
            }
            return;
        }
        if (key.contains("trunking vlans enabled") || key.contains("trunking vlans active")) {
            String trunkText = CliText.unquote(value);
            if ("ALL".equals(trunkText) || trunkText.isEmpty()) {
                port.put("trunk_vlans_all", "ALL".equals(trunkText));
                port.putArray("trunk_vlans");
            } else {
                port.set("trunk_vlans", vlanArray(trunkText));
            }
            return;
        }
        if (key.contains("administrative trunking encapsulation")) {
            port.put("encapsulation", CliText.unquote(value));
            return;
        }
        if (!value.isEmpty()) {
            port.put(key, CliText.unquote(value));
        }
    }

    /* ==================== show running-config ==================== */

    @Override
    public Void visitConfigLine(SwitchTopologyParser.ConfigLineContext ctx) {
        TokenCursor<SwitchTopologyParser.ElemContext> cursor =
                TokenCursor.of(ctx, SwitchTopologyParser.ElemContext.class);
        if (!cursor.hasNext()) {
            return null;
        }

        if (cursor.is(SwitchTopologyLexer.INTERFACE)) {
            cursor.skip(1);
            if (!cursor.hasNext()) {
                return null;
            }
            ObjectNode port = CliJson.object();
            port.put("name", CliText.trimPunct(cursor.take()));
            port.putArray("trunk_vlans");
            ports.add(port);
            currentPort = port;
            return null;
        }

        applySwitchportTokens(cursor, currentPort);
        return null;
    }

    /**
     * {@code switchport} 설정 줄을 직전 포트 레코드에 반영한다.
     *
     * <p>문법이 키워드를 전용 토큰으로 승격해 두었으므로 <b>단어 비교 없이</b>
     * 타입만 보고 분기한다.
     * <pre>
     *   switchport mode trunk              → SWITCHPORT MODEWORD TRUNK
     *   switchport mode access             → SWITCHPORT MODEWORD ACCESS
     *   switchport access vlan 99          → SWITCHPORT ACCESS VLANWORD NUMBER
     *   switchport trunk allowed vlan 1,2  → SWITCHPORT TRUNK ATTRWORD VLANWORD NUMBER COMMA NUMBER
     *   switchport trunk native vlan 5     → SWITCHPORT TRUNK NATIVE VLANWORD NUMBER
     * </pre>
     */
    private void applySwitchportTokens(TokenCursor<SwitchTopologyParser.ElemContext> cursor,
                                       ObjectNode port) {
        if (port == null || !cursor.is(SwitchTopologyLexer.SWITCHPORT)) {
            return;
        }
        cursor.skip(1);
        if (!cursor.hasNext()) {
            return;
        }

        int kind = cursor.type();

        // `switchport mode trunk|access`
        if (kind == SwitchTopologyLexer.MODEWORD) {
            cursor.skip(1);
            if (cursor.hasNext()) {
                port.put("mode", CliText.trimPunct(cursor.take()));
            }
            return;
        }

        // `switchport access vlan N`
        if (kind == SwitchTopologyLexer.ACCESS) {
            cursor.skip(1);
            if (cursor.is(SwitchTopologyLexer.VLANWORD)) {
                cursor.skip(1);
            }
            if (cursor.hasNext()) {
                String id = CliText.trimPunct(cursor.take());
                if (CliText.isNumber(id)) {
                    port.put("access_vlan", Integer.parseInt(id));
                }
            }
            return;
        }

        // `switchport trunk [native] vlan A,B`
        if (kind == SwitchTopologyLexer.TRUNK) {
            cursor.skip(1);
            boolean nativeVlan = false;
            if (cursor.is(SwitchTopologyLexer.NATIVE)) {
                nativeVlan = true;
                cursor.skip(1);
            }
            if (cursor.is(SwitchTopologyLexer.VLANWORD)) {
                cursor.skip(1);
            }

            // VLAN 번호는 NUMBER 토큰이므로 쉼표 위치를 따질 필요가 없다.
            ArrayNode numbers = CliJson.array();
            while (cursor.hasNext()) {
                if (cursor.is(SwitchTopologyLexer.NUMBER)) {
                    numbers.add(Integer.parseInt(cursor.take()));
                } else {
                    cursor.skip(1);
                }
            }
            if (nativeVlan) {
                if (numbers.size() > 0) {
                    port.put("native_vlan", numbers.get(0).asInt());
                }
            } else {
                port.set("trunk_vlans", numbers);
            }
        }
    }

    /* ==================== 도우미 ==================== */

    /**
     * {@code "Access Mode VLAN: 8 (VLAN8)"} 를 키/값으로 가른다.
     *
     * <p>문법이 {@code keyWord+ COLON elem*} 로 "키는 콜론 앞" 이라고 확정해 주므로
     * 첫 콜론만 기준으로 자르면 된다.
     *
     * @return 길이 2 배열 {키, 값}
     */
    private static String[] splitAttr(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            return new String[] { CliText.trim(line), "" };
        }
        return new String[] { CliText.trim(line.substring(0, colon)),
                              CliText.trim(line.substring(colon + 1)) };
    }

    /** 키 비교용 소문자 정규화. 공백을 하나로 접는다. */
    private static String normalizeKey(String key) {
        return CliText.trim(key).toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** {@code "111,112"} → VLAN 번호 배열. 값은 이미 콜론 뒤 원문이다. */
    private static ArrayNode vlanArray(String value) {
        ArrayNode array = CliJson.array();
        String[] parts = value.split("[,\\s]+");
        for (String part : parts) {
            String id = CliText.trim(part);
            if (CliText.isNumber(id)) {
                array.add(Integer.parseInt(id));
            }
        }
        return array;
    }
}