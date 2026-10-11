package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 망분리 위반에 대한 <b>AI 정책 조언</b> 한 건입니다.
 *
 * <h2>{@link LogAnalysis} 와 무엇이 다른가</h2>
 * <p>로그 분석은 <b>장비가 무엇을 했나</b> 를 묻고(관측), 정책 조언은
 * <b>정책을 어떻게 고치나</b> 를 묻습니다(설계). 그래서 담는 값이 다릅니다.
 *
 * <table border="1">
 *   <caption>두 분석 기록의 차이</caption>
 *   <tr><th></th><th>{@code log_analysis}</th><th>{@code policy_advice}</th></tr>
 *   <tr><td>대상</td><td>로그 줄</td><td>위반 / 프로젝트</td></tr>
 *   <tr><td>핵심 필드</td><td>{@code summary}, {@code root_cause}</td>
 *       <td>{@code policy_advice}, <b>{@code options_json}</b></td></tr>
 *   <tr><td>선택지 비교</td><td>없음 (조치 나열)</td><td><b>있음 (트레이드오프)</b></td></tr>
 * </table>
 *
 * <h2>왜 별도 테이블인가 (로그 분석에 합치지 않은 이유)</h2>
 * <p>한 테이블에 두 종류를 담으면 <b>한쪽만 쓰는 컬럼이 절반</b>이 됩니다.
 * 조회 조건도 다릅니다 — 로그 분석은 기간/장비로, 정책 조언은 프로젝트/규칙으로
 * 찾습니다. 인덱스와 정렬 기준이 다르면 한 테이블에 두는 이점이 사라집니다.
 *
 * <h2>⚠️ 실패도 저장한다</h2>
 * <p>AI 호출이 실패해도(키 만료, 모델명 오타, 서버 미기동) 기록을 남깁니다.
 * 남기지 않으면 화면에 아무 흔적이 없어 운영자가 같은 실수를 반복합니다.
 * {@code succeeded=false} 와 {@code errorMessage} 로 사유를 남깁니다.
 *
 * <h2>⚠️ 조언은 "지금" 의 스냅샷이다</h2>
 * <p>{@code violation_count} 를 함께 저장하는 이유: 나중에 프로젝트가 바뀌어도
 * <b>"그때 몇 건을 보고 이 조언을 했나"</b> 를 되짚을 수 있어야 합니다.
 * 조언을 근거로 정책을 바꾸는 결정을 감사(audit)할 때 필요합니다.
 */
@Entity
@Table(
        name = "policy_advice",
        indexes = {
                // 프로젝트별 최신순 조회
                @Index(name = "idx_policy_advice_project", columnList = "project_key, created_at"),
                // 규칙별 조회 (위반 카드에서 "이 위반의 과거 조언")
                @Index(name = "idx_policy_advice_rule", columnList = "rule_id, created_at"),
        })
@Getter
@Setter
@NoArgsConstructor
public class PolicyAdvice {

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 외부 노출용 조언 식별자 (예: {@code PADV-1A2B3C4D}). */
    @Column(name = "advice_id", nullable = false, unique = true, length = 60)
    private String adviceId;

    /** 대상 프로젝트 키. */
    @Column(name = "project_key", nullable = false, length = 120)
    private String projectKey;

    /**
     * 대상 프로젝트 이름입니다.
     *
     * <p>키와 함께 저장하는 이유: 프로젝트가 삭제·개명돼도 조언 기록은 남아야
     * 합니다. FK 만 두면 프로젝트 삭제 시 조언이 함께 사라지거나 이름을 잃습니다.
     */
    @Column(name = "project_name", length = 200)
    private String projectName;

    /**
     * 조언의 초점이 된 규칙 식별자입니다.
     *
     * <p>위반 카드를 눌러 물었으면 그 규칙 ID 가 들어갑니다. 프로젝트 전체를
     * 물었으면 {@code null} 입니다.
     */
    @Column(name = "rule_id", length = 120)
    private String ruleId;

    /**
     * 조언 범위입니다.
     * <ul>
     *   <li>{@code violation} — 특정 위반 한 건 (카드를 눌러 물은 경우)</li>
     *   <li>{@code project} — 프로젝트 전체 (위반 현황 버튼)</li>
     * </ul>
     */
    @Column(name = "scope", length = 20)
    private String scope = "project";

    /** 조언 시점의 위반 건수 (스냅샷). */
    @Column(name = "violation_count")
    private Integer violationCount = 0;

    /** 조언 시점의 준수 여부 (스냅샷). */
    @Column(name = "compliant")
    private Boolean compliant = false;

    /** 프롬프트에 실제로 넣은 위반 건수. (잘림 여부 판단용) */
    @Column(name = "included_violation_count")
    private Integer includedViolationCount = 0;

    /** 위반이 잘려서 들어갔는지. true 면 화면이 반드시 알려야 합니다. */
    @Column(name = "truncated")
    private Boolean truncated = false;

    /** 사용한 AI 공급자 이름 (공급자가 삭제돼도 기록은 남깁니다). */
    @Column(name = "provider_name", length = 120)
    private String providerName;

    /** 사용한 모델 이름. */
    @Column(name = "model", length = 200)
    private String model;

    /** AI 호출에 걸린 시간(밀리초). 로컬 모델 성능 판단에 씁니다. */
    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    /** 모델이 판단한 위험도 ({@code CRITICAL}/{@code HIGH}/{@code MEDIUM}/{@code LOW}). */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    /** 한두 문장 요약 (구조화 파싱 결과). */
    @Column(name = "summary", length = 4000)
    private String summary;

    /** 근본 원인 판단 (구조화 파싱 결과). */
    @Column(name = "root_cause", length = 8000)
    private String rootCause;

    /** 정책 관점 핵심 조언 한 줄 (구조화 파싱 결과). */
    @Column(name = "policy_advice", length = 4000)
    private String policyAdvice;

    /**
     * 해결 선택지 목록 (JSON 배열 문자열).
     *
     * <p>각 항목은 {@code title/approach/security_impact/operational_cost/recommended}
     * 입니다. 이 필드가 로그 분석과의 핵심 차이입니다 — <b>트레이드오프를
     * 비교할 수 있어야</b> 운영자가 근거 있는 결정을 합니다.
     */
    @Column(name = "options_json", length = 8000)
    private String optionsJson;

    /** 판단 근거 인용 (JSON 배열 문자열). */
    @Column(name = "evidence_json", length = 4000)
    private String evidenceJson;

    /** 모델이 추가 자료를 요청했는지. */
    @Column(name = "needs_more_data")
    private Boolean needsMoreData = false;

    /** 모델의 전체 응답 원문. (파싱 실패 시에도 이것은 항상 남습니다) */
    @Column(name = "raw_response", length = 30000)
    private String rawResponse;

    /** 구조화 파싱 성공 여부. false 면 화면이 원문을 보여줍니다. */
    @Column(name = "structured")
    private Boolean structured = false;

    /** 분석을 실행한 사용자. */
    @Column(name = "requested_by", length = 200)
    private String requestedBy;

    /** 실패 사유. 성공이면 null. */
    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    /**
     * 성공 여부입니다.
     *
     * <p>{@code false} 인 레코드도 저장합니다. 실패를 남기지 않으면 화면이
     * "조언을 눌렀는데 아무 기록이 없다" 로 보여 운영자가 계속 재시도합니다.
     */
    @Column(name = "succeeded")
    private Boolean succeeded = true;

    /** 생성 시각. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "created_at", nullable = false)
    private Date createdAt;
}