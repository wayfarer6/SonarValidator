package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.ArrayList;
import java.util.List;

import org.sonar.sonarvalidator_backend.Service.cli.CliJson;
import org.sonar.sonarvalidator_backend.Service.cli.CliText;
import org.sonar.sonarvalidator_backend.Service.cli.ParseSession;
import org.sonar.sonarvalidator_backend.grammar.FrrRouterBaseVisitor;
import org.sonar.sonarvalidator_backend.grammar.FrrRouterLexer;
import org.sonar.sonarvalidator_backend.grammar.FrrRouterParser;
import org.sonar.sonarvalidator_backend.grammar.FrrRouterParser.ElemContext;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class FrrRouterVisitor extends FrrRouterBaseVisitor<Void> {

    private final ParseSession<FrrRouterLexer, FrrRouterParser> session;

    public final ArrayNode routes = CliJson.array();

    public final ArrayNode brief = CliJson.array();

    public final ArrayNode details = CliJson.array();

    public FrrRouterVisitor(ParseSession<FrrRouterLexer, FrrRouterParser> session) {
        this.session = session;
    }

    // show ip route

    @Override
    public Void visitRouteItem(FrrRouterParser.RouteItemContext ctx) {
        if (ctx.routeLine() != null) {
            visitRouteLine(ctx.routeLine());
        }
        // subnetSummary(`10.0.0.0/8 is variably subnetted, 2 subnets, 2 masks`)는
        // 요약 줄이라 라우트 항목이 아니다. genericLine/blank 와 함께 버린다.
        return null;
    }

    @Override
    public Void visitRouteLine(FrrRouterParser.RouteLineContext ctx) {
        final ObjectNode route = CliJson.object();

        // ROUTECODE 는 코드 + 선택 마커(*, >, &)를 통째로 담고 있다.
        final StringBuilder rawCode = new StringBuilder();
        for (final FrrRouterParser.RouteCodeContext code : ctx.routeCode()) {
            rawCode.append(code.getText());
        }
        final String code = rawCode.toString();
        route.put("raw_code", code);
        route.put("protocol", RouteCodes.normalize(code));
        route.put("selected", code.indexOf('*') >= 0);
        route.put("fib", code.indexOf('>') >= 0);
        route.put("backup", code.indexOf('&') >= 0);

        final FrrRouterParser.DestinationContext destination = ctx.destination();
        if (destination != null) {
            // `default` 키워드와 `0.0.0.0/0` 주소는 같은 뜻이다.
            // 어느 쪽이든 문법이 전용 토큰으로 확정했으므로 비교는 토큰 존재 여부만 본다.
            final boolean isDefault = destination.DEFAULT() != null
                    || destination.DEFAULTADDR() != null;
            route.put("is_default", isDefault);
            final String prefix = isDefault ? "0.0.0.0/0" : destination.getText();
            route.put("prefix", prefix);
            route.put("destination", prefix);
        }

        // `[110/200]` → distance/metric. 판별이 아니라 확정된 값의 분해만 한다.
        if (ctx.metricBracket() != null) {
            applyMetricBracket(route, ctx.metricBracket().getText());
        }

        // 꼬리는 문법이 의미 단위(routeTail)로 나눠 두었다. visitor 는 옆 토큰을

        final ArrayNode extras = CliJson.array();
        for (final FrrRouterParser.RouteTailContext tail : ctx.routeTail()) {
            if (tail.directlyConnected() != null) {
                route.put("connected", true);
                applyInterface(route, tail.directlyConnected().interfaceTail());
            } else if (tail.viaHop() != null) {
                final String gateway = CliText.trimPunct(tail.viaHop().routeValue().getText());
                route.put("via", gateway);
                route.put("next_hop", gateway);
                applyInterface(route, tail.viaHop().interfaceTail());
            } else if (tail.weightField() != null) {
                route.put("weight", CliText.trimPunct(tail.weightField().routeValue().getText()));
            } else if (tail.routeExtras() != null) {
                final String text = CliText.trimPunct(tail.routeExtras().getText());
                if (!text.isEmpty() && extras.size() < 16) {
                    extras.add(text);
                }
            }
        }

        // 직접 연결 경로에는 게이트웨이가 없다. 기준 구현과 같이 표기를 남긴다.
        if (!route.has("next_hop") && route.path("connected").asBoolean(false)) {
            route.put("next_hop", "directly connected");
        }
        if (!extras.isEmpty()) {
            route.set("extras", extras);
        }

        routes.add(route);
        return null;
    }

    private static void applyInterface(ObjectNode route, FrrRouterParser.InterfaceTailContext tail) {
        if (tail == null || route.has("interface_name")) {
            return;
        }
        route.put("interface_name", CliText.trimPunct(tail.ifname().getText()));
    }

    private static void applyMetricBracket(ObjectNode route, String token) {
        final String inner = CliText.trimPunct(token);
        final String body = inner.startsWith("[") && inner.endsWith("]")
                ? inner.substring(1, inner.length() - 1) : inner;
        final int slash = body.indexOf('/');
        final String distance = slash >= 0 ? body.substring(0, slash) : body;
        final String metric = slash >= 0 ? body.substring(slash + 1) : "";

        if (CliText.isNumber(distance)) {
            route.put("distance", Integer.parseInt(distance));
        }
        if (CliText.isNumber(metric)) {
            route.put("metric", Integer.parseInt(metric));
        }
        // 어느 한쪽이라도 숫자가 아니면(예: 슬래시가 두 번 나오는 경우) 원문을
        // 남깁니다. 확정하지 못한 값을 조용히 버리지 않기 위한 것입니다.
        if (!CliText.isNumber(distance)
                || (slash >= 0 && !CliText.isNumber(metric))) {
            route.put("metric_raw", body);
        }
    }

    // show ip interface brief

    @Override
    public Void visitIfaceItem(FrrRouterParser.IfaceItemContext ctx) {
        if (ctx.briefEntry() != null) {
            visitBriefEntry(ctx.briefEntry());
        }
        return null;
    }

    @Override
    public Void visitBriefEntry(FrrRouterParser.BriefEntryContext ctx) {
        final List<BriefEntry.Piece> pieces = new ArrayList<>();

        if (ctx.ifname() != null) {
            pieces.add(new BriefEntry.Piece(BriefEntry.Kind.NAME, ctx.ifname().getText()));
        }
        if (ctx.addrOrUnassigned() != null) {
            final FrrRouterParser.AddrOrUnassignedContext value = ctx.addrOrUnassigned();
            pieces.add(value.UNASSIGNED() != null
                    ? new BriefEntry.Piece(BriefEntry.Kind.UNASSIGNED, "unassigned")
                    : new BriefEntry.Piece(BriefEntry.Kind.ADDRESS, value.ADDR().getText()));
        }
        for (final FrrRouterParser.BriefFieldContext field : ctx.briefField()) {
            pieces.add(field.METHOD() != null
                    ? new BriefEntry.Piece(BriefEntry.Kind.METHOD, field.METHOD().getText())
                    : new BriefEntry.Piece(BriefEntry.Kind.STATUS, field.STATUSWORD().getText()));
        }

        final TokenCursor<ElemContext> cursor = TokenCursor.of(ctx, ElemContext.class);
        while (cursor.hasNext()) {
            pieces.add(new BriefEntry.Piece(classifyBriefPiece(cursor.type()), cursor.take()));
        }

        brief.add(BriefEntry.parse(pieces));
        return null;
    }

    private static BriefEntry.Kind classifyBriefPiece(int type) {
        return switch (type) {
            case FrrRouterLexer.METHOD -> BriefEntry.Kind.METHOD;
            case FrrRouterLexer.STATUSWORD -> BriefEntry.Kind.STATUS;
            case FrrRouterLexer.ADDR -> BriefEntry.Kind.ADDRESS;
            case FrrRouterLexer.UNASSIGNED -> BriefEntry.Kind.UNASSIGNED;
            default -> BriefEntry.Kind.EXTRA;
        };
    }

    // show interface <name>

    @Override
    public Void visitDetailItem(FrrRouterParser.DetailItemContext ctx) {
        if (ctx.ifaceHeader() != null) {
            details.add(newDetailFromHeader(ctx.ifaceHeader()));
        } else if (ctx.detailAttr() != null) {
            applyDetailAttr(ctx.detailAttr());
        }
        return null;
    }

    private ObjectNode newDetailFromHeader(FrrRouterParser.IfaceHeaderContext ctx) {
        final ObjectNode detail = CliJson.object();
        if (ctx.ifname() != null) {
            detail.put("name", CliText.trimPunct(ctx.ifname().getText()));
        }
        detail.set("addresses", CliJson.array());
        currentDetail = detail;
        return detail;
    }

    private ObjectNode currentDetail;

    private void applyDetailAttr(FrrRouterParser.DetailAttrContext ctx) {
        if (currentDetail == null) {
            return;
        }
        final String key = ctx.keyToken() == null ? "" : ctx.keyToken().getText();
        if (!key.isEmpty()) {
            currentDetail.put("last_attr", CliText.trimPunct(key));
        }

        final TokenCursor<ElemContext> cursor = TokenCursor.of(ctx, ElemContext.class);
        while (cursor.hasNext()) {
            if (cursor.is(FrrRouterLexer.ADDR)) {
                // 주소인 것은 문법이 ADDR 로 확정한 조각뿐이다.
                applyAddress(currentDetail, cursor.take());
            } else {
                // 나머지 꼬리는 벤더마다 자유 형식이라 원문 표기로만 남긴다.
                cursor.skip(1);
            }
        }
    }

    private static void applyAddress(ObjectNode detail, String value) {
        final String bareValue = CliText.trimPunct(value);
        final String[] bare = new String[1];
        final int[] prefixLen = new int[1];
        if (!CliText.splitCidr(bareValue, bare, prefixLen)) {
            return;
        }
        final ObjectNode address = CliJson.object();
        address.put("address", bare[0]);
        if (prefixLen[0] >= 0) {
            address.put("prefix_len", prefixLen[0]);
        }
        detail.withArray("addresses").add(address);
        if (!detail.has("ip_address")) {
            detail.put("ip_address", bare[0]);
            if (prefixLen[0] >= 0) {
                detail.put("prefix_len", prefixLen[0]);
            }
        }
    }
}
