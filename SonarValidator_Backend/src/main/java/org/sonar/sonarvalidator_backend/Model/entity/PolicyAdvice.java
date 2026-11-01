package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "policy_advice",
        indexes = {
                // 프로젝트별 최신순 조회
                @Index(name = "idx_policy_advice_project", columnList = "project_key, created_at"),
                // 규칙별 조회 (위반 카드에서 "이 위반의 과거 조언")
                @Index(name = "idx_policy_advice_rule", columnList = "rule_id, created_at"),
        })
@Getter
@Setter
@NoArgsConstructor
public class PolicyAdvice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "advice_id", nullable = false, unique = true, length = 60)
    private String adviceId;

    @Column(name = "project_key", nullable = false, length = 120)
    private String projectKey;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "rule_id", length = 120)
    private String ruleId;

    @Column(name = "scope", length = 20)
    private String scope = "project";

    @Column(name = "violation_count")
    private Integer violationCount = 0;

    @Column(name = "compliant")
    private Boolean compliant = false;

    @Column(name = "included_violation_count")
    private Integer includedViolationCount = 0;

    @Column(name = "truncated")
    private Boolean truncated = false;

    @Column(name = "provider_name", length = 120)
    private String providerName;

    @Column(name = "model", length = 200)
    private String model;

    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "summary", length = 4000)
    private String summary;

    @Column(name = "root_cause", length = 8000)
    private String rootCause;

    @Column(name = "policy_advice", length = 4000)
    private String policyAdvice;

    @Column(name = "options_json", length = 8000)
    private String optionsJson;

    @Column(name = "commands_json", length = 30000)
    private String commandsJson;

    @Column(name = "context_fingerprint", length = 64)
    private String contextFingerprint;

    @Column(name = "target_agent", length = 120)
    private String targetAgent;

    @Column(name = "evidence_json", length = 4000)
    private String evidenceJson;

    @Column(name = "needs_more_data")
    private Boolean needsMoreData = false;

    @Column(name = "raw_response", length = 30000)
    private String rawResponse;

    @Column(name = "structured")
    private Boolean structured = false;

    @Column(name = "requested_by", length = 200)
    private String requestedBy;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "succeeded")
    private Boolean succeeded = true;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "created_at", nullable = false)
    private Date createdAt;
}
