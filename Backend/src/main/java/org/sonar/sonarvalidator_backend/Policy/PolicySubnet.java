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

    private Integer vlanId;

    public Integer getVlanId() { return vlanId; }
    public void setVlanId(Integer vlanId) { this.vlanId = vlanId; }

    /** 보안 등급. */
    private ZoneClass zoneClass;

    /** 표시용 이름. */
    private String name;

    /** 소속 장치(Agent) 식별자. 자동 수집으로 만들어진 경우 채워집니다. */
    private String agentId;

    /** 수동 편집 여부. 자동 수집된 서브넷과 구분해 UI 에서 표시합니다. */
    private boolean manuallyEdited;

    /**
     * 이 서브넷이 연결을 <b>허용하는</b> 상대 목록입니다.
     *
     * <h2>⚠️ 무엇을 뜻하는가</h2>
     * <p>비어 있으면 제한 없음(현재 동작)입니다. 값이 있으면 이 서브넷은
     * 목록에 있는 상대에게만 나갈 수 있고, 나머지는 정책 배포 시 차단됩니다.
     *
     * <p>원소는 상대 서브넷 식별자 또는 CIDR 이고, 특수 토큰 {@code internet}
     * 은 "인터넷(기본 경로)으로 나가는 것을 <b>허용 목록이 막지 않는다</b>" 는 뜻입니다.
     *
     * <h2>⚠️ 목록은 조이기만 한다 (보안 불변식)</h2>
     * <p>이 목록은 등급 규칙을 <b>완화할 수 없습니다</b>. 기밀망을
     * {@code Open} 서브넷이나 인터넷에 넣어도 등급 건너뛰기 판정이 그대로
     * 위반입니다. 목록에 넣는 것으로 망분리를 풀 수 있으면, 운영자의 실수
     * 한 번이 보안 경계를 없앱니다.
     *
     * <p>즉 실제 차단 집합은 항상
     * {@code (등급 위반) ∪ (허용 목록 밖)} 입니다.
     */
    private List<String> allowedPeers = new ArrayList<>();

    /** 인터넷(기본 경로)을 가리키는 허용 목록 토큰입니다. */
    public static final String INTERNET = "internet";

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

    /** @return 허용 상대 목록 (복사본) */
    public List<String> getAllowedPeers() {
        return new ArrayList<>(allowedPeers);
    }

    /**
     * @param allowedPeers 허용 상대 목록 (null 이면 제한 없음)
     */
    public void setAllowedPeers(List<String> allowedPeers) {
        this.allowedPeers = (allowedPeers == null) ? new ArrayList<>() : new ArrayList<>(allowedPeers);
    }

    /**
     * 허용 목록이 설정되어 있는지 알려줍니다.
     *
     * @return 제한 중이면 true
     */
    public boolean isRestricted() {
        return !allowedPeers.isEmpty();
    }

    /**
     * 상대를 허용 목록에서 받아들이는지 봅니다.
     *
     * <p>제한이 없으면(빈 목록) 항상 true 입니다.
     *
     * @param reference 상대 서브넷 식별자 또는 CIDR (null 허용)
     * @return 허용이면 true
     */
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

    /**
     * 인터넷(기본 경로)으로 나가는 것을 허용 목록이 막지 않는지 봅니다.
     *
     * @return 인터넷을 목록에 넣었거나 제한이 없으면 true
     */
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

    /**
     * 여러 표기 중 <b>하나라도</b> 허용 목록에 있으면 허용으로 봅니다.
     *
     * <h2>⚠️ 왜 필요한가</h2>
     * <p>같은 상대를 두 가지로 적을 수 있습니다 — 서브넷 <b>식별자</b>
     * ({@code VLAN9-ca6e…}, 자동 수집이 만드는 형태)와 <b>CIDR</b>
     * ({@code 10.0.9.0/24}, 사람이 읽기 쉬운 형태). 화면은 식별자를 보내고
     * 저장된 상대는 CIDR 일 수 있으므로, 한쪽만 비교하면 <b>운영자가 체크한
     * 항목이 매칭되지 않아</b> 허용했는데도 차단됩니다.
     *
     * <p>그래서 호출부는 상대의 <b>모든 표기</b>를 넘겨야 합니다.
     *
     * @param references 상대의 표기들 (식별자/CIDR, null 허용)
     * @return 하나라도 허용이면 true (제한이 없으면 true)
     */
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
