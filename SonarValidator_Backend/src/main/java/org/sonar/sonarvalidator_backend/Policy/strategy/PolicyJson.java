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
}