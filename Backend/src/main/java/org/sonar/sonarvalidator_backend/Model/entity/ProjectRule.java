package org.sonar.sonarvalidator_backend.Model.entity;

import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Policy.PolicyRule;

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
 * 프로젝트에 속한 연결 규칙 한 건입니다.
 *
 * <p>{@link PolicyRule} 의 영속 표현입니다. 포트는 "미지정" 상태가 있으므로
 * {@code NULL} 을 허용하는 {@link Integer} 로 두고, 꺼낼 때
 * {@link PacketVariables#ANY_PORT} 로 되돌립니다.
 *
 * <h2>프로젝트와의 관계</h2>
 * <p>{@link #project} 가 외래키({@code project_id})를 소유합니다. 서브넷과
 * 같은 방식이며, 부모의 {@code mappedBy} 와 짝을 이룹니다.
 */
@Entity
@Table(name = "project_rule")
@Getter
@Setter
@NoArgsConstructor
public class ProjectRule {

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 프로젝트입니다. (외래키 소유) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /** 프로젝트 안에서의 규칙 식별자 (예: {@code Rule-0001}). */
    @Column(name = "rule_id", nullable = false, length = 80)
    private String ruleId;

    /** 출발 서브넷 식별자. */
    @Column(name = "source_subnet_id", length = 80)
    private String source;

    /** 도착 서브넷 식별자. */
    @Column(name = "destination_subnet_id", length = 80)
    private String destination;

    /** 허용 포트. 미지정이면 {@code null}. */
    @Column(name = "port_number")
    private Integer port;

    /** 프로토콜. */
    @Column(length = 20)
    private String protocol = "tcp";

    /** 규칙 출처. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PolicyRule.Origin origin = PolicyRule.Origin.MANUAL;

    /** 검증 포함 여부. */
    @Column(nullable = false)
    private boolean enabled = true;

    /** 비활성 사유 메모. */
    @Column(length = 500)
    private String note;

    /**
     * 프로젝트 안에서의 표시 순서입니다.
     *
     * <h2>⚠️ {@code @OrderColumn} 대신 명시 필드를 쓰는 이유</h2>
     * <p>예전에는 부모의 {@code @OneToMany(mappedBy)} 에 {@code @OrderColumn} 을
     * 붙였습니다. 그런데 {@code @OrderColumn} 은 <b>자식 테이블을 소유한
     * 쪽(부모가 FK 를 들고 있는 단방향 관계)</b> 을 전제로 합니다.
     * {@code mappedBy}(양방향, 자식이 FK 소유)에서는 Hibernate 가 순서 값을
     * 관리할 주체를 찾지 못해 기동 시 경고를 내고, 순서가 저장되지 않을 수
     * 있습니다. 그래서 순서를 <b>자식의 실제 컬럼</b>으로 두고 부모에서
     * {@code @OrderBy("ordinal ASC")} 로 읽습니다.
     *
     * <p>컬럼 이름은 기존 {@code ordinal} 을 유지합니다 — 스키마가 그대로라
     * 마이그레이션이 필요 없습니다.
     */
    @Column(name = "ordinal")
    private Integer ordinal;

    /**
     * 도메인 객체로 변환합니다.
     *
     * @return 검증 엔진 입력용 규칙
     */
    public PolicyRule toPolicyRule() {
        final PolicyRule rule = new PolicyRule();
        rule.setId(ruleId);
        rule.setSource(source);
        rule.setDestination(destination);
        rule.setPort(port == null ? PacketVariables.ANY_PORT : port);
        rule.setProtocol(protocol);
        rule.setOrigin(origin == null ? PolicyRule.Origin.MANUAL : origin);
        rule.setEnabled(enabled);
        rule.setNote(note);
        return rule;
    }

    /**
     * 도메인 객체에서 영속 표현을 만듭니다.
     *
     * @param rule 원본
     * @return 저장용 엔티티
     */
    public static ProjectRule from(PolicyRule rule) {
        final ProjectRule entity = new ProjectRule();
        entity.setRuleId(rule.getId());
        entity.setSource(rule.getSource());
        entity.setDestination(rule.getDestination());
        entity.setPort(rule.hasPort() ? rule.getPort() : null);
        entity.setProtocol(rule.getProtocol());
        entity.setOrigin(rule.getOrigin());
        entity.setEnabled(rule.isEnabled());
        entity.setNote(rule.getNote());
        return entity;
    }
}
