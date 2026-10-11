package org.sonar.sonarvalidator_backend.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 라우팅 테이블 조회 API 입니다.
 *
 * <h2>기존 코드에서 바뀐 점</h2>
 * <p>이 클래스는 원래 {@code AbstractController} 를 상속하고
 * {@code handleRequestInternal} 에서 {@code null} 만 반환하는 스텁이었습니다.
 * Spring MVC 에서 {@code null} 을 반환하면 뷰를 결정할 수 없어 런타임 오류가
 * 나므로, 실제 조회 기능을 가진 REST 컨트롤러로 교체했습니다.
 *
 * <h2>라우팅 테이블을 별도로 보는 이유</h2>
 * <p>경로 테이블은 존 간 도달성을 판단하는 핵심 자료입니다. 인터페이스만 보면
 * "대역이 어디로 향하는가" 를 알 수 없고, 방화벽 규칙만 보면 "패킷이 실제로
 * 그 대역에 도달 가능한가" 를 알 수 없습니다. 그래서 경로를 따로 노출합니다.
 *
 * <p>라우터가 OSPF 등으로 경로를 교환하므로 <b>같은 대역에 대한 경로가 여러
 * 줄</b> 나올 수 있습니다. 이 API 는 그대로 모두 돌려주고, 어떤 것이 실제
 * 포워딩에 쓰이는지는 {@code selected} 플래그로 알려줍니다.
 * (프로토콜별 우선순위를 서버가 임의로 정하지 않습니다.)
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouterController {

    private static final Logger log = LoggerFactory.getLogger(RouterController.class);

    private final AgentMessageRouterService router;

    /**
     * @param router 텔레메트리/중립 설정 보관소
     */
    public RouterController(AgentMessageRouterService router) {
        this.router = router;
    }

    /**
     * 전체 장치의 라우팅 테이블을 반환합니다.
     *
     * @param protocol 선택적 프로토콜 필터 (예: {@code OSPF}, {@code static})
     * @return 장치별 경로 목록
     */
    @GetMapping
    public Map<String, Object> all(
            @RequestParam(value = "protocol", required = false) String protocol) {
        final List<Map<String, Object>> devices = new ArrayList<>();
        router.allConfigs().forEach((agentId, config) -> {
            final List<Map<String, Object>> routes = toRouteList(config, protocol);
            if (routes.isEmpty()) {
                return;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("hostname", config.getHostname());
            entry.put("product", config.getProduct());
            entry.put("route_count", routes.size());
            entry.put("routes", routes);
            devices.add(entry);
        });

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("protocol_filter", protocol);
        body.put("device_count", devices.size());
        body.put("devices", devices);
        return body;
    }

    /**
     * 특정 장치의 라우팅 테이블을 반환합니다.
     *
     * @param agentId  장치 식별자
     * @param protocol 선택적 프로토콜 필터
     * @return 경로 목록
     */
    @GetMapping("/{agentId}")
    public Map<String, Object> byAgent(@PathVariable String agentId,
                                       @RequestParam(value = "protocol", required = false) String protocol) {
        final NeutralDeviceConfig config = router.lastConfigOf(agentId);
        if (config == null) {
            throw new AgentNotFoundException(agentId);
        }
        final List<Map<String, Object>> routes = toRouteList(config, protocol);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", agentId);
        body.put("hostname", config.getHostname());
        body.put("product", config.getProduct());
        body.put("route_count", routes.size());
        body.put("routes", routes);
        return body;
    }

    /**
     * 프로토콜별 경로 개수를 요약합니다. (대시보드 카드용)
     *
     * @return 장치별 프로토콜 집계
     */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        final List<Map<String, Object>> devices = new ArrayList<>();
        router.allConfigs().forEach((agentId, config) -> {
            final Map<String, Integer> byProtocol = new LinkedHashMap<>();
            long defaultRoutes = 0;
            for (final NeutralDeviceConfig.RouteConfig route : config.getRoutes()) {
                final String key = route.getProtocol() == null ? "unknown" : route.getProtocol();
                byProtocol.merge(key, 1, Integer::sum);
                if (Boolean.TRUE.equals(route.getDefaultRoute())) {
                    defaultRoutes++;
                }
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("hostname", config.getHostname());
            entry.put("total", config.getRoutes().size());
            entry.put("default_routes", defaultRoutes);
            entry.put("by_protocol", byProtocol);
            devices.add(entry);
        });

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("device_count", devices.size());
        body.put("devices", devices);
        return body;
    }

    /**
     * 경로 목록을 응답 형태로 바꾸고 프로토콜로 거릅니다.
     *
     * @param config   중립 설정
     * @param protocol 프로토콜 필터 (null/공백이면 전체)
     * @return 경로 맵 목록
     */
    private List<Map<String, Object>> toRouteList(NeutralDeviceConfig config, String protocol) {
        final List<Map<String, Object>> routes = new ArrayList<>();
        final String filter = (protocol == null || protocol.isBlank())
                ? null
                : protocol.trim().toUpperCase(Locale.ROOT);

        for (final NeutralDeviceConfig.RouteConfig route : config.getRoutes()) {
            final String routeProtocol = route.getProtocol() == null
                    ? "unknown"
                    : route.getProtocol().toUpperCase(Locale.ROOT);
            if (filter != null && !routeProtocol.contains(filter)) {
                continue;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("protocol", route.getProtocol());
            entry.put("prefix", route.getPrefix());
            entry.put("next_hop", route.getNextHop());
            entry.put("next_hop_interface", route.getNextHopInterface());
            entry.put("metric", route.getMetric());
            entry.put("selected", route.getSelected());
            entry.put("default_route", route.getDefaultRoute());
            routes.add(entry);
        }
        log.debug("route list for {}: {} entries (filter={})", config.getHostname(), routes.size(), filter);
        return routes;
    }

    /** 장치를 찾지 못했을 때 404 를 내기 위한 예외입니다. */
    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class AgentNotFoundException extends RuntimeException {
        /**
         * @param agentId 찾지 못한 장치 식별자
         */
        public AgentNotFoundException(String agentId) {
            super("no routing table for agent: " + agentId);
        }
    }
}
