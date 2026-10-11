package org.sonar.sonarvalidator_backend.Service.cli;

import org.sonar.sonarvalidator_backend.Service.cli.CliJson;
import org.sonar.sonarvalidator_backend.Service.cli.CliText;
import org.sonar.sonarvalidator_backend.Service.cli.ParseSession;
import org.sonar.sonarvalidator_backend.grammar.IpAddrBaseVisitor;
import org.sonar.sonarvalidator_backend.grammar.IpAddrLexer;
import org.sonar.sonarvalidator_backend.grammar.IpAddrParser;
import org.sonar.sonarvalidator_backend.grammar.IpAddrParser.ElemContext;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code IpAddr} 문법의 파스 트리를 중립 JSON 으로 옮깁니다.
 *
 * <h2>문법이 판별하고, visitor 는 옮기기만 한다</h2>
 * <p>이 visitor 에는 "이 토큰이 MAC 처럼 생겼는가", "이 단어가 키워드인가" 같은
 * <b>문자열 추측이 없습니다.</b> 그 판별은 전부 문법이 전용 토큰으로 끝내 두었고,
 * visitor 는 토큰 타입 또는 파스 트리 접근자만 봅니다.
 *
 * <table border="1">
 *   <caption>문법이 제공하는 판별</caption>
 *   <tr><th>문법 요소</th><th>visitor 가 얻는 것</th></tr>
 *   <tr><td>{@code attrLead : LINK|INET|INET6|LIFETIME}</td>
 *       <td>속성 줄의 종류 (분기 하나로 끝)</td></tr>
 *   <tr><td>{@code ADDR4} / {@code ADDR6} 토큰</td>
 *       <td>주소 패밀리 (inet/inet6) — 추측 불필요</td></tr>
 *   <tr><td>{@code MAC} / {@code MACDOTTED} 토큰</td>
 *       <td>하드웨어 주소 위치와 표기법</td></tr>
 *   <tr><td>{@code FLAGS} 토큰 ({@code <UP,LOWER_UP>})</td>
 *       <td>인터페이스 플래그 — 텍스트 앞뒤 문자 검사 불필요</td></tr>
 *   <tr><td>{@code GLOBAL} / {@code SCOPEWORD} / {@code ADDRFLAG}</td>
 *       <td>{@code scope global eth1.131} 에서 인터페이스명이 붙는지 여부</td></tr>
 *   <tr><td>{@code VIA/DEV/PROTO/METRIC/SRC/SCOPE/TABLE/LINKDOWN}</td>
 *       <td>라우트 키 — 문자열 비교 대신 switch</td></tr>
 *   <tr><td>{@code MTU/QDISC/STATE/QLEN/GROUP/NETNSID}</td>
 *       <td>헤더의 키/값 쌍</td></tr>
 *   <tr><td>{@code AGE} / {@code NUD} / {@code ARPTYPE} / {@code LLADDR}</td>
 *       <td>이웃 테이블 컬럼의 의미</td></tr>
 * </table>
 *
 * <p>남은 문자열 작업은 <b>표기 정리</b>뿐입니다. CIDR 분해({@code /24} 떼기),
 * 점 표기 MAC 을 콜론 표기로 바꾸기, 끝 구두점 떼기. 이는 "무엇인가"의 판별이
 * 아니라 "어떻게 쓰여 있는가"의 정리이므로 문법이 아니라 helper 의 몫입니다.
 *
 * <p>순회는 ANTLR 이 자동 생성한 {@code IpAddrBaseVisitor} 를 그대로 씁니다.
 * {@code visitDocument} 같은 상위 방문 메서드는 생성된 기본 구현이 스스로
 * 자식을 걷기 때문에, 이 클래스는 "의미가 있는 규칙"의 방문 메서드만
 * 덮어씁니다({@code visitItem}, {@code visitIfaceHeader}, …).
 */
public class IpAddrVisitor extends IpAddrBaseVisitor<Void> {

    private final ParseSession<IpAddrLexer, IpAddrParser> session;

    /** {@code ip a} 결과. {@code nic_status.interfaces[]} 로 그대로 쓰입니다. */
    public final ArrayNode interfaces = CliJson.array();

