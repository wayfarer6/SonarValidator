package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.Date;

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

import org.sonar.sonarvalidator_backend.Model.Configuration;

@Entity
@Table(
        name = "quarantine_state",
        indexes = {
                // 장치별 현재 격리 여부 (가장 빈번)
                @Index(name = "idx_quarantine_agent", columnList = "agent_id, released_at"),
                // 노드별 현재 격리 여부 (Agent 없는 장비 경로 — DB Design v1.5)
                @Index(name = "idx_quarantine_node", columnList = "node_id, released_at"),
                // 격리 중인 장치 전체 조회 (토폴로지/푸시 대상 제외)
                @Index(name = "idx_quarantine_released", columnList = "released_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class QuarantineState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", length = 120)
    private String agentId;

    @Column(name = "node_id")
    private Integer nodeId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "scope", length = 20)
    private Scope scope = Scope.NODE;

    @Column(name = "target_cidr", length = 80)
    private String targetCidr;

    public enum Scope {
        NODE,
        CONNECTION
    }

    @Column(name = "project_key", length = 120)
    private String projectKey;

    @Column(name = "requested_by", length = 120)
    private String requestedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "command_delivered", nullable = false)
    private boolean commandDelivered = false;

    @Column(name = "quarantined_at", nullable = false)
    private Date quarantinedAt;

    @Column(name = "released_at")
    private Date releasedAt;

    @Column(name = "released_by", length = 120)
    private String releasedBy;

    public boolean isActive() {
        return releasedAt == null;
    }
}
