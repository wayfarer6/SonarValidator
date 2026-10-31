package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.entity.ComplianceChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceChangeRepository extends JpaRepository<ComplianceChange, Long> {

    List<ComplianceChange> findByProjectKeyOrderByTimestampDesc(String projectKey);

    List<ComplianceChange> findByAgentIdOrderByTimestampDesc(String agentId);

    List<ComplianceChange> findAllByOrderByTimestampDesc();

    List<ComplianceChange> findByProjectKeyAndAgentIdOrderByTimestampDesc(String projectKey, String agentId);
}
