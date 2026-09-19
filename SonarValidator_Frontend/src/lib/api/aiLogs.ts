import { apiRequest } from "./client";

/**
 * AI 공급자 설정과 로그 분석 API 입니다.
 *
 * <h2>API Key 를 어떻게 다루는가</h2>
 * <p>서버는 키를 <b>암호화해 저장</b>하고 조회 시 <b>마스킹된 값만</b> 돌려줍니다.
 * 프론트도 같은 원칙을 지킵니다.
 * <ul>
 *   <li>수정 화면은 키를 <b>표시하지 않습니다</b> ({@code has_api_key} 로 존재만 확인)</li>
 *   <li>키를 비워 저장하면 서버가 <b>기존 값을 유지</b>합니다</li>
 *   <li>키를 상태로 오래 들고 있지 않습니다 (저장 후 즉시 비움)</li>
 * </ul>
 */

// ---------------------------------------------------------------------------
// 타입
// ---------------------------------------------------------------------------

/** AI 공급자 한 건. */
export interface ApiAiProvider {
  id: number;
  name: string;
  base_url: string;
  model: string;
  auth_style: string;
  system_prompt: string | null;
  timeout_seconds: number | null;
  max_tokens: number | null;
  temperature: number | null;
  allow_insecure_tls: boolean;
  is_default: boolean;
  enabled: boolean;
  /** 키가 저장돼 있는지. 원문은 서버가 보내지 않습니다. */
  has_api_key: boolean;
  /** 표시용 마스킹 문자열. */
  api_key_masked: string;
  last_status: string | null;
  last_message: string | null;
  last_checked_at: string | null;
  created_at: string | null;
  updated_at: string | null;
}

export interface ApiAiProviderList {
  total: number;
  providers: ApiAiProvider[];
  default_base_url?: string;
}

/** 공급자 저장 요청. */
export interface ApiAiProviderInput {
  id?: number;
  name: string;
  base_url: string;
  /** 비우면 서버가 기존 키를 유지합니다. */
  api_key?: string;
  model: string;
  auth_style?: string;
  system_prompt?: string;
  timeout_seconds?: number;
  max_tokens?: number;
  temperature?: number;
  allow_insecure_tls?: boolean;
  is_default?: boolean;
  enabled?: boolean;
}

export interface ApiAiCheckResult {
  ok: boolean;
  message: string;
  elapsed_ms: number;
  provider_id: number;
  provider_name: string;
}

/** 로그 한 줄. */
export interface ApiDeviceLog {
  id: number;
  agent_id: string;
  project_id: string | null;
  product: string | null;
  logged_at: string;
  collected_at: string | null;
  severity: string;
  severity_num: number;
  facility: string | null;
  message_id: string | null;
  message: string | null;
  raw: string;
  source: string;
  repeat_count: number | null;
  highlighted: boolean;
  note: string | null;
}

/** 로그 조회 필터. */
export interface ApiLogFilters {
  agent_id: string | null;
  project_id: string | null;
  from: string | null;
  to: string | null;
  severity: string | null;
  max_severity_num: number | null;
  search: string | null;
  highlighted_only: boolean;
}

export interface ApiLogList {
  total: number;
  returned: number;
  /** 상한에 걸려 잘렸는지. true 면 화면이 반드시 알려야 합니다. */
  truncated: boolean;
  limit: number;
  filters: ApiLogFilters;
  logs: ApiDeviceLog[];
}

export interface ApiLogFilterOptions {
  agents: string[];
  severities: { name: string; num: number }[];
  total: number;
}

