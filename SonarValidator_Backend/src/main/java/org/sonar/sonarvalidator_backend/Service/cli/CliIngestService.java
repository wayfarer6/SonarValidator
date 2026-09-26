package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Service.DeviceConfigService;
import org.springframework.stereotype.Service;

import tools.jackson.databind.node.ObjectNode;

/**
 * 원문 CLI 문자열을 직접 받아 <b>내부 계약 JSON</b> 으로 바꿔 주는 서비스입니다.
 *
 * <h2>왜 필요한가</h2>
 * <p>정상 경로는 Agent(Prober) 가 C++ 파서로 이미 구조화한 JSON 을 보내는
 * 것입니다. 그런데 다음 경우에는 서버가 <b>원문을 직접 받아</b> 파싱해야 합니다.
 *
 * <ul>
 *   <li>구버전 Prober 가 원문만 실어 보낼 때 (개별 장비를 재배포하기 어려움)</li>
 *   <li>운영자가 진단 중 {@code show ip route} 결과를 그대로 붙여 넣을 때</li>
 *   <li>오프라인 스냅샷의 원문만 다시 파싱할 때</li>
 *   <li>수집된 JSON 의 키가 계약과 달라 원문으로 되돌아가 확인할 때</li>
 * </ul>
 *
 * <p>{@link CliIngestionService} 는 <b>telemetry payload 안의 원문</b>을 찾아
 * 정규화하지만, 이 서비스는 <b>원문 자체를 요청 본문으로</b> 받습니다. 즉
 * "어느 대상의 출력인지" 를 호출자가 명시할 수 있어 추정 오류가 없습니다.
 *
 * <h2>실패해도 예외를 던지지 않는다</h2>
 * <p>파서는 애초에 예외를 던지지 않고 {@code {"parsed": false, ...}} 를
 * 돌려줍니다. 이 서비스는 그 결과에 <b>계약 키</b>를 씌워 소비자
 * ({@code AbstractDeviceConfigParser}) 가 바로 읽을 수 있게 합니다. 다만
 * {@code parsed:false} 이거나 항목이 0건이면 <b>계약 키를 넣지 않습니다</b>.
 * 넣으면 소비자가 "조회했는데 아무것도 없음" 으로 해석해 오히려 데이터를
 * 지우기 때문입니다.
 */
@Service
public class CliIngestService {

    private static final Logger log = LoggerFactory.getLogger(CliIngestService.class);

    private final CliOutputParser parser;
    private final CliIngestionService ingestion;
    private final DeviceConfigService deviceConfigService;

    /**
     * 조회 대상 이름 → 계약 키 변환기입니다.
     *
     * <p>⚠️ 이전에는 이 클래스가 {@code TARGET_KEYS} 라는 별칭 표와
     * {@code contractKeyOf} 의 벤더 {@code switch} 를 <b>따로</b> 갖고
     * 있었습니다. 같은 사실이 파서에도 있어서, 대상 하나를 추가하면 두 곳을
     * 고쳐야 했고 한 곳을 빠뜨리면 <b>파싱은 되는데 화면은 빈</b> 상태가
     * 됐습니다. 지금은 파서의 전략이 유일한 출처입니다.
     */
    private static final org.sonar.sonarvalidator_backend.Service.cli.query.CliQueryStrategies
            QUERY_STRATEGIES =
            new org.sonar.sonarvalidator_backend.Service.cli.query.CliQueryStrategies();

    /**
     * @param parser              원문을 구조화하는 ANTLR 기반 파서
     * @param ingestion           벤더 판별/항목 수 판정을 재사용하기 위한 서비스
     * @param deviceConfigService 중립 설정까지 한 번에 만들어 주기 위한 서비스
     */
    public CliIngestService(CliOutputParser parser,
                            CliIngestionService ingestion,
                            DeviceConfigService deviceConfigService) {
        this.parser = parser;
        this.ingestion = ingestion;
        this.deviceConfigService = deviceConfigService;
    }

