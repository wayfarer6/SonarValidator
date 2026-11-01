package org.sonar.sonarvalidator_backend.Service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.Configuration;
import org.sonar.sonarvalidator_backend.Repository.ConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.core.annotation.Order;
import org.sonar.sonarvalidator_backend.Service.telemetry.TelemetryObserver;
import org.sonar.sonarvalidator_backend.Service.telemetry.TelemetrySnapshot;
import org.springframework.transaction.annotation.Transactional;

@Service
@Order(100)
public class NodeRegistryService implements TelemetryObserver {

    @Override
    @Transactional
    public void onTelemetry(TelemetrySnapshot snapshot) {
        resolveOrCreate(snapshot.agentId(), snapshot.config());
    }

    private static final Logger log = LoggerFactory.getLogger(NodeRegistryService.class);

    private final ConfigurationRepository repository;

    public NodeRegistryService(ConfigurationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Configuration resolveOrCreate(String agentId, NeutralDeviceConfig config) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        final String key = agentId.trim();
        try {
            final Optional<Configuration> existing = repository.findByAgentId(key);
            if (existing.isPresent()) {
                final Configuration node = existing.get();
                applyType(node, config);
                return repository.save(node);
            }

            final Configuration created = Configuration.from(key, config);
            // 유형이 비어 있으면 식별자 관례로 추론합니다.
            applyType(created, config);
            final Configuration saved = repository.save(created);
            log.info("장비 식별: id={} 노드={} 종류={}",
                    saved.getNodeId(), key, saved.getDeviceType());
            return saved;
        } catch (RuntimeException ex) {
            // 노드 적재 실패가 텔레메트리/로그 수신을 막으면 안 됩니다.
            log.warn("node registration failed for agent={}: {}", key, ex.getMessage());
            return repository.findByAgentId(key).orElse(null);
        }
    }

    @Transactional(readOnly = true)
    public Configuration find(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        return repository.findByAgentId(agentId.trim()).orElse(null);
    }

    @Transactional
    public Configuration resolveOrCreate(String agentId,
                                         org.sonar.sonarvalidator_backend.Model.DeviceType type) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        final String key = agentId.trim();
        try {
            final Configuration node = repository.findByAgentId(key)
                    .orElseGet(() -> Configuration.from(key, null));
            if (type != null) {
                node.setDeviceType(type);
            } else {
                applyType(node, null);
            }
            final Configuration saved = repository.save(node);
            log.info("장비 식별: id={} 노드={} 종류={}",
                    saved.getNodeId(), key, saved.getDeviceType());
            return saved;
        } catch (RuntimeException ex) {
            log.warn("node registration failed for agent={}: {}", key, ex.getMessage());
            return repository.findByAgentId(key).orElse(null);
        }
    }

    @Transactional
    public Configuration resolveOrCreateRequired(
            String agentId,
            org.sonar.sonarvalidator_backend.Model.DeviceType type) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("node identifier is required");
        }
        final String key = agentId.trim();
        if (key.length() > 120) {
            throw new IllegalArgumentException("node identifier must be at most 120 characters");
        }
        final Configuration node = repository.findByAgentId(key)
                .orElseGet(() -> Configuration.from(key, null));
        if (type != null) {
            node.setDeviceType(type);
        } else {
            applyType(node, null);
        }
        return repository.save(node);
    }

    @Transactional(readOnly = true)
    public Integer nodeIdOf(String agentId) {
        final Configuration node = find(agentId);
        return node == null ? null : node.getNodeId();
    }

    private static void applyType(Configuration node, NeutralDeviceConfig config) {
        if (node.getDeviceType() != null) {
            return;
        }
        final var type = org.sonar.sonarvalidator_backend.Model.DeviceType.fromString(
                config == null ? null : config.getDeviceType());
        if (type != null) {
            node.setDeviceType(type);
        } else if (node.getAgentId() != null) {
            node.setDeviceType(
                    org.sonar.sonarvalidator_backend.Model.DeviceType.inferFromDeviceId(node.getAgentId()));
        }
    }
}
