package org.sonar.sonarvalidator_backend.Model;

import org.springframework.stereotype.Component;

@Component
class Subnet {
    public Subnet() {

    }
    public Subnet(String switch_id, String description, String ip_range,int port) {}

    public String switch_id;
    public String description;
    public String ip_range;
    public int subnet_mask;


}