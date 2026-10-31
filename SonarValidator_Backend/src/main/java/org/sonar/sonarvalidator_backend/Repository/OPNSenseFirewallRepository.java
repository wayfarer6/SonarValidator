package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.OPNSenseFirewall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OPNSenseFirewallRepository extends JpaRepository<OPNSenseFirewall, Integer> {

    Optional<OPNSenseFirewall> findByManagementIp(String managementIp);

    List<OPNSenseFirewall> findByNameContainingIgnoreCase(String keyword);

    boolean existsByManagementIp(String managementIp);
}
