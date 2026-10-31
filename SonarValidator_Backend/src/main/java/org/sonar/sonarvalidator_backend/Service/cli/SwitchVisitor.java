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

    private static String[] splitAttr(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            return new String[] { CliText.trim(line), "" };
        }
        return new String[] { CliText.trim(line.substring(0, colon)),
                              CliText.trim(line.substring(colon + 1)) };
    }

    private static String normalizeKey(String key) {
        return CliText.trim(key).toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

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
