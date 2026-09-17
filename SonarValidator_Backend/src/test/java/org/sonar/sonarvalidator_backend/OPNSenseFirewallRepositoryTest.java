package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.entitiy.OPNSenseFirewall;
import org.sonar.sonarvalidator_backend.Repository.OPNSenseFirewallRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * JPA 매핑과 파생 쿼리가 실제로 동작하는지 검증합니다.
 *
 * <p>{@code @DataJpaTest} 는 JPA 관련 빈만 로드하고 각 테스트를 트랜잭션으로
 * 감싸 롤백하므로, H2 인메모리 DB 위에서 격리된 검증이 가능합니다.
 */
@DataJpaTest
@ActiveProfiles("test")
class OPNSenseFirewallRepositoryTest {

    @Autowired
    private OPNSenseFirewallRepository repository;

    /**
     * 저장 후 기본 키가 채워지고 다시 조회되는지 확인합니다.
     */
    @Test
    @DisplayName("엔티티를 저장하면 id 가 생성되고 조회된다")
    void savesAndFindsById() {
        final OPNSenseFirewall saved = repository.save(
                new OPNSenseFirewall("fw-01", "DMZ 방화벽"));

        assertNotNull(saved.getId(), "IDENTITY 전략으로 id 가 채워져야 합니다");

        final Optional<OPNSenseFirewall> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("fw-01", found.get().getAgentId());
        assertEquals("DMZ 방화벽", found.get().getName());
    }

    /**
     * 파생 쿼리 {@code findByAgentId} 가 동작하는지 확인합니다.
     */
    @Test
    @DisplayName("agentId 로 조회할 수 있다")
    void findsByAgentId() {
        repository.save(new OPNSenseFirewall("fw-02", "내부 방화벽"));

        final Optional<OPNSenseFirewall> found = repository.findByAgentId("fw-02");
        assertTrue(found.isPresent());
        assertEquals("내부 방화벽", found.get().getName());

        assertTrue(repository.existsByAgentId("fw-02"));
        assertFalse(repository.existsByAgentId("fw-does-not-exist"));
    }

    /**
     * 엔티티에 정의한 컬럼(name, managementIp, version)이 모두 저장·조회되는지
     * 확인합니다. 컬럼 누락/이름 오타를 잡기 위한 회귀 테스트입니다.
     */
    @Test
    @DisplayName("name, managementIp, version 컬럼이 모두 왕복한다")
    void persistsAllColumns() {
        final OPNSenseFirewall entity = new OPNSenseFirewall("fw-03", "경계 방화벽");
        entity.setManagementIp("10.0.0.1");
        entity.setVersion("24.7");
        repository.save(entity);

        final OPNSenseFirewall found = repository.findByAgentId("fw-03").orElseThrow();
        assertEquals("10.0.0.1", found.getManagementIp());
        assertEquals("24.7", found.getVersion());

        // 이름 검색은 대소문자를 무시해야 합니다.
        final List<OPNSenseFirewall> matched = repository.findByNameContainingIgnoreCase("경계");
        assertEquals(1, matched.size());
        assertEquals("fw-03", matched.get(0).getAgentId());
    }
}
