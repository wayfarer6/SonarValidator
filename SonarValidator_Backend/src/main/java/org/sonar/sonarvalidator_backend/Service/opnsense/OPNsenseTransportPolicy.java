package org.sonar.sonarvalidator_backend.Service.opnsense;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OPNsenseTransportPolicy {

    private final boolean allowHttp;

    private final Set<String> httpOrigins;

    @Autowired
    public OPNsenseTransportPolicy(@Value("${sonar.opnsense.allow-http:true}") boolean allowHttp,
                                   @Value("${sonar.opnsense.http-origins:}") String origins) {
        this.allowHttp = allowHttp;
        this.httpOrigins = Arrays.stream(origins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(OPNsenseTransportPolicy::httpOrigin)
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public OPNsenseTransportPolicy(String origins) {
        this(true, origins);
    }

    public boolean allows(OPNsenseConnection connection) {
        if (connection == null) return false;
        // HTTPS 는 항상 허용합니다.
        if (connection.isSecureTransport()) return true;
        // 여기서부터는 평문 HTTP 입니다.
        if (!allowHttp) return false;
        // 허용 목록을 지정하지 않았으면 모든 HTTP 를 허용합니다.
        if (httpOrigins.isEmpty()) return true;
        final String origin = httpOrigin(connection.normalizedBaseUrl());
        return !origin.isEmpty() && httpOrigins.contains(origin);
    }

    private static String httpOrigin(String value) {
        try {
            final URI uri = URI.create(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) return "";
            return "http://" + uri.getHost().toLowerCase(Locale.ROOT)
                    + ":" + (uri.getPort() == -1 ? 80 : uri.getPort());
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }
}
