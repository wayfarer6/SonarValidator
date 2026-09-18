package org.sonar.sonarvalidator_backend.Policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 프로젝트 편집기에서 다루는 <b>서브넷</b>입니다.
 *
 * <p>프론트엔드 {@code WizardSubnet} 과 같은 정보를 담되, 검증에 필요한
 * 등급/대역을 서버가 확정적으로 갖도록 만든 것입니다.
 */
public class PolicySubnet {

    /** 서브넷 식별자 (예: {@code Subnet-0001}). */
    private String id;

    /** CIDR 대역 (예: {@code 10.10.131.0/24}). */
    private String cidr;

    /** 보안 등급. */
    private ZoneClass zoneClass;

    /** 표시용 이름. */
    private String name;

    /** 소속 장치(Agent) 식별자. 자동 수집으로 만들어진 경우 채워집니다. */
    private String agentId;

    /** 수동 편집 여부. 자동 수집된 서브넷과 구분해 UI 에서 표시합니다. */
    private boolean manuallyEdited;

    /** 기본 생성자. */
    public PolicySubnet() {
    }

    /**
     * 최소 필드로 만듭니다.
     *
     * @param id        식별자
     * @param cidr      대역
     * @param zoneClass 등급
     */
    public PolicySubnet(String id, String cidr, ZoneClass zoneClass) {
        this.id = id;
        this.cidr = cidr;
        this.zoneClass = zoneClass;
    }

    /** @return 서브넷 식별자 */
    public String getId() {
        return id;
    }

    /**
     * @param id 서브넷 식별자
     */
    public void setId(String id) {
        this.id = id;
    }

    /** @return CIDR 대역 */
    public String getCidr() {
        return cidr;
    }

    /**
     * @param cidr CIDR 대역
     */
    public void setCidr(String cidr) {
        this.cidr = cidr;
    }

    /** @return 보안 등급 */
    public ZoneClass getZoneClass() {
        return zoneClass;
    }

    /**
     * @param zoneClass 보안 등급
     */
    public void setZoneClass(ZoneClass zoneClass) {
        this.zoneClass = zoneClass;
    }

    /** @return 표시용 이름 */
    public String getName() {
        return name;
    }

    /**
     * @param name 표시용 이름
     */
    public void setName(String name) {
        this.name = name;
    }

    /** @return 소속 장치 식별자 */
    public String getAgentId() {
        return agentId;
    }

    /**
     * @param agentId 소속 장치 식별자
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /** @return 수동 편집 여부 */
    public boolean isManuallyEdited() {
        return manuallyEdited;
    }

    /**
     * @param manuallyEdited 수동 편집 여부
     */
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

    /**
     * 서브넷 등급을 식별자 → 등급 맵으로 바꿉니다. (검증 엔진 입력)
     *
     * @param subnets 서브넷 목록
     * @return 식별자 → 등급
     */
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

    /**
     * 서브넷 등급을 CIDR → 등급 맵으로 바꿉니다.
     *
     * <p>CIDR 문자열을 정규화(호스트 비트 제거)해 넣으므로
     * {@code 10.0.0.5/24} 와 {@code 10.0.0.0/24} 가 같은 키가 됩니다.
     *
     * @param subnets 서브넷 목록
     * @return 정규화된 CIDR → 등급
     */
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

    /**
     * CIDR 을 네트워크 주소 기준으로 정규화합니다.
     *
     * @param cidr 입력 (예: {@code 10.10.131.7/24})
     * @return 정규화된 CIDR (예: {@code 10.10.131.0/24})
     */
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

    /**
     * 편의 생성 헬퍼.
     *
     * @param id        식별자
     * @param cidr      대역
     * @param zoneClass 등급
     * @return 서브넷
     */
    public static PolicySubnet of(String id, String cidr, ZoneClass zoneClass) {
        return new PolicySubnet(id, cidr, zoneClass);
    }

    /**
     * 목록을 방어적으로 복사합니다.
     *
     * @param subnets 원본 (null 허용)
     * @return 복사본 (null 아님)
     */
    public static List<PolicySubnet> copyOf(List<PolicySubnet> subnets) {
        return subnets == null ? new ArrayList<>() : new ArrayList<>(subnets);
    }
}
