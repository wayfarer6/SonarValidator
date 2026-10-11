package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Ip;
import org.sonar.sonarvalidator_backend.Model.Ip6;
import org.sonar.sonarvalidator_backend.Model.Prefix;

/**
 * 값 객체 {@link Ip}, {@link Ip6}, {@link Prefix} 의 동작을 고정합니다.
 *
 * <h2>왜 회귀 테스트가 필요한가</h2>
 * <p>이전 구현에는 <b>예외 없이 조용히 틀린 값을 돌려주는</b> 결함이 있었습니다.
 * 대표적으로:
 * <ul>
 *   <li>{@code Prefix.contains()} 가 포함 판정이 아니라 주소 동일 비교라
 *       {@code 10.99.10.0/24} 가 {@code 10.99.10.255} 를 부정했습니다.</li>
 *   <li>{@code Prefix.getEndIp()} 가 {@code /0} 에서 시프트 {@code mod 64} 로
 *       엉뚱한 마스크를 썼습니다.</li>
 *   <li>{@code Prefix.parse()} 가 항상 빈 객체를 돌려주는 스텁이었습니다.</li>
 * </ul>
 * 이런 오류는 예외를 던지지 않아서 <b>테스트로만</b> 잡힙니다. 아래 각 테스트는
 * 실제 랩 주소(10.99.10.x, 172.16.255.x, 192.168.122.x)를 씁니다.
 */
class IpValueObjectTest {

    // 랩 실측 값 (주소는 그대로 쓰고, long 은 검산해서 고정합니다)
    private static final String GATEWAY_ROUTER = "10.99.10.1";
    private static final String DMZ_ROUTER = "10.99.10.3";
    private static final String HOST_ENS4 = "192.168.122.58";

    /** 10.99.10.1 = 0x0A630A01 */
    private static final long GATEWAY_ROUTER_LONG = 0x0A630A01L;

    /** 192.168.122.58 = 0xC0A87A3A */
    private static final long HOST_ENS4_LONG = 0xC0A87A3AL;

    @Nested
    @DisplayName("Ip (IPv4)")
    class Ipv4Tests {

        @Test
        @DisplayName("점 표기 파싱과 long 변환이 일치한다")
        void parsesDotted() {
            final Ip ip = Ip.parse(GATEWAY_ROUTER);

            assertEquals(GATEWAY_ROUTER_LONG, ip.asLong());
            assertEquals(GATEWAY_ROUTER_LONG, ip.get_ip());
            assertEquals(174262785L, ip.asLong(), "10진수로도 확인");
            assertEquals(GATEWAY_ROUTER, ip.toString());
            assertEquals(HOST_ENS4, Ip.parse(HOST_ENS4).toString());
            assertEquals("0.0.0.0", Ip.parse("0.0.0.0").toString());
            assertEquals("255.255.255.255", Ip.parse("255.255.255.255").toString());
        }

        @Test
        @DisplayName("192.168 이상 주소도 음수로 변하지 않는다 (long 사용 이유)")
        void staysUnsigned() {
            // int 였다면 0xC0A87A3A 가 음수로 보입니다.
            final Ip ip = Ip.parse(HOST_ENS4);

            assertTrue(ip.asLong() > 0, "부호 없는 값이어야 합니다");
            assertEquals(HOST_ENS4_LONG, ip.asLong());
            assertEquals(3232266810L, ip.asLong(), "10진수로도 확인");
        }

        @Test
        @DisplayName("이전 구현의 toString 은 숫자를 돌려줬다 — 이제 점 표기다")
        void toStringIsHumanReadable() {
            final Ip ip = Ip.parse(DMZ_ROUTER);

            assertNotEquals("174262787", ip.toString());
            assertEquals(DMZ_ROUTER, ip.toString());
        }

