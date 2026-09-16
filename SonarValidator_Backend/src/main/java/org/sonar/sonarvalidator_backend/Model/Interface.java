package org.sonar.sonarvalidator_backend.Model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Interface {
    private String node_id;
    private String ip_range;
    private String subnet_mask;
    private boolean isActive;
    private int mtuSpeed;

}
