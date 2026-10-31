package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Policy.PolicyRule;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, unique = true, length = 120)
    private String projectKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(length = 1000)
    private String description;

    @Column(length = 50)
    private String status = "Planning";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    @Column(name = "management_prefix", length = 255)
    private String managementPrefix;

    @Column(name = "management_server_ip", length = 255)
    private String managementServerIp;

    @Column(name = "management_server_port")
    private Integer managementServerPort;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "created_at")
    private Date createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "updated_at")
    private Date updatedAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderColumn(name = "ordinal")
    private List<ProjectSubnet> subnets = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderColumn(name = "ordinal")
    private List<ProjectRule> rules = new ArrayList<>();

    //  변환 헬퍼

    public List<PolicySubnet> toPolicySubnets() {
        final List<PolicySubnet> result = new ArrayList<>(subnets.size());
        for (final ProjectSubnet subnet : subnets) {
            result.add(subnet.toPolicySubnet());
        }
        return result;
    }

    public List<PolicyRule> toPolicyRules() {
        final List<PolicyRule> result = new ArrayList<>(rules.size());
        for (final ProjectRule rule : rules) {
            result.add(rule.toPolicyRule());
        }
        return result;
    }

    public void replacePolicy(List<PolicySubnet> newSubnets, List<PolicyRule> newRules) {
        if (newSubnets != null) {
            subnets.clear();
            final Set<String> seenSubnetIds = new LinkedHashSet<>();
            for (final PolicySubnet subnet : newSubnets) {
                if (subnet.getId() != null && !seenSubnetIds.add(subnet.getId())) {
                    continue;
                }
                final ProjectSubnet entity = ProjectSubnet.from(subnet);
                entity.setProject(this);
                subnets.add(entity);
            }
        }
        if (newRules != null) {
            rules.clear();
            for (final PolicyRule rule : newRules) {
                final ProjectRule entity = ProjectRule.from(rule);
                entity.setProject(this);
                rules.add(entity);
            }
        }
    }
}
