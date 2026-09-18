package org.sonar.sonarvalidator_backend.Model.entity;

import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 프로젝트에 속한 서브넷 한 건입니다.
 *
 * <p>{@link PolicySubnet} 은 검증 엔진이 쓰는 <b>순수 도메인 객체</b>이고,
 * 이 클래스는 그것을 저장하기 위한 <b>영속 표현</b>입니다. 둘을 나눠 두면
 * 검증 로직이 JPA 에 묶이지 않고, 스키마 변경이 엔진에 영향을 주지 않습니다.
 */
@Entity
@Table(name = "project_subnet")
@Getter
@Setter
@NoArgsConstructor
public class ProjectSubnet {

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 프로젝트 안에서의 서브넷 식별자 (예: {@code Subnet-0004}). */
    @Column(name = "subnet_id", nullable = false, length = 80)
    private String subnetId;

    /** CIDR 대역. */
    @Column(nullable = false, length = 80)
    private String cidr;

    /**
     * 보안 등급.
     *
     * <p>{@link EnumType#STRING} 으로 저장합니다. ORDINAL 로 저장하면
     * 등급을 재정렬하는 순간 기존 데이터의 의미가 바뀝니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "zone_class", length = 30)
    private ZoneClass zoneClass;

    /** 표시용 이름. */
    @Column(length = 200)
    private String name;

    /** 자동 수집 출처 장치 식별자. */
    @Column(name = "agent_id", length = 120)
    private String agentId;

    /** 수동 편집 여부. */
    @Column(name = "manually_edited")
    private boolean manuallyEdited;

    /**
     * 도메인 객체로 변환합니다.
     *
     * @return 검증 엔진 입력용 서브넷
     */
    public PolicySubnet toPolicySubnet() {
        final PolicySubnet subnet = new PolicySubnet();
        subnet.setId(subnetId);
        subnet.setCidr(cidr);
        subnet.setZoneClass(zoneClass);
        subnet.setName(name);
        subnet.setAgentId(agentId);
        subnet.setManuallyEdited(manuallyEdited);
        return subnet;
    }

    /**
     * 도메인 객체에서 영속 표현을 만듭니다.
     *
     * @param subnet 원본
     * @return 저장용 엔티티
     */
    public static ProjectSubnet from(PolicySubnet subnet) {
        final ProjectSubnet entity = new ProjectSubnet();
        entity.setSubnetId(subnet.getId());
        entity.setCidr(subnet.getCidr());
        entity.setZoneClass(subnet.getZoneClass());
        entity.setName(subnet.getName());
        entity.setAgentId(subnet.getAgentId());
        entity.setManuallyEdited(subnet.isManuallyEdited());
        return entity;
    }
}
