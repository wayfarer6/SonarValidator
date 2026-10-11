package org.sonar.sonarvalidator_backend.Policy.strategy;

import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;

/**
 * 정책 JSON 을 만들 때 쓰는 <b>공용 유틸</b>입니다.
 *
 * <h2>문서 스키마 규칙 — 스칼라는 배열로 감쌉니다</h2>
 * <p>{@code docs/Agent/*_Policy_Design.md} 의 스키마는 모든 값을 배열로
 * 감싸는 관례를 씁니다. (예: {@code "command": ["create"]})
 * C++ 의 {@code policy_json::AsString} 이 배열/스칼라를 모두 받아주므로
 * 이 관례를 따릅니다. 한쪽만 스칼라로 바꾸면 계약이 갈라집니다.
 *
 * <p>그래서 값을 넣을 때는 항상 {@code node.putArray(key).add(value)} 형태를
 * 씁니다. 이 클래스는 그 반복을 줄이고, 배열/스칼라 변환·CIDR 분해 같은
 * 계산을 한 곳에 모읍니다.
 */
public final class PolicyJson {

    /**
     * 랩에서 장치 호스트 주소로 쓰는 오프셋입니다.
     *
     * <p>PoC 네트워크 문서의 주소 계획을 보면 모든 호스트가 대역의 {@code .10}
     * 을 씁니다 — ATICS {@code 10.10.131.10}, TOD-Cam {@code 10.20.111.10},
     * VDI-1 {@code 10.40.121.10}, Public-Web-Server {@code 10.30.141.10}.
     * 게이트웨이는 {@code .1} 입니다.
     */
    public static final int LAB_HOST_OFFSET = 10;

    private PolicyJson() {
    }

