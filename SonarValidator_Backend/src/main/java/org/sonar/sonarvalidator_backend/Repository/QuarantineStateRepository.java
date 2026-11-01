package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.QuarantineState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuarantineStateRepository extends JpaRepository<QuarantineState, Long> {

    Optional<QuarantineState> findByAgentIdAndReleasedAtIsNull(String agentId);

        Optional<QuarantineState> findFirstByAgentIdAndTargetCidrAndReleasedAtIsNull(
            String agentId, String targetCidr);

        List<QuarantineState> findAllByAgentIdAndReleasedAtIsNull(String agentId);

    Optional<QuarantineState> findByNodeIdAndReleasedAtIsNull(Integer nodeId);

        Optional<QuarantineState> findFirstByNodeIdAndTargetCidrAndReleasedAtIsNull(
            Integer nodeId, String targetCidr);

        List<QuarantineState> findAllByNodeIdAndReleasedAtIsNull(Integer nodeId);

    List<QuarantineState> findByNodeIdOrderByQuarantinedAtDesc(Integer nodeId, Pageable pageable);

    List<QuarantineState> findByReleasedAtIsNullOrderByQuarantinedAtDesc(Pageable pageable);

    List<QuarantineState> findByProjectKeyAndReleasedAtIsNullOrderByQuarantinedAtDesc(
            String projectKey, Pageable pageable);

    List<QuarantineState> findByReleasedAtIsNotNullAndApplyStatusInOrderByQuarantinedAtDesc(
            List<String> statuses, Pageable pageable);

    List<QuarantineState> findByAgentIdOrderByQuarantinedAtDesc(String agentId, Pageable pageable);
}
