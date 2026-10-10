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

/**
 * 프로젝트에 속한 서브넷 한 건입니다.
 *
 * <p>{@link PolicySubnet} 은 검증 엔진이 쓰는 <b>순수 도메인 객체</b>이고,
 * 이 클래스는 그것을 저장하기 위한 <b>영속 표현</b>입니다. 둘을 나눠 두면
 * 검증 로직이 JPA 에 묶이지 않고, 스키마 변경이 엔진에 영향을 주지 않습니다.
 *
 * <h2>프로젝트와의 관계</h2>
 * <p>{@link #project} 가 <b>외래키를 소유</b>합니다({@code project_id}).
 * 부모인 {@link Project#getSubnets()} 는 {@code mappedBy} 로 반대편을
 * 가리키므로, FK 컬럼은 DB 에 한 번만 생깁니다. (양쪽 모두
 * {@code @JoinColumn} 을 쓰면 같은 컬럼을 두 번 매핑해 기동에 실패합니다)
 *
 * <p>서브넷은 프로젝트 없이 존재할 수 없습니다. 다만 {@code nullable} 로
 * 두어 스키마 진화(Hibernate {@code ddl-auto=update})가 막히지 않게 했습니다.
 */
@Entity
@Table(name = "project_subnet")
@Getter
@Setter
@NoArgsConstructor
public class ProjectSubnet {

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소속 프로젝트입니다. (외래키 소유)
     *
     * <p>지연 로딩입니다. 서브넷을 쓸 때 프로젝트 전체를 끌어오면
     * 순환 참조와 불필요한 조회가 생깁니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /** 프로젝트 안에서의 서브넷 식별자 (예: {@code Subnet-0004}). */
    @Column(name = "subnet_id", nullable = false, length = 80)
    private String subnetId;

    /** CIDR 대역. */
    @Column(nullable = false, length = 80)
    private String cidr;

    /** VLAN identity survives even before an IP prefix is known. */
    @Column(name = "vlan_id")
    private Integer vlanId;

    /**
     * 보안 등급.
     *
     * <p>{@link EnumType#STRING} 으로 저장합니다. ORDINAL 로 저장하면
     * 등급을 재정렬하는 순간 기존 데이터의 의미가 바뀝니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "zone_class", length = 30)
    private ZoneClass zoneClass;

    /** 표시용 이름. */
    @Column(length = 200)
    private String name;

    /** 자동 수집 출처 장치 식별자. */
    @Column(name = "agent_id", length = 120)
    private String agentId;

    /** 수동 편집 여부. */
    @Column(name = "manually_edited")
    private boolean manuallyEdited;

    /**
     * 이 서브넷이 연결을 <b>허용하는</b> 상대 목록입니다. (쉼표 구분)
     *
     * <h2>⚠️ 왜 문자열 하나인가</h2>
     * <p>원소가 "상대 서브넷 식별자 또는 CIDR" 이고 개수도 가변입니다.
     * 별도 테이블로 분리하면 서브넷 한 건을 저장할 때마다 자식 행을
     * 전부 지우고 다시 써야 하고(순서·중복 관리), 편집기 저장 경로가
     * 여러 엔티티를 함께 다뤄야 합니다. 이 필드는 <b>선택적 태그 목록</b>에
     * 가까우므로 한 칼럼에 둡니다.
     *
     * <p>빈 문자열/null = 제한 없음(기존 동작)입니다.
     */
    @Column(name = "allowed_peers", length = 1000)
    private String allowedPeers;

    /**
     * 허용 상대를 목록으로 돌려줍니다.
     *
     * @return 허용 상대 목록 (비어 있으면 제한 없음)
     */
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

    /**
     * 허용 상대 목록을 저장합니다.
     *
     * @param peers 허용 상대 (null/빈 목록이면 제한 해제)
     */
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

    /**
     * 허용 목록이 설정되어 있는지 알려줍니다.
     *
     * @return 제한 중이면 true
     */
    public boolean isRestricted() {
        return !allowedPeerList().isEmpty();
    }

    /**
     * 상대를 허용 목록에서 받아들이는지 봅니다.
     *
     * <p>판정은 도메인 객체({@link PolicySubnet})가 합니다. 검증 엔진과 정책
     * 생성이 <b>같은 함수</b>를 써야 "검증은 통과했는데 장치에서는 막히는"
     * 불일치가 생기지 않습니다.
     *
     * @param reference 상대 서브넷 식별자 또는 CIDR
     * @return 허용이면 true
     */
    public boolean allowsPeer(String reference) {
        return toPolicySubnet().allowsPeer(reference);
    }

    /**
     * 인터넷(기본 경로)이 허용 목록에 있는지 봅니다.
     *
     * @return 인터넷을 넣었거나 제한이 없으면 true
     */
    public boolean allowsInternet() {
        return toPolicySubnet().allowsInternet();
    }

    /**
     * 상대의 여러 표기 중 하나라도 허용 목록에 있는지 봅니다.
     *
     * <p>판정은 도메인 객체가 합니다 — 검증 엔진과 정책 생성이 <b>같은 함수</b>를
     * 써야 "검증은 통과했는데 장치에서 막히는" 불일치가 생기지 않습니다.
     *
     * @param references 상대의 표기들 (식별자/CIDR)
     * @return 하나라도 허용이면 true
     */
    public boolean allowsAnyPeer(String... references) {
        return toPolicySubnet().allowsAnyPeer(references);
    }

    /**
     * 도메인 객체로 변환합니다.
     *
     * @return 검증 엔진 입력용 서브넷
     */
    public PolicySubnet toPolicySubnet() {
        final PolicySubnet subnet = new PolicySubnet();
        subnet.setId(subnetId);
        subnet.setCidr(cidr);
        subnet.setVlanId(vlanId);
        subnet.setZoneClass(zoneClass);
        subnet.setName(name);
        subnet.setAgentId(agentId);
        subnet.setManuallyEdited(manuallyEdited);
        subnet.setAllowedPeers(allowedPeerList());
        return subnet;
    }

    /**
     * 도메인 객체에서 영속 표현을 만듭니다.
     *
     * @param subnet 원본
     * @return 저장용 엔티티
     */
    public static ProjectSubnet from(PolicySubnet subnet) {
        final ProjectSubnet entity = new ProjectSubnet();
        entity.setSubnetId(subnet.getId());
        entity.setCidr(subnet.getCidr() == null ? "" : subnet.getCidr());
        entity.setVlanId(subnet.getVlanId());
        entity.setZoneClass(subnet.getZoneClass());
        entity.setName(subnet.getName());
        entity.setAgentId(subnet.getAgentId());
        entity.setManuallyEdited(subnet.isManuallyEdited());
        entity.setAllowedPeerList(subnet.getAllowedPeers());
        return entity;
    }
}
