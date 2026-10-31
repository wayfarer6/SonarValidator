package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Config.Vendors.CiscoRouterConfigParser;
import tools.jackson.databind.json.JsonMapper;

class CiscoConnectedPrefixTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void usesOnlyMatchingConnectedRouteForMissingInterfaceMask() {
        var payload = mapper.readTree("""
            {"nic_status":{"interfaces":[{"name":"GigabitEthernet2","state":"up",
              "addresses":[{"address":"10.10.20.1","family":"inet"}]}]},
             "route_status":{"routes":[
              {"prefix":"10.10.20.1/32","protocol":"local","interface_name":"GigabitEthernet2"},
              {"prefix":"10.10.20.0/24","protocol":"connected","interface_name":"GigabitEthernet2"}]}}
            """);
        var config = new CiscoRouterConfigParser().parse("R1", "Cisco 8000v", payload);
        assertEquals("10.10.20.1/24", config.getInterfaces().get("GigabitEthernet2").getAddresses().getFirst());
    }

    @Test
    void neverGuessesMaskFromDefaultLocalOrOtherInterfaceRoutes() {
        var payload = mapper.readTree("""
            {"nic_status":{"interfaces":[{"name":"GigabitEthernet2",
              "addresses":[{"address":"10.10.20.1","family":"inet"}]}]},
             "route_status":{"routes":[
              {"prefix":"0.0.0.0/0","protocol":"static","interface_name":"GigabitEthernet2"},
              {"prefix":"10.10.20.1/32","protocol":"local","interface_name":"GigabitEthernet2"},
              {"prefix":"10.10.20.0/24","protocol":"connected","interface_name":"GigabitEthernet3"}]}}
            """);
        var config = new CiscoRouterConfigParser().parse("R1", "Cisco 8000v", payload);
        assertEquals("10.10.20.1", config.getInterfaces().get("GigabitEthernet2").getAddresses().getFirst());
    }
}
