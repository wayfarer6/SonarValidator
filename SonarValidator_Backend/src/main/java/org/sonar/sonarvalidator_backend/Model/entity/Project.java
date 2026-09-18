package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.ArrayList;
import java.util.List;

import org.sonar.sonarvalidator_backend.Policy.PolicyRule;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 프로젝트 = 하나의 망분리 검증 대상입니다.
 *
 * <h2>왜 엔티티로 두는가</h2>
 * <p>지금까지 서브넷/규칙은 프론트엔드 메모리(React state)에만 있었습니다.
 * 서버가 최종 판정을 하려면 <b>판정 대상이 서버에 남아 있어야</b> 하므로
 * 프로젝트를 영속화합니다.
 *
 * <h2>저장 구조</h2>
 * <p>서브넷과 규칙은 프로젝트에 종속된 값이라 별도 테이블로 분리하고
 * {@code @OneToMany} 로 묶었습니다. 둘 다 프로젝트와 함께만 조회되므로
 * {@link FetchType#EAGER} 로 두어 지연 로딩 예외(세션 종료 후 접근)를 피합니다.
 * (프로젝트 1건의 서브넷/규칙은 수십 건 수준이라 성능 문제가 되지 않습니다.)
 *
 * <p>{@code cascade = ALL, orphanRemoval = true} 이므로 프로젝트를 지우면
 * 하위 행도 함께 정리됩니다.
 */
@Entity
@Table(name = "project")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    /** 프로젝트 식별자. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 외부에 노출하는 프로젝트 키입니다.
     *
     * <p>프론트엔드가 {@code /project/create/?project_id=<key>} 로 넘기던 값을
     * 그대로 받습니다. 숫자 PK 와 분리해 두면 URL 이 예측 가능해지는 것을
     * 피할 수 있고, 마이그레이션 시 PK 충돌도 없습니다.
     */
    @Column(name = "project_key", nullable = false, unique = true, length = 120)
    private String projectKey;

    /** 표시 이름. */
    @Column(nullable = false, length = 200)
    private String name;

    /** 분류 (예: Finance, Government). */
    @Column(length = 100)
    private String category;

    /** 설명. */
    @Column(length = 1000)
    private String description;

    /** 진행 상태 (예: Planning, In Progress, Completed). */
    @Column(length = 50)
    private String status = "Planning";

    /** 생성 시각 (ISO-8601 문자열). */
    @Column(name = "created_at", length = 40)
    private String createdAt;

    /** 마지막 수정 시각 (ISO-8601 문자열). */
    @Column(name = "updated_at", length = 40)
    private String updatedAt;

    /** 소속 서브넷. */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id")
    @OrderColumn(name = "ordinal")
    private List<ProjectSubnet> subnets = new ArrayList<>();

    /** 연결 규칙. */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id")
    @OrderColumn(name = "ordinal")
    private List<ProjectRule> rules = new ArrayList<>();

    // ------------------------------------------------------------------
    //  변환 헬퍼
    // ------------------------------------------------------------------

    /**
     * 서브넷 목록을 검증 엔진 입력 형태로 바꿉니다.
     *
     * @return 검증용 서브넷 목록
     */
    public List<PolicySubnet> toPolicySubnets() {
        final List<PolicySubnet> result = new ArrayList<>(subnets.size());
        for (final ProjectSubnet subnet : subnets) {
            result.add(subnet.toPolicySubnet());
        }
        return result;
    }

    /**
     * 규칙 목록을 검증 엔진 입력 형태로 바꿉니다.
     *
     * @return 검증용 규칙 목록
     */
    public List<PolicyRule> toPolicyRules() {
        final List<PolicyRule> result = new ArrayList<>(rules.size());
        for (final ProjectRule rule : rules) {
            result.add(rule.toPolicyRule());
        }
        return result;
    }

    /**
     * 검증 입력을 프로젝트에 반영합니다. 기존 목록은 비우고 새로 채웁니다.
     *
     * <p>{@code orphanRemoval = true} 이므로 목록을 clear 하면 삭제가 전파됩니다.
     *
     * @param newSubnets 반영할 서브넷 (null 이면 무시)
     * @param newRules   반영할 규칙 (null 이면 무시)
     */
    public void replacePolicy(List<PolicySubnet> newSubnets, List<PolicyRule> newRules) {
        if (newSubnets != null) {
            subnets.clear();
            for (final PolicySubnet subnet : newSubnets) {
                subnets.add(ProjectSubnet.from(subnet));
            }
        }
        if (newRules != null) {
            rules.clear();
            for (final PolicyRule rule : newRules) {
                rules.add(ProjectRule.from(rule));
            }
        }
    }
}
