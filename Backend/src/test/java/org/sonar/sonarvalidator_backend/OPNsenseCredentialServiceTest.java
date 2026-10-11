package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Configuration;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.entity.OPNsenseCredential;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Repository.ConfigurationRepository;
import org.sonar.sonarvalidator_backend.Repository.OPNsenseCredentialRepository;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.sonar.sonarvalidator_backend.Service.NodeRegistryService;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseApiClient;
import org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseCredentialService;
import org.sonar.sonarvalidator_backend.Service.secret.SecretCipher;

class OPNsenseCredentialServiceTest {

    private OPNsenseCredentialRepository credentialRepository;
    private ConfigurationRepository configurationRepository;
    private NodeRegistryService nodeRegistry;
    private SecretCipher secretCipher;
    private OPNsenseCredentialService service;
    private Configuration node;

    @BeforeEach
    void setUp() {
        credentialRepository = mock(OPNsenseCredentialRepository.class);
        configurationRepository = mock(ConfigurationRepository.class);
        nodeRegistry = mock(NodeRegistryService.class);
        secretCipher = mock(SecretCipher.class);
        service = new OPNsenseCredentialService(
                credentialRepository, configurationRepository, nodeRegistry,
                secretCipher, mock(OPNsenseApiClient.class));
        node = new Configuration();
        node.setNode_id(42);
        node.setAgentId("fw-edge");
    }