    /** {@code ip -br addr show} 결과. */
    public final ArrayNode brief = CliJson.array();

    /** {@code ip route show} 결과. {@code route_status.routes[]} 로 쓰입니다. */
    public final ArrayNode routes = CliJson.array();

    /** {@code ip neigh show} / {@code show arp} 결과. */
    public final ArrayNode arpEntries = CliJson.array();

    /** 현재 주소를 붙이고 있는 인터페이스 레코드. */
    private ObjectNode currentIface;

    /**
     * @param session 파싱 세션 (원문 라인 접근용)
     */
    public IpAddrVisitor(ParseSession<IpAddrLexer, IpAddrParser> session) {
        this.session = session;
    }

    // ==================================================================
    // ip a / ip addr show
    // ==================================================================

    /**
     * {@code item} 하나를 처리합니다.
     *
     * <p>{@code genericLine} / {@code blank} 는 흡수만 하고 버립니다. 문법이
     * "정보 누락을 막기 위해 자유 형식으로 둔다"고 선언한 종류라 여기서
     * 해석을 시도하지 않습니다.
     *
     * @param ctx item 컨텍스트
     */
    @Override
    public Void visitItem(IpAddrParser.ItemContext ctx) {
        if (ctx.ifaceHeader() != null) {
            visitIfaceHeader(ctx.ifaceHeader());
        } else if (ctx.ifaceAttr() != null) {
            visitIfaceAttr(ctx.ifaceAttr());
        } else if (ctx.lifetimeAttr() != null) {
            visitLifetimeAttr(ctx.lifetimeAttr());
        } else if (ctx.briefEntry() != null) {
            visitBriefEntry(ctx.briefEntry());
        } else if (ctx.routeEntry() != null) {
            visitRouteEntry(ctx.routeEntry());
        }
        return null;
    }

    /**
     * {@code 2: eth1.131@eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 ...}
     *
     * @param ctx 헤더 컨텍스트
     */
    @Override
    public Void visitIfaceHeader(IpAddrParser.IfaceHeaderContext ctx) {
        final ObjectNode iface = CliJson.object();

        if (ctx.INDEX() != null) {
            iface.put("index", Integer.parseInt(ctx.INDEX().getText()));
        }

        if (ctx.ifname() != null) {
            String name = ctx.ifname().getText();
            // `eth1.131@eth1` 의 '@' 뒤는 부모 링크다. '@' 는 표기 구분자이므로
            // 여기서 나누는 것이 맞다(종류 판별이 아니라 표기 해체).
            final int at = name.indexOf('@');
            if (at >= 0) {
                iface.put("parent", name.substring(at + 1));
                name = name.substring(0, at);
            }
            iface.put("name", name);
        }

        iface.set("addresses", CliJson.array());

        // 꼬리의 키/값 짝은 문법이 ifaceHeaderField 로 남겨 두었다.
        // visitor 는 옆 토큰을 세지 않고 그 짝을 그대로 꺼낸다.
        for (final IpAddrParser.IfaceHeaderPartContext part : ctx.ifaceHeaderPart()) {
            if (part.ifaceHeaderField() != null) {
                applyHeaderKeyValue(iface, part.ifaceHeaderField());
            } else if (part.elem() != null
                    && part.elem().getStart().getType() == IpAddrLexer.FLAGS) {
                // `<BROADCAST,MULTICAST,UP,LOWER_UP>` — 문법이 통째로 잡은 토큰이다.
                iface.set("flags", splitFlags(part.elem().getText()));
            }
        }

        interfaces.add(iface);
        currentIface = iface;
        return null;
    }

    /**
     * {@code link/ether ... brd ...} / {@code inet ...} / {@code valid_lft ...}
     *
     * @param ctx 속성 줄 컨텍스트
     */
    @Override
    public Void visitIfaceAttr(IpAddrParser.IfaceAttrContext ctx) {
        if (currentIface == null || ctx.attrLead() == null) {
            return null;
        }
        final IpAddrParser.AttrLeadContext lead = ctx.attrLead();

        if (lead.LINK() != null) {
            applyLinkLine(ctx, currentIface);
        } else if (lead.INET() != null || lead.INET6() != null) {
            currentIface.withArray("addresses").add(parseAddressLine(lead, ctx));
        }
        return null;
    }

