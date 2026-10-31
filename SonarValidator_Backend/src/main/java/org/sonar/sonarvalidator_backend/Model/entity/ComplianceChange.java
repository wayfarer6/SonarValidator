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
@Table(name = "compliance_change")
@Getter
@Setter
@NoArgsConstructor
public class ComplianceChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_id", nullable = false, unique = true, length = 80)
    private String changeId;

    @Column(length = 20)
    private String scope;

    @Column(name = "project_key", length = 120)
    private String projectKey;

    @Column(name = "agent_id", length = 120)
    private String agentId;

    @Column(name = "change_type", length = 60)
    private String type;

    @Column(length = 1000)
    private String summary;

    @Column(name = "changed_by", length = 200)
    private String changedBy;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "timestamp")
    private Date timestamp;

    @Column(length = 20)
    private String status;

    @Column(length = 20000)
    private String detail;
}
