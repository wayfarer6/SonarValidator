package org.sonar.sonarvalidator_backend.Model;
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

/**
 * 파싱된 장비 설정의 인메모리 표현입니다. (Batfish 의 Configuration 에 대응)
 *
 * <h2>영속 범위</h2>
 * <p>호스트명/장치 유형/설정 형식만 DB 컬럼으로 저장합니다. 인터페이스·VRF·
 * ACL·VLAN 은 파서가 만든 트리 구조라 컬럼으로 펼치면 조인이 폭발하므로
 * <b>여기서는 인메모리로만</b> 들고 있습니다({@link Transient}).
 * 정규화 저장이 필요해지면 각각을 별도 @Entity 로 분리하세요.
 *
 * <p>주의: {@code @Transient} 를 빠뜨리면 Hibernate 가 Map 의 JdbcType 을
 * 찾지 못해 "Could not determine recommended JdbcType" 으로 기동에 실패합니다.
 */
@Entity
@Table(name = "configuration")
@Getter
@Setter
@NoArgsConstructor
public class Configuration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int node_id;
    private String _hostname;
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
}
