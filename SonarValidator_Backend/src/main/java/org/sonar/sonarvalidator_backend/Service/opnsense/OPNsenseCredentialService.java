package org.sonar.sonarvalidator_backend.Service.opnsense;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.Configuration;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.entity.OPNsenseCredential;
import org.sonar.sonarvalidator_backend.Repository.ConfigurationRepository;
import org.sonar.sonarvalidator_backend.Repository.OPNsenseCredentialRepository;
import org.sonar.sonarvalidator_backend.Service.NodeRegistryService;
import org.sonar.sonarvalidator_backend.Service.secret.SecretCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

/** Stores OPNsense credentials attached to the canonical configuration node. */
@Service
public class OPNsenseCredentialService {

    private static final Logger log = LoggerFactory.getLogger(OPNsenseCredentialService.class);
    private static final Pattern NUMERIC_NODE_ID = Pattern.compile("[0-9]+");

    private final OPNsenseCredentialRepository repository;
    private final ConfigurationRepository configurationRepository;
    private final NodeRegistryService nodeRegistry;
    private final SecretCipher secretCipher;
    private final OPNsenseApiClient apiClient;

    public OPNsenseCredentialService(OPNsenseCredentialRepository repository,
                                     ConfigurationRepository configurationRepository,
                                     NodeRegistryService nodeRegistry,
                                     SecretCipher secretCipher,
                                     OPNsenseApiClient apiClient) {
        this.repository = repository;
        this.configurationRepository = configurationRepository;
        this.nodeRegistry = nodeRegistry;
        this.secretCipher = secretCipher;
        this.apiClient = apiClient;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAll() {
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final OPNsenseCredential credential : repository.findAllByOrderByIdAsc()) {
            result.add(toResponse(credential));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String nodeIdentifier) {
        return findCredential(nodeIdentifier).map(this::toResponse).orElse(null);
    }

    /** Resolves either a canonical numeric node_id or the legacy Agent-ID API alias. */
    @Transactional(readOnly = true)
    public Optional<OPNsenseCredential> findCredential(String nodeIdentifier) {
        final Configuration node = findNode(nodeIdentifier);
        return node == null ? Optional.empty() : repository.findByNodeId(node.getNodeId());
    }

    @Transactional(readOnly = true)
    public boolean hasCredentialForAgentIdentifier(String agentIdentifier) {
        final String normalized = normalizeIdentifier(agentIdentifier);
        if (normalized == null) {
            return false;
        }
        final Configuration node = configurationRepository.findByAgentId(normalized).orElse(null);
        return node != null && repository.existsByNodeId(node.getNodeId());
    }

    /**
     * Saves credentials in the same transaction as node resolution. Registration
     * failures propagate before credential persistence, so no orphan is possible.
     */
    @Transactional
    public Map<String, Object> save(String nodeIdentifier,
                                    String displayName,
                                    String baseUrl,
                                    String apiKey,
                                    String apiSecret,
                                    boolean allowInsecureTls,
                                    boolean verifyNow) {
        final String normalized = normalizeIdentifier(nodeIdentifier);
        if (normalized == null) {
            throw new IllegalArgumentException("node_id 는 필수입니다.");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("OPNsense 주소(base_url)는 필수입니다.");
        }
        if (!new OPNsenseConnection(baseUrl.trim(), "key", "secret", allowInsecureTls)
                .isSecureTransport()) {
            throw new IllegalArgumentException("OPNsense 자격증명은 HTTPS 주소에서만 사용할 수 있습니다.");
        }

        final Configuration node = resolveNodeForSave(normalized);
        if (node == null || node.getNodeId() == null) {
            throw new IllegalStateException("OPNsense 자격증명을 저장할 노드를 등록하지 못했습니다.");
        }

        final OPNsenseCredential credential = repository.findByNodeId(node.getNodeId())
                .orElseGet(() -> {
                    final OPNsenseCredential created = new OPNsenseCredential();
                    created.setNode(node);
                    created.setCreatedAt(new Date());
                    return created;
                });
        credential.setBaseUrl(baseUrl.trim());
        credential.setDisplayName(displayName == null || displayName.isBlank()
                ? displayNameFor(node)
                : displayName.trim());
        credential.setAllowInsecureTls(allowInsecureTls);
        if (apiKey != null && !apiKey.isBlank()) {
            credential.setApiKey(apiKey.trim());
        }
        if (apiSecret != null && !apiSecret.isBlank()) {
            credential.setSecret(secretCipher.encrypt(apiSecret.trim()));
        }
        credential.setUpdatedAt(new Date());

        OPNsenseCredential saved = repository.save(credential);
        if (verifyNow) {
            saved = verifyAndRecord(saved);
        }
        log.info("opnsense credential saved: node_id={} url={} status={}",
                saved.getNode().getNodeId(), saved.getBaseUrl(), saved.getStatus());
        return toResponse(saved);
    }

    @Transactional
    public boolean delete(String nodeIdentifier) {
        final Optional<OPNsenseCredential> found = findCredential(nodeIdentifier);
        if (found.isEmpty()) {
            return false;
        }
        repository.delete(found.get());
        log.info("opnsense credential deleted: node_id={}", found.get().getNode().getNodeId());
        return true;
    }

    @Transactional
    public Map<String, Object> verify(String nodeIdentifier) {
        final OPNsenseCredential credential = findCredential(nodeIdentifier)
                .orElseThrow(() -> new IllegalArgumentException(
                        "OPNsense 접속 정보가 등록되지 않았습니다: " + nodeIdentifier));
        return toResponse(verifyAndRecord(credential));
    }

    @Transactional
    public List<Map<String, Object>> verifyAll() {
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final OPNsenseCredential credential : repository.findAllByOrderByIdAsc()) {
            result.add(toResponse(verifyAndRecord(credential)));
        }
        return result;
    }

