package org.sonar.sonarvalidator_backend.Model;

import java.math.BigInteger;
import java.util.Locale;

/**
 * IPv6 주소 <b>값 객체</b>입니다. ({@link Ip} 의 128비트 짝)
 *
 * <h2>왜 {@link BigInteger} 인가</h2>
 * <p>128비트는 {@code long}(64비트)에 담기지 않습니다. 상위/하위 64비트로
 * 쪼개는 방법도 있지만, 비교·마스킹·정렬을 매번 손으로 구현해야 하고 실수하기
 * 쉽습니다. {@link BigInteger} 는 임의 정밀도라 128비트를 그대로 담고
 * {@code compareTo}/{@code and} 를 이미 제공합니다.
 * ({@code Policy.PacketVariables} 는 IPv4 전용이라 여기 영향받지 않습니다.)
 *
 * <h2>부호 없는 값으로 다룹니다</h2>
 * <p>IPv6 주소는 부호 없는 128비트입니다. {@link BigInteger} 는 부호를 가지므로
 * <b>음수와 2^128 이상을 생성 시점에 거부</b>합니다. 이렇게 해야 {@code toString}
 * 이 {@code -...} 같은 값을 만들지 않습니다.
 *
 * <pre>
 *   Ip6 addr = Ip6.parse("2001:db8::1");
 *   addr.toString();        // "2001:db8::1"  (축약)
 *   addr.toExpanded();      // "2001:0db8:0000:...:0001"
 * </pre>
 */
public final class Ip6 implements Comparable<Ip6> {

    /** 주소가 차지하는 비트 수. */
    public static final int BITS = 128;

    /**
     * 128비트 전체 마스크.
     *
     * <p>⚠️ <b>{@link #ZERO} 보다 먼저 선언되어야 합니다.</b> 정적 필드는
     * 선언 순서대로 초기화되므로, {@code ZERO} 가 앞에 있으면 그 생성자가
     * 아직 {@code null} 인 {@code MASK} 를 참조해
     * {@code ExceptionInInitializerError} 로 클래스 자체가 로드되지 않습니다.
     * (실제로 이 순서 때문에 모든 IPv6 테스트가 {@code NoClassDefFoundError}
     * 로 무너졌습니다. 테스트가 잡아준 버그입니다.)
     */
    private static final BigInteger MASK =
            BigInteger.ONE.shiftLeft(BITS).subtract(BigInteger.ONE);

    /** {@code ::} (모두 0). */
    public static final Ip6 ZERO = new Ip6(BigInteger.ZERO);

    /** 128비트 부호 없는 주소 값 ({@code 0 ~ 2^128-1}). */
    private final BigInteger _ip6;

    /**
     * 주소 값을 직접 지정해 만듭니다.
     *
     * @param value 128비트 부호 없는 값
     * @throws IllegalArgumentException null/음수/2^128 이상이면
     */
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

    /**
     * 콜론 표기 문자열을 파싱합니다.
     *
     * <p>지원하는 형태:
     * <ul>
     *   <li>전체 표기 — {@code 2001:0db8:0000:0000:0000:0000:0000:0001}</li>
     *   <li>축약 — {@code 2001:db8::1}, {@code ::1}, {@code ::}</li>
     *   <li>후위 IPv4 혼합 — {@code ::ffff:192.168.122.58}</li>
     * </ul>
     *
     * <p>지원하지 않는 것: <b>CIDR</b>({@code /64}) — 대역은 {@link Prefix6} 의
     * 몫입니다. <b>존 ID</b>({@code fe80::1%eth0}) — 링크 로컬 주소에 붙는
     * 인터페이스 표시는 주소 자체가 아니므로 거부합니다.
     *
     * @param text 콜론 표기 주소
     * @return 주소
     * @throws IllegalArgumentException null/공백/형식 오류
     */
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

        // 마지막 토큰이 IPv4 혼합이면 16비트 그룹 2개 대신 32비트 값 1개를
        // 더해야 합니다. 위 루프는 그룹 2개로 세어 자리를 비워 두었으므로
        // 여기서 남은 16비트를 채웁니다.
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

    /**
     * 16진 그룹 문자열을 값으로 바꿉니다. IPv4 혼합 토큰은 상위 16비트만 씁니다.
     *
     * @param part      그룹 토큰 (예: {@code db8}, {@code 192.168.122.58})
     * @param original  오류 메시지용 원문
     * @return 16비트 값
     */
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