    /**
     * {@code valid_lft forever preferred_lft forever} → 직전 주소에 채웁니다.
     *
     * <p>키/값 짝은 문법이 {@code lifetimePair : LIFETIME elem} 로 남겨 두었으므로
     * visitor 는 옆 토큰을 세지 않고 짝을 그대로 꺼냅니다.
     *
     * @param ctx 생명주기 줄 컨텍스트
     */
    @Override
    public Void visitLifetimeAttr(IpAddrParser.LifetimeAttrContext ctx) {
        if (currentIface == null) {
            return null;
        }
        final ArrayNode addresses = currentIface.withArray("addresses");
        if (addresses.isEmpty()) {
            return null;
        }
        final ObjectNode last = (ObjectNode) addresses.get(addresses.size() - 1);

        for (final IpAddrParser.LifetimePairContext pair : ctx.lifetimePair()) {
            last.put(pair.LIFETIME().getText(), CliText.trimPunct(pair.elem().getText()));
        }
        return null;
    }

    /**
     * {@code link/ether 02:42:7c:24:78:01 brd ff:ff:ff:ff:ff:ff}
     *
     * <p>{@code link/ether} 는 {@code LINK} 토큰 하나이고 하드웨어 주소는
     * {@code MAC}/{@code MACDOTTED} 토큰이다. 둘 다 타입으로 구분되므로
     * "MAC 처럼 생겼는가" 를 볼 필요가 없다.
     *
     * @param ctx   속성 줄 컨텍스트
     * @param iface 대상 인터페이스
     */
    private void applyLinkLine(IpAddrParser.IfaceAttrContext ctx, ObjectNode iface) {
        final TokenCursor<ElemContext> cursor = TokenCursor.of(ctx, ElemContext.class);
        while (cursor.hasNext()) {
            final int type = cursor.type();

            if (type == IpAddrLexer.LINK) {
                final String link = cursor.take();
                final int slash = link.indexOf('/');
                if (slash >= 0) {
                    iface.put("link_type", link.substring(slash + 1));
                }
            } else if (type == IpAddrLexer.MAC || type == IpAddrLexer.MACDOTTED) {
                iface.put("mac", mac(cursor.take(), type));
            } else if (type == IpAddrLexer.BRD) {
                cursor.skip(1);
                iface.put("broadcast", takeMacLike(cursor));
            } else if (type == IpAddrLexer.PROMISCUITY) {
                cursor.skip(1);
                iface.put("promiscuity", cursor.takeValue());
            } else if (type == IpAddrLexer.NETNSID) {
                cursor.skip(1);
                iface.put("link-netnsid", cursor.takeValue());
            } else {
                cursor.skip(1);
            }
        }
    }

    /**
     * {@code inet 10.10.131.1/24 scope global eth1.131}
     * {@code inet6 fe80::1/64 scope link}                             ← dev 없음
     * {@code inet6 ... scope global dynamic mngtmpaddr noprefixroute}  ← 플래그만
     *
     * <p>"scope 뒤에 오는 것이 인터페이스명인가 주소 플래그인가" 는 문법이
     * {@code GLOBAL}/{@code SCOPEWORD}/{@code ADDRFLAG} 토큰으로 확정해 두었습니다.
     * <b>global</b> 일 때만 dev 가 출력되고, 그 외에는 플래그가 옵니다.
     *
     * @param lead 속성 줄 종류
     * @param ctx  속성 줄 컨텍스트
     * @return 주소 레코드
     */
    private ObjectNode parseAddressLine(IpAddrParser.AttrLeadContext lead,
                                        IpAddrParser.IfaceAttrContext ctx) {
        final ObjectNode address = CliJson.object();
        address.put("family", lead.INET6() != null ? "inet6" : "inet");

        final TokenCursor<ElemContext> cursor = TokenCursor.of(ctx, ElemContext.class);
        final ArrayNode flags = CliJson.array();

        while (cursor.hasNext()) {
            final int type = cursor.type();

            if (type == IpAddrLexer.ADDR4 || type == IpAddrLexer.ADDR6) {
                applyCidr(address, cursor.take());
            } else if (type == IpAddrLexer.SCOPE) {
                cursor.skip(1);
                applyScope(address, cursor);
            } else if (type == IpAddrLexer.PEER) {
                cursor.skip(1);
                address.put("peer", cursor.takeValue());
            } else if (type == IpAddrLexer.ADDRFLAG) {
                flags.add(cursor.take());
            } else if (type == IpAddrLexer.NETNSID) {
                address.put("link_netnsid", cursor.takeValue());
            } else {
                cursor.skip(1);
            }
        }

        if (!flags.isEmpty()) {
            address.set("flags", flags);
        }
        return address;
    }

