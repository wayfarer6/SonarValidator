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
import org.sonar.sonarvalidator_backend.Repository.ConfigurationRepository;
import org.sonar.sonarvalidator_backend.Repository.OPNsenseCredentialRepository;
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
    @DisplayName("Basic credentials cannot be saved for explicit HTTP URLs")
    void rejectsHttpBeforeResolvingOrSaving() {
        assertThrows(IllegalArgumentException.class, () -> service.save(
                "fw-edge", null, "http://fw.example", "key", "secret", false, false));

        verify(nodeRegistry, never()).resolveOrCreateRequired(any(), any());
        verify(credentialRepository, never()).save(any(OPNsenseCredential.class));
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
