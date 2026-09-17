package org.sonar.sonarvalidator_backend.Model.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "rest_api_node_config")
@Getter
@Setter
public class RestAPIConnectionConfig {

    @OneToOne
    @JoinColumn (name = "node_id")
    
    private String apikey;
    private String baseurl; // http://localhost:8080 (포트 번호 까지!!!)
}
