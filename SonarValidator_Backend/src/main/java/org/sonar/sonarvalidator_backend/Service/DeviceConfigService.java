package org.sonar.sonarvalidator_backend.Service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.Config.DeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

/**
 * 수집된 텔레메트리를 <b>중립 장비 설정</b>으로 변환하는 단일 진입점입니다.
 *
 * <h2>역할</h2>
 * <p>벤더 파서들({@link DeviceConfigParser})을 목록으로 들고 있다가, 제품명을
 * 보고 맞는 파서를 골라 위임합니다. 호출자는 벤더를 몰라도 됩니다.
 *
 * <pre>
 *   Agent telemetry payload
 *        │
 *        ▼   parse(hostname, product, payload)
 *   DeviceConfigService ──► Cisco / FRR / Alpine / OVS / Linux / Arista 파서
 *        │
 *        ▼
 *   NeutralDeviceConfig  (분석·저장·표시가 공통으로 쓰는 구조)
 * </pre>
 *
 * <h2>파서 선택 순서</h2>
 * <p>Spring 이 주입한 파서를 <b>선언 순서대로</b> 검사해 처음 {@code supports()}
 * 가 참인 것을 씁니다. 어떤 파서도 맞지 않으면 Linux 파서로 폴백하고,
 * 그래도 없으면 빈 설정에 경고를 담아 돌려줍니다. <b>예외를 던지지 않습니다</b>
 * — 텔레메트리 경로에서 예외는 수집 유실로 이어지기 때문입니다.
 */
@Service
public class DeviceConfigService {

    private static final Logger log = LoggerFactory.getLogger(DeviceConfigService.class);

    private final List<DeviceConfigParser> parsers;

    /**
     * 사용 가능한 파서들을 주입받습니다.
     *
     * @param parsers Spring 이 등록한 파서 목록 (순서 유지)
     */
    public DeviceConfigService(List<DeviceConfigParser> parsers) {
        this.parsers = List.copyOf(parsers);
        log.info("device config parsers registered: {}", this.parsers.size());
    }

    /**
     * 제품명/장치유형에 맞는 파서를 찾습니다.
     *
     * @param  productName Agent 가 보고한 제품명
     * @param  deviceType  Agent 가 보고한 장치 유형
     * @return 선택된 파서 (없으면 비어 있음)
     */
    public Optional<DeviceConfigParser> parserFor(String productName, String deviceType) {
        for (final DeviceConfigParser parser : parsers) {
            try {
                if (parser.supports(productName, deviceType)) {
                    return Optional.of(parser);
                }
            } catch (RuntimeException ex) {
                // 한 파서의 판정 실패가 전체 선택을 막지 않게 합니다.
                log.warn("parser {} supports() failed: {}", parser.getClass().getSimpleName(), ex.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * 텔레메트리를 중립 설정으로 변환합니다.
     *
     * @param  hostname Agent 식별자
     * @param  product  Agent 가 보고한 제품명
     * @param  payload  텔레메트리 payload
     * @return 중립 설정 (null 이 아님; 실패 시 경고가 담긴 빈 설정)
     */
    public NeutralDeviceConfig parse(String hostname, String product, JsonNode payload) {
        final Optional<DeviceConfigParser> selected = parserFor(product, null);
        if (selected.isEmpty()) {
            log.warn("no config parser for product={} host={}; returning empty config", product, hostname);
            final NeutralDeviceConfig empty = new NeutralDeviceConfig();
            empty.setHostname(hostname);
            empty.setProduct(product);
            empty.getWarnings().add("no parser matched product '" + product + "'");
            return empty;
        }

        final DeviceConfigParser parser = selected.get();
        try {
            final NeutralDeviceConfig config = parser.parse(hostname, product, payload);
            if (log.isDebugEnabled()) {
                log.debug("parsed {} via {}: ifaces={} routes={} vlans={}",
                        hostname, parser.getClass().getSimpleName(),
                        config.getInterfaces().size(), config.getRoutes().size(),
                        config.getVlans().size());
            }
            return config;
        } catch (RuntimeException ex) {
            // 파서 버그가 수집 파이프라인을 죽이지 않도록 흡수합니다.
            log.error("parser {} failed for host={} product={}: {}",
                    parser.getClass().getSimpleName(), hostname, product, ex.getMessage(), ex);
            final NeutralDeviceConfig failed = new NeutralDeviceConfig();
            failed.setHostname(hostname);
            failed.setProduct(product);
            failed.getWarnings().add("parser error: " + ex.getClass().getSimpleName());
            return failed;
        }
    }

    /**
     * 등록된 파서들의 형식 이름을 돌려줍니다. (진단/문서용)
     *
     * @return 형식 이름 목록
     */
    public List<String> supportedFormats() {
        return parsers.stream().map(DeviceConfigParser::format).toList();
    }
}
