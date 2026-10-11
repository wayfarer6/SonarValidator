package org.sonar.sonarvalidator_backend.Service.cli;

/**
 * FRR/Cisco 라우트 코드를 프로토콜 이름으로 정규화합니다.
 *
 * <p>라우팅 테이블 선두에 붙는 한 글자 코드는 벤더마다 의미가 약간 다르고,
 * 선택({@code *})/FIB 설치({@code >})/백업({@code &amp;}) 마커가 뒤에 붙습니다.
 * 그래서 코드 전체를 보존한 뒤 첫 글자만 보고 프로토콜 이름을 정합니다.
 *
 * <p>C++ Prober 의 {@code NormalizeRouteCode} / {@code IsRouteCodeToken} /
 * {@code IsDestinationToken} 과 동일합니다.
 */
public final class RouteCodes {

    private RouteCodes() {
    }

    /**
     * 라우트 코드를 프로토콜 이름으로 바꿉니다.
     *
     * @param code 라우트 코드 (예: {@code O>*}, {@code C}, {@code S*})
     * @return 프로토콜 이름 (모르는 코드는 소문자 한 글자)
     */
    public static String normalize(String code) {
        if (code == null || code.isEmpty()) {
            return "unknown";
        }
        return switch (code.charAt(0)) {
            case 'K' -> "kernel";
            case 'C' -> "connected";
            case 'S' -> "static";
            case 'R' -> "rip";
            case 'O' -> "ospf";
            case 'I' -> "isis";
            case 'B' -> "bgp";
            case 'E', 'D' -> "eigrp";
            case 'N', 'H', 'G' -> "nhrp";
            case 'A' -> "babel";
            case 'L' -> "local";
            case 'M' -> "mobile";
            case 'P' -> "periodic";
            case 'U' -> "per-user";
            case 'T' -> "table";
            default -> String.valueOf(Character.toLowerCase(code.charAt(0)));
        };
    }

    /**
     * 라우트 코드 토큰인지 판별합니다. (알파벳과 {@code * < > &} 로만 구성)
     *
     * @param token 토큰
     * @return 코드 토큰 여부
     */
    public static boolean isRouteCodeToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            final char ch = token.charAt(i);
            if (!Character.isLetter(ch) && ch != '*' && ch != '<' && ch != '>' && ch != '&') {
                return false;
            }
        }
        return true;
    }

    /**
     * 목적지(CIDR) 토큰인지 판별합니다.
     *
     * @param token 토큰
     * @return 목적지 여부
     */
    public static boolean isDestinationToken(String token) {
        return token != null && token.indexOf('/') >= 0 && token.matches(".*[0-9].*");
    }
}