    /**
     * {@code scope} 뒤 조각을 읽습니다.
     *
     * <ul>
     *   <li>{@code global eth1.131} → scope=global, interface=eth1.131</li>
     *   <li>{@code host|link|nowhere} → scope 만 기록 (dev 없음)</li>
     *   <li>{@code ADDRFLAG} → 플래그로 남김</li>
     * </ul>
     *
     * @param address 대상 주소 레코드
     * @param cursor  scope 다음 위치의 커서
     */
    private void applyScope(ObjectNode address, TokenCursor<ElemContext> cursor) {
        final int type = cursor.type();
        if (type == IpAddrLexer.GLOBAL) {
            address.put("scope", cursor.take());
            // global 뒤 IFNAME 이 있으면 그것이 dev 다.
            if (cursor.is(IpAddrLexer.IFNAME)) {
                address.put("interface", cursor.take());
            }
        } else if (type == IpAddrLexer.SCOPEWORD) {
            address.put("scope", cursor.take());
        } else {
            // scope 값이 곧 플래그이거나 형식이 다르다. 값은 버리지 않는다.
            address.put("scope", cursor.takeValue());
        }
    }

    /**
     * 기본 경로 여부. {@code default} 키워드 표기와 {@code 0.0.0.0/0}
     * 주소 표기를 모두 "기본 경로" 로 본다(벤더 표기 차이일 뿐이다).
     *
     * @param prefix 목적지
     * @return 기본 경로 여부
     */
    private static boolean isDefaultPrefix(String prefix) {
        return "0.0.0.0/0".equals(prefix) || "::/0".equals(prefix);
    }

    // ==================================================================
    // ip -br addr show
    // ==================================================================

    /**
     * {@code eth0  UP  10.40.121.10/24 fe80::42:2fff:fe7e:d600/64}
     *
     * <p>주소와 MAC 의 구분은 {@code briefAddr : addr | MAC | MACDOTTED} 가
     * 끝내 두었으므로 접근자만 고르면 됩니다.
     *
     * @param ctx 브리프 줄 컨텍스트
     */
    @Override
    public Void visitBriefEntry(IpAddrParser.BriefEntryContext ctx) {
        final ObjectNode entry = CliJson.object();

        if (ctx.IFNAME() != null) {
            entry.put("name", ctx.IFNAME().getText());
        }
        if (ctx.linkState() != null) {
            entry.put("state", ctx.linkState().getText());
        }

        final ArrayNode addresses = CliJson.array();
        final ArrayNode families = CliJson.array();
        String mac = null;

        for (final IpAddrParser.BriefAddrContext addr : ctx.briefAddr()) {
            if (addr.addr() != null) {
                final IpAddrParser.AddrContext value = addr.addr();
                addresses.add(value.getText());
                // 패밀리는 토큰 타입이 알려준다.
                families.add(value.ADDR6() != null ? "inet6" : "inet");
            } else if (addr.MAC() != null) {
                mac = addr.MAC().getText();
            } else if (addr.MACDOTTED() != null) {
                mac = CliText.dottedMacToColon(addr.MACDOTTED().getText());
            }
        }

        if (!addresses.isEmpty()) {
            entry.set("addresses", addresses);
            if (families.size() == 1) {
                entry.put("family", families.get(0).asString());
            }
        }
        if (mac != null) {
            entry.put("mac", mac);
        }
        brief.add(entry);
        return null;
    }

    // ==================================================================
    // ip route show
    // ==================================================================

