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

    /**
     * 기본 키. JPA 는 모든 @Entity 에 식별자를 요구합니다.
     *
     * <p>주의: 이 필드에 {@code @ManyToOne} 을 함께 붙이면 Hibernate 가
     * {@code Long} 을 연관 엔티티 타입으로 해석해
     * "Primary key referenced an unknown entity: java.lang.Long" 으로
     * 컨텍스트 기동에 실패합니다. 식별자와 연관관계는 반드시 분리하세요.
     *
     * <p>전략을 {@code AUTO} 로 두면 H2 에서 시퀀스 기반(
     * {@code NETWORK_INTERFACE_SEQ}) 으로 생성되어, DB 내 다른
     * IDENTITY 테이블과 번호 체계가 갈라집니다. IDENTITY 를 명시해
     * 한 가지 방식으로 통일합니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long interfaceId;

    /** 이 인터페이스가 속한 노드. 소유 측(인터페이스)이 FK 를 가집니다. */
    @ManyToOne
    @JoinColumn(name = "node_id")
    private Configuration node;

    @Column(name = "member_name", nullable = false, length = 50)
    private String interfaceName;

}
