package org.sonar.sonarvalidator_backend.Model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NetworkNode {
    private String nodeId;
    private String dnsServerIp;
    private ConfigurationType configType;
    private String timeZone;
}