        @Test
        @DisplayName("값이 같으면 equals 가 참이고 Map 키로 쓸 수 있다")
        void valueEquality() {
            // 예전에는 equals 가 없어 Object 동일성 비교였습니다.
            assertEquals(Ip.parse(GATEWAY_ROUTER), Ip.parse(GATEWAY_ROUTER));
            assertEquals(Ip.parse(GATEWAY_ROUTER).hashCode(),
                    Ip.parse(GATEWAY_ROUTER).hashCode());
            assertNotEquals(Ip.parse(GATEWAY_ROUTER), Ip.parse(DMZ_ROUTER));

            final Map<Ip, String> owners = Map.of(Ip.parse(GATEWAY_ROUTER), "Gateway-Router");
            assertEquals("Gateway-Router", owners.get(Ip.parse(GATEWAY_ROUTER)));
        }

        @Test
        @DisplayName("주소 순서로 정렬된다")
        void sortsByValue() {
            final List<Ip> sorted = List.of(
                    Ip.parse("192.168.122.1"), Ip.parse("10.99.10.1"), Ip.parse("10.99.10.3"))
                    .stream().sorted().toList();

            assertEquals("10.99.10.1", sorted.get(0).toString());
            assertEquals("10.99.10.3", sorted.get(1).toString());
            assertEquals("192.168.122.1", sorted.get(2).toString());
        }

