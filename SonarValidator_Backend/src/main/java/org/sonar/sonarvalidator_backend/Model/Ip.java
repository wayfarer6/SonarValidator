package org.sonar.sonarvalidator_backend.Model;

/**
 * IPv4 주소 <b>값 객체</b>입니다. (Batfish 의 {@code org.batfish.datamodel.Ip} 대응)
 *
 * <h2>왜 {@code long} 에 담는가</h2>
 * <p>32비트 주소를 부호 없는 정수로 다룹니다. Java 에는 부호 없는 정수 타입이
 * 없어 {@code int} 에 담으면 {@code 192.168.0.1} 이상의 주소가 음수로 보이고,
 * 마스킹·비교에서 부호 확장 실수가 생깁니다. {@code long} 에 담으면
 * {@code 0 ~ 4294967295} 가 그대로 유지됩니다.
 * ({@code Policy.PacketVariables} 가 BDD 입력으로 {@code int} 를 쓰는 것은
 * 비트 연산 전용이라 별개입니다.)
 *
 * <h2>값 객체로서 지켜지는 것</h2>
 * <ul>
 *   <li><b>불변</b> — 생성 후 바뀌지 않으므로 방어적 복사가 필요 없습니다.</li>
 *   <li><b>범위 검증</b> — 32비트를 벗어난 값은 생성 시점에 거부합니다.
 *       잘못된 값이 라우팅 계산까지 흘러가면 원인을 찾기 어렵습니다.</li>
 *   <li><b>값 동등성</b> — {@code equals}/{@code hashCode} 를 값으로 구현해
 *       맵 키로 쓸 수 있습니다. (예: 방문한 주소 집합)</li>
 * </ul>
 *
 * <pre>
 *   Ip addr = Ip.parse("10.99.10.1");
 *   addr.asLong();      // 174654977
 *   addr.toString();    // "10.99.10.1"   (점 표기)
 *   Ip.parse("10.99.10.7").withMask(24);  // 10.99.10.0
 * </pre>
 */
public final class Ip implements Comparable<Ip> {

    /** 주소가 차지하는 비트 수. */
    public static final int BITS = 32;

    /** 32비트 전체 마스크({@code 0xFFFFFFFF}). */
    public static final long MASK = 0xFFFFFFFFL;

    /** {@code 0.0.0.0}. 기본 경로의 네트워크 주소로 자주 씁니다. */
    public static final Ip ZERO = new Ip(0L);

    /** 32비트 주소 값 ({@code 0 ~ 2^32-1}). */
    private final long _ip;

    /**
     * 주소 값을 직접 지정해 만듭니다.
     *
     * @param value 32비트 주소 ({@code 0 ~ 4294967295})
     * @throws IllegalArgumentException 범위를 벗어나면
     */
    public Ip(long value) {
        if (value < 0L || value > MASK) {
            throw new IllegalArgumentException(
                    "ipv4 out of range: " + value + " (expected 0.." + MASK + ")");
        }
        this._ip = value;
    }

    /**
     * 점 표기 문자열을 파싱합니다. (예: {@code "10.99.10.1"}, {@code "0.0.0.0"})
     *
     * <p>앞뒤 공백은 무시합니다. 옥텟은 <b>10진수</b>로만 해석합니다 — {@code "010"}
     * 을 8진수 8 로 읽으면 조용히 다른 주소가 되므로, 선행 0 이 있어도 10 으로
     * 봅니다.
     *
     * <p>CIDR({@code "10.99.10.0/24"})은 주소가 아니라 대역이므로 거부합니다.
     * 대역이 필요하면 {@link Prefix#parse(String)} 를 쓰세요.
     *
     * @param text 점 표기 주소
     * @return 주소
     * @throws IllegalArgumentException null/공백/형식 오류/범위 초과
     */
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

    /**
     * 주소 값을 돌려줍니다.
     *
     * @return 32비트 주소 ({@code 0 ~ 4294967295})
     */
    public long asLong() {
        return this._ip;
    }

    /**
     * 주소 값을 돌려줍니다. ({@link #asLong()} 과 같은 값)
     *
     * <p>기존 코드가 Lombok 게터 이름({@code get_ip()})을 쓰고 있어 남겨 둡니다.
     *
     * @return 32비트 주소
     */
    public long get_ip() {
        return this._ip;
    }

    /**
     * 주소가 0 인지 확인합니다.
     *
     * @return {@code 0.0.0.0} 이면 {@code true}
     */
    public boolean isZero() {
        return this._ip == 0L;
    }

    /**
     * 주어진 접두사 길이의 마스크를 적용한 네트워크 주소를 돌려줍니다.
     *
     * <p>호스트 비트를 0 으로 만듭니다. {@link Prefix} 가 대역을 정규화할 때
     * 쓰는 연산이며, "이 주소가 속한 네트워크" 를 구할 때도 씁니다.
     *
     * <pre>
     *   Ip.parse("10.99.10.7").withMask(24)   // 10.99.10.0
     *   Ip.parse("10.99.10.7").withMask(32)   // 10.99.10.7
     *   Ip.parse("10.99.10.7").withMask(0)    // 0.0.0.0
     * </pre>
     *
     * @param prefixLength 접두사 길이 ({@code 0 ~ 32})
     * @return 호스트 비트를 지운 주소
     * @throws IllegalArgumentException 범위를 벗어나면
     */
    public Ip withMask(int prefixLength) {
        return new Ip(this._ip & maskOf(prefixLength));
    }

    /**
     * 접두사 길이에 해당하는 32비트 마스크를 만듭니다.
     *
     * <p>⚠️ {@code (1L << (32 - len)) - 1} 방식은 {@code len == 0} 일 때
     * Java 의 시프트가 {@code mod 64} 로 동작해 엉뚱한 값이 나옵니다.
     * 그래서 경계를 먼저 처리합니다.
     *
     * @param prefixLength 접두사 길이 ({@code 0 ~ 32})
     * @return 마스크 ({@code /0 → 0}, {@code /32 → 0xFFFFFFFF})
     * @throws IllegalArgumentException 범위를 벗어나면
     */
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

    /**
     * 점 표기 문자열로 만듭니다. (예: {@code 10.99.10.1})
     *
     * <p>로그에 주소가 숫자로 찍히면 사람이 읽을 수 없으므로, 표현은 항상
     * 점 표기입니다. 숫자 값이 필요하면 {@link #asLong()} 을 쓰세요.
     *
     * @return 점 표기 주소
     */
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

    /**
     * 주소 순서로 비교합니다. ({@code 0.0.0.0} 이 가장 작음)
     *
     * <p>대역을 정렬해 출력하거나 최단 경로를 고를 때 씁니다.
     *
     * @param other 비교 대상
     * @return 음수/0/양수
     */
    @Override
    public int compareTo(Ip other) {
        return Long.compare(this._ip, other._ip);
    }
}
