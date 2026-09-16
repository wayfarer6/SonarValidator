package org.sonar.sonarvalidator_backend.Model;

import java.util.Map;

public class Configuration {
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
