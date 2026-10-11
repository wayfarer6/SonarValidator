import { apiRequest } from "./client";

/**
 * 망분리 위반에 대한 AI 정책 조언 API 입니다. (SONAR-43)
 *
 * <h2>화면 흐름</h2>
 * <ol>
 *   <li>위반 메시지 카드를 누르면 이 모듈의 {@link requestPolicyAdvice} 를
 *       호출합니다.</li>
 *   <li>같은 위반을 다시 물으면 <b>AI 호출 비용이 두 번</b> 나갑니다. 그래서
 *       먼저 {@link listPolicyAdvices} 로 <b>이력을 보여주고</b>, 운영자가
 *       "다시 물어보기" 를 눌렀을 때만 POST 합니다.</li>
 * </ol>
 *
 * <h2>⚠️ 실패도 200 으로 온다</h2>
 * <p>AI 호출 실패(키 만료·모델명 오타·서버 미기동)는 정상 응답에
 * {@code succeeded=false} 와 {@code error_message} 로 담겨 옵니다.
 * HTTP 상태만 보면 실패를 성공으로 오해하므로 반드시 확인해야 합니다.
 * ({@code analyzeLogs} 와 같은 규칙)
 */

// ---------------------------------------------------------------------------
// 타입
// ---------------------------------------------------------------------------

/**
 * AI 가 제시한 해결 <b>선택지</b> 한 가지.
 *
 * <p>"해결책"이 아니라 "선택지"인 이유: 망분리 위반의 해결은 하나로 정해지지
 * 않습니다. 등급 재분류(빠르지만 체계가 흔들림)와 중계 구간 신설(안전하지만
 * 비용)이 모두 유효하므로, 운영자가 <b>트레이드오프를 보고</b> 고르려면
 * 각 안의 보안 영향과 운영 부담이 함께 있어야 합니다.
 */
export interface ApiPolicyAdviceOption {
  title: string;
  approach: string;
  security_impact: string;
  operational_cost: string;
  recommended: boolean;
}

/** AI 정책 조언 한 건. */
export interface ApiPolicyAdvice {
  advice_id: string;
  project_id: string;
  project_name: string | null;
  /** 조언의 초점이 된 규칙. 프로젝트 전체를 물었으면 null. */
  rule_id: string | null;
  /** `violation` (특정 위반) 또는 `project` (전체). */
  scope: string;
  /** 조언 시점의 위반 건수 (스냅샷). */
  violation_count: number | null;
  compliant: boolean;
  /** 프롬프트에 실제로 넣은 위반 건수. */
  included_violation_count: number | null;
  /** 위반이 잘려서 들어갔는지. true 면 화면이 반드시 알려야 합니다. */
  truncated: boolean;
  provider_name: string | null;
  model: string | null;
  elapsed_ms: number | null;
  requested_by: string | null;
  succeeded: boolean;
  error_message: string | null;
  created_at: string;
  risk_level: string | null;
  summary: string | null;
  root_cause: string | null;
  /** 정책 관점 핵심 조언 (한 줄). */
  policy_advice: string | null;
  options: ApiPolicyAdviceOption[];
  evidence: string[];
  needs_more_data: boolean;
  /** 구조화 파싱 성공 여부. false 면 원문만 있습니다. */
  structured: boolean;
  raw_response: string | null;
}

export interface ApiPolicyAdviceList {
  project_id: string;
  total: number;
  advices: ApiPolicyAdvice[];
}

/** 조언 요청 본문. */
export interface ApiPolicyAdviceRequest {
  /** 초점 위반의 규칙 식별자. 없으면 프로젝트 전체를 묻습니다. */
  rule_id?: string | null;
  /** 같은 규칙이 여러 쌍을 위반할 때 구분용. */
  src_subnet?: string | null;
  dst_subnet?: string | null;
  /** 사용할 AI 공급자. 없으면 서버가 기본 공급자를 씁니다. */
  provider_id?: number | null;
  /** 사용자 추가 질문. */
  prompt?: string | null;
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

/**
 * 위반에 대한 정책 조언을 요청합니다.
 *
 * <p>⚠️ POST 인 이유: 같은 요청을 두 번 보내면 AI 호출이 두 번 일어나 비용이
 * 두 번 나갑니다. PUT 의 "여러 번 보내도 같은 결과" 약속이 이 동작에는 맞지
 * 않습니다. 대신 {@link listPolicyAdvices} 로 재사용할 수 있게 했습니다.
 *
 * @param projectId 프로젝트 키
 * @param request   요청 (rule_id 등)
 */
export function requestPolicyAdvice(
  projectId: string,
  request: ApiPolicyAdviceRequest = {},
): Promise<ApiPolicyAdvice> {
  return apiRequest<ApiPolicyAdvice>(
    `/api/v1/policy/advice/${encodeURIComponent(projectId)}`,
    { method: "POST", body: request },
  );
}

/**
 * 조언 이력을 조회합니다.
 *
 * <p>카드를 열 때 먼저 부르면, AI 를 다시 호출하지 않고도 지난 조언을
 * 보여줄 수 있습니다.
 *
 * @param projectId 프로젝트 키
 * @param options   규칙 필터 / 건수
 */
export function listPolicyAdvices(
  projectId: string,
  options: { ruleId?: string | null; limit?: number } = {},
): Promise<ApiPolicyAdviceList> {
  return apiRequest<ApiPolicyAdviceList>(
    `/api/v1/policy/advice/${encodeURIComponent(projectId)}`,
    {
      params: {
        rule_id: options.ruleId,
        limit: options.limit,
      },
    },
  );
}

/** 조언 한 건의 상세를 조회합니다. */
export function getPolicyAdvice(
  projectId: string,
  adviceId: string,
): Promise<ApiPolicyAdvice> {
  return apiRequest<ApiPolicyAdvice>(
    `/api/v1/policy/advice/${encodeURIComponent(projectId)}/${encodeURIComponent(adviceId)}`,
  );
}

// ---------------------------------------------------------------------------
// 표시 유틸
// ---------------------------------------------------------------------------

/**
 * 위험도에 맞는 Badge 색상입니다.
 *
 * <p>{@code aiLogs.ts} 의 {@code riskColor} 와 같은 규칙을 씁니다 —
 * 두 화면이 같은 위험도를 다른 색으로 보여주면 운영자가 혼동합니다.
 */
export function adviceRiskColor(
  risk: string | null,
): "error" | "warning" | "success" | "light" {
  switch (risk) {
    case "CRITICAL":
      return "error";
    case "HIGH":
      return "warning";
    case "MEDIUM":
      return "warning";
    case "LOW":
      return "success";
    default:
      return "light";
  }
}