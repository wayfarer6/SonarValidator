package org.sonar.sonarvalidator_backend.Service.opnsense;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 접속 주소의 전송 방식(HTTPS / 평문 HTTP) 허용 정책입니다.
 *
 * <h2>기본값 — 둘 다 허용</h2>
 * <p>랩 장비는 평문 HTTP 로 API 를 여는 경우({@code http://10.20.0.2})와
 * 자체 서명 인증서를 쓰는 HTTPS 가 섞여 있습니다. 그래서 <b>두 방식 모두
 * 기본 허용</b>입니다.
 *
 * <p>운영 환경에서 평문 HTTP 를 막으려면
 * {@code sonar.opnsense.allow-http=false} 로 끄고, 꼭 필요한 장비만
 * {@code sonar.opnsense.http-origins} 에 나열합니다.
 *
 * <h2>⚠️ 평문 HTTP 로 Basic 인증을 보내는 것의 의미</h2>
 * <p>Basic 헤더의 Base64 는 <b>암호화가 아니라 인코딩</b>입니다. 평문 HTTP 로
 * 보내면 같은 링크의 누구나 API Key/Secret 을 그대로 읽을 수 있습니다.
 * 격리된 랩이 아니라면 HTTPS 를 쓰세요. (자체 서명 인증서라면 화면의
 * "자체 서명 인증서 허용" 옵션을 켭니다 — 이 정책과는 별개입니다)
 */
@Component
public class OPNsenseTransportPolicy {

    /** 평문 HTTP 허용 여부. 기본값은 {@code true}. */
    private final boolean allowHttp;

    /**
     * 평문 HTTP 허용 주소 목록입니다.
     *
     * <p>비어 있으면 모든 HTTP 주소를 허용합니다. 값이 있으면 <b>그 주소만</b>
     * 허용합니다 — 운영 환경에서 허용 대상을 좁힐 때 씁니다.
     */
    private final Set<String> httpOrigins;

    /**
     * @param allowHttp   평문 HTTP 허용 여부 ({@code sonar.opnsense.allow-http})
     * @param origins     허용할 HTTP 주소 목록, 쉼표 구분
     *                    ({@code sonar.opnsense.http-origins})
     */
    @Autowired
    public OPNsenseTransportPolicy(@Value("${sonar.opnsense.allow-http:true}") boolean allowHttp,
                                   @Value("${sonar.opnsense.http-origins:}") String origins) {
        this.allowHttp = allowHttp;
        this.httpOrigins = Arrays.stream(origins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(OPNsenseTransportPolicy::httpOrigin)
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 주소 목록만 지정하는 생성자입니다. (테스트·수동 생성용)
     *
     * @param origins 허용할 HTTP 주소 목록, 쉼표 구분
     */
    public OPNsenseTransportPolicy(String origins) {
        this(true, origins);
    }

    /**
     * 이 접속 정보로 요청을 보내도 되는지 판단합니다.
     *
     * @param connection 접속 정보
     * @return 허용되면 {@code true}
     */
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

    /**
     * 주소를 {@code scheme://host:port} 형태로 정규화합니다.
     *
     * <p>허용 목록 비교는 이 형태로만 합니다. 경로·쿼리·user-info 가 붙은
     * 값은 허용하지 않습니다 — 자격증명 탈취를 노린 주소를 그대로 통과시키지
     * 않기 위함입니다.
     *
     * @param value 원본 주소
     * @return 정규화된 origin, 형식이 아니면 빈 문자열
     */
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