    /**
     * {@code default via 10.99.10.1 dev eth0 proto ospf metric 20}
     * {@code 10.99.10.0/24 dev eth0 proto kernel scope link src 10.99.10.4}
     * {@code blackhole 10.0.0.0/8}   ← routeHead 가 IFNAME, destination 보정 필요
     *
     * @param ctx 라우트 줄 컨텍스트
     */
    @Override
    public Void visitRouteEntry(IpAddrParser.RouteEntryContext ctx) {
        final IpAddrParser.RouteHeadContext head = ctx.routeHead();
        if (head == null) {
            return null;
        }

        final ObjectNode route = CliJson.object();

        // 목적지 자리는 문법이 네 가지로 확정한다.
        //   `default ...`            → DEFAULT
        //   `0.0.0.0/0 ...`          → DEFAULTADDR (기본 경로 주소 표기)
        //   `blackhole 10.0.0.0/8`   → blackholeDestination (목적지가 따로 온다)
        //   `10.99.10.0/24 ...`      → addr
        final String prefix = head.DEFAULT() != null || head.DEFAULTADDR() != null
                ? "0.0.0.0/0"
                : head.blackholeDestination() != null
                        ? head.blackholeDestination().addr().getText()
                        : head.addr() != null
                                ? head.addr().getText()
                                : head.getText();

        route.put("is_default", isDefaultPrefix(prefix));
        route.put("selected", true);
        route.put("prefix", prefix);
        route.put("destination", prefix);

        // 꼬리도 문법이 routeField 로 의미 단위를 나눠 두었다.
        final ArrayNode extras = CliJson.array();
        for (final IpAddrParser.RouteFieldContext field : ctx.routeField()) {
            if (field.routeVia() != null) {
                final String nextHop = field.routeVia().addr().getText();
                // 소비자는 next_hop 을 먼저 보고 via 로 되돌린다. 둘 다 넣는다.
                route.put("via", nextHop);
                route.put("next_hop", nextHop);
            } else if (field.routeDev() != null) {
                route.put("interface_name", field.routeDev().ifname().getText());
            } else if (field.routeProto() != null) {
                route.put("protocol", field.routeProto().protoName().getText());
            } else if (field.routeMetric() != null) {
                // `metric 20` — 커널 iproute2 는 브래킷이 아니라 키워드를 쓴니다.
                // 계약은 정수다: 소비자(AbstractDeviceConfigParser)가 metric 을
                // Long 으로 읽으므로 문자열로 담으면 값이 사라진다.
                // 숫자가 아니면 원문을 metric_raw 에 남겨 정보를 버리지 않는다.
                final String metric = CliText.trimPunct(field.routeMetric().INDEX().getText());
                if (CliText.isNumber(metric)) {
                    route.put("metric", Integer.parseInt(metric));
                } else {
                    route.put("metric_raw", metric);
                }
            } else if (field.routeSrc() != null) {
                route.put("pref_src", field.routeSrc().addr().getText());
            } else if (field.routeScope() != null) {
                route.put("scope", field.routeScope().scopeName().getText());
                if (field.routeScope().ifname() != null && !route.has("interface_name")) {
                    route.put("interface_name", field.routeScope().ifname().getText());
                }
            } else if (field.routeTable() != null) {
                route.put("table", field.routeTable().INDEX().getText());
            } else if (field.routeLinkdown() != null) {
                route.put("linkdown", true);
            } else if (field.elem() != null) {
                // `nhid 30` 처럼 문법에 없는 조각은 버리지 않고 원문을 남긴다.
                final String text = field.elem().getText();
                if (!text.isEmpty() && extras.size() < 16) {
                    extras.add(text);
                }
            }
        }
        if (!extras.isEmpty()) {
            route.set("extras", extras);
        }

        routes.add(route);
        return null;
    }

    // ==================================================================
    // ip neigh show / show arp / show ip arp
    // ==================================================================