/** AI 분석 결과. */
export interface ApiLogAnalysis {
  analysis_id: string;
  project_id: string | null;
  agent_id: string | null;
  scope: string;
  severity_filter: string | null;
  period_from: string | null;
  period_to: string | null;
  provider_name: string | null;
  model: string | null;
  succeeded: boolean;
  error_message: string | null;
  risk_level: string | null;
  summary: string | null;
  root_cause: string | null;
  recommendations: string[];
  included_log_count: number | null;
  total_log_count: number | null;
  truncated: boolean;
  log_ids: string[];
  elapsed_ms: number | null;
  requested_by: string | null;
  created_at: string;
  /** 구조화 파싱에 성공했는지. false 면 원문만 있습니다. */
  structured: boolean;
  raw_response: string | null;
}

export interface ApiLogAnalysisList {
  total: number;
  analyses: ApiLogAnalysis[];
}

// ---------------------------------------------------------------------------
// AI 공급자
// ---------------------------------------------------------------------------

/** AI 공급자 목록을 조회합니다. */
export function listAiProviders(): Promise<ApiAiProviderList> {
  return apiRequest<ApiAiProviderList>("/api/v1/ai/providers");
}

/** 사용 중인 AI 공급자만 조회합니다. (분석 대상 선택용) */
export function listEnabledAiProviders(): Promise<ApiAiProviderList> {
  return apiRequest<ApiAiProviderList>("/api/v1/ai/providers/enabled");
}

/** AI 공급자를 생성하거나 수정합니다. */
export function saveAiProvider(
  input: ApiAiProviderInput,
): Promise<ApiAiProvider> {
  return apiRequest<ApiAiProvider>("/api/v1/ai/providers", {
    method: "POST",
    body: input,
  });
}

/** AI 공급자를 삭제합니다. */
export function deleteAiProvider(id: number): Promise<{ deleted: boolean }> {
  return apiRequest<{ deleted: boolean }>(`/api/v1/ai/providers/${id}`, {
    method: "DELETE",
  });
}

/** AI 공급자 연결을 확인합니다. */
export function checkAiProvider(id: number): Promise<ApiAiCheckResult> {
  return apiRequest<ApiAiCheckResult>(`/api/v1/ai/providers/${id}/check`, {
    method: "POST",
  });
}

/** AI 공급자 사용 여부를 바꿉니다. */
export function setAiProviderEnabled(
  id: number,
  enabled: boolean,
): Promise<ApiAiProvider> {
  return apiRequest<ApiAiProvider>(`/api/v1/ai/providers/${id}/enabled`, {
    method: "POST",
    params: { enabled },
  });
}

/** 기본 AI 공급자로 지정합니다. */
export function setDefaultAiProvider(id: number): Promise<ApiAiProvider> {
  return apiRequest<ApiAiProvider>(`/api/v1/ai/providers/${id}/default`, {
    method: "POST",
  });
}

// ---------------------------------------------------------------------------
// 로그 조회
// ---------------------------------------------------------------------------

/** 로그 조회 옵션입니다. 모두 선택입니다. */
export interface ApiLogQuery {
  agentId?: string | null;
  projectId?: string | null;
  /** ISO-8601 (예: 2026-09-19T00:00:00Z) */
  from?: string | null;
  to?: string | null;
  /** 최소 심각도 이름 (warning 이면 warning 이상) */
  severity?: string | null;
  search?: string | null;
  highlightedOnly?: boolean;
  limit?: number;
}

/**
 * 로그를 조회합니다.
 *
 * <p>필터를 하나의 함수로 통합한 이유: 축을 각각 별도 API 로 만들면
 * 조합(프로젝트 + 기간 + 장비)을 표현할 수 없습니다.
 */
export function listLogs(query: ApiLogQuery = {}): Promise<ApiLogList> {
  return apiRequest<ApiLogList>("/api/v1/logs", {
    params: {
      agent_id: query.agentId,
      project_id: query.projectId,
      from: query.from,
      to: query.to,
      severity: query.severity,
      search: query.search,
      highlighted_only: query.highlightedOnly ? true : undefined,
      limit: query.limit,
    },
  });
}

/** 필터 드롭다운 선택지를 조회합니다. */
export function getLogFilterOptions(): Promise<ApiLogFilterOptions> {
  return apiRequest<ApiLogFilterOptions>("/api/v1/logs/filters");
}

