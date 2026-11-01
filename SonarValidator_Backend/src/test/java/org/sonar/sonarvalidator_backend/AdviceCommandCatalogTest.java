package org.sonar.sonarvalidator_backend;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Policy.advice.*;
import tools.jackson.databind.ObjectMapper;

class AdviceCommandCatalogTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void previewsMatchTheSharedNativeContract() throws Exception {
        var rows=mapper.readTree(Files.readString(Path.of("..","tests","contracts","advice-actions.json")));
        for(var row:rows) {
            Map<String,Object> args=new TreeMap<>();row.path("arguments").properties().forEach(e->args.put(e.getKey(),e.getValue().isString()?e.getValue().asString():e.getValue().asInt()));
            var plan=AdviceActionCatalog.compile(row.path("operation").asString(),row.path("product").asString(),args);
            assertEquals(row.path("commands"),mapper.valueToTree(plan.commands()));
            assertEquals(row.path("verification"),mapper.valueToTree(plan.verification()));
        }
    }
    @Test void injectionAndUnknownParametersAreNotExecutable() {
        assertThrows(IllegalArgumentException.class,()->AdviceActionCatalog.compile("set_interface_description","Cisco IOS XE",Map.of("interface","GigabitEthernet2;reload","description","x")));
        assertThrows(IllegalArgumentException.class,()->AdviceActionCatalog.compile("set_interface_description","Arista",Map.of("interface","Ethernet1","description","x\nshutdown")));
        assertThrows(IllegalArgumentException.class,()->AdviceActionCatalog.compile("inspect_routes","Cisco",Map.of("command","reload")));
        assertThrows(IllegalArgumentException.class,()->AdviceActionCatalog.compile("manual","Cisco",Map.of()));
    }
    @Test void linuxFirewallCannotRunInRouterOrSwitchContainers() {
        for(String product:List.of("Cisco IOS XE","Arista")) assertThrows(IllegalArgumentException.class,()->AdviceActionCatalog.compile("block_forward_ipv4",product,Map.of("source","10.0.0.0/24","destination","10.1.0.0/24","protocol","tcp","port",443)));
    }
    @Test void modelResponseCommandsRemainStructuredData() {
        var parser=new PolicyAdviceParser();var answer=parser.parse("{\"summary\":\"check\",\"command_proposals\":[{\"agent_id\":\"r1\",\"operation\":\"inspect_routes\",\"arguments\":{},\"commands\":[\"show ip route\"]}]}");
        assertEquals(1,answer.commands().size());assertEquals("inspect_routes",answer.commands().getFirst().operation());
        assertEquals(1,parser.readCommands(mapper.valueToTree(answer.commands().stream().map(AdviceCommandProposal::toView).toList())).size());
        assertTrue(parser.parse("not json; reload").commands().isEmpty());
    }
    @org.junit.jupiter.api.Test void malformedArgumentsCannotBecomeAnExecutableEmptyMap() {
        var parser=new PolicyAdviceParser();
        var answer=parser.parse("{\"command_proposals\":[{\"agent_id\":\"r1\",\"operation\":\"inspect_routes\",\"arguments\":{\"extra\":true}}]}");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->AdviceActionCatalog.compile(answer.commands().get(0).operation(),"Cisco",answer.commands().get(0).arguments()));
    }
}
