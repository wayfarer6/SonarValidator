package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "log_analysis",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_log_analysis_agent_project",
                columnNames = {"agent_id", "project_id"}),
        indexes = {
                // 프로젝트별 최신순 조회
                @Index(name = "idx_log_analysis_project", columnList = "project_key, created_at"),
                // 장비별 최신순 조회
                @Index(name = "idx_log_analysis_agent", columnList = "agent_id, created_at"),
        })
@Getter
@Setter
@NoArgsConstructor
public class LogAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, unique = true, length = 60)
    private String analysisId;

    @Column(name = "log_id")
    private Long logId;

    @Column(name = "project_key", length = 120)
    private String projectKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "agent_id", length = 120)
    private String agentId;

    @Column(name = "scope", length = 20)
    private String scope = "filter";

    @Column(name = "severity_filter", length = 20)
    private String severityFilter;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "period_from")
    private Date periodFrom;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "period_to")
    private Date periodTo;

    @Column(name = "filter_json", length = 4000)
    private String filterJson;

    @Column(name = "log_ids_json", length = 4000)
    private String logIdsJson;

    @Column(name = "included_log_count")
    private Integer includedLogCount = 0;

    @Column(name = "total_log_count")
    private Integer totalLogCount = 0;

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

    @Column(name = "recommendations_json", length = 8000)
    private String recommendationsJson;

    @Column(name = "raw_response", length = 30000)
    private String rawResponse;

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
