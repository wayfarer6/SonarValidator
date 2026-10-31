package org.sonar.sonarvalidator_backend.Model.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.sonar.sonarvalidator_backend.Model.Configuration;

@Entity
@Table(name = "network_interface")
@Getter
@Setter
public class InterfaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long interfaceId;

    @ManyToOne
    @JoinColumn(name = "node_id")
    private Configuration node;

    @Column(name = "member_name", nullable = false, length = 50)
    private String interfaceName;

}