    /**
     * 그룹 문자열을 나누고 기본 형식을 검사합니다.
     *
     * @param section  {@code ::} 를 기준으로 나뉜 한쪽 (빈 문자열 가능)
     * @param original 오류 메시지용 원문
     * @return 그룹 배열 (IPv4 혼합은 마지막 한 덩어리로 남습니다)
     */
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

    /**
     * 그룹 수를 셉니다. 마지막이 IPv4 혼합이면 2 그룹으로 봅니다.
     *
     * @param parts    그룹 배열
     * @param original 오류 메시지용 원문
     * @return 16비트 그룹 기준 개수
     */
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

    /**
     * 주소 값을 돌려줍니다.
     *
     * @return 128비트 값
     */
    public BigInteger value() {
        return this._ip6;
    }

    /**
     * 주소 값을 돌려줍니다. ({@link #value()} 와 같은 값)
     *
     * <p>기존 코드가 Lombok 게터 이름({@code get_ip6()})을 쓰고 있어 남겨 둡니다.
     *
     * @return 128비트 값
     */
    public BigInteger get_ip6() {
        return this._ip6;
    }

    /**
     * 주소가 0 인지 확인합니다.
     *
     * @return {@code ::} 이면 {@code true}
     */
    public boolean isZero() {
        return this._ip6.signum() == 0;
    }

    /**
     * 주어진 접두사 길이의 마스크를 적용한 네트워크 주소를 돌려줍니다.
     *
     * @param prefixLength 접두사 길이 ({@code 0 ~ 128})
     * @return 호스트 비트를 지운 주소
     * @throws IllegalArgumentException 범위를 벗어나면
     */
    public Ip6 withMask(int prefixLength) {
        return new Ip6(this._ip6.and(maskOf(prefixLength)));
    }

    /**
     * 접두사 길이에 해당하는 128비트 마스크를 만듭니다.
     *
     * <p>⚠️ 마스크는 <b>상위</b> {@code prefixLength} 비트가 1 입니다.
     * {@code MASK.shiftRight(128 - len)} 로 만들면 <b>하위</b> 비트가 1 인
     * 마스크가 되어, {@code /32} 가 "앞 32비트 유지" 가 아니라 "뒤 32비트만
     * 유지" 로 동작합니다. ({@code 2001:db8::abcd/32} 가 {@code ::abcd} 가 됨)
     * 그래서 아래처럼 {@code (2^len - 1)} 을 좌측 정렬해 만듭니다.
     *
     * @param prefixLength 접두사 길이 ({@code 0 ~ 128})
     * @return 마스크 ({@code /0 → 0}, {@code /32 → 0xFFFFFFFF000...0})
     * @throws IllegalArgumentException 범위를 벗어나면
     */
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

    /**
     * 축약 표기 문자열로 만듭니다. (RFC 5952)
     *
     * <p>가장 긴 0 그룹 구간을 찾아 {@code ::} 로 접고, 그 앞뒤는 소문자 16진수로
     * 씁니다. 그룹의 선행 0 은 제거합니다. 0 그룹이 하나뿐이면 접지 않습니다 —
     * {@code 2001:0:0:1:0:0:0:1} 에서 어느 쪽을 접을지 모호해지고, RFC 도
     * 최장 구간만 접도록 정하고 있습니다.
     *
     * @return 축약 주소 (예: {@code 2001:db8::1})
     */
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

    /**
     * 선행 0 을 채운 전체 표기를 만듭니다. (예: {@code 2001:0db8:0000:...:0001})
     *
     * <p>축약 표기는 사람이 읽기엔 좋지만 정렬·비교용 문자열로는 쓸 수 없습니다.
     * ({@code "2001:db8::1"} 과 {@code "2001:db8::01"} 이 다르게 정렬됨)
     * 사전순 비교가 필요하면 이 메서드를 쓰세요.
     *
     * @return 8그룹 × 4자리 전체 표기
     */
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

    /**
     * 값을 16비트 그룹 8개로 풀어놓습니다.
     *
     * @return 길이 8 배열 (앞이 최상위)
     */
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

    /**
     * 주소 순서로 비교합니다.
     *
     * @param other 비교 대상
     * @return 음수/0/양수
     */
    @Override
    public int compareTo(Ip6 other) {
        return this._ip6.compareTo(other._ip6);
    }
}
