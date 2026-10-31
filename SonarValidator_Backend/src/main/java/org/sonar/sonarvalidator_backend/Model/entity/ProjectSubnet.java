package org.sonar.sonarvalidator_backend.Model.entity;

import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_subnet")
@Getter
@Setter
@NoArgsConstructor
public class ProjectSubnet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "subnet_id", nullable = false, length = 80)
    private String subnetId;

    @Column(nullable = false, length = 80)
    private String cidr;

    @Column(name = "vlan_id")
    private Integer vlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_class", length = 30)
    private ZoneClass zoneClass;

    @Column(length = 200)
    private String name;

    @Column(name = "network_role", length = 30)
    private String networkRole;

    @Column(name = "agent_id", length = 120)
    private String agentId;

    @Column(name = "manually_edited")
    private boolean manuallyEdited;

    @Column(name = "allowed_peers", length = 1000)
    private String allowedPeers;

    public java.util.List<String> allowedPeerList() {
        if (allowedPeers == null || allowedPeers.isBlank()) {
            return new java.util.ArrayList<>();
        }
        final java.util.List<String> result = new java.util.ArrayList<>();
        for (final String token : allowedPeers.split(",")) {
            final String trimmed = token.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public void setAllowedPeerList(java.util.List<String> peers) {
        if (peers == null || peers.isEmpty()) {
            this.allowedPeers = null;
            return;
        }
        final java.util.List<String> cleaned = new java.util.ArrayList<>();
        for (final String peer : peers) {
            if (peer == null) {
                continue;
            }
            final String trimmed = peer.trim();
            if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
                cleaned.add(trimmed);
            }
        }
        this.allowedPeers = cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    public boolean isRestricted() {
        return !allowedPeerList().isEmpty();
    }

    public boolean allowsPeer(String reference) {
        return toPolicySubnet().allowsPeer(reference);
    }

    public boolean allowsInternet() {
        return toPolicySubnet().allowsInternet();
    }

    public boolean allowsAnyPeer(String... references) {
        return toPolicySubnet().allowsAnyPeer(references);
    }

    public PolicySubnet toPolicySubnet() {
        final PolicySubnet subnet = new PolicySubnet();
        subnet.setId(subnetId);
        subnet.setCidr(cidr);
        subnet.setVlanId(vlanId);
        subnet.setZoneClass(zoneClass);
        subnet.setName(name);
        subnet.setNetworkRole(networkRole);
        subnet.setAgentId(agentId);
        subnet.setManuallyEdited(manuallyEdited);
        subnet.setAllowedPeers(allowedPeerList());
        return subnet;
    }

    public static ProjectSubnet from(PolicySubnet subnet) {
        final ProjectSubnet entity = new ProjectSubnet();
        entity.setSubnetId(subnet.getId());
        entity.setCidr(subnet.getCidr() == null ? "" : subnet.getCidr());
        entity.setVlanId(subnet.getVlanId());
        entity.setZoneClass(subnet.getZoneClass());
        entity.setName(subnet.getName());
        entity.setNetworkRole(subnet.getNetworkRole());
        entity.setAgentId(subnet.getAgentId());
        entity.setManuallyEdited(subnet.isManuallyEdited());
        entity.setAllowedPeerList(subnet.getAllowedPeers());
        return entity;
    }
}
