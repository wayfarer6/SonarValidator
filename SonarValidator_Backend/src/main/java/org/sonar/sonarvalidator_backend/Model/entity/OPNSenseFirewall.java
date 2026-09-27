package org.sonar.sonarvalidator_backend.Model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.sonar.sonarvalidator_backend.Model.Configuration;

/**
 * OPNSense 방화벽 노드의 영속 엔티티입니다.
 *
 * <p>Agent(C++ Prober) 또는 REST API 로 관리되는 방화벽 장치 정보를 저장합니다.
 * 정책 요청-응답은 여전히 WebSocket 봉투로 처리하고, 이 엔티티는
 * <b>관리 대상 장치의 현재 상태</b> 를 DB 에 남기는 역할입니다.
 *
 * <h2>⚠️ DB Design v1.5 — configuration 과 node_id 로 묶는다</h2>
 * <p>방화벽은 노드망의 한 구성요소이고, 노드의 정본은
 * {@code configuration} 테이블입니다. 그래서 이 엔티티는 <b>별도 대리 키를
 * 두지 않고</b>, 노드 연관을 기본 키로 씁니다(파생 식별자).
 *
 * <pre>
 *   configuration.node_id (PK)
 *          ▲
 *          │ FK (같은 컬럼이 이 테이블의 PK)
 *   opnsense_firewall.node_id
 * </pre>
 *
 * <p>기본 키를 노드에서 파생시키는 이유:
 * <ul>
 *   <li>같은 장치에 번호가 두 개 생기지 않습니다</li>
 *   <li>{@code node_id} 가 <b>실제 외래키</b>가 되어 고아 행이 생기지 않습니다</li>
 *   <li>조인이 한 다리 줄어듭니다</li>
 * </ul>
 *
 * <h2>⚠️ DB Design v1.5 — agent_id 를 제거한 이유</h2>
 * <p>OPNsense 는 <b>REST API 로 직접 연결</b>되므로 Agent 가 없을 수 있습니다.
 * 기존에는 {@code agent_id} 가 NOT NULL 이라 Agent 없는 방화벽을 저장할 수
 * 없었고, 화면이 관리 서버 IP 대신 {@code agent_id}(예: {@code opnsense-1})를
 * 잘못 사용하는 결함(SONAR-36)의 원인이기도 했습니다.
 *
 * <p>그래서 {@code agent_id} 컬럼을 <b>제거</b>했습니다. 장치 식별이 필요하면
 * {@link Configuration#getAgentId()} 를 참조하세요. 관계의 정본은
 * {@code node_id} 하나입니다.
 */
@Entity
@Table(name = "opnsense_firewall")
@Getter
@Setter
@NoArgsConstructor
public class OPNSenseFirewall {

    /**
     * 노드 식별자입니다. ({@code configuration.node_id} 와 같은 값)
     *
     * <p>⚠️ <b>{@code @GeneratedValue} 를 쓰지 않습니다.</b> 값은 노드가
     * 정하므로, 여기서 자동 생성을 켜면 노드 번호와 무관한 값이 배정되어
     * 참조가 끊깁니다. {@link #node} 의 {@link MapsId} 가 이 값을 채웁니다.
     */
    @Id
    @Column(name = "node_id")
    private Integer nodeId;

    /**
     * 노드 정본. ({@code configuration.node_id})
     *
     * <p>{@link MapsId} 는 이 연관의 식별자를 그대로 이 엔티티의 PK 로
     * 복사합니다(파생 식별자). 그래서 노드가 없는 방화벽 행은 만들어질 수
     * 없고, {@code node_id} 가 <b>실제 외래키</b>가 되어 고아 행을 막습니다.
     * (DB Design v1.5 의 요구 — "방화벽은 configuration 과 node_id 로 묶인다")
     */
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id")
    private Configuration node;

    /** 표시용 장치 이름. */
    @Column(name = "name", length = 128)
    private String name;

    /** 관리 IP 주소. (REST API 접속 기준) */
    @Column(name = "management_ip", length = 64)
    private String managementIp;

    /** 펌웨어/OS 버전. */
    @Column(name = "version", length = 64)
    private String version;

    /**
     * 엔티티를 만듭니다.
     *
     * @param node 노드 정본 ({@code configuration}) — 필수
     * @param name 표시용 이름
     */
    public OPNSenseFirewall(Configuration node, String name) {
        this.node = node;
        this.name = name;
    }
}
