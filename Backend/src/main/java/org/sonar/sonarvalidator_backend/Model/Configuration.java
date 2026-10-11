package org.sonar.sonarvalidator_backend.Model;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;

/**
 * 파싱된 장비 설정의 영속 표현입니다. (Batfish 의 Configuration 에 대응)
 *
 * <h2>영속 범위</h2>
 * <p>호스트명/장치 유형/설정 형식만 DB 컬럼으로 저장합니다. 인터페이스·VRF·
 * ACL·VLAN 은 파서가 만든 트리 구조라 컬럼으로 펼치면 조인이 폭발하므로
 * <b>여기서는 인메모리로만</b> 들고 있습니다({@link Transient}).
 * 정규화 저장이 필요해지면 각각을 별도 @Entity 로 분리하세요.
 *
 * <p>주의: {@code @Transient} 를 빠뜨리면 Hibernate 가 Map 의 JdbcType 을
 * 찾지 못해 "Could not determine recommended JdbcType" 으로 기동에 실패합니다.
 *
 * <h2>⚠️ DB Design v1.5 — 노드의 정본(canonical) 테이블</h2>
 * <p>이 테이블은 <b>노드의 정본</b>입니다. {@code device_log.node_id} 와
 * {@code opnsense_firewall.node_id} 가 이 테이블의 {@code node_id} 를
 * 외래키로 참조합니다.
 *
 * <p>그래서 <b>자연키 {@code agent_id}</b> 를 추가했습니다. 기존에는
 * {@code _hostname} 만 있어서 "같은 장비가 여러 행으로 쌓이는" 문제가
 * 있었습니다(설계 문서 v1 약점 #4). {@code agent_id} 에 unique 제약을 걸어
 * upsert 기준을 명확히 합니다. (에이전트가 없는 장비도 있어 값은 null 허용)
 */
@Entity
@Table(name = "configuration")
@Getter
@Setter
@NoArgsConstructor
public class Configuration {
    /**
     * 노드 번호 (기본 키).
     *
     * <p>⚠️ primitive {@code int} 가 아니라 {@link Integer} 입니다. primitive 면
     * 초기값 {@code 0} 이 "저장 안 됨" 의 표시로 쓰이는데, Hibernate 는
     * {@code @GeneratedValue} 와 별개로 <b>0 을 실제 키 0</b> 으로도 해석할 수
     * 있어 {@code save()} 가 {@code merge} 경로를 타면서
     * {@code null identifier} 로 실패하는 경우가 있습니다.
     * {@code Integer}(null = 미저장)가 의도를 분명히 합니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "node_id")
    private Integer node_id;

    private String _hostname;

    /**
     * 장비(Agent) 식별자 — 노드의 자연키입니다.
     *
     * <p>{@code device_log} / {@code opnsense_firewall} 이 이 값을 기준으로
     * {@link #node_id} 를 찾습니다. Agent 없이 REST API 로만 관리되는
     * 장비(예: OPNsense)는 이 값이 비어 있을 수 있습니다.
     */
    @Column(name = "agent_id", unique = true, length = 120)
    private String agentId;

    private DeviceType _deviceType;
    private ConfigurationFormat _configurationFormat;

    @Transient
    private Map<String,Interface> _interfaces;
    @Transient
    private Map<String,Vrf> _vrfs;
    @Transient
    private Map<String,IpAccessList> _packetFilters;
    @Transient
    private Map<Integer,VLan> _vlans;

    public String getHostname()
    {
        return this._hostname;
    }

    /**
     * 노드 번호를 돌려줍니다. (camelCase 접근자)
     *
     * <p>필드명이 {@code node_id} 라 Lombok 이 {@code getNode_id()} 를 만들지만,
     * 외부(서비스·테스트)는 camelCase 를 기대합니다. 두 표기를 모두 제공해
     * 호출부가 필드명 규칙에 묶이지 않게 합니다.
     *
     * @return 노드 번호 (미저장이면 null)
     */
    public Integer getNodeId() {
        return this.node_id;
    }

    public Map<String,Interface> getInterfaces() {
        return this._interfaces;
    }
    public Map<String,Vrf> getVrfs() {
        return this._vrfs;
    }
    public Map<String,IpAccessList> getPacketFilters()
    {
        return this._packetFilters;
    }

    /**
     * 장치 유형을 돌려줍니다. (camelCase 접근자)
     *
     * <p>필드명이 {@code _deviceType} 이라 Lombok 이 {@code get_deviceType()} 를
     * 만듭니다. 외부는 camelCase 를 기대하므로 두 표기를 모두 제공합니다.
     *
     * @return 장치 유형 (없으면 null)
     */
    public DeviceType getDeviceType() {
        return this._deviceType;
    }

    /**
     * 장치 유형을 설정합니다.
     *
     * @param deviceType 장치 유형
     */
    public void setDeviceType(DeviceType deviceType) {
        this._deviceType = deviceType;
    }

    /**
     * 설정 형식을 돌려줍니다. (camelCase 접근자)
     *
     * @return 설정 형식 (없으면 null)
     */
    public ConfigurationFormat getConfigurationFormat() {
        return this._configurationFormat;
    }

    /**
     * 설정 형식을 설정합니다.
     *
     * @param configurationFormat 설정 형식
     */
    public void setConfigurationFormat(ConfigurationFormat configurationFormat) {
        this._configurationFormat = configurationFormat;
    }

    /**
     * 중립 설정에서 노드 행을 만듭니다.
     *
     * <h2>⚠️ 트리 구조는 저장하지 않는다</h2>
     * <p>{@link NeutralDeviceConfig} 의 인터페이스/라우팅/VLAN 은 분석용
     * 인메모리 표현입니다. 여기서는 <b>노드를 식별하는 최소 정보</b>만
     * 컬럼으로 옮깁니다. 나머지는 {@link Transient} 로 남습니다.
     *
     * @param agentId 장비(Agent) 식별자 — 자연키 (없으면 null)
     * @param config  파서가 만든 중립 설정 (null 허용)
     * @return 저장 준비가 된 엔티티
     */
    public static Configuration from(String agentId, NeutralDeviceConfig config) {
        final Configuration entity = new Configuration();
        entity.setAgentId(agentId);
        entity.set_hostname(agentId);
        if (config != null) {
            entity.setDeviceType(DeviceType.fromString(config.getDeviceType()));
            entity.setConfigurationFormat(ConfigurationFormat.fromString(config.getFormat()));
        }
        return entity;
    }
}
