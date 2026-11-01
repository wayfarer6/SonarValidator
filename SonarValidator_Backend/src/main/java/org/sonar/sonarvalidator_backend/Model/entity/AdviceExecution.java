package org.sonar.sonarvalidator_backend.Model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="advice_execution", indexes={@Index(name="idx_advice_execution_history", columnList="project_key,created_at")})
@Getter @Setter @NoArgsConstructor
public class AdviceExecution {
    @Id @Column(length=36) private String executionId;
    @Version private long version;
    @Column(name="project_key",nullable=false,length=120) private String projectKey;
    @Column(nullable=false,length=60) private String adviceId;
    private int proposalIndex;
    @Column(nullable=false,length=120) private String agentId;
    @Column(name="active_agent_id",unique=true,length=120) private String activeAgentId;
    @Column(nullable=false,length=80) private String operation;
    @Column(nullable=false,length=32) private String status;
    @Column(length=120) private String sourceSessionId;
    @Column(nullable=false,length=200) private String requestedBy;
    @Column(nullable=false,length=12000) private String planJson;
    @Column(length=120000) private String resultJson;
    @Column(length=2000) private String detail;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    private Instant dispatchedAt;
    private Instant finishedAt;
    @Column(nullable=false) private Instant expiresAt;
}
