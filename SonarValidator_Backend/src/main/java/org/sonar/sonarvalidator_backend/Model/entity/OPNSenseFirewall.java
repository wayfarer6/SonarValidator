package org.sonar.sonarvalidator_backend.Model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * <h2>기본 키를 node_id 로 두는 이유</h2>
 * <p>방화벽은 노드망의 한 구성요소이고, 노드의 정본은
 * {@code configuration} 테이블입니다({@code configuration.node_id}).
 * 별도 대리 키({@code id})를 두면 <b>같은 장치에 번호가 두 개</b> 생겨
 * 어느 쪽이 정본인지 헷갈리고, 조인도 한 다리 더 늘어납니다.
 * 노드 식별자를 그대로 키로 쓰면 장치 대 장치 연결 관계가 명확해집니다.
 *
 * <p>타입은 {@code Integer} 입니다. {@code configuration.node_id} 가
 * INTEGER 이므로 맞춥니다. (방화벽 테이블만 BIGINT 면 조인 시 형변환이
 * 끼어 인덱스를 못 타는 경우가 있습니다)
 *
 * <h2>자동 생성 전략을 쓰지 않는 이유</h2>
 * <p>PK 값은 노드가 정합니다. 여기서 {@code @GeneratedValue} 를 쓰면
 * 노드 번호와 무관한 값이 배정되어 참조가 끊어집니다.
 */
@Entity
@Table(name = "opnsense_firewall")
@Getter
@Setter
@NoArgsConstructor
public class OPNSenseFirewall {

    /** 노드 식별자. {@code configuration.node_id} 와 같은 값을 씁니다. */
    @Id
    @Column(name = "node_id")
    private Integer nodeId;

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
     * @param nodeId  노드 식별자 ({@code configuration.node_id})
     * @param agentId Agent 식별자
     * @param name    표시용 이름
     */
    public OPNSenseFirewall(Integer nodeId, String agentId, String name) {
        this.nodeId = nodeId;
        this.agentId = agentId;
        this.name = name;
    }
}
