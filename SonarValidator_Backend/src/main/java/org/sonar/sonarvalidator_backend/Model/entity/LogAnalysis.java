package org.sonar.sonarvalidator_backend.Model.entity;

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
 * AI 로그 분석 결과 한 건입니다.
 *
 * <h2>왜 결과를 저장하는가</h2>
 * <p>AI 호출은 <b>돈과 시간이 듭니다.</b> 같은 로그를 다시 분석하면
 * 비용을 두 번 내고, 로컬 모델이면 수십 초를 다시 기다립니다. 그래서 결과를
 * 남기고 화면에서 과거 분석을 바로 다시 보여줍니다.
 *
 * <p>추가로 감사(audit) 목적도 있습니다. "언제 어떤 로그를 어떤 모델로
 * 분석했고 무엇이라고 판단했나" 는 보안 사고 조사에서 필요해집니다.
 *
 * <h2>입력 범위를 어떻게 기록하는가</h2>
 * <p>분석은 <b>필터 결과 전체</b>일 수도 있고 <b>선택한 몇 줄</b>일 수도 있습니다.
 * 그래서 두 가지를 모두 남깁니다.
 * <ul>
 *   <li>{@code filterJson} — 분석 시점에 적용한 필터 (재현용)</li>
 *   <li>{@code logIdsJson} — 실제로 프롬프트에 넣은 로그 ID 목록</li>
 * </ul>
 * <p>나중에 "이 결론이 어떤 근거였나" 를 정확히 되짚을 수 있습니다.
 *
 * <h2>⚠️ 응답을 구조화해 저장하는 이유</h2>
 * <p>모델은 자유 형식 텍스트를 돌려줍니다. 그대로 저장하면 화면에서 요약/원인/
 * 권장조치를 구분해 보여줄 수 없습니다. 그래서 <b>JSON 응답을 요구</b>하고,
 * 파싱에 성공하면 구조화된 필드({@code summary}/{@code rootCause}/{@code severity})를
 * 채우고, 실패하면 원문만 남깁니다.
 *
 * <p>파싱 실패를 오류로 처리하지 않는 이유: 모델이 코드블록으로 감싸거나
 * 앞뒤에 설명을 붙이는 일이 흔합니다. 그때 분석 자체를 버리면 사용자는
 * "분석 실패" 만 보게 됩니다. 원문이라도 보여 주는 편이 낫습니다.
 */
@Entity
@Table(
        name = "log_analysis",
        indexes = {
                // 프로젝트별 최신순 조회
                @Index(name = "idx_log_analysis_project", columnList = "project_key, created_at"),
                // 장비별 최신순 조회
                @Index(name = "idx_log_analysis_agent", columnList = "agent_id, created_at"),
        })
@Getter
@Setter
@NoArgsConstructor
public class LogAnalysis {

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 외부 노출용 분석 식별자 (예: {@code AIA-1A2B3C4D}). */
    @Column(name = "analysis_id", nullable = false, unique = true, length = 60)
    private String analysisId;

    /** 분석 대상 프로젝트 키. (필터에 있었으면 채움) */
    @Column(name = "project_key", length = 120)
    private String projectKey;

    /** 분석 대상 장비 식별자. (여러 장비면 null — {@code scope} 참고) */
    @Column(name = "agent_id", length = 120)
    private String agentId;

    /**
     * 분석 범위입니다.
     * <ul>
     *   <li>{@code selected} — 사용자가 고른 로그만</li>
     *   <li>{@code filter} — 필터 결과 전체</li>
     *   <li>{@code single} — 로그 한 줄</li>
     * </ul>
     */
    @Column(name = "scope", length = 20)
    private String scope = "filter";

    /** 분석에 사용한 등급 필터 (예: {@code warning}). */
    @Column(name = "severity_filter", length = 20)
    private String severityFilter;

    /** 분석에 사용한 기간 시작 (ISO-8601). */
    @Column(name = "period_from", length = 40)
    private String periodFrom;

    /** 분석에 사용한 기간 끝 (ISO-8601). */
    @Column(name = "period_to", length = 40)
    private String periodTo;

    /** 필터 전체 조건 (재현용 JSON). */
    @Column(name = "filter_json", length = 4000)
    private String filterJson;

    /** 실제 프롬프트에 넣은 로그 ID 목록 (JSON 배열 문자열). */
    @Column(name = "log_ids_json", length = 4000)
    private String logIdsJson;

    /** 프롬프트에 실제로 전달한 로그 줄 수. (잘림 여부 판단용) */
    @Column(name = "included_log_count")
    private Integer includedLogCount = 0;

    /** 필터에 걸린 전체 로그 수. (전달 수와 다르면 잘렸다는 뜻) */
    @Column(name = "total_log_count")
    private Integer totalLogCount = 0;

    /** 사용한 AI 공급자 이름 (공급자가 삭제돼도 기록은 남깁니다). */
    @Column(name = "provider_name", length = 120)
    private String providerName;

    /** 사용한 모델 이름. */
    @Column(name = "model", length = 200)
    private String model;

    /** 분석에 걸린 시간(밀리초). 로컬 모델 성능 판단에 씁니다. */
    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    /**
     * 모델이 판단한 전체 위험도 ({@code CRITICAL}/{@code HIGH}/{@code MEDIUM}/{@code LOW}).
     *
     * <p>구조화 파싱에 성공했을 때만 채워집니다. 화면의 색상 배지가 이 값을
     * 씁니다. 파싱 실패면 null 이고, 화면은 원문만 보여줍니다.
     */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    /** 한 줄 요약 (구조화 파싱 결과). */
    @Column(name = "summary", length = 4000)
    private String summary;

    /** 추정 원인 (구조화 파싱 결과). */
    @Column(name = "root_cause", length = 8000)
    private String rootCause;

    /** 권장 조치 (구조화 파싱 결과, JSON 배열 문자열). */
    @Column(name = "recommendations_json", length = 8000)
    private String recommendationsJson;

    /** 모델의 전체 응답 원문. (파싱 실패 시에도 이것은 항상 남습니다) */
    @Column(name = "raw_response", length = 30000)
    private String rawResponse;

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
     * "분석을 눌렀는데 아무 기록이 없다" 로 보여 사용자가 계속 재시도합니다.
     */
    @Column(name = "succeeded")
    private Boolean succeeded = true;

    /** 생성 시각 (ISO-8601). */
    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;
}
