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

/**
 * {@code FrrRouter} 문법의 파스 트리를 중립 JSON 으로 옮깁니다.
 * FRR 라우터와 Cisco IOS-XE 라우터가 같은 문법을 공유합니다.
 *
 * <h2>문법이 판별하는 것</h2>
 * <ul>
 *   <li>{@code routeCode : ROUTECODE} — {@code O>*}, {@code C} 처럼
 *       코드+선택 마커를 <b>한 토큰</b>으로 잡습니다. 그래서
 *       {@code NormalizeRouteCode} 가 다시 토큰을 쪼갤 필요가 없습니다.</li>
 *   <li>{@code destination : DEFAULT | ADDR} — 기본 경로 여부가 파스 트리에서
 *       확정됩니다.</li>
 *   <li>{@code addrOrUnassigned : ADDR | UNASSIGNED} — 브리프 줄의 두 번째
 *       열이 주소인지 {@code unassigned} 인지 확정됩니다.</li>
 *   <li>{@code briefField : METHOD | STATUSWORD} — 상태 단어와 획득 방법이
 *       구분됩니다.</li>
 *   <li>{@code METRICBRACKET} — {@code [110/200]} 을 통째로 잡습니다.</li>
 * </ul>
 *
 * <p>따라서 여기에는 "이 토큰이 대시(-)인가", "세 글자 코드인가" 같은 판별이
 * 없습니다.
 *
 * <p>순회는 ANTLR 이 자동 생성한 {@code FrrRouterBaseVisitor} 를 그대로 씁니다.
 * 생성된 기본 구현이 자식 규칙을 알아서 걷기 때문에, 여기서는 의미가 있는
 * 규칙의 방문 메서드만 덮어씁니다.
 */
public class FrrRouterVisitor extends FrrRouterBaseVisitor<Void> {

    private final ParseSession<FrrRouterLexer, FrrRouterParser> session;

    /** {@code show ip route} 결과. {@code route_status.routes[]} 로 쓰입니다. */
    public final ArrayNode routes = CliJson.array();

    /** {@code show ip interface brief} 결과. */
    public final ArrayNode brief = CliJson.array();

    /** {@code show interface <name>} 결과. 상세 인터페이스 레코드. */
    public final ArrayNode details = CliJson.array();

    /**
     * @param session 파싱 세션
     */
    public FrrRouterVisitor(ParseSession<FrrRouterLexer, FrrRouterParser> session) {
        this.session = session;
    }

    // ==================================================================
    // show ip route
    // ==================================================================

    /**
     * {@code routeItem} 하나를 처리합니다.
     *
     * @param ctx routeItem 컨텍스트
     */
    @Override
    public Void visitRouteItem(FrrRouterParser.RouteItemContext ctx) {
        if (ctx.routeLine() != null) {
            visitRouteLine(ctx.routeLine());
        }
        // subnetSummary(`10.0.0.0/8 is variably subnetted, 2 subnets, 2 masks`)는
        // 요약 줄이라 라우트 항목이 아니다. genericLine/blank 와 함께 버린다.
        return null;
    }

    /**
     * {@code O>* 10.20.111.0/24 [110/200] via 10.99.10.5, eth0, weight 1, 00:17:25}
     * {@code O   10.99.10.0/24 [110/100] is directly connected, eth0, weight 1, 00:17:35}
     * {@code S*  0.0.0.0/0 [1/0] via 192.168.122.1}
     *
     * @param ctx 라우트 줄 컨텍스트
     */
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
        // 세지 않고, 각 항목이 무엇인지 파스 트리에서 그대로 읽는다.
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

    /**
     * `via <hop>, eth0` / `directly connected, eth0` 의 인터페이스명을 넣습니다.
     *
     * @param route 대상 라우트
     * @param tail  인터페이스 꼬리 (없을 수 있다)
     */
    private static void applyInterface(ObjectNode route, FrrRouterParser.InterfaceTailContext tail) {
        if (tail == null || route.has("interface_name")) {
            return;
        }
        route.put("interface_name", CliText.trimPunct(tail.ifname().getText()));
    }

