package org.sonar.sonarvalidator_backend.Model.entity;

import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Policy.PolicyRule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_rule")
@Getter
@Setter
@NoArgsConstructor
public class ProjectRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "rule_id", nullable = false, length = 80)
    private String ruleId;

    @Column(name = "source_subnet_id", length = 80)
    private String source;

    @Column(name = "destination_subnet_id", length = 80)
    private String destination;

    @Column(name = "port_number")
    private Integer port;

    @Column(length = 20)
    private String protocol = "tcp";

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PolicyRule.Origin origin = PolicyRule.Origin.MANUAL;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 500)
    private String note;

    public PolicyRule toPolicyRule() {
        final PolicyRule rule = new PolicyRule();
        rule.setId(ruleId);
        rule.setSource(source);
        rule.setDestination(destination);
        rule.setPort(port == null ? PacketVariables.ANY_PORT : port);
        rule.setProtocol(protocol);
        rule.setOrigin(origin == null ? PolicyRule.Origin.MANUAL : origin);
        rule.setEnabled(enabled);
        rule.setNote(note);
        return rule;
    }

    public static ProjectRule from(PolicyRule rule) {
        final ProjectRule entity = new ProjectRule();
        entity.setRuleId(rule.getId());
        entity.setSource(rule.getSource());
        entity.setDestination(rule.getDestination());
        entity.setPort(rule.hasPort() ? rule.getPort() : null);
        entity.setProtocol(rule.getProtocol());
        entity.setOrigin(rule.getOrigin());
        entity.setEnabled(rule.isEnabled());
        entity.setNote(rule.getNote());
        return entity;
    }
}
