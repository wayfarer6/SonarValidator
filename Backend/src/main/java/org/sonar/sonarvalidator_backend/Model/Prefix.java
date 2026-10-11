package org.sonar.sonarvalidator_backend.Model;

/**
 * IPv4 대역(접두사) <b>값 객체</b>입니다. (Batfish 의 {@code Prefix} 대응)
 *
 * <h2>표현</h2>
 * <p>네트워크 주소({@link Ip}) + 접두사 길이 두 값으로만 이뤄집니다.
 * 호스트 비트는 <b>생성 시점에 0 으로 정규화</b>합니다. 그래야
 * {@code 10.99.10.7/24} 와 {@code 10.99.10.0/24} 가 같은 객체로 취급되고,
 * {@code equals} 로 대역을 비교할 수 있습니다.
 *
 * <pre>
 *   Prefix net = Prefix.parse("10.99.10.0/24");
 *   net.contains(Ip.parse("10.99.10.7"));   // true
 *   net.contains(Ip.parse("10.99.11.1"));   // false
 *   net.getEndIp();                         // 10.99.10.255
 * </pre>
 *
 * <h2>⚠️ 이전 구현에서 고친 것</h2>
 * <ul>
 *   <li>{@code contains()} 가 접두사 비교가 아니라 <b>주소 동일 비교</b>였습니다
 *       ({@code _ip.asLong() == ip.get_ip()}). 그래서 {@code 10.99.10.0/24} 가
 *       {@code 10.99.10.255} 를 포함하지 않는다고 답했습니다.</li>
 *   <li>{@code getEndIp()} 의 {@code (1L << (32 - len)) - 1} 은 {@code /0} 에서
 *       Java 시프트가 {@code mod 64} 로 동작해 마스크가 31비트가 됐습니다.</li>
 *   <li>{@code parse()} 가 항상 빈 객체를 돌려주는 스텁이었습니다.</li>
 *   <li>{@code _ip} 가 null 이면 {@code contains()} 가 NPE 였습니다.</li>
 * </ul>
 */
public final class Prefix implements Comparable<Prefix> {

    /** 네트워크 주소 (호스트 비트는 0). */
    private final Ip _ip;

    /** 접두사 길이 ({@code 0 ~ 32}). */
    private final int _prefixLength;

    /**
     * 네트워크 주소와 접두사 길이로 만듭니다.
     *
     * <p>호스트 비트가 켜져 있으면 <b>조용히 정규화</b>합니다.
     * ({@code 10.99.10.7/24} → {@code 10.99.10.0/24})
     *
     * @param network      네트워크 주소
     * @param prefixLength 접두사 길이 ({@code 0 ~ 32})
     * @throws IllegalArgumentException 주소가 null 이거나 길이가 범위 밖이면
     */
    public Prefix(Ip network, int prefixLength) {
        if (network == null) {
            throw new IllegalArgumentException("prefix network is null");
        }
        // 범위 검증은 maskOf 가 합니다. (0~32 밖이면 예외)
        this._ip = network.withMask(prefixLength);
        this._prefixLength = prefixLength;
    }

    /**
     * CIDR 문자열을 파싱합니다. (예: {@code "10.99.10.0/24"}, {@code "0.0.0.0/0"})
     *
     * <p>접두사 길이를 생략하면({@code "10.99.10.1"}) {@code /32} 로 봅니다 —
     * 단일 호스트를 가리키는 표기이기 때문입니다.
     *
     * @param text CIDR 또는 단일 주소
     * @return 대역
     * @throws IllegalArgumentException null/공백/형식 오류
     */
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

    /**
     * "default" 나 빈 문자열 같은 느슨한 표기를 흡수해 파싱합니다.
     *
     * <p>Agent 가 라우팅 테이블을 보낼 때 기본 경로를 {@code "default"} 로 쓰는
     * 경우가 있습니다. 예외를 던지면 그 장비의 수집 전체가 실패하므로,
     * 기본 경로로 해석합니다.
     *
     * @param text CIDR / 주소 / {@code "default"} / null
     * @return 대역 (해석 불가면 null)
     */
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

    /**
     * 네트워크 주소를 돌려줍니다. (호스트 비트는 0)
     *
     * @return 네트워크 주소
     */
    public Ip getStartIp() {
        return this._ip;
    }

