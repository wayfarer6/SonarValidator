package org.sonar.sonarvalidator_backend.Policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PolicySubnet {

    private String id;

    private String cidr;

    private Integer vlanId;
    private String networkRole;
    public String getNetworkRole() { return networkRole; }
    public void setNetworkRole(String value) { networkRole = value; }

    public Integer getVlanId() { return vlanId; }
    public void setVlanId(Integer vlanId) { this.vlanId = vlanId; }

    private ZoneClass zoneClass;

    private String name;

    private String agentId;

    private boolean manuallyEdited;

    private List<String> allowedPeers = new ArrayList<>();

    public static final String INTERNET = "internet";

    public PolicySubnet() {
    }

    public PolicySubnet(String id, String cidr, ZoneClass zoneClass) {
        this.id = id;
        this.cidr = cidr;
        this.zoneClass = zoneClass;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCidr() {
        return cidr;
    }

    public void setCidr(String cidr) {
        this.cidr = cidr;
    }

    public ZoneClass getZoneClass() {
        return zoneClass;
    }

    public void setZoneClass(ZoneClass zoneClass) {
        this.zoneClass = zoneClass;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAgentId() {
        return agentId;
    }

    public List<String> getAllowedPeers() {
        return new ArrayList<>(allowedPeers);
    }

    public void setAllowedPeers(List<String> allowedPeers) {
        this.allowedPeers = (allowedPeers == null) ? new ArrayList<>() : new ArrayList<>(allowedPeers);
    }

    public boolean isRestricted() {
        return !allowedPeers.isEmpty();
    }

    public boolean allowsPeer(String reference) {
        if (!isRestricted()) {
            return true;
        }
        if (reference == null || reference.isBlank()) {
            return false;
        }
        final String needle = reference.trim();
        for (final String candidate : allowedPeers) {
            if (candidate == null) {
                continue;
            }
            final String allowed = candidate.trim();
            if (allowed.isEmpty()) {
                continue;
            }
            // 식별자로 적었든 CIDR 로 적었든 같은 서브넷을 가리킬 수 있습니다.
            if (allowed.equalsIgnoreCase(needle)
                    || normalizeCidr(allowed).equalsIgnoreCase(normalizeCidr(needle))) {
                return true;
            }
        }
        return false;
    }

    public boolean allowsInternet() {
        if (!isRestricted()) {
            return true;
        }
        for (final String candidate : allowedPeers) {
            if (candidate != null && INTERNET.equalsIgnoreCase(candidate.trim())) {
                return true;
            }
        }
        return false;
    }

    public boolean allowsAnyPeer(String... references) {
        if (!isRestricted()) {
            return true;
        }
        if (references == null) {
            return false;
        }
        for (final String reference : references) {
            if (allowsPeer(reference)) {
                return true;
            }
        }
        return false;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public boolean isManuallyEdited() {
        return manuallyEdited;
    }

    public void setManuallyEdited(boolean manuallyEdited) {
        this.manuallyEdited = manuallyEdited;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PolicySubnet subnet && Objects.equals(id, subnet.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return id + "[" + cidr + "/" + (zoneClass == null ? "?" : zoneClass.label()) + "]";
    }

    public static Map<String, ZoneClass> classIndex(List<PolicySubnet> subnets) {
        final Map<String, ZoneClass> index = new LinkedHashMap<>();
        if (subnets == null) {
            return index;
        }
        for (final PolicySubnet subnet : subnets) {
            if (subnet != null && subnet.getId() != null && subnet.getZoneClass() != null) {
                index.put(subnet.getId(), subnet.getZoneClass());
            }
        }
        return index;
    }

    public static Map<String, ZoneClass> cidrIndex(List<PolicySubnet> subnets) {
        final Map<String, ZoneClass> index = new LinkedHashMap<>();
        if (subnets == null) {
            return index;
        }
        for (final PolicySubnet subnet : subnets) {
            if (subnet == null || subnet.getCidr() == null || subnet.getZoneClass() == null) {
                continue;
            }
            index.put(normalizeCidr(subnet.getCidr()), subnet.getZoneClass());
        }
        return index;
    }

    public static String normalizeCidr(String cidr) {
        try {
            final PacketVariables.ParsedCidr parsed = PacketVariables.parseCidr(cidr);
            final int mask = parsed.prefixLength() == 0
                    ? 0
                    : (int) (0xFFFFFFFFL << (PacketVariables.IP_BITS - parsed.prefixLength()));
            final int network = parsed.address() & mask;
            return ((network >>> 24) & 0xFF) + "."
                    + ((network >>> 16) & 0xFF) + "."
                    + ((network >>> 8) & 0xFF) + "."
                    + (network & 0xFF) + "/" + parsed.prefixLength();
        } catch (IllegalArgumentException ex) {
            return cidr == null ? "" : cidr.trim();
        }
    }

    public static PolicySubnet of(String id, String cidr, ZoneClass zoneClass) {
        return new PolicySubnet(id, cidr, zoneClass);
    }

    public static List<PolicySubnet> copyOf(List<PolicySubnet> subnets) {
        return subnets == null ? new ArrayList<>() : new ArrayList<>(subnets);
    }
}
