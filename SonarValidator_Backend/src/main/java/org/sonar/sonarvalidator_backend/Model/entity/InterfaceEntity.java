package org.sonar.sonarvalidator_backend.Model.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "network_interface")
@Getter
@Setter
public class InterfaceEntity {
    @ManyToOne
    @JoinColumn (name = "node_id")

    @Id @GeneratedValue
    private Long interfaceId;
    
    @Column(name = "member_name", nullable = false, length = 50)
    private String interfaceName;

}