    /**
     * 네트워크 주소를 돌려줍니다.
     *
     * @return 네트워크 주소
     */
    public Ip get_ip() {
        return this._ip;
    }

    /**
     * 접두사 길이를 돌려줍니다.
     *
     * @return {@code 0 ~ 32}
     */
    public int get_prefixLength() {
        return this._prefixLength;
    }

    /**
     * 대역의 마지막 주소(브로드캐스트 주소)를 돌려줍니다.
     *
     * <p>⚠️ 호스트 비트를 전부 1 로 채운 값입니다. {@code /31}(RFC 3021)이나
     * {@code /32} 처럼 브로드캐스트가 정의되지 않는 대역에서도 "범위의 끝"
     * 이라는 의미로 같은 값을 돌려줍니다.
     *
     * <pre>
     *   10.99.10.0/24 → 10.99.10.255
     *   10.99.10.0/0  → 255.255.255.255
     *   10.99.10.1/32 → 10.99.10.1
     * </pre>
     *
     * @return 대역의 마지막 주소
     */
    public Ip getEndIp() {
        final long hostMask = ~Ip.maskOf(_prefixLength) & Ip.MASK;
        return new Ip(_ip.asLong() | hostMask);
    }

    /**
     * 대역에 포함된 주소 개수를 돌려줍니다. ({@code 2^(32-len)})
     *
     * @return 주소 개수 ({@code /0} 이면 {@code 4294967296})
     */
    public long size() {
        return 1L << (Ip.BITS - _prefixLength);
    }

    /**
     * 주어진 주소가 이 대역에 포함되는지 확인합니다.
     *
     * <p>구현은 "같은 접두사 길이의 마스크를 씌운 값이 같은가" 입니다.
     * 범위 비교({@code start <= ip <= end})보다 경계 실수가 적고,
     * 호스트 비트가 켜진 입력도 자연스럽게 처리됩니다.
     *
     * @param ip 확인할 주소 (null 이면 {@code false})
     * @return 포함되면 {@code true}
     */
    public boolean contains(Ip ip) {
        if (ip == null) {
            return false;
        }
        return _ip.asLong() == ip.withMask(_prefixLength).asLong();
    }

    /**
     * 주어진 대역이 이 대역에 완전히 포함되는지 확인합니다.
     *
     * <p>이름을 {@code contains} 로 오버로드하지 않은 이유: {@code contains(Ip)}
     * 와 함께 두면 {@code net.contains(net)} 같은 호출이 <b>모호</b>해져
     * 컴파일 오류가 납니다. 주소와 대역은 의미도 다르므로 이름을 나눕니다.
     *
     * @param other 확인할 대역 (null 이면 {@code false})
     * @return {@code other} 의 모든 주소가 이 대역에 속하면 {@code true}
     */
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

    /**
     * 두 대역이 겹치는지 확인합니다.
     *
     * @param other 확인할 대역 (null 이면 {@code false})
     * @return 공통 주소가 있으면 {@code true}
     */
    public boolean overlaps(Prefix other) {
        if (other == null) {
            return false;
        }
        final int shorter = Math.min(this._prefixLength, other._prefixLength);
        return this._ip.withMask(shorter).asLong() == other._ip.withMask(shorter).asLong();
    }

    /**
     * CIDR 문자열로 만듭니다. (예: {@code 10.99.10.0/24})
     *
     * <p>{@link #parse(String)} 가 다시 읽을 수 있는 형태입니다. 이전 구현은
     * 숫자를 그대로 돌려줘 로그에서 읽을 수 없었습니다.
     *
     * @return CIDR 표기
     */
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

    /**
     * 더 넓은 대역이 앞에 오도록 비교합니다.
     *
     * <p>같은 접두사 길이면 네트워크 주소 순서입니다. 라우팅 테이블을
     * 출력할 때 상위 대역부터 보이게 하려는 것입니다.
     *
     * @param other 비교 대상
     * @return 음수/0/양수
     */
    @Override
    public int compareTo(Prefix other) {
        final int byLength = Integer.compare(this._prefixLength, other._prefixLength);
        if (byLength != 0) {
            return byLength;
        }
        return this._ip.compareTo(other._ip);
    }
}
