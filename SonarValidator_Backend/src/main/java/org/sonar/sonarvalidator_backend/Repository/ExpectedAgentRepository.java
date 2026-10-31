package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpectedAgentRepository extends JpaRepository<ExpectedAgent, Long> {

    Optional<ExpectedAgent> findByAgentId(String agentId);

    List<ExpectedAgent> findByProjectKeyOrderByCreatedAtDesc(String projectKey);

    List<ExpectedAgent> findAllByOrderByCreatedAtDesc();

    boolean existsByAgentId(String agentId);

    void deleteByProjectKey(String projectKey);
}