    /**
     * 이웃(ARP) 한 줄을 옮깁니다.
     *
     * <pre>
     *   ip neigh show   10.0.9.1 dev ens3 lladdr 0c:2d:07:65:99:f3 REACHABLE
     *   show arp        172.18.10.1  2:31:51  0cae.21dd.0001  Ethernet1
     *   show ip arp     Internet 10.20.0.4 - 0c2d.0765.99f3 ARPA GigabitEthernet4
     * </pre>
     *
     * <p>Cisco 헤더 줄은 {@code arpAddress : addr | INETWORD} 덕분에 애초에
     * {@code arpEntry} 로 매칭되지 않습니다(헤더의 첫 단어 {@code Protocol} 은
     * 어느 쪽에도 해당하지 않아 {@code genericLine} 으로 떨어집니다). 그래서
     * 여기에 헤더 필터링 코드가 없습니다.
     *
     * @param ctx 이웃 줄 컨텍스트
     */
    @Override
    public Void visitArpEntry(IpAddrParser.ArpEntryContext ctx) {
        final IpAddrParser.ArpAddressContext first = ctx.arpAddress();
        if (first == null) {
            return null;
        }

        final ObjectNode entry = CliJson.object();
        boolean hasAddress = false;

        if (first.addr() != null) {
            applyCidr(entry, first.addr().getText());
            entry.put("family", first.addr().ADDR6() != null ? "inet6" : "inet");
            hasAddress = true;
        } else if (first.INETWORD() != null) {
            // Cisco: 첫 단어가 패밀리 표기이고 주소는 다음 조각에 온다.
            entry.put("family", familyOfInetWord(first.INETWORD().getText()));
        }

        String mac = null;
        String state = null;
        String age = null;
        String type = null;
        // 인터페이스는 여러 개 올 수 있다(`Vlan8, Ethernet2`).
        // 쉼표를 별도 토큰으로 뜯어 둔 덕분에 이름만 모으면 된다.
        final List<String> ifaces = new ArrayList<>();
        final ArrayNode extras = CliJson.array();

        final TokenCursor<ElemContext> cursor = TokenCursor.of(ctx, ElemContext.class);
        while (cursor.hasNext()) {
            final int tokenType = cursor.type();

            if (tokenType == IpAddrLexer.ADDR4 || tokenType == IpAddrLexer.ADDR6) {
                if (!hasAddress) {
                    applyCidr(entry, cursor.take());
                    entry.put("family", tokenType == IpAddrLexer.ADDR6 ? "inet6" : "inet");
                    hasAddress = true;
                } else {
                    cursor.skip(1);
                }
            } else if (tokenType == IpAddrLexer.MAC || tokenType == IpAddrLexer.MACDOTTED) {
                mac = mac(cursor.take(), tokenType);
            } else if (tokenType == IpAddrLexer.LLADDR) {
                cursor.skip(1);
                mac = takeMacLike(cursor);
            } else if (tokenType == IpAddrLexer.DEV) {
                cursor.skip(1);
                final String dev = cursor.takeValue();
                if (!dev.isEmpty()) {
                    ifaces.add(dev);
                }
            } else if (tokenType == IpAddrLexer.NUD) {
                state = cursor.take();
            } else if (tokenType == IpAddrLexer.AGE) {
                age = cursor.take();
            } else if (tokenType == IpAddrLexer.ARPTYPE) {
                type = cursor.take();
            } else if (tokenType == IpAddrLexer.IFNAME) {
                // Arista/Cisco 는 인터페이스명만 덩그러니 온다(쉼표로 여러 개).
                ifaces.add(cursor.take());
            } else if (tokenType == IpAddrLexer.INETWORD) {
                extras.add(cursor.take());
            } else {
                cursor.skip(1);
            }
        }

        // 주소가 없거나 MAC/인터페이스가 모두 없으면 ARP 항목으로 쓸 수 없다.
        if (!hasAddress || (mac == null && ifaces.isEmpty())) {
            return null;
        }

        if (mac != null) {
            entry.put("mac", mac);
        }
        if (state != null) {
            entry.put("state", state);
        }
        if (age != null) {
            entry.put("age", age);
        }
        if (type != null) {
            entry.put("type", type);
        }
        if (!ifaces.isEmpty()) {
            final ArrayNode list = CliJson.array();
            ifaces.forEach(list::add);
            entry.set("interfaces", list);
            // 기준 구현과 동일하게 대표 인터페이스는 첫 항목이다.
            entry.put("interface", ifaces.get(0));
        }
        if (!extras.isEmpty()) {
            entry.set("extras", extras);
        }

        arpEntries.add(entry);
        return null;
    }

