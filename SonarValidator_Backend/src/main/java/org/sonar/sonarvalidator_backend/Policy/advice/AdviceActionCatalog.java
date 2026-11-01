package org.sonar.sonarvalidator_backend.Policy.advice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.sonar.sonarvalidator_backend.Policy.PacketVariables;

public final class AdviceActionCatalog {
    public record Plan(String operation, String profile, Map<String, Object> arguments,
                       List<String> commands, List<String> verification, boolean changesConfiguration) {}
    public static final Set<String> OPERATIONS = Set.of("inspect_interfaces", "inspect_routes", "inspect_vlans",
            "set_interface_description", "ensure_vlan", "block_forward_ipv4");
    private AdviceActionCatalog() {}

    public static String profile(String product) {
        String p = product == null ? "" : product.toLowerCase(java.util.Locale.ROOT);
        if (p.contains("cisco") || p.contains("ios") || p.contains("catalyst")) return "ios";
        if (p.contains("arista") || p.contains("veos")) return "eos";
        if (p.contains("openvswitch") || p.contains("open vswitch") || p.contains("ovs")) return "ovs";
        if (p.contains("ubuntu") || p.contains("linux") || p.contains("nft") || p.contains("frr")) return "linux";
        return "unsupported";
    }
    public static Plan compile(String operation, String product, Map<String, Object> input) {
        if (!OPERATIONS.contains(operation)) throw new IllegalArgumentException("Prober 실행을 지원하지 않는 명령 유형입니다.");
        String p = profile(product);
        if (p.equals("unsupported")) throw new IllegalArgumentException("장비 제품에 맞는 실행기를 찾을 수 없습니다.");
        Map<String, Object> a = new TreeMap<>(input == null ? Map.of() : input);
        List<String> commands;
        List<String> checks = List.of();
        boolean write = false;
        switch (operation) {
            case "inspect_interfaces" -> {
                keys(a, Set.of());
                commands = List.of(p.equals("ios") ? "show ip interface brief" : p.equals("eos") ? "show interfaces status" : "ip -j address show");
            }
            case "inspect_routes" -> {
                keys(a, Set.of());
                commands = List.of(p.equals("ios") || p.equals("eos") ? "show ip route" : "ip -j route show");
            }
            case "inspect_vlans" -> {
                keys(a, Set.of());
                if (!p.equals("eos") && !p.equals("ovs")) throw new IllegalArgumentException("이 제품의 VLAN 조회는 수동 확인이 필요합니다.");
                commands = List.of(p.equals("eos") ? "show vlan" : "ovs-vsctl --format=json --columns=name,tag,trunks list Port");
            }
            case "set_interface_description" -> {
                keys(a, Set.of("interface", "description"));
                if (!p.equals("ios") && !p.equals("eos")) throw new IllegalArgumentException("인터페이스 설명 적용은 IOS/EOS에서 지원합니다.");
                String iface = string(a, "interface", "[A-Za-z][A-Za-z0-9./:-]{0,63}");
                String description = string(a, "description", "[A-Za-z0-9][A-Za-z0-9 _.,:/()=-]{0,79}");
                commands = List.of("configure terminal", "interface " + iface, "description " + description, "end");
                checks = List.of("show running-config interface " + iface); write = true;
            }
            case "ensure_vlan" -> {
                keys(a, Set.of("vlan_id", "name"));
                if (!p.equals("eos")) throw new IllegalArgumentException("VLAN 생성 적용은 EOS에서 지원합니다.");
                int id = integer(a, "vlan_id", 2, 4094);
                String name = string(a, "name", "[A-Za-z0-9][A-Za-z0-9_.-]{0,31}");
                a.put("vlan_id", id);
                commands = List.of("configure terminal", "vlan " + id, "name " + name, "end");
                checks = List.of("show vlan id " + id); write = true;
            }
            case "block_forward_ipv4" -> {
                keys(a, Set.of("source", "destination", "protocol", "port"));
                if (!p.equals("linux")) throw new IllegalArgumentException("전달 패킷 차단 적용은 Linux nftables에서 지원합니다. IOS/EOS 컨테이너에는 적용하지 않습니다.");
                String src = cidr(a, "source"), dst = cidr(a, "destination");
                String protocol = string(a, "protocol", "any|tcp|udp|icmp");
                int port = integer(a, "port", 0, 65535);
                if (port > 0 && !protocol.equals("tcp") && !protocol.equals("udp")) throw new IllegalArgumentException("TCP/UDP에만 포트를 지정할 수 있습니다.");
                String table = nftTable(src, dst, protocol, port);
                String match = "ip saddr " + src + " ip daddr " + dst;
                if (port > 0) match += " " + protocol + " dport " + port;
                else if (!protocol.equals("any")) match += " meta l4proto " + protocol;
                commands = List.of("table inet " + table + " { chain forward { type filter hook forward priority -150; policy accept; "
                        + match + " counter drop comment \"sonar-ai-managed\"; } }");
                checks = List.of("nft -j list table inet " + table); write = true;
            }
            default -> throw new IllegalArgumentException("지원하지 않는 명령 유형입니다.");
        }
        return new Plan(operation, p, Map.copyOf(a), commands, checks, write);
    }
    public static String nftTable(String source, String destination, String protocol, int port) {
        return "sonar_ai_" + hash(source + "|" + destination + "|" + protocol + "|" + port).substring(0, 12);
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void keys(Map<String, Object> a, Set<String> keys) {
        if (!keys.containsAll(a.keySet())) throw new IllegalArgumentException("허용되지 않은 명령 인자가 있습니다.");
    }
    private static String string(Map<String, Object> a, String key, String pattern) {
        Object v = a.get(key);
        if (!(v instanceof String s) || !s.matches(pattern)) throw new IllegalArgumentException("유효하지 않은 명령 인자: " + key);
        return s;
    }
    private static int integer(Map<String, Object> a, String key, int min, int max) {
        Object v = a.get(key);
        if (!(v instanceof Number n) || v instanceof Double || v instanceof Float || n.doubleValue() != n.intValue() || n.intValue() < min || n.intValue() > max)
            throw new IllegalArgumentException("유효하지 않은 숫자 인자: " + key);
        return n.intValue();
    }
    private static String cidr(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (!(v instanceof String s)) throw new IllegalArgumentException("IPv4 CIDR이 필요합니다: " + key);
        var c = PacketVariables.parseCidr(s);
        int mask = c.prefixLength() == 0 ? 0 : -1 << (32 - c.prefixLength());
        int ip = c.address() & mask;
        String normalized = ((ip >>> 24) & 255) + "." + ((ip >>> 16) & 255) + "." + ((ip >>> 8) & 255) + "." + (ip & 255) + "/" + c.prefixLength();
        a.put(key, normalized); return normalized;
    }
}
