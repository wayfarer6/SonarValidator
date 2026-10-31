package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.PolicyAdvice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyAdviceRepository extends JpaRepository<PolicyAdvice, Long> {

    Optional<PolicyAdvice> findByAdviceId(String adviceId);

    List<PolicyAdvice> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<PolicyAdvice> findByProjectKeyOrderByCreatedAtDesc(String projectKey, Pageable pageable);

    List<PolicyAdvice> findByProjectKeyAndRuleIdOrderByCreatedAtDesc(String projectKey,
                                                                     String ruleId,
                                                                     Pageable pageable);
}