    /**
     * {@code [110/200]} 을 administrative distance 와 metric 으로 나눕니다.
     *
     * @param route 대상 라우트
     * @param token 브래킷 토큰 텍스트
     */
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

    // ==================================================================
    // show ip interface brief
    // ==================================================================

    /**
     * {@code ifaceItem} 하나를 처리합니다.
     *
     * @param ctx ifaceItem 컨텍스트
     */
    @Override
    public Void visitIfaceItem(FrrRouterParser.IfaceItemContext ctx) {
        if (ctx.briefEntry() != null) {
            visitBriefEntry(ctx.briefEntry());
        }
        return null;
    }

    /**
     * {@code eth0  up  up} / {@code GigabitEthernet1  192.168.122.254  YES  NVRAM  up  up}
     *
     * <p>조각의 의미는 문법이 정해 둡니다. 여기서는 그것을
     * {@link BriefEntry.Kind} 로 옮기기만 합니다.
     *
     * @param ctx 브리프 줄 컨텍스트
     */
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

    /**
     * 브리프 줄 꼬리 조각의 의미를 문법 토큰 타입으로 판정합니다.
     *
     * <p>{@code method}/{@code status} 토큰으로 이미 분류된 것은 파스 트리에서
     * 직접 읽으므로, 여기까지 오는 것은 벤더가 덧붙인 열이나 형식이 다른
     * 표기뿐입니다.
     *
     * @param type 토큰 타입
     * @return 의미
     */
    private static BriefEntry.Kind classifyBriefPiece(int type) {
        return switch (type) {
            case FrrRouterLexer.METHOD -> BriefEntry.Kind.METHOD;
            case FrrRouterLexer.STATUSWORD -> BriefEntry.Kind.STATUS;
            case FrrRouterLexer.ADDR -> BriefEntry.Kind.ADDRESS;
            case FrrRouterLexer.UNASSIGNED -> BriefEntry.Kind.UNASSIGNED;
            default -> BriefEntry.Kind.EXTRA;
        };
    }

    // ==================================================================
    // show interface <name>
    // ==================================================================

    /**
     * {@code detailItem} 하나를 처리합니다.
     *
     * @param ctx detailItem 컨텍스트
     */
    @Override
    public Void visitDetailItem(FrrRouterParser.DetailItemContext ctx) {
        if (ctx.ifaceHeader() != null) {
            details.add(newDetailFromHeader(ctx.ifaceHeader()));
        } else if (ctx.detailAttr() != null) {
            applyDetailAttr(ctx.detailAttr());
        }
        return null;
    }

    /**
     * {@code Interface eth0 is up, line protocol is up}
     *
     * @param ctx 헤더 컨텍스트
     * @return 새 상세 레코드
     */
    private ObjectNode newDetailFromHeader(FrrRouterParser.IfaceHeaderContext ctx) {
        final ObjectNode detail = CliJson.object();
        if (ctx.ifname() != null) {
            detail.put("name", CliText.trimPunct(ctx.ifname().getText()));
        }
        detail.set("addresses", CliJson.array());
        currentDetail = detail;
        return detail;
    }

    /** 현재 채우고 있는 상세 레코드. */
    private ObjectNode currentDetail;

    /**
     * {@code MTU 1500 bytes, BW 10000000 Kbit/sec} / {@code Hardware is ...}
     *
     * <p>키가 어휘로 확정되지 않는 줄이라 꼬리는 자유 조각입니다. 다만 방문자는
     * "이 조각이 MAC 처럼 생겼는가" 를 묻지 않고 <b>문법이 MAC/ADDR 로
     * 토큰화한 조각만</b> 꺼냅니다.
     *
     * @param ctx 상세 속성 컨텍스트
     */
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

    /**
     * CIDR 문자열을 주소 배열과 요약 필드에 넣습니다.
     *
     * @param detail 대상 상세 레코드
     * @param value  주소 (CIDR 포함)
     */
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