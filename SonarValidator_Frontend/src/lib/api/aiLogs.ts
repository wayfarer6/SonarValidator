import { errorLog } from "../errors";
import { apiRequest } from "./client";


// 타입

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
  has_api_key: boolean;
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

export interface ApiAiProviderInput {
  id?: number;
  name: string;
  base_url: string;
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
  structured: boolean;
  raw_response: string | null;
}

export interface ApiLogAnalysisList {
  total: number;
  analyses: ApiLogAnalysis[];
}

// AI 공급자

export function listAiProviders(): Promise<ApiAiProviderList> {
  return apiRequest<ApiAiProviderList>("/api/v1/ai/providers");
}

export function listEnabledAiProviders(): Promise<ApiAiProviderList> {
  return apiRequest<ApiAiProviderList>("/api/v1/ai/providers/enabled");
}

export function saveAiProvider(
  input: ApiAiProviderInput,
): Promise<ApiAiProvider> {
  return apiRequest<ApiAiProvider>("/api/v1/ai/providers", {
    method: "POST",
    body: input,
  });
}

export function deleteAiProvider(id: number): Promise<{ deleted: boolean }> {
  return apiRequest<{ deleted: boolean }>(`/api/v1/ai/providers/${id}`, {
    method: "DELETE",
  });
}

export function checkAiProvider(id: number): Promise<ApiAiCheckResult> {
  const path = `/api/v1/ai/providers/${id}/check`;
  return apiRequest<ApiAiCheckResult>(path, { method: "POST" }).then(result => {
    if (!result.ok) errorLog.record({ summary: "AI 공급자 연결에 실패했습니다.", details: result, method: "POST", path, status: 200 });
    return result;
  });
}

export function setAiProviderEnabled(
  id: number,
  enabled: boolean,
): Promise<ApiAiProvider> {
  return apiRequest<ApiAiProvider>(`/api/v1/ai/providers/${id}/enabled`, {
    method: "POST",
    params: { enabled },
  });
}

export function setDefaultAiProvider(id: number): Promise<ApiAiProvider> {
  return apiRequest<ApiAiProvider>(`/api/v1/ai/providers/${id}/default`, {
    method: "POST",
  });
}

// 로그 조회

export interface ApiLogQuery {
  agentId?: string | null;
  projectId?: string | null;
  from?: string | null;
  to?: string | null;
  severity?: string | null;
  search?: string | null;
  highlightedOnly?: boolean;
  limit?: number;
}

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

export function getLogFilterOptions(): Promise<ApiLogFilterOptions> {
  return apiRequest<ApiLogFilterOptions>("/api/v1/logs/filters");
}

export function getLogSummary(): Promise<{
  summary: { severity: string; num: number; count: number }[];
}> {
  return apiRequest("/api/v1/logs/summary");
}

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

export function updateLogFlags(
  id: number,
  flags: { highlighted?: boolean; note?: string },
): Promise<ApiDeviceLog> {
  return apiRequest<ApiDeviceLog>(`/api/v1/logs/${id}/flags`, {
    method: "POST",
    body: flags,
  });
}

// AI 분석

export interface ApiAnalyzeRequest {
  log_ids?: number[];
  agent_id?: string | null;
  project_id?: string | null;
  from?: string | null;
  to?: string | null;
  severity?: string | null;
  scope?: "selected" | "filter" | "single";
  provider_id?: number | null;
  prompt?: string | null;
}

export function analyzeLogs(
  request: ApiAnalyzeRequest,
): Promise<ApiLogAnalysis> {
  return apiRequest<ApiLogAnalysis>("/api/v1/logs/analyze", { method: "POST", body: request }).then(result => {
    if (!result.succeeded) errorLog.record({ summary: "AI 로그 분석에 실패했습니다.", details: result, method: "POST", path: "/api/v1/logs/analyze", status: 200 });
    return result;
  });
}

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

export function getLogAnalysis(analysisId: string): Promise<ApiLogAnalysis> {
  return apiRequest<ApiLogAnalysis>(
    `/api/v1/logs/analyses/${encodeURIComponent(analysisId)}`,
  );
}

// 표시 유틸

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
