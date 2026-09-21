package org.sonar.sonarvalidator_backend.Model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * OPNSense 방화벽 노드의 영속 엔티티입니다.
 *
 * <p>Agent(C++ Prober)가 보고한 방화벽 장치 정보를 저장합니다.
 * 정책 요청-응답은 여전히 WebSocket 봉투로 처리하고, 이 엔티티는
 * <b>관리 대상 장치의 현재 상태</b> 를 DB 에 남기는 역할입니다.
 *
 * <p>Lombok 의 {@code @Getter}/{@code @Setter} 로 접근자를 만들고,
 * JPA 스펙이 요구하는 기본 생성자는 {@code @NoArgsConstructor} 로 제공합니다.
 */
@Entity
@Table(name = "opnsense_firewall")
@Getter
@Setter
@NoArgsConstructor
public class OPNSenseFirewall {

    /** 기본 키. DB 가 생성 전략을 담당합니다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Agent 식별자. 봉투의 {@code agent_id} 와 같은 값입니다. */
    @Column(name = "agent_id", nullable = false, length = 128)
    private String agentId;

    /** 표시용 장치 이름. */
    @Column(name = "name", length = 128)
    private String name;

    /** 관리 IP 주소. */
    @Column(name = "management_ip", length = 64)
    private String managementIp;

    /** 펌웨어/OS 버전. */
    @Column(name = "version", length = 64)
    private String version;

    /**
     * 엔티티를 만듭니다. (편의용)
     *
     * @param agentId Agent 식별자
     * @param name 표시용 이름
     */
    public OPNSenseFirewall(String agentId, String name) {
        this.agentId = agentId;
        this.name = name;
    }
}
