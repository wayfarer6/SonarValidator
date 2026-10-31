package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {

    List<AiProvider> findAllByOrderByNameAsc();

    List<AiProvider> findByIsDefaultTrue();

    List<AiProvider> findByEnabledTrueOrderByNameAsc();

    Optional<AiProvider> findByName(String name);
}