    /**
     * CIDR 을 네트워크 주소와 접두사 길이로 나눕니다.
     *
     * @param cidr 입력 (예: {@code 10.10.131.7/24})
     * @return {@code [네트워크주소, 접두사길이]} (형식 오류면 null)
     */
    public static String[] splitCidr(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return null;
        }
        final String normalized = PolicySubnet.normalizeCidr(cidr);
        final int slash = normalized.indexOf('/');
        if (slash <= 0 || slash == normalized.length() - 1) {
            return null;
        }
        return new String[] {normalized.substring(0, slash), normalized.substring(slash + 1)};
    }

    /**
     * 접두사 길이를 점 표기 넷마스크로 바꿉니다.
     *
     * @param prefix 접두사 길이 문자열
     * @return 넷마스크 (예: {@code 255.255.255.0}), 알 수 없으면 null
     */
    public static String maskOf(String prefix) {
        if (prefix == null) {
            return null;
        }
        final int length;
        try {
            length = Integer.parseInt(prefix.trim());
        } catch (RuntimeException ex) {
            return null;
        }
        if (length < 0 || length > 32) {
            return null;
        }
        final long mask = (length == 0) ? 0L : (0xFFFFFFFFL << (32 - length)) & 0xFFFFFFFFL;
        return ((mask >>> 24) & 0xFF) + "." + ((mask >>> 16) & 0xFF) + "."
                + ((mask >>> 8) & 0xFF) + "." + (mask & 0xFF);
    }

    /**
     * 접두사 길이를 Cisco/FRR 표기의 <b>와일드카드 마스크</b>(역마스크)로 바꿉니다.
     *
     * <h2>⚠️ 왜 넷마스크와 따로 필요한가</h2>
     * <p>Cisco IOS ACL 과 FRR access-list 는 마스크를 <b>뒤집어</b> 씁니다.
     * {@code 10.0.8.0/24} 는 {@code 10.0.8.0 0.0.0.255} 입니다. 넷마스크
     * ({@code 255.255.255.0})를 그대로 넣으면 ACL 이 <b>엉뚱한 대역</b>을 막습니다.
     * 잘못 차단되면 망분리가 깨지므로 표기를 섞으면 안 됩니다.
     *
     * @param prefix 접두사 길이 문자열
     * @return 와일드카드 마스크 (예: {@code 0.0.0.255}), 알 수 없으면 null
     */
    public static String wildcardOf(String prefix) {
        final String mask = maskOf(prefix);
        if (mask == null) {
            return null;
        }
        final StringBuilder result = new StringBuilder();
        for (final String octet : mask.split("\\.")) {
            if (result.length() > 0) {
                result.append('.');
            }
            result.append(255 - Integer.parseInt(octet));
        }
        return result.toString();
    }

    /**
     * 서브넷 대역에서 장치가 쓸 <b>호스트 주소</b>를 만듭니다.
     *
     * <h2>⚠️ 왜 네트워크 주소를 그대로 쓰면 안 되는가</h2>
     * <p>서브넷 CIDR 은 {@code 10.20.111.0/24} 처럼 <b>네트워크 주소</b>입니다.
     * 이것을 장치의 인터페이스 주소로 내려보내면:
     * <ul>
     *   <li>네트워크 주소({@code .0})는 호스트에 할당할 수 없습니다.</li>
     *   <li>리눅스는 이 주소로 인터페이스를 잡으면 자기 대역 판정이 깨져
     *       게이트웨이/ARP 가 어긋나고, 결국 <b>통신이 끊깁니다.</b></li>
     *   <li>프로버 자신이 서버에 보고하지 못하게 되어 스스로 고립됩니다.</li>
     * </ul>
     *
     * <p>실측: VM 정책이 {@code addresses: ["10.20.111.0/24"]} 로 내려가
     * 실제 장치에 네트워크 주소가 설정될 뻔했습니다.
     *
     * @param cidr 서브넷 CIDR
     * @return 호스트 주소 (예: {@code 10.20.111.10/24}), 형식 오류면 null
     */
    public static String hostCidrOf(String cidr) {
        final String[] parts = splitCidr(cidr);
        if (parts == null) {
            return null;
        }
        final String[] octets = parts[0].split("\\.");
        if (octets.length != 4) {
            return null;
        }
        final int[] values = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                values[i] = Integer.parseInt(octets[i]);
            } catch (RuntimeException ex) {
                return null;
            }
            if (values[i] < 0 || values[i] > 255) {
                return null;
            }
        }

        // 네트워크 + 오프셋. 마지막 옥텟에서 자리올림이 생기면 대역을 벗어나므로
        // 그 경우에는 계산을 포기합니다. (틀린 주소를 넣느니 넣지 않습니다)
        final int last = values[3] + LAB_HOST_OFFSET;
        if (last > 254) {
            return null;
        }
        return values[0] + "." + values[1] + "." + values[2] + "." + last + "/" + parts[1];
    }

    /**
     * 비어 있지 않은 첫 값을 돌려줍니다.
     *
     * @param values 후보들
     * @return 첫 비어 있지 않은 값 (모두 비면 null)
     */
    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 등급을 화면 표기로 바꿉니다.
     *
     * @param zone 등급 (null 허용)
     * @return 표시 이름 (null 이면 {@code "Unknown"})
     */
    public static String label(org.sonar.sonarvalidator_backend.Policy.ZoneClass zone) {
        return zone == null ? "Unknown" : zone.label();
    }

    /**
     * 두 대역이 겹치는지 판정합니다.
     *
     * <h2>⚠️ 왜 필요한가 — 관리 평면 자기 차단 방지</h2>
     * <p>자동 집행을 켜면 <b>모든 금지 쌍</b>이 장치로 내려갑니다. 그런데
     * 프로젝트에 관리 대역({@code 172.16.255.0/24})이 서브넷으로 들어 있으면,
     * 그 대역과 등급 차이가 나는 대역 사이의 이동도 "금지" 로 판정될 수
     * 있습니다.
     *
     * <p>그 규칙이 스위치/라우터에 내려가면 <b>관리 트래픽이 차단되어</b>
     * 프로버가 서버에 보고하지 못하고 스스로 고립됩니다. 그러면 정책을 되돌릴
     * 원격 경로도 사라집니다 — 도구가 자기 발판을 끊는 실패입니다.
     *
     * <p>그래서 집행 규칙을 만들 때 관리 대역과 겹치는 항목은 <b>걸러냅니다.</b>
     * 수집·화면에는 그대로 남습니다(운영자가 위반을 봐야 하므로).
     *
     * @param cidrA 첫 대역 (CIDR)
     * @param cidrB 둘째 대역 (CIDR)
     * @return 겹치면 true (형식을 알 수 없으면 false)
     */
    public static boolean overlaps(String cidrA, String cidrB) {
        final String[] left = splitCidr(cidrA);
        final String[] right = splitCidr(cidrB);
        if (left == null || right == null) {
            return false;
        }
        final long addressA = ipv4Of(left[0]);
        final long addressB = ipv4Of(right[0]);
        final int lengthA = prefixLengthOf(left[1]);
        final int lengthB = prefixLengthOf(right[1]);
        if (addressA < 0 || addressB < 0 || lengthA < 0 || lengthB < 0) {
            return false;
        }
        // 짧은 쪽(넓은 쪽) 접두사로 맞춰 비교합니다. /16 과 /24 는
        // /16 기준으로 같은지 보면 겹침을 알 수 있습니다.
        final int shorter = Math.min(lengthA, lengthB);
        final long mask = (shorter == 0) ? 0L : (0xFFFFFFFFL << (32 - shorter)) & 0xFFFFFFFFL;
        return (addressA & mask) == (addressB & mask);
    }

    /**
     * 집행 항목이 관리 평면과 충돌하는지 판정합니다.
     *
     * <h2>⚠️ 왜 {@link #overlaps} 를 그대로 쓰면 안 되는가</h2>
     * <p>인터넷 기본 경로 차단은 {@code 0.0.0.0/0} 입니다. 이 대역은
     * <b>모든</b> 대역과 겹치므로, 단순히 겹침만 보면 관리 대역을 겨냥하지
     * 않은 정상 차단까지 전부 걸러집니다.
     * (실측: 허용 목록 밖 기본 경로 차단이 사라져 테스트가 실패했습니다)
     *
     * <p>그래서 <b>전체를 겨냥한 대역({@code /0})은 제외</b>하고, 특정 대역과
     * 관리 대역이 실제로 겹칠 때만 관리 평면 충돌로 봅니다.
     *
     * @param cidr   검사할 대역
     * @param prefix 관리 대역
     * @return 관리 평면과 충돌하면 true
     */
    public static boolean overlapsManagementPrefix(String cidr, String prefix) {
        if (cidr == null || prefix == null || prefix.isBlank()) {
            return false;
        }
        final String[] parts = splitCidr(cidr);
        if (parts == null || "0".equals(parts[1])) {
            // 전체를 겨냥한 차단은 관리 대역만을 노린 것이 아닙니다.
            return false;
        }
        return overlaps(cidr, prefix);
    }

    /**
     * 점 표기 IPv4 주소를 32비트 값으로 바꿉니다.
     *
     * @param dotted 주소 (예: {@code 10.20.111.0})
     * @return 값 (형식 오류면 -1)
     */
    private static long ipv4Of(String dotted) {
        if (dotted == null) {
            return -1;
        }
        final String[] octets = dotted.split("\\.");
        if (octets.length != 4) {
            return -1;
        }
        long value = 0;
        for (final String octet : octets) {
            final int part;
            try {
                part = Integer.parseInt(octet);
            } catch (RuntimeException ex) {
                return -1;
            }
            if (part < 0 || part > 255) {
                return -1;
            }
            value = (value << 8) | part;
        }
        return value;
    }

    /**
     * 접두사 길이 문자열을 정수로 바꿉니다.
     *
     * @param prefix 접두사 (예: {@code 24})
     * @return 길이 (형식 오류면 -1)
     */
    private static int prefixLengthOf(String prefix) {
        if (prefix == null) {
            return -1;
        }
        try {
            final int length = Integer.parseInt(prefix.trim());
            return (length < 0 || length > 32) ? -1 : length;
        } catch (RuntimeException ex) {
            return -1;
        }
    }
}