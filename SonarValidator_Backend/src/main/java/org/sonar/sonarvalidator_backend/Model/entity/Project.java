package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.sonar.sonarvalidator_backend.Policy.PolicyRule;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 *
 * <h2>소유자</h2>
 * <p>프로젝트는 {@link User} 한 명이 소유합니다({@link #owner}).
 * 컬럼 이름은 {@code user_id} 입니다. 한동안 {@code app_user_id} 로
 * 두었던 적이 있는데, 그건 테이블 이름이 {@code app_user} 이던 시절의
 * 흔적입니다. 지금은 테이블 이름이 설계 ERD 와 같은 {@code USER} 이므로
 * <b>컬럼 이름만 다를 이유가 없어</b> 원래 이름으로 되돌렸습니다.
 *
 * <p><b>nullable</b> 입니다. 소유자를 알 수 없는 기존 행을 지우거나
 * 가짜 소유자를 만들 필요가 없고, 인증을 붙이기 전에 만들어진
 * 데이터도 그대로 살아 있습니다.
 *
 * <h2>날짜 타입</h2>
 * <p>{@link #createdAt}/{@link #updatedAt} 은 {@link Date} 입니다.
 * DB 에는 TIMESTAMP 로 들어가고, JSON 으로 나갈 때
 * {@code @JsonFormat} 이 <b>ISO-8601 문자열</b> 로 직렬화합니다.
 * 문자열 컬럼으로 두면 형식이 제각각으로 섞여 들어와 정렬·비교가
 * 어긋나므로, 저장은 시각 타입으로 합니다.
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

    /**
     * 소유자입니다.
     *
     * <p>{@code nullable} 입니다. 소유자가 없는 기존 행(인증 도입 전
     * 생성분)을 살리기 위해서입니다. 조회는 지연 로딩으로 두어
     * 목록 화면이 사용자 테이블까지 매번 읽지 않게 합니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    /** 생성 시각. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "created_at")
    private Date createdAt;

    /** 마지막 수정 시각. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "updated_at")
    private Date updatedAt;

    /**
     * 소속 서브넷.
     *
     * <p>{@code mappedBy} 입니다. 외래키는 자식({@link ProjectSubnet#getProject()})
     * 이 소유하므로, 여기서 {@code @JoinColumn} 을 또 쓰면 같은 컬럼이 두 번
     * 매핑되어 기동에 실패합니다.
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderColumn(name = "ordinal")
    private List<ProjectSubnet> subnets = new ArrayList<>();

    /** 연결 규칙. ({@link #subnets} 와 같은 구조) */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
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
     * <p>자식이 외래키를 소유하므로 <b>부모를 반드시 넣어 줍니다</b>.
     * 빠뜨리면 {@code project_id} 가 NULL 인 행이 생겨 정책이 고아가 됩니다.
     * ({@code @OneToMany(mappedBy)} 는 자식 필드를 자동으로 채우지 않습니다)
     *
     * @param newSubnets 반영할 서브넷 (null 이면 무시)
     * @param newRules   반영할 규칙 (null 이면 무시)
     */
    public void replacePolicy(List<PolicySubnet> newSubnets, List<PolicyRule> newRules) {
        if (newSubnets != null) {
            subnets.clear();
            for (final PolicySubnet subnet : newSubnets) {
                final ProjectSubnet entity = ProjectSubnet.from(subnet);
                entity.setProject(this);
                subnets.add(entity);
            }
        }
        if (newRules != null) {
            rules.clear();
            for (final PolicyRule rule : newRules) {
                final ProjectRule entity = ProjectRule.from(rule);
                entity.setProject(this);
                rules.add(entity);
            }
        }
    }
}
