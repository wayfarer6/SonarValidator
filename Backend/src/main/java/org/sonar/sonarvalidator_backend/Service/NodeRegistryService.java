package org.sonar.sonarvalidator_backend.Service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.Configuration;
import org.sonar.sonarvalidator_backend.Repository.ConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노드 정본({@code configuration})을 만들고 갱신합니다.
 *
 * <h2>⚠️ 왜 이 서비스가 필요했나 — 노드 정본이 DB 에 없었다</h2>
 * <p>{@code device_log} 와 {@code opnsense_firewall} 이 {@code node_id} 로
 * 노드를 참조하는데도, {@code configuration} 테이블에 <b>쓰는 코드가 없었습니다.</b>
 * 텔레메트리로 들어온 설정은 {@link AgentTelemetryStore} 의 인메모리 맵에만
 * 남아 재기동하면 사라졌고, 그래서 노드 번호가 DB 에 존재하지 않아
 * 외래키를 걸 대상 자체가 없었습니다.
 *
 * <h2>⚠️ upsert 기준은 자연키(agent_id)</h2>
 * <p>같은 장비가 텔레메트리를 다시 보낼 때마다 새 행을 만들면
 * "같은 장비가 여러 행" 이 되어 {@code node_id} 참조가 갈라집니다.
 * 그래서 {@code agent_id} 로 먼저 찾고, 있으면 그 행을 갱신합니다.
 *
 * <h2>⚠️ 예외를 던지지 않는다</h2>
 * <p>이 경로는 텔레메트리 수신 파이프라인 위에 있습니다. 노드 적재 실패가
 * 수신 자체를 막으면 텔레메트리가 통째로 유실됩니다. 실패는 로그로 남기고
 * 기존 노드가 있으면 그것을, 없으면 빈 값을 돌려줍니다.
 */
@Service
public class NodeRegistryService {

    private static final Logger log = LoggerFactory.getLogger(NodeRegistryService.class);

    private final ConfigurationRepository repository;

    /**
     * @param repository 노드 정본 저장소
     */
    public NodeRegistryService(ConfigurationRepository repository) {
        this.repository = repository;
    }

    /**
     * 장비(Agent)의 노드 정본을 찾거나 만듭니다.
     *
     * <p>텔레메트리/로그 적재가 {@code node_id} 를 알아야 할 때 씁니다.
     * 못 만들면 {@code null} 을 돌려줍니다 — 호출자는 FK 를 비워 저장하고,
     * 그것이 "노드 미상" 이라는 정직한 상태입니다.
     *
     * @param agentId 장비(Agent) 식별자 (null/blank 이면 null 반환)
     * @param config  파서가 만든 중립 설정 (없으면 null — 유형은 추론)
     * @return 노드 정본 (실패 시 null)
     */
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
            log.info("node registered: id={} agent={} type={}",
                    saved.getNodeId(), key, saved.getDeviceType());
            return saved;
        } catch (RuntimeException ex) {
            // 노드 적재 실패가 텔레메트리/로그 수신을 막으면 안 됩니다.
            log.warn("node registration failed for agent={}: {}", key, ex.getMessage());
            return repository.findByAgentId(key).orElse(null);
        }
    }

    /**
     * 노드 정본을 조회합니다. (없으면 {@code null})
     *
     * @param agentId 장비(Agent) 식별자
     * @return 노드 정본
     */
    @Transactional(readOnly = true)
    public Configuration find(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        return repository.findByAgentId(agentId.trim()).orElse(null);
    }

    /**
     * 장치 유형을 지정해 노드를 찾거나 만듭니다.
     *
     * <h2>⚠️ 왜 유형을 강제하는가</h2>
     * <p>식별자 추론은 관례일 뿐입니다. {@code opnsense-1} 처럼 이름에 유형이
     * 없는 장치는 VM 으로 추론되어, 정책 분기와 격리 방식이 모두 어긋납니다.
     * 그래서 연결 경로(예: OPNsense REST 등록)가 <b>자기가 아는 유형</b>을
     * 명시할 수 있어야 합니다.
     *
     * @param agentId 장비(Agent) 식별자
     * @param type    지정할 장치 유형 (null 이면 추론)
     * @return 노드 정본 (실패 시 null)
     */
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
            log.info("node registered: id={} agent={} type={}",
                    saved.getNodeId(), key, saved.getDeviceType());
            return saved;
        } catch (RuntimeException ex) {
            log.warn("node registration failed for agent={}: {}", key, ex.getMessage());
            return repository.findByAgentId(key).orElse(null);
        }
    }

    /**
     * Resolves or creates a node and propagates persistence failures to callers.
     *
     * <p>Credential registration must not continue when this operation fails:
     * the credential row is meaningful only when attached to a persisted node.
     * Unlike telemetry ingestion, this method deliberately does not swallow
     * repository errors.
     *
     * @param agentId external node identifier used by existing API clients
     * @param type    known device type
     * @return persisted canonical node
     */
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

    /**
     * 노드 번호를 조회합니다.
     *
     * @param agentId 장비(Agent) 식별자
     * @return 노드 번호 (없으면 null)
     */
    @Transactional(readOnly = true)
    public Integer nodeIdOf(String agentId) {
        final Configuration node = find(agentId);
        return node == null ? null : node.getNodeId();
    }

    /**
     * 중립 설정에서 알 수 있는 유형을 반영합니다.
     *
     * <p>이미 유형이 있으면 덮어쓰지 않습니다. 운영자가 등록한 값이
     * 파서의 추정보다 신뢰할 수 있기 때문입니다.
     *
     * @param node   노드 정본
     * @param config 중립 설정 (없으면 null)
     */
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