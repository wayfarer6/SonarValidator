package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Configuration;
import org.sonar.sonarvalidator_backend.Model.entity.OPNSenseFirewall;
import org.sonar.sonarvalidator_backend.Repository.ConfigurationRepository;
import org.sonar.sonarvalidator_backend.Repository.OPNSenseFirewallRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * JPA 매핑과 파생 쿼리가 실제로 동작하는지 검증합니다.
 *
 * <p>{@code @DataJpaTest} 는 JPA 관련 빈만 로드하고 각 테스트를 트랜잭션으로
 * 감싸 롤백하므로, H2 인메모리 DB 위에서 격리된 검증이 가능합니다.
 *
 * <h2>⚠️ DB Design v1.5 — agent_id 대신 node_id 관계를 검증한다</h2>
 * <p>이전 테스트는 {@code findByAgentId} 를 검증했지만, OPNsense 는 REST API
 * 로 직접 연결되어 Agent 가 없을 수 있습니다. 그래서 이제는
 * <b>{@code node_id} 가 {@code configuration} 을 참조하는 관계</b>와
 * <b>관리 IP 조회</b>를 검증합니다.
 */
@DataJpaTest
@ActiveProfiles("test")
class OPNSenseFirewallRepositoryTest {

    @Autowired
    private OPNSenseFirewallRepository repository;

    @Autowired
    private ConfigurationRepository configurationRepository;

    /**
     * 저장 후 기본 키({@code node_id})가 그대로 유지되고 다시 조회되는지
     * 확인합니다.
     *
     * <p>대리 키를 쳐다보지 않으므로 <b>IDENTITY 가 아니어도</b> 됩니다.
     * 저장 전에 넣은 노드 번호가 그대로 키가 되는지가 핵심입니다.
     */
    @Test
    @DisplayName("노드 정본을 참조하면 그 node_id 가 방화벽의 키가 된다")
    void mapsIdFromConfiguration() {
        final Configuration node = configurationRepository.save(
                Configuration.from("opnsense-1", null));

        final OPNSenseFirewall saved = repository.save(
                new OPNSenseFirewall(node, "DMZ 방화벽"));

        assertNotNull(saved.getNodeId(), "노드 번호가 키로 배정되어야 합니다");
        assertEquals(node.getNodeId(), saved.getNodeId(),
                "방화벽 키가 configuration.node_id 와 같아야 합니다");

        final Optional<OPNSenseFirewall> found = repository.findById(node.getNodeId());
        assertTrue(found.isPresent());
        assertEquals("DMZ 방화벽", found.get().getName());
    }

    /**
     * Agent 없이(노드 정본 + 관리 IP 만으로) 저장·조회가 되는지 확인합니다.
     *
     * <p>OPNsense 는 Agent 가 없을 수 있으므로, {@code agent_id} 없이
     * 노드 정본을 참조해 적재되는 경로가 동작해야 합니다.
     * ({@code management_ip} 가 사실상 유일한 조회 키입니다)
     */
    @Test
    @DisplayName("agent_id 없이 노드 정본과 관리 IP 만으로 저장된다")
    void storesWithoutAgentId() {
        final Configuration node = configurationRepository.save(
                Configuration.from(null, null));
        node.set_hostname("opnsense-dmz");
        configurationRepository.save(node);

        final OPNSenseFirewall entity = new OPNSenseFirewall(node, "경계 방화벽");
        entity.setManagementIp("10.99.143.2");
        repository.save(entity);

        final Optional<OPNSenseFirewall> found = repository.findByManagementIp("10.99.143.2");
        assertTrue(found.isPresent());
        assertEquals(node.getNodeId(), found.get().getNodeId());
        assertEquals("경계 방화벽", found.get().getName());

        assertTrue(repository.existsByManagementIp("10.99.143.2"));
        assertFalse(repository.existsByManagementIp("10.99.143.99"));
    }

    /**
     * 엔티티에 정의한 컬럼(name, managementIp, version)이 모두 저장·조회되는지
     * 확인합니다. 컬럼 누락/이름 오타를 잡기 위한 회귀 테스트입니다.
     */
    @Test
    @DisplayName("name, managementIp, version 컬럼이 모두 왕복한다")
    void persistsAllColumns() {
        final Configuration node = configurationRepository.save(
                Configuration.from("opnsense-3", null));

        final OPNSenseFirewall entity = new OPNSenseFirewall(node, "경계 방화벽");
        entity.setManagementIp("10.99.143.3");
        entity.setVersion("26.1");
        repository.save(entity);

        final OPNSenseFirewall found = repository.findById(node.getNodeId()).orElseThrow();
        assertEquals("10.99.143.3", found.getManagementIp());
        assertEquals("26.1", found.getVersion());

        // 이름 검색은 대소문자를 무시해야 합니다.
        final List<OPNSenseFirewall> matched = repository.findByNameContainingIgnoreCase("경계");
        assertEquals(1, matched.size());
        assertEquals(node.getNodeId(), matched.get(0).getNodeId());
    }
}