        @Test
        @DisplayName("32비트 범위를 벗어나면 생성 시점에 거부한다")
        void rejectsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () -> new Ip(-1L));
            assertThrows(IllegalArgumentException.class, () -> new Ip(0x100000000L));
        }

        @Test
        @DisplayName("형식이 틀린 문자열은 예외로 알려준다")
        void rejectsMalformed() {
            assertThrows(IllegalArgumentException.class, () -> Ip.parse(null));
            assertThrows(IllegalArgumentException.class, () -> Ip.parse("   "));
            assertThrows(IllegalArgumentException.class, () -> Ip.parse("10.99.10"));
            assertThrows(IllegalArgumentException.class, () -> Ip.parse("10.99.10.1.5"));
            assertThrows(IllegalArgumentException.class, () -> Ip.parse("10.99.10.256"));
            assertThrows(IllegalArgumentException.class, () -> Ip.parse("10.99.10."));
            assertThrows(IllegalArgumentException.class, () -> Ip.parse("10.99.10.a"));
            assertThrows(IllegalArgumentException.class, () -> Ip.parse("10.99.10.1234"));
        }

        @Test
        @DisplayName("CIDR 은 주소가 아니므로 거부하고 Prefix 를 안내한다")
        void rejectsCidr() {
            final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> Ip.parse("10.99.10.0/24"));
            assertTrue(ex.getMessage().contains("Prefix.parse"), "안내 문구가 있어야 합니다");
        }

        @Test
        @DisplayName("선행 0 이 있어도 10진수로 읽는다 (8진수 오해 방지)")
        void treatsLeadingZeroAsDecimal() {
            // "010" 을 8진수로 읽으면 10.99.10.8 이라는 다른 주소가 됩니다.
            assertEquals("10.99.10.10", Ip.parse("10.99.10.010").toString());
        }

        @Test
        @DisplayName("withMask 가 호스트 비트를 지운다")
        void appliesMask() {
            assertEquals("10.99.10.0", Ip.parse("10.99.10.7").withMask(24).toString());
            assertEquals("10.99.10.7", Ip.parse("10.99.10.7").withMask(32).toString());
            assertEquals("0.0.0.0", Ip.parse("10.99.10.7").withMask(0).toString());
            assertEquals("10.99.0.0", Ip.parse("10.99.10.7").withMask(16).toString());
        }

        @Test
        @DisplayName("접두사 마스크는 /0 과 /32 경계에서도 정확하다")
        void maskBoundaries() {
            // 이전 코드는 (1L << (32 - len)) - 1 이라 /0 에서 시프트 mod 64 로
            // 31비트 마스크가 나왔습니다.
            assertEquals(0L, Ip.maskOf(0));
            assertEquals(0xFF000000L, Ip.maskOf(8));
            assertEquals(0xFFFFFF00L, Ip.maskOf(24));
            assertEquals(0xFFFFFFFFL, Ip.maskOf(32));
            assertThrows(IllegalArgumentException.class, () -> Ip.maskOf(-1));
            assertThrows(IllegalArgumentException.class, () -> Ip.maskOf(33));
        }
    }

    @Nested
    @DisplayName("Prefix (IPv4 대역)")
    class PrefixTests {

        @Test
        @DisplayName("CIDR 를 파싱한다 (이전 구현은 빈 객체를 돌려주는 스텁이었다)")
        void parsesCidr() {
            final Prefix net = Prefix.parse("10.99.10.0/24");

            assertEquals("10.99.10.0/24", net.toString());
            assertEquals("10.99.10.0", net.getStartIp().toString());
            assertEquals(24, net.get_prefixLength());
        }

        @Test
        @DisplayName("호스트 비트가 켜진 입력은 생성 시 정규화된다")
        void normalizesHostBits() {
            // 10.99.10.7/24 은 10.99.10.0/24 와 같은 대역이어야 합니다.
            assertEquals(Prefix.parse("10.99.10.0/24"), Prefix.parse("10.99.10.7/24"));
            assertEquals("10.99.10.0/24", Prefix.parse("10.99.10.7/24").toString());
        }

        @Test
        @DisplayName("길이를 생략하면 /32 로 본다")
        void defaultsTo32() {
            assertEquals("10.99.10.1/32", Prefix.parse("10.99.10.1").toString());
        }

        @Test
        @DisplayName("contains 가 접두사 기준으로 판정한다 (이전 구현의 핵심 버그)")
        void containsChecksWholeRange() {
            // 이전 코드는 _ip.asLong() == ip.get_ip() 라 "대역 시작 주소만" 참이었습니다.
            final Prefix net = Prefix.parse("10.99.10.0/24");

            assertTrue(net.contains(Ip.parse("10.99.10.0")), "네트워크 주소 포함");
            assertTrue(net.contains(Ip.parse("10.99.10.7")), "중간 주소 포함");
            assertTrue(net.contains(Ip.parse("10.99.10.255")), "브로드캐스트 주소 포함");
            assertFalse(net.contains(Ip.parse("10.99.11.1")), "다음 대역은 제외");
            assertFalse(net.contains(Ip.parse("10.99.9.255")), "이전 대역은 제외");
            assertFalse(net.contains(null), "null 은 포함되지 않음");
        }

        @Test
        @DisplayName("getEndIp 가 대역 끝을 돌려준다 (/0 경계 포함)")
        void endIp() {
            // 이전 코드는 /0 에서 1L << 32 가 mod 64 로 동작해 틀린 값을 냈습니다.
            assertEquals("10.99.10.255", Prefix.parse("10.99.10.0/24").getEndIp().toString());
            assertEquals("255.255.255.255", Prefix.parse("0.0.0.0/0").getEndIp().toString());
            assertEquals("10.99.10.1", Prefix.parse("10.99.10.1/32").getEndIp().toString());
            assertEquals("10.99.255.255", Prefix.parse("10.99.0.0/16").getEndIp().toString());
        }

        @Test
        @DisplayName("size 가 주소 개수를 돌려준다")
        void size() {
            assertEquals(256L, Prefix.parse("10.99.10.0/24").size());
            assertEquals(1L, Prefix.parse("10.99.10.1/32").size());
            assertEquals(4294967296L, Prefix.parse("0.0.0.0/0").size());
        }

        @Test
        @DisplayName("대역 포함과 겹침을 판정한다")
        void nestingAndOverlap() {
            final Prefix broad = Prefix.parse("10.99.0.0/16");
            final Prefix narrow = Prefix.parse("10.99.10.0/24");

            assertTrue(broad.containsPrefix(narrow), "좁은 대역은 넓은 대역에 포함");
            assertFalse(narrow.containsPrefix(broad), "넓은 대역은 좁은 대역에 포함되지 않음");
            assertTrue(broad.overlaps(narrow));
            assertFalse(broad.overlaps(Prefix.parse("10.100.0.0/16")));
            assertTrue(broad.containsPrefix(broad), "자기 자신은 포함");
        }

        @Test
        @DisplayName("parseOrNull 이 기본 경로 표기를 흡수한다")
        void looseParsing() {
            assertEquals("0.0.0.0/0", Prefix.parseOrNull("default").toString());
            assertEquals("0.0.0.0/0", Prefix.parseOrNull("DEFAULT").toString());
            assertEquals("10.99.10.0/24", Prefix.parseOrNull(" 10.99.10.0/24 ").toString());
            assertNull(Prefix.parseOrNull(null));
            assertNull(Prefix.parseOrNull(""));
            assertNull(Prefix.parseOrNull("not-a-prefix"));
        }

        @Test
        @DisplayName("널리 쓰이는 대역을 값으로 비교·정렬한다")
        void valueEqualityAndOrder() {
            assertEquals(Prefix.parse("10.99.10.0/24"), Prefix.parse("10.99.10.255/24"));
            assertNotEquals(Prefix.parse("10.99.10.0/24"), Prefix.parse("10.99.10.0/25"));

            final List<Prefix> sorted = List.of(
                    Prefix.parse("10.99.10.0/24"), Prefix.parse("0.0.0.0/0"),
                    Prefix.parse("10.99.0.0/16")).stream().sorted().toList();

            assertEquals("0.0.0.0/0", sorted.get(0).toString(), "가장 넓은 대역이 앞");
            assertEquals("10.99.0.0/16", sorted.get(1).toString());
            assertEquals("10.99.10.0/24", sorted.get(2).toString());
        }

        @Test
        @DisplayName("형식 오류는 예외로 알려준다")
        void rejectsMalformed() {
            assertThrows(IllegalArgumentException.class, () -> Prefix.parse(null));
            assertThrows(IllegalArgumentException.class, () -> Prefix.parse(""));
            assertThrows(IllegalArgumentException.class, () -> Prefix.parse("10.99.10.0/"));
            assertThrows(IllegalArgumentException.class, () -> Prefix.parse("10.99.10.0/33"));
            assertThrows(IllegalArgumentException.class, () -> Prefix.parse("10.99.10.0/24/8"));
            assertThrows(IllegalArgumentException.class, () -> Prefix.parse("10.99.10.0/abc"));
            assertThrows(IllegalArgumentException.class,
                    () -> new Prefix(null, 24));
            assertThrows(IllegalArgumentException.class,
                    () -> new Prefix(Ip.ZERO, 40));
        }

        @Test
        @DisplayName("toString 이 다시 parse 가능한 형태다 (로그 왕복)")
        void toStringRoundTrips() {
            for (final String cidr : List.of("0.0.0.0/0", "10.99.10.0/24", "192.168.122.58/32")) {
                assertEquals(cidr, Prefix.parse(Prefix.parse(cidr).toString()).toString());
            }
        }
    }

    @Nested
    @DisplayName("Ip6 (IPv6)")
    class Ipv6Tests {

        @Test
        @DisplayName("축약 표기를 파싱하고 다시 축약해 출력한다")
        void parsesCompressed() {
            assertEquals("2001:db8::1", Ip6.parse("2001:db8::1").toString());
            assertEquals("::1", Ip6.parse("::1").toString());
            assertEquals("::", Ip6.parse("::").toString());
            assertEquals("fe80::1", Ip6.parse("fe80::1").toString());
        }

        @Test
        @DisplayName("전체 표기와 축약 표기가 같은 값이다")
        void expandedEqualsCompressed() {
            final Ip6 expanded = Ip6.parse("2001:0db8:0000:0000:0000:0000:0000:0001");
            final Ip6 compressed = Ip6.parse("2001:db8::1");

            assertEquals(expanded, compressed);
            assertEquals(expanded.hashCode(), compressed.hashCode());
            assertEquals("2001:0db8:0000:0000:0000:0000:0000:0001", expanded.toExpanded());
            assertEquals(compressed.toExpanded(), expanded.toExpanded());
        }

        @Test
        @DisplayName("후위 IPv4 혼합 표기를 파싱한다")
        void parsesEmbeddedIpv4() {
            // ::ffff:192.168.122.58  →  IPv4-mapped 주소
            final Ip6 mapped = Ip6.parse("::ffff:192.168.122.58");
            final Ip6 hexForm = Ip6.parse("::ffff:c0a8:7a3a");

            assertEquals(hexForm, mapped);
            assertEquals(hexForm.toExpanded(), mapped.toExpanded());
        }

        @Test
        @DisplayName("128비트 최대값도 값으로 보존된다")
        void handlesMaximum() {
            final Ip6 max = Ip6.parse("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");

            assertEquals(BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE), max.value());
            assertEquals("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", max.toString(),
                    "접을 0 구간이 없어야 합니다");
        }

        @Test
        @DisplayName("0 그룹이 하나뿐이면 접지 않는다 (RFC 5952)")
        void compressesOnlyLongestRun() {
            // 가장 긴 0 구간 뒤(마지막)를 접습니다.
            assertEquals("2001:db8::1", Ip6.parse("2001:db8:0:0:0:0:0:1").toString());
            // 0 이 하나뿐이면 "::" 를 쓰지 않습니다.
            assertEquals("2001:db8:0:1:1:1:1:1", Ip6.parse("2001:db8:0:1:1:1:1:1").toString());
        }

        @Test
        @DisplayName("값 동등성과 정렬이 동작한다")
        void equalityAndOrder() {
            assertNotEquals(Ip6.parse("2001:db8::1"), Ip6.parse("2001:db8::2"));

            final List<Ip6> sorted = List.of(
                    Ip6.parse("2001:db8::2"), Ip6.parse("::1"), Ip6.parse("2001:db8::1"))
                    .stream().sorted().toList();

            assertEquals("::1", sorted.get(0).toString());
            assertEquals("2001:db8::1", sorted.get(1).toString());
            assertEquals("2001:db8::2", sorted.get(2).toString());
        }

        @Test
        @DisplayName("withMask 가 호스트 비트를 지운다")
        void appliesMask() {
            assertEquals("2001:db8::",
                    Ip6.parse("2001:db8::abcd").withMask(32).toString());
            assertEquals("2001:db8::abcd",
                    Ip6.parse("2001:db8::abcd").withMask(128).toString());
            assertEquals("::", Ip6.parse("2001:db8::abcd").withMask(0).toString());
            assertEquals("2001:db8::", Ip6.parse("2001:db8:1:2::9").withMask(32).toString());
        }

        @Test
        @DisplayName("부호 없는 128비트만 허용한다")
        void rejectsOutOfRange() {
            assertThrows(IllegalArgumentException.class, () -> new Ip6(null));
            assertThrows(IllegalArgumentException.class, () -> new Ip6(BigInteger.valueOf(-1)));
            assertThrows(IllegalArgumentException.class,
                    () -> new Ip6(BigInteger.ONE.shiftLeft(128)));
        }

        @Test
        @DisplayName("형식 오류와 CIDR/존 ID 를 거부한다")
        void rejectsMalformed() {
            assertThrows(IllegalArgumentException.class, () -> Ip6.parse(null));
            assertThrows(IllegalArgumentException.class, () -> Ip6.parse("  "));
            assertThrows(IllegalArgumentException.class, () -> Ip6.parse("2001:db8::1/64"));
            assertThrows(IllegalArgumentException.class, () -> Ip6.parse("fe80::1%eth0"));
            assertThrows(IllegalArgumentException.class, () -> Ip6.parse("2001:db8::1::2"));
            assertThrows(IllegalArgumentException.class, () -> Ip6.parse("2001:db8"));
            assertThrows(IllegalArgumentException.class, () -> Ip6.parse("2001:db8:0:0:0:0:0:0:1"));
            assertThrows(IllegalArgumentException.class, () -> Ip6.parse("20g1::1"));
        }

        @Test
        @DisplayName("isZero 와 ZERO 상수")
        void zero() {
            assertTrue(Ip6.parse("::").isZero());
            assertTrue(Ip6.parse("0:0:0:0:0:0:0:0").isZero());
            assertFalse(Ip6.parse("::1").isZero());
            assertTrue(Ip6.ZERO.isZero());
        }
    }
}
