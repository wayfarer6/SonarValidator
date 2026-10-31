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

@DataJpaTest
@ActiveProfiles("test")
class OPNSenseFirewallRepositoryTest {

    @Autowired
    private OPNSenseFirewallRepository repository;

    @Autowired
    private ConfigurationRepository configurationRepository;

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
