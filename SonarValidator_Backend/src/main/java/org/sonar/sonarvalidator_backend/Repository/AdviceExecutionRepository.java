package org.sonar.sonarvalidator_backend.Repository;
import java.util.*;
import org.sonar.sonarvalidator_backend.Model.entity.AdviceExecution;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AdviceExecutionRepository extends JpaRepository<AdviceExecution,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from AdviceExecution e where e.executionId=:id")
    Optional<AdviceExecution> findForUpdate(@Param("id") String id);
    List<AdviceExecution> findByProjectKeyAndAdviceIdOrderByCreatedAtDesc(String project, String advice);
    List<AdviceExecution> findByActiveAgentIdIsNotNull();
    List<AdviceExecution> findTop12ByProjectKeyAndStatusInOrderByCreatedAtDesc(String projectKey, Collection<String> statuses);
}
