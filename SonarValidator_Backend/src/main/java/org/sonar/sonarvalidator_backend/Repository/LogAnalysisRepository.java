package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.LogAnalysis;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogAnalysisRepository extends JpaRepository<LogAnalysis, Long> {

    Optional<LogAnalysis> findByAnalysisId(String analysisId);

    List<LogAnalysis> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<LogAnalysis> findByProjectKeyOrderByCreatedAtDesc(String projectKey, Pageable pageable);

    List<LogAnalysis> findByAgentIdOrderByCreatedAtDesc(String agentId, Pageable pageable);

    List<LogAnalysis> findByProjectKeyAndAgentIdOrderByCreatedAtDesc(String projectKey,
                                                                     String agentId,
                                                                     Pageable pageable);
}
