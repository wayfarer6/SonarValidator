package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * CLI 파서가 쓰는 <b>값 정리</b> 유틸리티입니다.
 *
 * <h2>여기에 무엇을 두고, 무엇을 두지 않는가</h2>
 * <p>"이 토큰이 무엇인가"(MAC 인가 IP 인가, 키워드인가 값인가)는 <b>문법의
 * 몫</b>입니다. 문법이 {@code MAC}/{@code MACDOTTED}/{@code ADDR4}/{@code ADDR6}/
 * {@code AGE}/{@code NUMBER}/{@code STATUSWORD} 같은 전용 토큰을 선언해 두면
 * 파서 생성 시점에 판별이 끝나고, visitor 는
 * {@code elem.getStart().getType()} 만 봅니다.
 *
 * <p>그래서 여기에는 <b>종류 판별 함수가 없습니다.</b> 남는 것은 표기 정리
 * (구두점 제거, 따옴표 제거, CIDR 분해, 토큰 나누기)처럼 문법과 무관한
 * 순수 문자열 작업뿐입니다.
 */
public final class CliText {

    private CliText() {
    }

    /**
     * 문자열 앞뒤 공백을 제거합니다.
     *
     * @param text 입력 (null 허용)
     * @return 다듬은 문자열
     */
    public static String trim(String text) {
        return text == null ? "" : text.strip();
    }

    /**
     * 끝에 붙은 구두점을 제거합니다. ({@code eth0,} → {@code eth0})
     *
     * <p>따옴표는 값 자체일 수 있으므로 제거하지 않습니다. 제거하면
     * {@code "eth0"} 이 {@code "eth0} 이 되어 짝이 깨집니다.
     *
     * @param text 입력
     * @return 구두점을 뗀 문자열
     */
    public static String trimPunct(String text) {
        if (text == null) {
            return "";
        }
        int end = text.length();
        while (end > 0) {
            final char ch = text.charAt(end - 1);
            if (ch == ',' || ch == ';' || ch == ')') {
                end--;
            } else {
                break;
            }
        }
        return text.substring(0, end);
    }

    /**
     * 양끝의 큰따옴표를 벗깁니다.
     *
     * @param text 입력
     * @return 벗긴 문자열
     */
    public static String unquote(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    /**
     * 숫자로만 이루어졌는지 확인합니다.
     *
     * <p>문법이 {@code NUMBER} 토큰을 제공하는 곳에서는 쓰지 않습니다.
     * CIDR 의 prefix 길이처럼 <b>토큰 안에 포함된 조각</b>을 검사할 때만 씁니다.
     *
     * @param text 입력
     * @return 숫자 여부
     */
    public static boolean isNumber(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code 10.10.131.1/24} 을 주소와 prefix 길이로 나눕니다.
     *
     * <p>주소인지 아닌지는 문법({@code ADDR4}/{@code ADDR6})이 이미 판별합니다.
     * 여기서는 <b>판별된 값</b>을 슬래시 기준으로 쪼개기만 합니다.
     *
     * @param value      입력
     * @param address    [out] 주소 (길이 1 배열)
     * @param prefixLen  [out] prefix 길이 (슬래시 없으면 -1)
     * @return 주소가 비어있지 않으면 참
     */
    public static boolean splitCidr(String value, String[] address, int[] prefixLen) {
        if (value == null) {
            return false;
        }
        final int slash = value.indexOf('/');
        if (slash < 0) {
            address[0] = value;
            prefixLen[0] = -1;
            return !value.isEmpty();
        }
        address[0] = value.substring(0, slash);
        final String prefix = value.substring(slash + 1);
        prefixLen[0] = isNumber(prefix) ? Integer.parseInt(prefix) : -1;
        return !address[0].isEmpty();
    }

    /**
     * Arista/Cisco 의 점 표기 하드웨어 주소를 콜론 표기로 바꿉니다.
     *
     * <p>{@code 0cae.21dd.0001} → {@code 0c:ae:21:dd:00:01}
     *
     * <p>이미 문법이 {@code MACDOTTED} 토큰으로 "점 표기 하드웨어 주소"임을
     * 확정한 뒤에 부리는 함수다. 여기서 다시 "점이 몇 개인가"로 종류를
     * 추측하는 것이 아니라, 확정된 표기를 옮겨 적는 것이다.
     *
     * @param token 점 표기 하드웨어 주소
     * @return 콜론 표기 주소 (형식이 아니면 입력 그대로)
     */
    public static String dottedMacToColon(String token) {
        final String value = trimPunct(token);
        final StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (Character.digit(ch, 16) >= 0) {
                digits.append(Character.toLowerCase(ch));
            }
        }
        if (digits.length() != 12) {
            return value;
        }

        final StringBuilder normalized = new StringBuilder(17);
        for (int i = 0; i < digits.length(); i++) {
            if (i != 0 && i % 2 == 0) {
                normalized.append(':');
            }
            normalized.append(digits.charAt(i));
        }
        return normalized.toString();
    }

    /**
     * 공백으로 토큰을 나눕니다. (원문 라인 → 토큰 목록)
     *
     * @param line 라인
     * @return 토큰 목록 (null 이 아님)
     */
    public static List<String> splitTokens(String line) {
        final List<String> tokens = new ArrayList<>();
        if (line == null) {
            return tokens;
        }
        for (final String token : line.strip().split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}