    /**
     * 원문 CLI 한 건을 해석해 결과를 만듭니다.
     *
     * @param  request 요청 (제품명/대상/원문)
     * @param  includeConfig {@code true} 면 중립 설정까지 함께 만들어 담습니다
     * @return {@code accepted}/{@code errors} 를 담은 응답 본문 (null 아님)
     */
    public Map<String, Object> ingest(CliIngestRequest request, boolean includeConfig) {
        final CliIngestRequest req = request == null ? CliIngestRequest.empty() : request;
        final Map<String, Object> result = new LinkedHashMap<>();

        if (!req.hasRaw()) {
            // 붙여넣기가 비었거나 공백뿐인 경우입니다. 400 대신 200 + 사유를
            // 돌려주는 이유는 오프라인 가져오기와 같습니다 — 프론트가 사유를
            // 그대로 보여줄 수 있어야 하기 때문입니다.
            result.put("accepted", false);
            result.put("errors", List.of("raw 출력이 비어 있습니다"));
            return result;
        }

        final CliVendor vendor = ingestion.vendorOf(req.product());
        final String query = normalizeTarget(req.target(), vendor);
        final String contractKey = contractKeyOf(query, vendor);

        final ObjectNode parsed = parser.parseQueryOutput(vendor, query, req.raw());

        result.put("accepted", parsed.path("parsed").asBoolean(false));
        result.put("vendor", vendor.name());
        result.put("vendor_name", vendor.displayName());
        result.put("query", query);
        result.put("target_key", contractKey);
        result.put("item_count", countOf(parsed));
        result.put("parsed", parsed);

        final List<String> errors = new java.util.ArrayList<>();
        if (!parsed.path("parsed").asBoolean(false)) {
            errors.add("파싱 실패: " + parsed.path("parse_error").asString("unknown"));
        } else if (countOf(parsed) == 0) {
            // 문법의 catch-all 규칙은 어떤 텍스트든 통과시킵니다. 항목이 0건이면
            // "성공" 을 믿으면 안 됩니다.
            errors.add("파싱은 됐지만 항목이 0건입니다. 대상(" + query + ")이 맞는지 확인하세요");
        }
        result.put("errors", errors);

        if (includeConfig) {
            // 계약 키를 씌운 payload 로 중립 설정까지 만듭니다. 소비자가 실제로 보게
            // 될 결과를 그대로 돌려주어 "파싱은 맞는데 화면은 빈" 상황을 빨리 찾습니다.
            final ObjectNode payload = CliJson.object();
            if (errors.isEmpty()) {
                payload.set(contractKey, parsed);
            }
            final NeutralDeviceConfig config =
                    deviceConfigService.parse("(cli-ingest)", req.product(), payload);
            result.put("config", config);
        }

        log.info("cli ingest: product={} vendor={} target={} key={} count={} accepted={}",
                req.product(), vendor.name(), req.target(), contractKey,
                countOf(parsed), errors.isEmpty());
        return result;
    }

    /**
     * 조회 대상 이름을 정규화합니다.
     *
     * <p>비어 있으면 벤더 기본 조회를 쓰도록 {@code null} 을 유지합니다.
     * ({@link CliOutputParser#parseQueryOutput} 이 벤더별 기본값으로 폴백합니다)
     *
     * @param  target 요청의 대상 이름 (null 허용)
     * @param  vendor 판별된 벤더 (로그/기본값용)
     * @return 정규화된 대상 이름 (없으면 {@code null})
     */
    private static String normalizeTarget(String target, CliVendor vendor) {
        if (target == null || target.isBlank()) {
            log.debug("cli ingest target 미지정 → 벤더 기본 조회 사용: {}", vendor.displayName());
            return null;
        }
        return target.trim();
    }

    /**
     * 정규화된 대상 이름을 payload 계약 키로 바꿉니다.
     *
     * <p>파서의 전략이 판단한 키와 <b>같은 결과</b>를 돌려줍니다. 여기서
     * 따로 계산하면 파싱은 어떤 키로 하고 응답에는 다른 키를 적는 일이
     * 생깁니다.
     *
     * @param  query  대상 이름 (null 허용)
     * @param  vendor 벤더 (기본값 판정용)
     * @return 계약 키
     */
    private static String contractKeyOf(String query, CliVendor vendor) {
        return QUERY_STRATEGIES.select(query, vendor).contractKey();
    }

    /**
     * 파싱 결과에서 대표 항목 수를 셉니다.
     *
     * @param  parsed 파싱 본문
     * @return 항목 수 (판정 불가면 0)
     */
    private static int countOf(ObjectNode parsed) {
        for (final String key : new String[]{
                "interface_count", "route_count", "entry_count", "vlan_count",
                "port_count", "bridge_count", "table_count"}) {
            if (parsed.path(key).isNumber()) {
                return parsed.path(key).asInt(0);
            }
        }
        return 0;
    }
}