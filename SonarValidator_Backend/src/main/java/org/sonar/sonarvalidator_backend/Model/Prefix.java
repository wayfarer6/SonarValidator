package org.sonar.sonarvalidator_backend.Model;

public final class Prefix implements Comparable<Prefix> {

    private final Ip _ip;

    private final int _prefixLength;

    public Prefix(Ip network, int prefixLength) {
        if (network == null) {
            throw new IllegalArgumentException("prefix network is null");
        }
        // 범위 검증은 maskOf 가 합니다. (0~32 밖이면 예외)
        this._ip = network.withMask(prefixLength);
        this._prefixLength = prefixLength;
    }

    public static Prefix parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("prefix is null");
        }
        final String value = text.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("prefix is blank");
        }

        final int slash = value.indexOf('/');
        if (slash < 0) {
            return new Prefix(Ip.parse(value), Ip.BITS);
        }
        if (slash != value.lastIndexOf('/')) {
            throw new IllegalArgumentException("too many '/' in prefix: " + text);
        }

        final String addressPart = value.substring(0, slash).trim();
        final String lengthPart = value.substring(slash + 1).trim();
        if (lengthPart.isEmpty()) {
            throw new IllegalArgumentException("missing prefix length: " + text);
        }

        final int length;
        try {
            length = Integer.parseInt(lengthPart);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid prefix length: " + text, ex);
        }

        return new Prefix(Ip.parse(addressPart), length);
    }

    public static Prefix parseOrNull(String text) {
        if (text == null) {
            return null;
        }
        final String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.equalsIgnoreCase("default") || value.equalsIgnoreCase("any")) {
            return new Prefix(Ip.ZERO, 0);
        }
        try {
            return parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public Ip getStartIp() {
        return this._ip;
    }

    public Ip get_ip() {
        return this._ip;
    }

    public int get_prefixLength() {
        return this._prefixLength;
    }

    public Ip getEndIp() {
        final long hostMask = ~Ip.maskOf(_prefixLength) & Ip.MASK;
        return new Ip(_ip.asLong() | hostMask);
    }

    public long size() {
        return 1L << (Ip.BITS - _prefixLength);
    }

    public boolean contains(Ip ip) {
        if (ip == null) {
            return false;
        }
        return _ip.asLong() == ip.withMask(_prefixLength).asLong();
    }

    public boolean containsPrefix(Prefix other) {
        if (other == null) {
            return false;
        }
        // this 가 더 길면(= 범위가 더 좁으면) other 를 담을 수 없습니다.
        if (other._prefixLength < this._prefixLength) {
            return false;
        }
        return contains(other._ip);
    }

    public boolean overlaps(Prefix other) {
        if (other == null) {
            return false;
        }
        final int shorter = Math.min(this._prefixLength, other._prefixLength);
        return this._ip.withMask(shorter).asLong() == other._ip.withMask(shorter).asLong();
    }

    @Override
    public String toString() {
        return _ip + "/" + _prefixLength;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Prefix prefix
                && prefix._prefixLength == this._prefixLength
                && prefix._ip.equals(this._ip);
    }

    @Override
    public int hashCode() {
        return 31 * _ip.hashCode() + _prefixLength;
    }

    @Override
    public int compareTo(Prefix other) {
        final int byLength = Integer.compare(this._prefixLength, other._prefixLength);
        if (byLength != 0) {
            return byLength;
        }
        return this._ip.compareTo(other._ip);
    }
}
