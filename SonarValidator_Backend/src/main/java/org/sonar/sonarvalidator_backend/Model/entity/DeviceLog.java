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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.sonar.sonarvalidator_backend.Model.Configuration;

@Entity
@Table(
        name = "device_log",
        indexes = {
                // 장비별 + 시간 범위 조회 (가장 빈번)
                @Index(name = "idx_device_log_agent_time",
                        columnList = "agent_id, logged_at"),
                // 전체 시간순 조회 (장비 미지정)
                @Index(name = "idx_device_log_time", columnList = "logged_at"),
                // 심각도 필터
                @Index(name = "idx_device_log_severity", columnList = "severity_num"),
                // 노드별 조회 (configuration 조인)
                @Index(name = "idx_device_log_node", columnList = "node_id"),
        })
@Getter
@Setter
@NoArgsConstructor
public class DeviceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id")
    private Configuration node;

    @Column(name = "agent_id", nullable = false, length = 120)
    private String agentId;

    @Column(name = "project_key", length = 120)
    private String projectKey;

    @Column(name = "product", length = 120)
    private String product;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "logged_at", nullable = false)
    private Date loggedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "collected_at")
    private Date collectedAt;

    @Column(name = "severity_num", nullable = false)
    private Integer severityNum = 6;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "info";

    @Column(name = "facility", length = 60)
    private String facility;

    @Column(name = "message_id", length = 120)
    private String messageId;

    @Column(name = "message", length = 8000)
    private String message;

    @Column(name = "raw", length = 8000)
    private String raw;

    @Column(name = "source", length = 20)
    private String source = "agent";

    @Column(name = "fingerprint", length = 64)
    private String fingerprint;

    @Column(name = "repeat_count")
    private Integer repeatCount = 1;

    @Column(name = "highlighted")
    private Boolean highlighted = false;

    @Column(name = "note", length = 1000)
    private String note;

    public Integer nodeId() {
        return node == null ? null : node.getNodeId();
    }
}
