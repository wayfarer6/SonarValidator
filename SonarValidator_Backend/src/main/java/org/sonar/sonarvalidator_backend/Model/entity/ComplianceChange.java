package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 네트워크 설정 변경 이력 한 건입니다.
 *
 * <h2>왜 필요한가</h2>
 * <p>정책 검증은 "지금 올바른가" 만 답합니다. 감사(audit) 관점에서는
 * "언제 누가 무엇을 바꿨나" 가 별도로 필요합니다. 프론트엔드
 * {@code Compliance.tsx} 가 쓰던 더미 데이터를 실제로 남기기 위해
 * 이력을 별도 테이블로 분리했습니다.
 *
 * <h2>변경 대상을 어떻게 표현하는가</h2>
 * <p>변경은 <b>프로젝트 단위</b> 일 수도 있고 <b>장치 단위</b> 일 수도 있습니다.
 * 그래서 {@code projectKey} 는 항상 채우고, 장치 단위 변경이면 {@code agentId}
 * 를 추가로 채웁니다. 조회는 두 축으로 각각 가능합니다.
 *
 * <p>변경 내용 자체는 요약 문자열 + 선택적 JSON 본문으로 둡니다. 스키마를
 * 고정하면 새 변경 유형이 생길 때마다 마이그레이션이 필요해지기 때문입니다.
 */
@Entity
@Table(name = "compliance_change")
@Getter
@Setter
@NoArgsConstructor
public class ComplianceChange {

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 변경 식별자 (예: {@code CHG-3001}). 외부 노출용입니다. */
    @Column(name = "change_id", nullable = false, unique = true, length = 80)
    private String changeId;

    /** 변경 범위 ({@code Project} 또는 {@code Agent}). */
    @Column(length = 20)
    private String scope;

    /** 대상 프로젝트 키. */
    @Column(name = "project_key", length = 120)
    private String projectKey;

    /** 대상 장치 식별자. 프로젝트 단위 변경이면 null. */
    @Column(name = "agent_id", length = 120)
    private String agentId;

    /** 변경 유형 (예: {@code Policy Update}, {@code Topology Change}). */
    @Column(name = "change_type", length = 60)
    private String type;

    /** 사람이 읽는 요약. */
    @Column(length = 1000)
    private String summary;

    /** 변경 주체 (이메일 또는 {@code system}). */
    @Column(name = "changed_by", length = 200)
    private String changedBy;

    /** 변경 시각. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "timestamp")
    private Date timestamp;

    /** 적용 상태 ({@code Applied} / {@code Pending} / {@code Rejected}). */
    @Column(length = 20)
    private String status;

    /**
     * 변경 전후 비교용 JSON 본문. (선택)
     *
     * <p>{@code @Lob} 대신 {@code length} 를 크게 준 TEXT 로 둡니다.
     * H2 와 PostgreSQL 모두에서 동작하게 하기 위함입니다.
     */
    @Column(length = 20000)
    private String detail;
}