/** 심각도별 집계를 조회합니다. */
export function getLogSummary(): Promise<{
  summary: { severity: string; num: number; count: number }[];
}> {
  return apiRequest("/api/v1/logs/summary");
}

/** 로그를 적재합니다. (직접 입력/붙여넣기) */
export function ingestLogs(input: {
  agent_id: string;
  product?: string;
  project_id?: string;
  source?: string;
  lines?: string[];
  text?: string;
}): Promise<{
  received: number;
  inserted: number;
  duplicated: number;
  skipped: number;
}> {
  return apiRequest("/api/v1/logs/ingest", { method: "POST", body: input });
}

/** 로그의 표시/메모를 수정합니다. */
export function updateLogFlags(
  id: number,
  flags: { highlighted?: boolean; note?: string },
): Promise<ApiDeviceLog> {
  return apiRequest<ApiDeviceLog>(`/api/v1/logs/${id}/flags`, {
    method: "POST",
    body: flags,
  });
}

// ---------------------------------------------------------------------------
// AI 분석
// ---------------------------------------------------------------------------

/** 분석 요청입니다. */
export interface ApiAnalyzeRequest {
  /** 특정 로그만 분석할 때. 비우면 필터 결과 전체를 분석합니다. */
  log_ids?: number[];
  agent_id?: string | null;
  project_id?: string | null;
  from?: string | null;
  to?: string | null;
  /** 최소 심각도 (warning 이면 warning 이상만) */
  severity?: string | null;
  scope?: "selected" | "filter" | "single";
  provider_id?: number | null;
  /** 사용자 추가 질문/지시 */
  prompt?: string | null;
}

/**
 * AI 로그 분석을 실행합니다.
 *
 * <p>⚠️ 분석 실패도 <b>200</b> 으로 돌아오고 {@code succeeded=false} 와
 * {@code error_message} 가 담깁니다. 화면은 이 값을 반드시 확인해야 합니다.
 * (HTTP 상태만 보면 실패를 성공으로 오해합니다)
 */
export function analyzeLogs(
  request: ApiAnalyzeRequest,
): Promise<ApiLogAnalysis> {
  return apiRequest<ApiLogAnalysis>("/api/v1/logs/analyze", {
    method: "POST",
    body: request,
  });
}

/** 분석 이력을 조회합니다. */
export function listLogAnalyses(options?: {
  projectId?: string | null;
  agentId?: string | null;
  limit?: number;
}): Promise<ApiLogAnalysisList> {
  return apiRequest<ApiLogAnalysisList>("/api/v1/logs/analyses", {
    params: {
      project_id: options?.projectId,
      agent_id: options?.agentId,
      limit: options?.limit,
    },
  });
}

/** 분석 한 건의 상세를 조회합니다. */
export function getLogAnalysis(analysisId: string): Promise<ApiLogAnalysis> {
  return apiRequest<ApiLogAnalysis>(
    `/api/v1/logs/analyses/${encodeURIComponent(analysisId)}`,
  );
}

// ---------------------------------------------------------------------------
// 표시 유틸
// ---------------------------------------------------------------------------

/** 심각도에 맞는 Badge 색상입니다. */
export function severityColor(
  severity: string | null,
): "error" | "warning" | "info" | "light" {
  switch (severity) {
    case "emergency":
    case "alert":
    case "critical":
    case "error":
      return "error";
    case "warning":
      return "warning";
    case "notice":
      return "info";
    default:
      return "light";
  }
}

/** 위험도에 맞는 Badge 색상입니다. */
export function riskColor(
  risk: string | null,
): "error" | "warning" | "success" | "light" {
  switch (risk) {
    case "CRITICAL":
      return "error";
    case "HIGH":
      return "warning";
    case "MEDIUM":
      return "info" as "warning";
    case "LOW":
      return "success";
    default:
      return "light";
  }
}
