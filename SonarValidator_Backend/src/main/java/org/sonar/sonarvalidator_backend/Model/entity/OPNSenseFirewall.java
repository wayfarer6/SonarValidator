package org.sonar.sonarvalidator_backend.Model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.sonar.sonarvalidator_backend.Model.Configuration;

@Entity
@Table(name = "opnsense_firewall")
@Getter
@Setter
@NoArgsConstructor
public class OPNSenseFirewall {

    @Id
    @Column(name = "node_id")
    private Integer nodeId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id")
    private Configuration node;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "management_ip", length = 64)
    private String managementIp;

    @Column(name = "version", length = 64)
    private String version;

    public OPNSenseFirewall(Configuration node, String name) {
        this.node = node;
        this.name = name;
    }
}
