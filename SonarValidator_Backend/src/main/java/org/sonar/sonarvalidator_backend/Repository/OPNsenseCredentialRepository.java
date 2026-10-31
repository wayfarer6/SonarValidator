package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.OPNsenseCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OPNsenseCredentialRepository extends JpaRepository<OPNsenseCredential, Long> {

    @Query("select c from OPNsenseCredential c where c.node.node_id = :nodeId")
    Optional<OPNsenseCredential> findByNodeId(@Param("nodeId") Integer nodeId);

    @Query("select (count(c) > 0) from OPNsenseCredential c where c.node.node_id = :nodeId")
    boolean existsByNodeId(@Param("nodeId") Integer nodeId);

    List<OPNsenseCredential> findAllByOrderByIdAsc();

    List<OPNsenseCredential> findByApiKeyIsNotNullAndSecretIsNotNull();
}
