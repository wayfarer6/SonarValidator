package org.sonar.sonarvalidator_backend.Model;

public final class Ip implements Comparable<Ip> {

    public static final int BITS = 32;

    public static final long MASK = 0xFFFFFFFFL;

    public static final Ip ZERO = new Ip(0L);

    private final long _ip;

    public Ip(long value) {
        if (value < 0L || value > MASK) {
            throw new IllegalArgumentException(
                    "ipv4 out of range: " + value + " (expected 0.." + MASK + ")");
        }
        this._ip = value;
    }

    public static Ip parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("ipv4 is null");
        }
        final String value = text.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("ipv4 is blank");
        }
        if (value.indexOf('/') >= 0) {
            throw new IllegalArgumentException(
                    "cidr is not an address (use Prefix.parse): " + text);
        }

        long[] octets = new long[4];
        int octetIndex = 0;
        int digitCount = 0;
        int octet = 0;

        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == '.') {
                if (digitCount == 0) {
                    throw new IllegalArgumentException("empty octet in ipv4: " + text);
                }
                if (octetIndex > 3) {
                    throw new IllegalArgumentException("too many octets in ipv4: " + text);
                }
                octets[octetIndex++] = octet;
                octet = 0;
                digitCount = 0;
                continue;
            }
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException(
                        "invalid character '" + c + "' in ipv4: " + text);
            }
            digitCount++;
            if (digitCount > 3) {
                throw new IllegalArgumentException("octet too long in ipv4: " + text);
            }
            octet = octet * 10 + (c - '0');
            if (octet > 255) {
                throw new IllegalArgumentException("octet out of range in ipv4: " + text);
            }
        }

        // 마지막 옥텟
        if (digitCount == 0) {
            throw new IllegalArgumentException("empty octet in ipv4: " + text);
        }
        if (octetIndex != 3) {
            throw new IllegalArgumentException("ipv4 must have 4 octets: " + text);
        }
        octets[3] = octet;

        return new Ip((octets[0] << 24) | (octets[1] << 16) | (octets[2] << 8) | octets[3]);
    }

    public long asLong() {
        return this._ip;
    }

    public long get_ip() {
        return this._ip;
    }

    public boolean isZero() {
        return this._ip == 0L;
    }

    public Ip withMask(int prefixLength) {
        return new Ip(this._ip & maskOf(prefixLength));
    }

    public static long maskOf(int prefixLength) {
        if (prefixLength < 0 || prefixLength > BITS) {
            throw new IllegalArgumentException("prefix length out of range: " + prefixLength);
        }
        if (prefixLength == 0) {
            return 0L;
        }
        if (prefixLength == BITS) {
            return MASK;
        }
        return (MASK << (BITS - prefixLength)) & MASK;
    }

    @Override
    public String toString() {
        return ((_ip >> 24) & 0xFF) + "." + ((_ip >> 16) & 0xFF) + "."
                + ((_ip >> 8) & 0xFF) + "." + (_ip & 0xFF);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Ip ip && ip._ip == this._ip;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(_ip);
    }

    @Override
    public int compareTo(Ip other) {
        return Long.compare(this._ip, other._ip);
    }
}
