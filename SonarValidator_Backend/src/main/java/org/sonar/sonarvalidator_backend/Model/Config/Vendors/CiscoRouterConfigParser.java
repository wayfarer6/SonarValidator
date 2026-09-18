package org.sonar.sonarvalidator_backend.Model.Config.Vendors;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.Config.AbstractDeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * Cisco IOS / IOS-XE 라우터·스위치의 설정 파서입니다.
 *
 * <h2>수집 경로</h2>
 * <p>Agent 는 Cisco 에서 guestshell 의 {@code dohost} 로 IOS CLI 를 실행합니다.
 * 조회 명령은 {@code show ip interface brief}, {@code show ip route},
 * {@code show ip arp} 입니다. 즉 <b>수집 경로가 CLI 라우터와 다릅니다.</b>
 *
 * <h2>중요: 서브넷 마스크가 없다</h2>
 * <p>{@code show ip interface brief} 는 {@code 192.168.122.254} 처럼 마스크 없는
 * 주소를 줍니다. Agent 파서도 이 경우 {@code prefix_len} 을 채우지 않습니다.
 * 그래서 여기서도 <b>추측하지 않고</b> 마스크 없는 주소를 그대로 둡니다.
 * (임의로 /24 를 붙이면 잘못된 도달성 분석을 낳습니다.)
 */
@Component
public class CiscoRouterConfigParser extends AbstractDeviceConfigParser {

    /** Agent 가 보고하는 제품명 조각. */
    private static final String PRODUCT_MARKER = "Cisco";

    @Override
    public boolean supports(String productName, String deviceType) {
        return productName != null && productName.contains(PRODUCT_MARKER);
    }

    @Override
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final NeutralDeviceConfig config = newConfig(hostname, product, payload);
        config.setDeviceType("ROUTER");

        if (isEmptyPayload(payload)) {
            config.getWarnings().add("no telemetry payload; Cisco config left empty");
            return config;
        }

        applyNicStatus(config, payload);
        applyRouteStatus(config, payload);
        applyArpTable(config, payload);

        // Cisco 는 IOS CLI 로 라우팅을 수집하므로 인터페이스 요약에 L2 정보가 없습니다.
        // `show interfaces switchport` 를 수집하지 않으므로 VLAN/트렁크는 채우지 않고,
        // 그 사실을 경고로 남겨 분석 단계가 "없음" 과 "미수집" 을 구분할 수 있게 합니다.
        config.getWarnings().add("VLAN/trunk not collected for Cisco (IOS CLI lacks switchport show)");
        return config;
    }

    @Override
    protected String vendorName() {
        return "Cisco";
    }

    @Override
    protected String formatName() {
        return "CISCO_IOS";
    }

    @Override
    public List<String> capabilities() {
        return List.of("interfaces", "routes", "arp");
    }
}
