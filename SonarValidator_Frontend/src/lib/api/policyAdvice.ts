import { apiRequest } from "./client";


// 타입

export interface ApiPolicyAdviceOption {
  title: string;
  approach: string;
  security_impact: string;
  operational_cost: string;
  recommended: boolean;
}

export interface ApiPolicyAdvice {
  advice_id: string;
  project_id: string;
  project_name: string | null;
  rule_id: string | null;
  scope: string;
  violation_count: number | null;
  compliant: boolean;
  included_violation_count: number | null;
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
  policy_advice: string | null;
  options: ApiPolicyAdviceOption[];
  evidence: string[];
  needs_more_data: boolean;
  structured: boolean;
  raw_response: string | null;
}

export interface ApiPolicyAdviceList {
  project_id: string;
  total: number;
  advices: ApiPolicyAdvice[];
}

export interface ApiPolicyAdviceRequest {
  rule_id?: string | null;
  src_subnet?: string | null;
  dst_subnet?: string | null;
  provider_id?: number | null;
  prompt?: string | null;
}

// API

export function requestPolicyAdvice(
  projectId: string,
  request: ApiPolicyAdviceRequest = {},
): Promise<ApiPolicyAdvice> {
  return apiRequest<ApiPolicyAdvice>(
    `/api/v1/policy/advice/${encodeURIComponent(projectId)}`,
    { method: "POST", body: request },
  );
}

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

export function getPolicyAdvice(
  projectId: string,
  adviceId: string,
): Promise<ApiPolicyAdvice> {
  return apiRequest<ApiPolicyAdvice>(
    `/api/v1/policy/advice/${encodeURIComponent(projectId)}/${encodeURIComponent(adviceId)}`,
  );
}

// 표시 유틸

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
