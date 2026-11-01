package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.sonarvalidator_backend.Service.AgentBundleService;
import org.sonar.sonarvalidator_backend.Controller.AgentBundleController;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentBundleServiceTest {
    record Fixture(AgentBundleService service, ExpectedAgentRepository agents, Project project) {}
    private Fixture fixture(Path stage) throws Exception {
        Files.write(stage.resolve("sonar_validator_prober"), new byte[]{0x7f,'E','L','F'});
        Files.writeString(stage.resolve("default_template.sqlite"), "template");
        var agents = mock(ExpectedAgentRepository.class);
        var projects = mock(ProjectRepository.class);
        var agent = new ExpectedAgent(); agent.setAgentId("Lab2-Ubuntu"); agent.setProjectKey("lab2"); agent.setNodeType("VM");
        var project = new Project(); project.setProjectKey("lab2"); project.setManagementPrefix("192.168.150.0/24");
        project.setManagementServerIp("192.168.150.10"); project.setManagementServerPort(3000);
        when(agents.findByAgentId("Lab2-Ubuntu")).thenReturn(Optional.of(agent));
        when(projects.findByProjectKey("lab2")).thenReturn(Optional.of(project));
        return new Fixture(new AgentBundleService(stage.toString(),"test-terminal-shared-secret-that-is-long-enough",agents,projects),agents,project);
    }
    private Map<String,String> files(byte[] bytes) throws Exception {
        var values = new LinkedHashMap<String,String>();
        try(var tar=new TarArchiveInputStream(new GzipCompressorInputStream(new ByteArrayInputStream(bytes)))) {
            for(var entry=tar.getNextEntry();entry!=null;entry=tar.getNextEntry()) values.put(entry.getName(),new String(tar.readAllBytes(),StandardCharsets.UTF_8));
        }
        return values;
    }
    private String config(Fixture f) throws Exception { return files(f.service.build("Lab2-Ubuntu",null,"lab2",null)).get("Installer/default.conf"); }
    @Test void archiveUsesGzipAndInstallerRoot(@TempDir Path stage) throws Exception {
        var data=fixture(stage).service.build("Lab2-Ubuntu",null,"lab2",null);
        assertEquals(0x1f,data[0]&255);assertEquals(0x8b,data[1]&255);
        assertEquals(5,files(data).size());
        assertTrue(files(data).get("Installer/licenses/antlr4-runtime-LICENSE.txt").contains("The ANTLR Project")); assertTrue(files(data).keySet().stream().allMatch(n->n.startsWith("Installer/")));
    }
    @Test void projectSuppliesAllNetworkDefaults(@TempDir Path stage) throws Exception {
        var f=fixture(stage);var text=config(f);
        assertTrue(text.contains("SERVER_IP=192.168.150.10;"));assertTrue(text.contains("SERVER_PORT=3000;"));
        assertTrue(text.contains("MANAGEMENT_PREFIX=192.168.150.0/24;"));assertTrue(text.contains("AGENT_NAME=Lab2-Ubuntu;"));assertTrue(text.contains("NODE_TYPE=VM;"));
    }
    @Test void projectEditsAffectNextDownloadAndInfo(@TempDir Path stage) throws Exception {
        var f=fixture(stage);assertTrue(config(f).contains("SERVER_PORT=3000;"));
        f.project.setManagementServerIp("192.168.150.11");f.project.setManagementServerPort(8443);
        assertTrue(config(f).contains("SERVER_PORT=8443;"));assertTrue(config(f).contains("SERVER_IP=192.168.150.11;"));
        var info=f.service.describe("Lab2-Ubuntu",null,"lab2");assertEquals("192.168.150.11",info.get("server_ip"));assertEquals(8443,info.get("server_port"));assertEquals("project",info.get("server_ip_source"));
    }
    @Test void missingRegistrationCannotDownload(@TempDir Path stage) throws Exception {
        var f=fixture(stage);assertThrows(ResponseStatusException.class,()->f.service.build("other",null,"lab2",null));
    }
    @Test void differentProjectCannotDownload(@TempDir Path stage) throws Exception {
        var f=fixture(stage);assertThrows(ResponseStatusException.class,()->f.service.build("Lab2-Ubuntu",null,"lab1",null));
    }
    @Test void missingProjectEndpointCannotFallBackToLocalhost(@TempDir Path stage) throws Exception {
        var f=fixture(stage);f.project.setManagementServerIp(null);assertThrows(ResponseStatusException.class,()->config(f));
        f.project.setManagementServerIp("192.168.150.10");f.project.setManagementServerPort(null);assertThrows(ResponseStatusException.class,()->config(f));
    }
    @Test void invalidPortCannotFallBackTo3000(@TempDir Path stage) throws Exception {
        var f=fixture(stage);for(int port:new int[]{0,-1,65536}){f.project.setManagementServerPort(port);assertThrows(ResponseStatusException.class,()->config(f));}
    }
    @Test void endpointCannotInjectConfiguration(@TempDir Path stage) throws Exception {
        var f=fixture(stage);f.project.setManagementServerIp("192.168.150.10;\nAGENT_NAME=other");assertThrows(ResponseStatusException.class,()->config(f));
    }
    @Test void managementPrefixCannotInjectConfiguration(@TempDir Path stage) throws Exception {
        var f=fixture(stage);f.project.setManagementPrefix("192.168.150.0/24;\nSERVER_IP=other");assertThrows(IllegalArgumentException.class,()->config(f));
    }
    @Test void registeredTypeIsDefaultAndMismatchIsRejected(@TempDir Path stage) throws Exception {
        var f=fixture(stage);assertTrue(config(f).contains("NODE_TYPE=VM;"));
        assertThrows(ResponseStatusException.class,()->f.service.build("Lab2-Ubuntu","Switch","lab2",null));
    }
    @Test void dataPathCannotInjectConfiguration(@TempDir Path stage) throws Exception {
        var f=fixture(stage);assertThrows(ResponseStatusException.class,()->f.service.build("Lab2-Ubuntu",null,"lab2","/data;\nSERVER_IP=other"));
    }
    @Test void defaultConfigIsPrivateAndBinaryExecutable(@TempDir Path stage) throws Exception {
        var f=fixture(stage);
        try(var tar=new TarArchiveInputStream(new GzipCompressorInputStream(new ByteArrayInputStream(f.service.build("Lab2-Ubuntu",null,"lab2",null))))) {
            for(var e=tar.getNextEntry();e!=null;e=tar.getNextEntry()){
                if(e.getName().endsWith("default.conf"))assertEquals(0600,e.getMode());
                if(e.getName().endsWith("sonar_validator_prober"))assertEquals(0755,e.getMode());
            }
        }
    }
    @Test void missingTemplatePreventsIncompleteDownload(@TempDir Path stage) throws Exception {
        var f=fixture(stage);Files.delete(stage.resolve("default_template.sqlite"));
        assertEquals(false,f.service.describe("Lab2-Ubuntu",null,"lab2").get("download_ready"));assertThrows(IllegalStateException.class,()->config(f));
    }
    @Test void missingBinaryPreventsIncompleteDownload(@TempDir Path stage) throws Exception {
        var f=fixture(stage);Files.delete(stage.resolve("sonar_validator_prober"));assertThrows(IllegalStateException.class,()->config(f));
    }
    @Test void readmeUsesActualBundleAndKeepsExistingDatabase(@TempDir Path stage) throws Exception {
        var f=fixture(stage);var readme=files(f.service.build("Lab2-Ubuntu",null,"lab2",null)).get("Installer/README.txt");
        assertTrue(readme.contains("$PWD/default.conf"));assertTrue(readme.contains("sudo sh Installer.sh"));assertFalse(readme.contains("rm -f"));assertFalse(readme.contains("restart.sh"));
    }
    @Test void publicApiRejectsEndpointOverridesOnInfoAndDownload(@TempDir Path stage) throws Exception {
        var f=fixture(stage);var mvc=MockMvcBuilders.standaloneSetup(new AgentBundleController(f.service)).build();
        for(String path:new String[]{"/api/v1/agents/download/Lab2-Ubuntu","/api/v1/agents/download/info?agent_id=Lab2-Ubuntu"}){
            mvc.perform(get(path).param("project_id","lab2").param("server_ip","203.0.113.1")).andExpect(status().isBadRequest());
            mvc.perform(get(path).param("project_id","lab2").param("server_port","9000")).andExpect(status().isBadRequest());
        }
    }
    @Test void publicApiRequiresMatchingProject(@TempDir Path stage) throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new AgentBundleController(fixture(stage).service)).build();
        mvc.perform(get("/api/v1/agents/download/Lab2-Ubuntu")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/agents/download/Lab2-Ubuntu").param("project_id","other")).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/agents/download/Lab2-Ubuntu").param("project_id","lab2")).andExpect(status().isOk());
    }
    @Test void downloadErrorsHaveActionableMessagesWithoutStackTrace(@TempDir Path stage) throws Exception {
        var f=fixture(stage);var mvc=MockMvcBuilders.standaloneSetup(new AgentBundleController(f.service)).build();
        mvc.perform(get("/api/v1/agents/download/missing").param("project_id","lab2"))
            .andExpect(status().isConflict())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("먼저 프로젝트에 Agent를 등록하세요."))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.trace").doesNotExist());
        Files.delete(stage.resolve("default_template.sqlite"));
        mvc.perform(get("/api/v1/agents/download/Lab2-Ubuntu").param("project_id","lab2"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").exists())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.trace").doesNotExist());
    }

}
