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

@Entity
@Table(name = "configuration")
@Getter
@Setter
@NoArgsConstructor
public class Configuration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "node_id")
    private Integer node_id;

    private String _hostname;

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

    public DeviceType getDeviceType() {
        return this._deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this._deviceType = deviceType;
    }

    public ConfigurationFormat getConfigurationFormat() {
        return this._configurationFormat;
    }

    public void setConfigurationFormat(ConfigurationFormat configurationFormat) {
        this._configurationFormat = configurationFormat;
    }

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
