package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "expected_agent")
@Getter
@Setter
@NoArgsConstructor
public class ExpectedAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, unique = true, length = 120)
    private String agentId;

    @Column(name = "project_key", length = 120)
    private String projectKey;

    @Column(name = "device_type", length = 30)
    private String deviceType;

    @Column(name = "node_type", length = 30)
    private String nodeType;

    @Column(name = "expected_ip", length = 60)
    private String expectedIp;

    @Column(length = 20)
    private String status = "Pending";

    @Column(length = 500)
    private String note;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "created_at")
    private Date createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "updated_at")
    private Date updatedAt;
}
