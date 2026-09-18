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

import org.sonar.sonarvalidator_backend.Model.Configuration;

@Entity
@Table(name = "rest_api_node_config")
@Getter
@Setter
public class RestAPIConnectionConfig {

    /**
     * 기본 키. JPA 는 모든 @Entity 에 식별자를 요구합니다.
     * (없으면 기동 시 AnnotationException 으로 컨텍스트가 뜨지 않습니다.)
     */
    @Id
    @GeneratedValue
    private Long id;

    /**
     * 이 설정이 가리키는 노드.
     * 노드당 설정 1개이므로 OneToOne 이며, 소유 측(설정)이 FK 를 가집니다.
     */
    @OneToOne
    @JoinColumn(name = "node_id")
    private Configuration node;

    private String apikey;
    private String baseurl; // http://localhost:8080 (포트 번호 까지!!!)
}
