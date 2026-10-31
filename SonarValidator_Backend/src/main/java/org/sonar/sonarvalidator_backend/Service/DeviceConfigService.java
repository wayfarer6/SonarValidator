package org.sonar.sonarvalidator_backend.Service;

import java.util.List;
import java.util.Optional;
import org.sonar.sonarvalidator_backend.extension.StrategyRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.Config.DeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

@Service
public class DeviceConfigService {

    private static final Logger log = LoggerFactory.getLogger(DeviceConfigService.class);

    private final List<DeviceConfigParser> parsers;
    private final StrategyRegistry<DeviceConfigParser> registry;

    public DeviceConfigService(List<DeviceConfigParser> parsers) {
        this.parsers = List.copyOf(parsers);
        this.registry = new StrategyRegistry<>(parsers, DeviceConfigParser::priority);
        log.info("device config parsers registered: {}", this.parsers.size());
    }

    public Optional<DeviceConfigParser> parserFor(String productName, String deviceType) {
        try {
            return registry.select(parser -> {
                try { return parser.supports(productName, deviceType); }
                catch (RuntimeException ex) {
                    log.warn("parser {} supports() failed: {}", parser.getClass().getSimpleName(), ex.getMessage());
                    return false;
                }
            });
        } catch (IllegalStateException ex) {
            log.warn("config parser selection rejected for product={}: {}", productName, ex.getMessage());
            return Optional.empty();
        }
    }

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

    public List<String> supportedFormats() {
        return parsers.stream().map(DeviceConfigParser::format).toList();
    }
}
