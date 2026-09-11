package org.sonar.sonarvalidator_backend.Model;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
class Switch{
    public Switch() {
        interfaces = new HashMap<>();
    }

    private String switch_id;
    private HashMap<Integer,Nic> interfaces;

    public Switch(String switch_id) {
        this.switch_id = switch_id;
    }
}