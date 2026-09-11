package org.sonar.sonarvalidator_backend.Controller;

import org.springframework.boot.jackson.autoconfigure.JacksonProperties;

import java.util.ArrayList;

public interface AgentController {

    public ArrayList<JacksonProperties.Json> FetchPolicy();
    public ArrayList<JacksonProperties.Json> FetchSystemInfo(String agent_id);
}
