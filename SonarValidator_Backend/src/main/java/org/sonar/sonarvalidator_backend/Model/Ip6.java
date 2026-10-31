package org.sonar.sonarvalidator_backend.Model;

import java.math.BigInteger;
import java.util.Locale;

public final class Ip6 implements Comparable<Ip6> {

    public static final int BITS = 128;

    private static final BigInteger MASK =
            BigInteger.ONE.shiftLeft(BITS).subtract(BigInteger.ONE);

    public static final Ip6 ZERO = new Ip6(BigInteger.ZERO);

    private final BigInteger _ip6;

    public Ip6(BigInteger value) {
        if (value == null) {
            throw new IllegalArgumentException("ipv6 is null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("ipv6 must not be negative: " + value);
        }
        if (value.compareTo(MASK) > 0) {
            throw new IllegalArgumentException("ipv6 out of 128-bit range: " + value);
        }
        this._ip6 = value;
    }

    public static Ip6 parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("ipv6 is null");
        }
        final String value = text.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("ipv6 is blank");
        }
        if (value.indexOf('/') >= 0) {
            throw new IllegalArgumentException(
                    "cidr is not an address (use Prefix6): " + text);
        }
        if (value.indexOf('%') >= 0) {
            // fe80::1%eth0 — 존 ID 는 주소가 아니라 인터페이스 지정입니다.
            throw new IllegalArgumentException("zone id is not part of the address: " + text);
        }

        final String normalized = value.toLowerCase(Locale.ROOT);

        // "::" 는 최대 한 번만 올 수 있습니다.
        final int firstGap = normalized.indexOf("::");
        if (firstGap >= 0 && normalized.indexOf("::", firstGap + 1) >= 0) {
            throw new IllegalArgumentException("at most one '::' allowed: " + text);
        }

        final String head;
        final String tail;
        if (firstGap < 0) {
            head = normalized;
            tail = "";
        } else {
            head = normalized.substring(0, firstGap);
            tail = normalized.substring(firstGap + 2);
        }

        final String[] headParts = splitAndCheck(head, text);
        final String[] tailParts = splitAndCheck(tail, text);

        // 그룹 수 계산: 혼합 표기의 IPv4 는 그룹 2개로 셉니다.
        final int headGroups = countGroups(headParts, text);
        final int tailGroups = countGroups(tailParts, text);
        final int explicit = headGroups + tailGroups;

        if (firstGap < 0 && explicit != 8) {
            throw new IllegalArgumentException("ipv6 needs 8 groups: " + text);
        }
        if (firstGap >= 0 && explicit > 7) {
            // "::" 는 최소 한 그룹을 대신해야 합니다.
            throw new IllegalArgumentException("'::' must replace at least one group: " + text);
        }

        BigInteger result = BigInteger.ZERO;
        for (final String part : headParts) {
            result = result.shiftLeft(16).or(valueOfGroup(part, text));
        }
        if (firstGap >= 0) {
            final int gapGroups = 8 - explicit;
            result = result.shiftLeft(16 * gapGroups);
        }
        for (final String part : tailParts) {
            result = result.shiftLeft(16).or(valueOfGroup(part, text));
        }




        final String lastToken = tailParts.length > 0
                ? tailParts[tailParts.length - 1]
                : (headParts.length > 0 ? headParts[headParts.length - 1] : "");
        if (lastToken.indexOf('.') >= 0) {
            final long v4 = Ip.parse(lastToken).asLong();
            // 이미 그룹 1개(16비트)가 들어갔으므로 나머지 16비트를 더합니다.
            result = result.shiftLeft(16).or(BigInteger.valueOf(v4 & 0xFFFFL));
        }

        return new Ip6(result);
    }

    private static BigInteger valueOfGroup(String part, String original) {
        if (part.indexOf('.') >= 0) {
            // IPv4 혼합의 상위 16비트. 하위 16비트는 호출부에서 더합니다.
            return BigInteger.valueOf((Ip.parse(part).asLong() >>> 16) & 0xFFFFL);
        }
        try {
            return new BigInteger(part, 16);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid hex group '" + part + "': " + original, ex);
        }
    }

    private static String[] splitAndCheck(String section, String original) {
        if (section.isEmpty()) {
            return new String[0];
        }
        if (section.charAt(0) == ':' || section.charAt(section.length() - 1) == ':') {
            throw new IllegalArgumentException("misplaced ':' in ipv6: " + original);
        }
        final String[] parts = section.split(":", -1);
        for (final String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("empty group in ipv6: " + original);
            }
        }
        return parts;
    }

    private static int countGroups(String[] parts, String original) {
        if (parts.length == 0) {
            return 0;
        }
        final String last = parts[parts.length - 1];
        if (last.indexOf('.') < 0) {
            for (final String part : parts) {
                if (part.length() > 4) {
                    throw new IllegalArgumentException("group too long in ipv6: " + original);
                }
            }
            return parts.length;
        }
        // IPv4 혼합은 마지막 그룹에만 올 수 있습니다.
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].indexOf('.') >= 0) {
                throw new IllegalArgumentException("embedded ipv4 must be last: " + original);
            }
            if (parts[i].length() > 4) {
                throw new IllegalArgumentException("group too long in ipv6: " + original);
            }
        }
        return parts.length - 1 + 2;
    }

    public BigInteger value() {
        return this._ip6;
    }

    public BigInteger get_ip6() {
        return this._ip6;
    }

    public boolean isZero() {
        return this._ip6.signum() == 0;
    }

    public Ip6 withMask(int prefixLength) {
        return new Ip6(this._ip6.and(maskOf(prefixLength)));
    }

    public static BigInteger maskOf(int prefixLength) {
        if (prefixLength < 0 || prefixLength > BITS) {
            throw new IllegalArgumentException("prefix length out of range: " + prefixLength);
        }
        if (prefixLength == 0) {
            return BigInteger.ZERO;
        }
        if (prefixLength == BITS) {
            return MASK;
        }
        return BigInteger.ONE.shiftLeft(prefixLength).subtract(BigInteger.ONE)
                .shiftLeft(BITS - prefixLength);
    }

    @Override
    public String toString() {
        final int[] groups = toGroups();

        int bestStart = -1;
        int bestLength = 0;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int j = i;
            while (j < 8 && groups[j] == 0) {
                j++;
            }
            if (j - i > bestLength) {
                bestStart = i;
                bestLength = j - i;
            }
            i = j;
        }
        if (bestLength < 2) {
            // 한 그룹만 0 이면 접지 않습니다.
            bestStart = -1;
        }

        final StringBuilder out = new StringBuilder(39);
        for (int i = 0; i < 8; i++) {
            if (bestStart >= 0 && i == bestStart) {
                out.append("::");
                i += bestLength - 1;
                continue;
            }
            if (out.length() > 0 && out.charAt(out.length() - 1) != ':') {
                out.append(':');
            }
            out.append(Integer.toHexString(groups[i]));
        }
        return out.length() == 0 ? "::" : out.toString();
    }

    public String toExpanded() {
        final int[] groups = toGroups();
        final StringBuilder out = new StringBuilder(39);
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                out.append(':');
            }
            final String hex = Integer.toHexString(groups[i]);
            out.append("0".repeat(4 - hex.length())).append(hex);
        }
        return out.toString();
    }

    private int[] toGroups() {
        long high = _ip6.shiftRight(64).longValue();
        final long low = _ip6.longValue();
        final int[] groups = new int[8];
        for (int i = 0; i < 4; i++) {
            groups[i] = (int) ((high >>> (48 - i * 16)) & 0xFFFF);
        }
        for (int i = 0; i < 4; i++) {
            groups[i + 4] = (int) ((low >>> (48 - i * 16)) & 0xFFFF);
        }
        return groups;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Ip6 ip6 && ip6._ip6.equals(this._ip6);
    }

    @Override
    public int hashCode() {
        return _ip6.hashCode();
    }

    @Override
    public int compareTo(Ip6 other) {
        return this._ip6.compareTo(other._ip6);
    }
}
