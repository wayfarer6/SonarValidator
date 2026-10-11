package org.sonar.sonarvalidator_backend.Model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 프로젝트가 <b>수집된 VLAN 에 붙인 이름</b>입니다.
 *
 * <h2>⚠️ 왜 {@link ProjectSubnet} 으로는 안 되는가</h2>
 * <p>VLAN 이름을 서브넷 행에 저장하려면 {@code ProjectSubnet.cidr} 이
 * {@code nullable = false} 라 <b>가짜 대역</b>을 넣어야 합니다. 그러면 정책
 * 엔진이 그 가짜 대역을 실제 서브넷으로 보고 검증·집행 대상에 넣습니다 —
 * 차단 규칙이 엉뚱한 대역에 걸립니다.
 *
 * <p>그런데 Open vSwitch 의 VLAN 은 <b>주소가 없는 L2 전용</b>이 대부분입니다
 * (실측: Switch-1 VLAN111/112 는 access 포트와 uplink trunk 만 있고 SVI 가
 * 없습니다). 그래서 대역 없는 VLAN 의 이름을 담을 자리가 필요합니다.
 *
 * <h2>키</h2>
 * <p>{@code (project, agent_id, vlan_id)} 가 유일합니다. 같은 VLAN 번호라도
 * 장비가 다르면 다른 이름을 쓸 수 있어야 합니다 — 한 스위치의 VLAN 111 과
 * 다른 스위치의 VLAN 111 은 서로 다른 브로드캐스트 도메인입니다.
 */
@Entity
@Table(name = "project_vlan",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_vlan", columnNames = {"project_id", "agent_id", "vlan_id"}))
public class ProjectVlan {

    /** 숫자 PK. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 프로젝트입니다. (외래키 소유) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /** VLAN 을 보고한 Agent 식별자입니다. */
    @Column(name = "agent_id", nullable = false, length = 120)
    private String agentId;

    /** VLAN 번호입니다. */
    @Column(name = "vlan_id", nullable = false)
    private Integer vlanId;

    /** 운영자가 붙인 이름입니다. (비어 있으면 미지정) */
    @Column(length = 200)
    private String name;

    /** JPA 기본 생성자. */
    public ProjectVlan() {
    }

    /**
     * @param project 소속 프로젝트
     * @param agentId VLAN 을 보고한 Agent
     * @param vlanId  VLAN 번호
     * @param name    이름
     */
    public ProjectVlan(Project project, String agentId, Integer vlanId, String name) {
        this.project = project;
        this.agentId = agentId;
        this.vlanId = vlanId;
        this.name = name;
    }

    /** @return 숫자 PK */
    public Long getId() {
        return id;
    }

    /** @param id 숫자 PK */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return 소속 프로젝트 */
    public Project getProject() {
        return project;
    }

    /** @param project 소속 프로젝트 */
    public void setProject(Project project) {
        this.project = project;
    }

    /** @return Agent 식별자 */
    public String getAgentId() {
        return agentId;
    }

    /** @param agentId Agent 식별자 */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /** @return VLAN 번호 */
    public Integer getVlanId() {
        return vlanId;
    }

    /** @param vlanId VLAN 번호 */
    public void setVlanId(Integer vlanId) {
        this.vlanId = vlanId;
    }

    /** @return 이름 (미지정이면 null) */
    public String getName() {
        return name;
    }

    /** @param name 이름 */
    public void setName(String name) {
        this.name = name;
    }
}
