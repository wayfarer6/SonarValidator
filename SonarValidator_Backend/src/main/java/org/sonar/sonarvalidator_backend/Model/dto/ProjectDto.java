package org.sonar.sonarvalidator_backend.Model.dto;

import java.util.List;

import org.sonar.sonarvalidator_backend.Policy.PolicyRule;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ProjectDto {

    private ProjectDto() {
    }

    public record SubnetPayload(
            String id,
            String cidr,
            @JsonProperty("subnet_class") String subnetClass,
            String name,
            @JsonProperty("agent_id") String agentId,
            @JsonProperty("manually_edited") Boolean manuallyEdited,
            @JsonProperty("vlan_id") Integer vlanId,
            @JsonProperty("allowed_peers") List<String> allowedPeers,
            @JsonProperty("network_role") String networkRole) {

        public SubnetPayload(String id, String cidr, String subnetClass, String name,
                             String agentId, Boolean manuallyEdited, Integer vlanId, List<String> allowedPeers) {
            this(id, cidr, subnetClass, name, agentId, manuallyEdited, vlanId, allowedPeers, null);
        }

        public SubnetPayload(String id, String cidr, String subnetClass, String name,
                             String agentId, Boolean manuallyEdited) {
            this(id, cidr, subnetClass, name, agentId, manuallyEdited, null, null);
        }

        public SubnetPayload(String id, String cidr, String subnetClass, String name,
                             String agentId, Boolean manuallyEdited, Integer vlanId) {
            this(id, cidr, subnetClass, name, agentId, manuallyEdited, vlanId, null);
        }

        public PolicySubnet toPolicySubnet() {
            final PolicySubnet subnet = new PolicySubnet();
            subnet.setId(id);
            subnet.setCidr(cidr == null ? "" : cidr);
            subnet.setVlanId(vlanId);
            subnet.setZoneClass(ZoneClass.fromString(subnetClass));
            subnet.setName(name);
            if (networkRole != null && !java.util.Set.of("MANAGEMENT", "TRANSIT", "SERVICE", "GUEST", "INTERNET").contains(networkRole)) {
                throw new IllegalArgumentException("Unknown network_role: " + networkRole);
            }
            subnet.setNetworkRole(networkRole);
            subnet.setAgentId(agentId);
            subnet.setManuallyEdited(manuallyEdited != null && manuallyEdited);



            subnet.setAllowedPeers(allowedPeers);
            return subnet;
        }
    }

    public record RulePayload(
            String id,
            String src,
            String dst,
            Integer port,
            String protocol,
            String origin,
            Boolean enabled,
            String note) {

        public PolicyRule toPolicyRule() {
            final PolicyRule rule = new PolicyRule();
            rule.setId(id);
            rule.setSource(src);
            rule.setDestination(dst);
            if (port != null) {
                rule.setPort(port);
            }
            if (protocol != null && !protocol.isBlank()) {
                rule.setProtocol(protocol);
            }
            if (origin != null && "DISCOVERED".equalsIgnoreCase(origin.trim())) {
                rule.setOrigin(PolicyRule.Origin.DISCOVERED);
            } else {
                rule.setOrigin(PolicyRule.Origin.MANUAL);
            }
            rule.setEnabled(enabled == null || enabled);
            rule.setNote(note);
            return rule;
        }
    }

    public record CreateRequest(
            @JsonProperty("project_id") String projectId,
            String name,
            String category,
            String description,
            String status,
            @JsonProperty("management_prefix") String managementPrefix) {
    }

    public record UpdateRequest(
            String name,
            String category,
            String description,
            String status,
            @JsonProperty("management_prefix") String managementPrefix,
            @JsonProperty("management_server_ip") String managementServerIp,
            @JsonProperty("management_server_port") Integer managementServerPort,
            List<SubnetPayload> subnets,
            List<RulePayload> rules) {

        public UpdateRequest(
                String name,
                String category,
                String description,
                String status,
                String managementPrefix,
                List<SubnetPayload> subnets,
                List<RulePayload> rules) {
            this(name, category, description, status, managementPrefix, null, null, subnets, rules);
        }
    }

    public record ValidateRequest(
            List<SubnetPayload> subnets,
            List<RulePayload> rules) {
    }
}