    // ==================================================================
    // 값 정리 helper (판별이 아니라 표기 정리)
    // ==================================================================

    /**
     * {@code ADDR4}/{@code ADDR6} 토큰 텍스트를 주소와 prefix 길이로 나눠 넣습니다.
     *
     * @param target 대상 객체
     * @param value  {@code 10.10.131.1/24} 같은 값
     */
    private static void applyCidr(ObjectNode target, String value) {
        final String bare = CliText.trimPunct(value);
        final String[] address = new String[1];
        final int[] prefixLen = new int[1];
        if (CliText.splitCidr(bare, address, prefixLen)) {
            target.put("address", address[0]);
            if (prefixLen[0] >= 0) {
                target.put("prefix_len", prefixLen[0]);
            }
        }
    }

    /**
     * {@code MACDOTTED} 토큰이면 콜론 표기로 바꾸고, {@code MAC} 이면 그대로 씁니다.
     *
     * @param value 토큰 텍스트
     * @param type  토큰 타입
     * @return 콜론 표기 MAC
     */
    private static String mac(String value, int type) {
        final String trimmed = CliText.trimPunct(value);
        return type == IpAddrLexer.MACDOTTED ? CliText.dottedMacToColon(trimmed) : trimmed;
    }

    /**
     * 다음 조각이 MAC 이면 표기 정리해 돌려줍니다. ({@code brd}/{@code lladdr} 뒤 값)
     *
     * @param cursor 커서
     * @return MAC (없으면 빈 문자열)
     */
    private static String takeMacLike(TokenCursor<ElemContext> cursor) {
        final int type = cursor.type();
        final String value = cursor.take();
        if (type == IpAddrLexer.MAC || type == IpAddrLexer.MACDOTTED) {
            return mac(value, type);
        }
        return CliText.trimPunct(value);
    }

    /**
     * {@code <BROADCAST,MULTICAST,UP,LOWER_UP>} 을 플래그 배열로 나눕니다.
     *
     * @param token FLAGS 토큰 텍스트
     * @return 플래그 배열
     */
    private static ArrayNode splitFlags(String token) {
        final ArrayNode flags = CliJson.array();
        for (final String flag : token.substring(1, token.length() - 1).split(",", -1)) {
            if (!flag.isEmpty()) {
                flags.add(flag);
            }
        }
        return flags;
    }

    /**
     * 헤더 줄의 키/값 짝을 넣습니다.
     *
     * <p>키/값의 짝은 문법이 {@code ifaceHeaderField : ifaceHeaderKey ifaceHeaderValue}
     * 로 남겨 두었으므로, 여기서는 키 토큰 이름을 보고 필드 이름만 정한다.
     *
     * @param iface 대상 인터페이스
     * @param field 문법이 확정한 키/값 짝
     */
    private static void applyHeaderKeyValue(ObjectNode iface, IpAddrParser.IfaceHeaderFieldContext field) {
        final String value = CliText.trimPunct(field.ifaceHeaderValue().getText());
        final IpAddrParser.IfaceHeaderKeyContext key = field.ifaceHeaderKey();

        if (key.MTU() != null) {
            iface.put("mtu", value);
        } else if (key.QDISC() != null) {
            iface.put("qdisc", value);
        } else if (key.STATE() != null) {
            iface.put("state", value);
        } else if (key.QLEN() != null) {
            iface.put("qlen", value);
        } else if (key.GROUP() != null) {
            iface.put("group", value);
        } else if (key.NETNSID() != null) {
            iface.put("link-netnsid", value);
        }
    }

    /**
     * {@code Internet}/{@code ipv6} 같은 패밀리 단어를 {@code inet}/{@code inet6} 로 맞춥니다.
     *
     * @param word 패밀리 단어
     * @return 패밀리
     */
    private static String familyOfInetWord(String word) {
        return word.toLowerCase().contains("ipv6") ? "inet6" : "inet";
    }
}