    private Configuration resolveNodeForSave(String normalizedIdentifier) {
        if (NUMERIC_NODE_ID.matcher(normalizedIdentifier).matches()) {
            final Integer nodeId;
            try {
                nodeId = Integer.valueOf(normalizedIdentifier);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("node_id is outside the supported range");
            }
            return configurationRepository.findById(nodeId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "configuration node does not exist: " + normalizedIdentifier));
        }
        return nodeRegistry.resolveOrCreateRequired(normalizedIdentifier, DeviceType.FIREWALL);
    }

    private Configuration findNode(String identifier) {
        final String normalized = normalizeIdentifier(identifier);
        if (normalized == null) {
            return null;
        }
        if (NUMERIC_NODE_ID.matcher(normalized).matches()) {
            try {
                return configurationRepository.findById(Integer.valueOf(normalized)).orElse(null);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return configurationRepository.findByAgentId(normalized).orElse(null);
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        final String normalized = identifier.trim();
        if (!NUMERIC_NODE_ID.matcher(normalized).matches() && normalized.length() > 120) {
            throw new IllegalArgumentException("node identifier must be at most 120 characters");
        }
        return normalized;
    }

    private static String displayNameFor(Configuration node) {
        return node.getAgentId() == null ? "OPNsense node " + node.getNodeId() : node.getAgentId();
    }

    private OPNsenseCredential verifyAndRecord(OPNsenseCredential credential) {
        final OPNsenseConnection connection = toConnection(credential);
        if (connection == null) {
            credential.markChecked(false, null, "API Key 또는 Secret 이 없습니다.");
            return repository.save(credential);
        }
        final OPNsenseApiClient.Result result = apiClient.checkConnection(connection);
        if (result.ok()) {
            final String version = apiClient.extractVersion(result.body());
            credential.markChecked(true, version, null);
            log.info("opnsense connection verified: node_id={} version={}",
                    credential.getNode().getNodeId(), version == null ? "(unknown)" : version);
        } else {
            credential.markChecked(false, null, result.error());
            log.warn("opnsense connection failed: node_id={} reason={}",
                    credential.getNode().getNodeId(), result.error());
        }
        return repository.save(credential);
    }

    public OPNsenseConnection toConnection(OPNsenseCredential credential) {
        if (credential == null) {
            return null;
        }
        String secret = null;
        if (secretCipher.isPresent(credential.getSecret())) {
            try {
                secret = secretCipher.decrypt(credential.getSecret());
            } catch (IllegalStateException ex) {
                log.error("cannot decrypt OPNsense secret for node_id={}: {}",
                        credential.getNode().getNodeId(), ex.getMessage());
                return null;
            }
        }
        return new OPNsenseConnection(
                credential.getBaseUrl(),
                credential.getApiKey(),
                secret,
                credential.isAllowInsecureTls());
    }

    public Map<String, Object> toResponse(OPNsenseCredential credential) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("node_id", credential.getNode().getNodeId());
        // Kept for existing UI clients. This is derived from the associated
        // canonical node, never used as the credential's persistence key.
        body.put("agent_id", credential.getNode().getAgentId());
        body.put("display_name", credential.getDisplayName());
        body.put("base_url", credential.getBaseUrl());
        body.put("api_key_masked", OPNsenseConnection.mask(credential.getApiKey()));
        body.put("has_api_key", credential.getApiKey() != null && !credential.getApiKey().isBlank());
        body.put("has_secret", secretCipher.isPresent(credential.getSecret()));
        body.put("allow_insecure_tls", credential.isAllowInsecureTls());
        body.put("status", credential.getStatus() == null ? null : credential.getStatus().name());
        body.put("last_checked_at", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(
                credential.getLastCheckedAt()));
        body.put("last_error", credential.getLastError());
        body.put("detected_version", credential.getDetectedVersion());
        body.put("created_at", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(
                credential.getCreatedAt()));
        body.put("updated_at", credential.getUpdatedAt());
        return body;
    }

    @Transactional(readOnly = true)
    public List<String> agentsWithoutCredential(List<String> knownAgentIds) {
        final List<String> result = new ArrayList<>();
        if (knownAgentIds == null) {
            return result;
        }
        for (final String agentId : knownAgentIds) {
            if (agentId != null && !agentId.isBlank() && !hasCredentialForAgentIdentifier(agentId)) {
                result.add(agentId.trim());
            }
        }
        return result;
    }

    public Map<String, Object> summarize(JsonNode body) {
        return apiClient.summarize(body);
    }
}
