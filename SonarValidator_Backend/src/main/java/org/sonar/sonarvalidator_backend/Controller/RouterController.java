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

@RestController
@RequestMapping("/api/v1/routes")
public class RouterController {

    private static final Logger log = LoggerFactory.getLogger(RouterController.class);

    private final AgentMessageRouterService router;

    public RouterController(AgentMessageRouterService router) {
        this.router = router;
    }

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

    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class AgentNotFoundException extends RuntimeException {
        public AgentNotFoundException(String agentId) {
            super("no routing table for agent: " + agentId);
        }
    }
}
