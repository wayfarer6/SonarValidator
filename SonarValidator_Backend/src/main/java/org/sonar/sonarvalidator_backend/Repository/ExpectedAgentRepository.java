package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ExpectedAgentRepository extends JpaRepository<ExpectedAgent, Long> {

    Optional<ExpectedAgent> findByAgentId(String agentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExpectedAgent a where a.agentId = :agentId")
    Optional<ExpectedAgent> findForMonitorUpdate(@Param("agentId") String agentId);

    List<ExpectedAgent> findByProjectKeyOrderByCreatedAtDesc(String projectKey);

    List<ExpectedAgent> findAllByOrderByCreatedAtDesc();

    boolean existsByAgentId(String agentId);

    void deleteByProjectKey(String projectKey);
}
