package org.sonar.sonarvalidator_backend.Model;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

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
    private Map<String,Interface> _interfaces;
    private Map<String,Vrf> _vrfs;
    private Map<String,IpAccessList> _packetFilters;
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
