package org.sonar.sonarvalidator_backend.Policy;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * BDD 변수 배치와 패킷 값 ↔ 이진 표현 변환을 담당합니다.
 *
 * <h2>왜 인코딩이 필요한가</h2>
 * <p>BDD 는 <b>불리언 변수</b>만 다룹니다. 그래서 "10.10.131.0/24 에서 오는
 * 패킷" 같은 조건을 변수들의 논리식으로 바꿔야 합니다. 방식은 단순합니다 —
 * 패킷을 이진수 여러 개로 보고, 각 비트를 변수 하나에 대응시킵니다.
 *
 * <pre>
 *   변수 배치 (총 80 비트)
 *   ┌───────────────┬───────────────┬──────────┐
 *   │ 출발지 IP 32  │ 목적지 IP 32  │ 포트 16  │
 *   └───────────────┴───────────────┴──────────┘
 *    0           31  32          63  64      79
 * </pre>
 *
 * <p>비트 순서는 <b>MSB 먼저</b>입니다. 이렇게 두면 접두사(prefix) 조건이
 * 자연스럽게 "앞쪽 비트가 주어진 값과 같다" 로 표현됩니다. 예를 들어
 * {@code /24} 는 상위 24개 비트만 고정하고 하위 8개 비트를 자유로 둡니다.
 *
 * <h2>주소 미지정 표현</h2>
 * <p>{@code 0.0.0.0/0} 처럼 전체를 뜻하는 접두사는 어떤 비트도 제약하지 않으므로
 * {@link BddManager#one()} (전체 집합) 이 됩니다. 즉 별도 처리가 필요 없습니다.
 */
public final class PacketVariables {

    /** IP 주소 하나가 차지하는 비트 수. */
    public static final int IP_BITS = 32;

    /** 포트가 차지하는 비트 수. */
    public static final int PORT_BITS = 16;

    /** 출발지 IP 첫 비트의 변수 인덱스. */
    public static final int SRC_IP_OFFSET = 0;

    /** 목적지 IP 첫 비트의 변수 인덱스. */
    public static final int DST_IP_OFFSET = SRC_IP_OFFSET + IP_BITS;

    /** 포트 첫 비트의 변수 인덱스. */
    public static final int PORT_OFFSET = DST_IP_OFFSET + IP_BITS;

    /** 전체 변수 개수 (80). */
    public static final int TOTAL_BITS = PORT_OFFSET + PORT_BITS;

    /** 포트를 지정하지 않은 규칙을 뜻하는 값. */
    public static final int ANY_PORT = -1;

    /** 변수 배치를 고려하지 않고 만들 때 쓰는 기본 매니저 생성 헬퍼. */
    private PacketVariables() {
    }

    /**
     * 비어 있는 매니저를 만들어 변수 80개를 등록합니다.
     *
     * <p>변수를 미리 등록해 두면 {@code satCount} 가 전체 공간 크기
     * (2^80) 을 기준으로 셉니다. 등록하지 않으면 방문한 변수만 세므로
     * "허용 조합 수" 비교가 어긋납니다.
     *
     * @return 변수 80개가 등록된 매니저
     */
    public static BddManager newManager() {
        final BddManager manager = new BddManager();
        for (int index = 0; index < TOTAL_BITS; index++) {
            manager.variable(index);
        }
        return manager;
    }

    /**
     * CIDR 접두사 조건을 BDD 로 만듭니다.
     *
     * <p>{@code 10.10.131.0/24} → "출발지 상위 24비트가 10.10.131 과 같다".
     * 하위 8비트는 자유이므로 그대로 두면 그 비트들의 조합이 모두 포함됩니다.
     *
     * @param manager 변수 80개가 등록된 매니저
     * @param cidr    {@code a.b.c.d/len} 형식 문자열
     * @param offset  이 주소가 시작하는 변수 인덱스 ({@link #SRC_IP_OFFSET} 또는
     *                {@link #DST_IP_OFFSET})
     * @return 접두사 조건을 나타내는 BDD
     * @throws IllegalArgumentException 형식이 잘못된 경우
     */
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

    /**
     * 포트 조건을 BDD 로 만듭니다.
     *
     * @param manager 변수 80개가 등록된 매니저
     * @param port    포트 번호, 또는 {@link #ANY_PORT}
     * @return 포트 조건 (미지정이면 전체 집합)
     */
    public static BddNode port(BddManager manager, int port) {
        if (port == ANY_PORT) {
            return manager.one();
        }
        BddNode result = manager.one();
        for (int bit = 0; bit < PORT_BITS; bit++) {
            final int variable = PORT_OFFSET + bit;
            final boolean value = ((port >>> (PORT_BITS - 1 - bit)) & 1) != 0;
            result = manager.and(result, value ? manager.variable(variable) : manager.not(manager.variable(variable)));
        }
        return result;
    }

    /**
     * BDD 할당에서 출발지 IP 문자열을 복원합니다.
     *
     * @param assignment {@link BddManager#anySat} 결과
     * @return 점 표기 IP 주소 (미지정 비트는 0 으로 채움)
     */
    public static String srcIpOf(int[] assignment) {
        return ipOf(assignment, SRC_IP_OFFSET);
    }

    /**
     * BDD 할당에서 목적지 IP 문자열을 복원합니다.
     *
     * @param assignment {@link BddManager#anySat} 결과
     * @return 점 표기 IP 주소
     */
    public static String dstIpOf(int[] assignment) {
        return ipOf(assignment, DST_IP_OFFSET);
    }

    /**
     * BDD 할당에서 포트 번호를 복원합니다.
     *
     * @param assignment {@link BddManager#anySat} 결과
     * @return 포트 번호, 모든 비트가 미지정이면 {@link #ANY_PORT}
     */
    public static int portOf(int[] assignment) {
        if (assignment == null) {
            return ANY_PORT;
        }
        int value = 0;
        boolean any = false;
        for (int bit = 0; bit < PORT_BITS; bit++) {
            final int index = PORT_OFFSET + bit;
            if (index >= assignment.length) {
                break;
            }
            final int bitValue = assignment[index];
            if (bitValue < 0) {
                continue;
            }
            any = true;
            value = (value << 1) | bitValue;
        }
        return any ? value : ANY_PORT;
    }

    /** 할당에서 IP 주소를 조립합니다. */
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

    /**
     * CIDR 문자열을 주소(정수)와 접두사 길이로 나눕니다.
     *
     * @param cidr {@code a.b.c.d/len} 형식
     * @return 파싱 결과
     * @throws IllegalArgumentException 형식이 잘못된 경우
     */
    public static ParsedCidr parseCidr(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            throw new IllegalArgumentException("cidr is blank");
        }
        final String[] parts = cidr.trim().split("/");
        final String addressText = parts[0].trim();

        // 허용 관용 표기: "192.168.0.x/24" 처럼 마지막 옥텟이 문자인 경우 x 를 0 으로 봅니다.
        final String normalized = addressText.replaceAll("(?i)x", "0").trim();

        final int prefixLength;
        if (parts.length > 1 && !parts[1].isBlank()) {
            try {
                prefixLength = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("invalid prefix length in cidr: " + cidr, ex);
            }
        } else {
            prefixLength = IP_BITS;
        }
        if (prefixLength < 0 || prefixLength > IP_BITS) {
            throw new IllegalArgumentException("prefix length out of range: " + cidr);
        }

        final int address = parseIp(normalized);
        return new ParsedCidr(address, prefixLength);
    }

    /**
     * 점 표기 IP 주소를 정수로 바꿉니다.
     *
     * @param ip {@code a.b.c.d}
     * @return 부호 없는 32비트 값을 담은 int
     * @throws IllegalArgumentException 형식이 잘못된 경우
     */
    public static int parseIp(String ip) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("ip is blank");
        }
        final String[] octets = ip.trim().split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("invalid ipv4 address: " + ip);
        }
        int value = 0;
        for (final String octet : octets) {
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

    /**
     * 호스트명이나 IP 문자열에서 주소를 얻습니다. (Agent 가 준 값을 관대하게 흡수)
     *
     * @param value IP 또는 호스트명
     * @return 정규화된 IP 문자열, 알 수 없으면 {@code null}
     */
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

    /**
     * CIDR 파싱 결과입니다.
     *
     * @param address      주소 (부호 없는 32비트를 담은 int)
     * @param prefixLength 접두사 길이 (0~32)
     */
    public record ParsedCidr(int address, int prefixLength) {
    }
}
