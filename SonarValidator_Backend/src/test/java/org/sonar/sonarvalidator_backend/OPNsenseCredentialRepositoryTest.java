package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Configuration;
import org.sonar.sonarvalidator_backend.Model.entity.OPNsenseCredential;
import org.sonar.sonarvalidator_backend.Repository.ConfigurationRepository;
import org.sonar.sonarvalidator_backend.Repository.OPNsenseCredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class OPNsenseCredentialRepositoryTest {

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private OPNsenseCredentialRepository credentialRepository;

    @Test
    @DisplayName("credentials are stored and retrieved through canonical configuration.node_id")
    void credentialUsesCanonicalNodeIdentity() {
        final Configuration node = configurationRepository.save(Configuration.from("fw-edge", null));
        final OPNsenseCredential credential = new OPNsenseCredential();
        credential.setNode(node);
        credential.setBaseUrl("https://firewall.example");
        credential.setCreatedAt(new Date());

        final OPNsenseCredential saved = credentialRepository.saveAndFlush(credential);

        assertEquals(node.getNodeId(), saved.getNode().getNodeId());
        assertTrue(credentialRepository.findByNodeId(node.getNodeId()).isPresent());
        assertTrue(credentialRepository.existsByNodeId(node.getNodeId()));
    }
}
