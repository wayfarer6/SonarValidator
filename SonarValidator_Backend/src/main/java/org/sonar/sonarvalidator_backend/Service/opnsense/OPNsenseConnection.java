package org.sonar.sonarvalidator_backend.Service.opnsense;

import java.net.URI;
import java.util.Locale;

public record OPNsenseConnection(
        String baseUrl,
        String apiKey,
        String apiSecret,
        boolean allowInsecureTls) {

    public boolean isUsable() {
        return hasUsableBaseUrl()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }

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

    @Override
    public String toString() {
        return "OPNsenseConnection[baseUrl=" + baseUrl
                + ", apiKey=" + mask(apiKey)
                + ", apiSecret=[REDACTED]"
                + ", allowInsecureTls=" + allowInsecureTls + "]";
    }

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
