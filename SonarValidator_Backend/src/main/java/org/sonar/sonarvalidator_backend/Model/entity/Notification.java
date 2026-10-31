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

@Entity
@Table(
        name = "notification",
        indexes = {
                // 최신순 목록 조회 (가장 빈번)
                @Index(name = "idx_notification_occurred", columnList = "occurred_at"),
                // 안읽음 배지 개수
                @Index(name = "idx_notification_read", columnList = "is_read"),
                // 프로젝트별 알림
                @Index(name = "idx_notification_project", columnList = "project_key, occurred_at"),
                // 장치별 알림
                @Index(name = "idx_notification_agent", columnList = "agent_id, occurred_at"),
        })
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, unique = true, length = 80)
    private String notificationId;

    @Column(nullable = false, length = 40)
    private String category = "SYSTEM";

    @Column(nullable = false, length = 20)
    private String severity = "info";

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 2000)
    private String message;

    @Column(name = "project_key", length = 120)
    private String projectKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "agent_id", length = 120)
    private String agentId;

    @Column(name = "node_id")
    private Integer nodeId;

    @Column(length = 200)
    private String source = "system";

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "occurred_at", nullable = false)
    private Date occurredAt;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(length = 300)
    private String link;

    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    @Column(name = "repeat_count", nullable = false)
    private int repeatCount = 1;
}
