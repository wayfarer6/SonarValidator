package org.sonar.sonarvalidator_backend.Service.opnsense;

import java.net.URI;
import java.util.Locale;

/**
 * OPNsense 접속 정보입니다. (요청 시점의 값)
 *
 * <h2>왜 DTO 로 따로 두는가</h2>
 * <p>엔티티({@code OPNsenseCredential})는 <b>암호화된 시크릿</b>을 들고 있고
 * JPA 세션에 묶여 있습니다. 실제 호출에 필요한 것은 <b>복호화된 평문</b>과
 * URL 뿐이므로, 이 값만 담은 불변 객체를 만들어 서비스 계층에 넘깁니다.
 * 이렇게 하면:
 * <ul>
 *   <li>평문 시크릿이 엔티티에 남지 않습니다.</li>
 *   <li>호출 코드가 복호화 시점을 신경 쓰지 않아도 됩니다.</li>
 *   <li>테스트에서 가짜 접속 정보를 쉽게 만들 수 있습니다.</li>
 * </ul>
 *
 * <p><b>주의</b>: {@link #toString()} 에 시크릿을 넣지 마세요. 로그에 남으면
 * 암호화 저장의 의미가 사라집니다. ({@code "[REDACTED]"} 로 대체했습니다.)
 *
 * @param baseUrl          기준 URL (예: {@code https://10.99.143.2}, 끝 슬래시 없음)
 * @param apiKey           OPNsense API Key
 * @param apiSecret        OPNsense API Secret (복호화된 평문)
 * @param allowInsecureTls 자체 서명 인증서 허용 여부 (랩 전용)
 */
public record OPNsenseConnection(
        String baseUrl,
        String apiKey,
        String apiSecret,
        boolean allowInsecureTls) {

    /**
     * 호출에 필요한 값이 모두 있는지 확인합니다.
     *
     * <h2>⚠️ 전송 방식(HTTP/HTTPS)은 여기서 판단하지 않습니다</h2>
     * <p>예전에는 이 메서드가 <b>HTTPS 만</b> 통과시켰습니다. 그런데 허용 판단은
     * {@link OPNsenseTransportPolicy} 가 담당하도록 바뀌었습니다(HTTP 랩 장비를
     * 서버 설정으로 열어 주기 위함). 두 곳이 서로 다른 기준을 들고 있으면
     * <b>저장은 되는데 조회만 400</b> 같은 모순이 생깁니다.
     * 그래서 이 메서드는 "값이 채워졌는가" 만 봅니다.
     *
     * @return 호출 가능하면 {@code true}
     */
    public boolean isUsable() {
        return hasUsableBaseUrl()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }

    /**
     * 기준 URL 이 실제로 요청에 쓸 수 있는 형태인지 확인합니다.
     *
     * <p>스킴(HTTP/HTTPS)은 <b>가리지 않습니다.</b> 다만
     * {@code http://user:pass@host} 처럼 user-info 가 붙은 주소는 거부합니다 —
     * Basic 인증 헤더와 별개로 자격증명이 URL 에 실려 로그에 남기 때문입니다.
     *
     * @return 사용 가능한 주소면 {@code true}
     */
    public boolean hasUsableBaseUrl() {
        try {
            final URI uri = URI.create(normalizedBaseUrl());
            return uri.getScheme() != null
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** HTTPS 주소인지 확인합니다. (전송 정책 판단용) */
    public boolean isSecureTransport() {
        try {
            final URI uri = URI.create(normalizedBaseUrl());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * 기준 URL 을 정규화합니다.
     *
     * <p>OPNsense API 는 {@code /api/...} 하위이므로 base URL 에 경로가 붙어
     * 있으면 제거하고, 끝 슬래시도 없앱니다.
     *
     * @return 정규화된 base URL
     */
    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String value = baseUrl.trim();
        if (!value.regionMatches(true, 0, "http://", 0, "http://".length())
                && !value.regionMatches(true, 0, "https://", 0, "https://".length())) {
            // 스킴을 생략한 입력은 https 로 간주합니다.
            // (OPNsense 는 기본이 https 이고, 평문 http 는 자격증명 노출 위험이 큽니다)
            value = "https://" + value;
        }
        final int schemeEnd = value.indexOf("://");
        if (schemeEnd > 0) {
            value = value.substring(0, schemeEnd).toLowerCase(Locale.ROOT)
                    + value.substring(schemeEnd);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 시크릿이 로그에 남지 않도록 가립니다.
     *
     * @return 마스킹된 문자열
     */
    @Override
    public String toString() {
        return "OPNsenseConnection[baseUrl=" + baseUrl
                + ", apiKey=" + mask(apiKey)
                + ", apiSecret=[REDACTED]"
                + ", allowInsecureTls=" + allowInsecureTls + "]";
    }

    /**
     * 값을 앞 4자만 남기고 가립니다.
     *
     * @param value 원본
     * @return 마스킹된 값
     */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 4) + "****";
    }
}
