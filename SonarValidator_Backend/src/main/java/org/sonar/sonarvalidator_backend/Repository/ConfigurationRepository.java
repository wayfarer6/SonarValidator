package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.Configuration;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigurationRepository extends JpaRepository<Configuration, Integer> {

    Optional<Configuration> findByAgentId(String agentId);

    Optional<Configuration> findBy_hostname(String hostname);

    List<Configuration> findByDeviceType(DeviceType deviceType);

    boolean existsByAgentId(String agentId);
}
