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

public class IpAddrVisitor extends IpAddrBaseVisitor<Void> {

    private final ParseSession<IpAddrLexer, IpAddrParser> session;

    public final ArrayNode interfaces = CliJson.array();

    public final ArrayNode brief = CliJson.array();

    public final ArrayNode routes = CliJson.array();

    public final ArrayNode arpEntries = CliJson.array();

    private ObjectNode currentIface;

    public IpAddrVisitor(ParseSession<IpAddrLexer, IpAddrParser> session) {
        this.session = session;
    }

    // ip a / ip addr show

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

    private static boolean isDefaultPrefix(String prefix) {
        return "0.0.0.0/0".equals(prefix) || "::/0".equals(prefix);
    }

    // ip -br addr show

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

    // ip route show

    @Override
    public Void visitRouteEntry(IpAddrParser.RouteEntryContext ctx) {
        final IpAddrParser.RouteHeadContext head = ctx.routeHead();
        if (head == null) {
            return null;
        }

        final ObjectNode route = CliJson.object();

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

    // ip neigh show / show arp / show ip arp

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

    // 값 정리 helper (판별이 아니라 표기 정리)

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

    private static String mac(String value, int type) {
        final String trimmed = CliText.trimPunct(value);
        return type == IpAddrLexer.MACDOTTED ? CliText.dottedMacToColon(trimmed) : trimmed;
    }

    private static String takeMacLike(TokenCursor<ElemContext> cursor) {
        final int type = cursor.type();
        final String value = cursor.take();
        if (type == IpAddrLexer.MAC || type == IpAddrLexer.MACDOTTED) {
            return mac(value, type);
        }
        return CliText.trimPunct(value);
    }

    private static ArrayNode splitFlags(String token) {
        final ArrayNode flags = CliJson.array();
        for (final String flag : token.substring(1, token.length() - 1).split(",", -1)) {
            if (!flag.isEmpty()) {
                flags.add(flag);
            }
        }
        return flags;
    }

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

    private static String familyOfInetWord(String word) {
        return word.toLowerCase().contains("ipv6") ? "inet6" : "inet";
    }
}