    @Test
    @DisplayName("legacy identifier is normalized then credential is saved against canonical node")
    void saveUsesRegisteredNodeId() {
        when(nodeRegistry.resolveOrCreateRequired("fw-edge", DeviceType.FIREWALL)).thenReturn(node);
        when(credentialRepository.findByNodeId(42)).thenReturn(Optional.empty());
        when(credentialRepository.save(any(OPNsenseCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(secretCipher.encrypt("secret")).thenReturn("encrypted");
        when(secretCipher.isPresent("encrypted")).thenReturn(true);

        final var response = service.save(
                " fw-edge ", null, "https://fw.example", " key ", " secret ", false, false);

        assertEquals(42, response.get("node_id"));
        assertEquals("fw-edge", response.get("agent_id"));
        verify(nodeRegistry).resolveOrCreateRequired("fw-edge", DeviceType.FIREWALL);
        verify(credentialRepository).save(any(OPNsenseCredential.class));
    }

    @Test
    @DisplayName("node registration failure prevents credential persistence")
    void registrationFailureDoesNotSaveOrphanCredential() {
        when(nodeRegistry.resolveOrCreateRequired(eq("fw-edge"), eq(DeviceType.FIREWALL)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.save(
                " fw-edge ", null, "https://fw.example", "key", "secret", false, false));

        verify(credentialRepository, never()).save(any(OPNsenseCredential.class));
    }

    @Test
    @DisplayName("numeric node_id must already exist and does not create an Agent node")
    void numericNodeIdResolvesCanonicalNodeOnly() {
        when(configurationRepository.findById(42)).thenReturn(Optional.of(node));
        when(credentialRepository.findByNodeId(42)).thenReturn(Optional.empty());
        when(credentialRepository.save(any(OPNsenseCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(secretCipher.encrypt("secret")).thenReturn("encrypted");
        when(secretCipher.isPresent("encrypted")).thenReturn(true);

        final var response = service.save(
                "42", null, "https://fw.example", "key", "secret", false, false);

        assertEquals(42, response.get("node_id"));
        verify(nodeRegistry, never()).resolveOrCreateRequired(any(), any());
    }

    @Test
    @DisplayName("프로젝트 키를 저장하면 응답에 프로젝트 이름이 함께 나온다")
    void storesProjectAndResolvesItsName() {
        final ProjectRepository projectRepository = mock(ProjectRepository.class);
        final Project project = new Project();
        project.setProjectKey("PRJ-1");
        project.setName("RVI-Network");
        when(projectRepository.findByProjectKey("PRJ-1")).thenReturn(Optional.of(project));
        service = new OPNsenseCredentialService(credentialRepository, configurationRepository,
                nodeRegistry, secretCipher, mock(OPNsenseApiClient.class),
                new org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseTransportPolicy(""),
                projectRepository);
        when(nodeRegistry.resolveOrCreateRequired("fw-edge", DeviceType.FIREWALL)).thenReturn(node);
        when(credentialRepository.findByNodeId(42)).thenReturn(Optional.empty());
        when(credentialRepository.save(any(OPNsenseCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(secretCipher.encrypt("secret")).thenReturn("encrypted");
        when(secretCipher.isPresent("encrypted")).thenReturn(true);

        final var response = service.save("fw-edge", null, "https://fw.example",
                "key", "secret", false, false, "PRJ-1");

        assertEquals("PRJ-1", response.get("project_id"));
        assertEquals("RVI-Network", response.get("project_name"));
    }

    @Test
    @DisplayName("없는 프로젝트로는 저장하지 않는다")
    void rejectsUnknownProject() {
        final ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(projectRepository.findByProjectKey("PRJ-NOPE")).thenReturn(Optional.empty());
        service = new OPNsenseCredentialService(credentialRepository, configurationRepository,
                nodeRegistry, secretCipher, mock(OPNsenseApiClient.class),
                new org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseTransportPolicy(""),
                projectRepository);

        assertThrows(IllegalArgumentException.class, () -> service.save(
                "fw-edge", null, "https://fw.example", "key", "secret", false, false, "PRJ-NOPE"));

        verify(credentialRepository, never()).save(any(OPNsenseCredential.class));
    }

    @Test
    @DisplayName("빈 프로젝트 키는 지정 해제, 생략(null)은 기존 값 유지")
    void blankProjectKeyClearsWhileNullKeeps() {
        final OPNsenseCredential existing = new OPNsenseCredential();
        existing.setNode(node);
        existing.setBaseUrl("https://fw.example");
        existing.setProjectKey("PRJ-1");
        when(nodeRegistry.resolveOrCreateRequired("fw-edge", DeviceType.FIREWALL)).thenReturn(node);
        when(credentialRepository.findByNodeId(42)).thenReturn(Optional.of(existing));
        when(credentialRepository.save(any(OPNsenseCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // null(필드 생략) → 기존 값 유지
        final var kept = service.save("fw-edge", null, "https://fw.example",
                null, null, false, false, null);
        assertEquals("PRJ-1", kept.get("project_id"));

        // ""(명시적 해제) → 지정 지움
        final var cleared = service.save("fw-edge", null, "https://fw.example",
                null, null, false, false, "");
        assertEquals(null, cleared.get("project_id"));
    }

    @Test
    @DisplayName("legacy API alias resolves through Configuration then queries by node_id")
    void legacyReadUsesCanonicalRepositoryKey() {
        when(configurationRepository.findByAgentId("fw-edge")).thenReturn(Optional.of(node));
        final OPNsenseCredential credential = new OPNsenseCredential();
        credential.setNode(node);
        credential.setBaseUrl("https://fw.example");
        when(credentialRepository.findByNodeId(42)).thenReturn(Optional.of(credential));

        final var response = service.get(" fw-edge ");

        assertEquals(42, response.get("node_id"));
        verify(credentialRepository).findByNodeId(42);
    }

    @Test
    @DisplayName("allow-http=false 면 평문 HTTP 주소를 저장하지 않는다")
    void rejectsHttpWhenPlainHttpDisabled() {
        service = new OPNsenseCredentialService(credentialRepository, configurationRepository,
                nodeRegistry, secretCipher, mock(OPNsenseApiClient.class),
                new org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseTransportPolicy(false, ""));

        assertThrows(IllegalArgumentException.class, () -> service.save(
                "fw-edge", null, "http://fw.example", "key", "secret", false, false));

        verify(nodeRegistry, never()).resolveOrCreateRequired(any(), any());
        verify(credentialRepository, never()).save(any(OPNsenseCredential.class));
    }

    @Test
    void explicitlyConfiguredRviOriginCanBeSaved() {
        service = new OPNsenseCredentialService(credentialRepository, configurationRepository,
                nodeRegistry, secretCipher, mock(OPNsenseApiClient.class),
                new org.sonar.sonarvalidator_backend.Service.opnsense.OPNsenseTransportPolicy("http://10.20.0.2"));
        when(nodeRegistry.resolveOrCreateRequired("fw-edge", DeviceType.FIREWALL)).thenReturn(node);
        when(credentialRepository.findByNodeId(42)).thenReturn(Optional.empty());
        when(credentialRepository.save(any(OPNsenseCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(secretCipher.encrypt("secret")).thenReturn("encrypted");
        final var response = service.save("fw-edge", null, "http://10.20.0.2", "key", "secret", false, false);
        assertEquals(42, response.get("node_id"));
        verify(credentialRepository).save(any(OPNsenseCredential.class));
    }

    @Test
    @DisplayName("legacy identifiers enforce the configuration.agent_id length")
    void rejectsIdentifierLongerThanConfigurationColumn() {
        assertThrows(IllegalArgumentException.class, () -> service.save(
                "a".repeat(121), null, "https://fw.example", "key", "secret", false, false));

        verify(nodeRegistry, never()).resolveOrCreateRequired(any(), any());
        verify(credentialRepository, never()).save(any(OPNsenseCredential.class));
    }
}
