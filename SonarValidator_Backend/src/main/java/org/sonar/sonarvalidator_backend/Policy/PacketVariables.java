package org.sonar.sonarvalidator_backend.Policy;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class PacketVariables {

    public static final int IP_BITS = 32;

    public static final int PORT_BITS = 16;

    public static final int PROTOCOL_BITS = 8;

    public static final int SRC_IP_OFFSET = 0;

    public static final int DST_IP_OFFSET = SRC_IP_OFFSET + IP_BITS;

    public static final int PORT_OFFSET = DST_IP_OFFSET + IP_BITS;

    public static final int PROTOCOL_OFFSET = PORT_OFFSET + PORT_BITS;

    public static final int TOTAL_BITS = PROTOCOL_OFFSET + PROTOCOL_BITS;

    public static final int ANY_PORT = -1;

    private PacketVariables() {
    }

    public static BddManager newManager() {
        final BddManager manager = new BddManager();
        for (int index = 0; index < TOTAL_BITS; index++) {
            manager.variable(index);
        }
        return manager;
    }

    public static BddNode cidr(BddManager manager, String cidr, int offset) {
        final ParsedCidr parsed = parseCidr(cidr);
        BddNode result = manager.one();
        for (int bit = 0; bit < parsed.prefixLength(); bit++) {
            final int variable = offset + bit;
            final boolean value = ((parsed.address() >>> (IP_BITS - 1 - bit)) & 1) != 0;
            result = manager.and(result, value ? manager.variable(variable) : manager.not(manager.variable(variable)));
        }
        return result;
    }

    public static BddNode port(BddManager manager, int port) {
        if (port == ANY_PORT) {
            return manager.one();
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        BddNode result = manager.one();
        for (int bit = 0; bit < PORT_BITS; bit++) {
            final int variable = PORT_OFFSET + bit;
            final boolean value = ((port >>> (PORT_BITS - 1 - bit)) & 1) != 0;
            result = manager.and(result, value ? manager.variable(variable) : manager.not(manager.variable(variable)));
        }
        return result;
    }

    public static BddNode protocol(BddManager manager, String protocol) {
        final int number = protocolNumber(protocol);
        if (number < 0) {
            return manager.one();
        }
        BddNode result = manager.one();
        for (int bit = 0; bit < PROTOCOL_BITS; bit++) {
            final int variable = PROTOCOL_OFFSET + bit;
            final boolean value = ((number >>> (PROTOCOL_BITS - 1 - bit)) & 1) != 0;
            result = manager.and(
                    result,
                    value ? manager.variable(variable) : manager.not(manager.variable(variable)));
        }
        return result;
    }

    public static int protocolNumber(String protocol) {
        final String value = protocol == null || protocol.isBlank()
                ? "tcp"
                : protocol.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "any" -> -1;
            case "icmp" -> 1;
            case "tcp" -> 6;
            case "udp" -> 17;
            default -> throw new IllegalArgumentException("unsupported protocol: " + protocol);
        };
    }

    public static String protocolOf(int[] assignment) {
        if (assignment == null) {
            return "any";
        }
        int value = fieldOf(assignment, PROTOCOL_OFFSET, PROTOCOL_BITS);
        if (value < 0) return "any";
        return switch (value) {
            case 1 -> "icmp";
            case 6 -> "tcp";
            case 17 -> "udp";
            default -> "ip-protocol-" + value;
        };
    }

    public static String srcIpOf(int[] assignment) {
        return ipOf(assignment, SRC_IP_OFFSET);
    }

    public static String dstIpOf(int[] assignment) {
        return ipOf(assignment, DST_IP_OFFSET);
    }

    public static int portOf(int[] assignment) {
        return fieldOf(assignment, PORT_OFFSET, PORT_BITS);
    }

    private static int fieldOf(int[] assignment, int offset, int width) {
        if (assignment == null) return -1;
        int value = 0;
        boolean constrained = false;
        for (int bit = 0; bit < width; bit++) {
            final int index = offset + bit;
            final int assigned = index < assignment.length ? assignment[index] : -1;
            value = (value << 1) | (assigned > 0 ? 1 : 0);
            constrained |= assigned >= 0;
        }
        return constrained ? value : -1;
    }

    private static String ipOf(int[] assignment, int offset) {
        if (assignment == null) {
            return "0.0.0.0";
        }
        int value = 0;
        for (int bit = 0; bit < IP_BITS; bit++) {
            final int index = offset + bit;
            final int bitValue = (index < assignment.length && assignment[index] > 0) ? 1 : 0;
            value = (value << 1) | bitValue;
        }
        return new StringBuilder()
                .append((value >>> 24) & 0xFF).append('.')
                .append((value >>> 16) & 0xFF).append('.')
                .append((value >>> 8) & 0xFF).append('.')
                .append(value & 0xFF)
                .toString();
    }

    public static ParsedCidr parseCidr(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            throw new IllegalArgumentException("cidr is blank");
        }
        final String[] parts = cidr.trim().split("/", -1);
        if (parts.length > 2 || (parts.length == 2 && !parts[1].trim().matches("[0-9]{1,2}"))) {
            throw new IllegalArgumentException("invalid cidr: " + cidr);
        }
        final String addressText = parts[0].trim();
        final String normalized = addressText.replaceFirst("(?i)\\.x$", ".0");
        final int prefixLength = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : IP_BITS;
        if (prefixLength < 0 || prefixLength > IP_BITS) {
            throw new IllegalArgumentException("prefix length out of range: " + cidr);
        }

        final int address = parseIp(normalized);
        return new ParsedCidr(address, prefixLength);
    }

    public static int parseIp(String ip) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("ip is blank");
        }
        final String[] octets = ip.trim().split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("invalid ipv4 address: " + ip);
        }
        int value = 0;
        for (final String octet : octets) {
            if (!octet.matches("[0-9]{1,3}")) {
                throw new IllegalArgumentException("invalid octet in ip: " + ip);
            }
            final int parsed;
            try {
                parsed = Integer.parseInt(octet.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("invalid octet '" + octet + "' in ip: " + ip, ex);
            }
            if (parsed < 0 || parsed > 255) {
                throw new IllegalArgumentException("octet out of range '" + octet + "' in ip: " + ip);
            }
            value = (value << 8) | parsed;
        }
        return value;
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(value.trim()).getHostAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    public record ParsedCidr(int address, int prefixLength) {
    }
}
