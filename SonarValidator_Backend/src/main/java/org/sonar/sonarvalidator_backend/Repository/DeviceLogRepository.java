package org.sonar.sonarvalidator_backend.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.DeviceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface DeviceLogRepository extends JpaRepository<DeviceLog, Long>,
        JpaSpecificationExecutor<DeviceLog> {

    Optional<DeviceLog> findByFingerprint(String fingerprint);

    @Query("""
            SELECT l.severityNum, COUNT(l) FROM DeviceLog l
            GROUP BY l.severityNum
            ORDER BY l.severityNum
            """)
    List<Object[]> countBySeverity();

    @Query("""
            SELECT l.agentId, COUNT(l) FROM DeviceLog l
            GROUP BY l.agentId
            ORDER BY COUNT(l) DESC
            """)
    List<Object[]> countByAgent();

    @Query("SELECT DISTINCT l.agentId FROM DeviceLog l ORDER BY l.agentId")
    List<String> distinctAgentIds();

    List<DeviceLog> findByLoggedAtBefore(Date cutoff);
}
