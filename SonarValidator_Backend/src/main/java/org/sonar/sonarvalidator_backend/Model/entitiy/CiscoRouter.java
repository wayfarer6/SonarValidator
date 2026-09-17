package org.sonar.sonarvalidator_backend.Model.entitiy;
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
@Table(name = "cisco_router")
@Getter
@Setter
@NoArgsConstructor
public class CiscoRouter {
    @Id // foreign key (agent id)
    private Long id;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "version", )
}
