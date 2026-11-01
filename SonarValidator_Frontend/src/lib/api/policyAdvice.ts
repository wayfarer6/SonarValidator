import { errorLog } from "../errors";
import { apiRequest } from "./client";


// 타입

export interface ApiPolicyAdviceOption {
  title: string;
  approach: string;
  security_impact: string;
  operational_cost: string;
  recommended: boolean;
}

export interface ApiAdviceCommand {
  index: number; agent_id: string; title: string; reason: string; operation: string;
  arguments: Record<string, string | number>; suggested_commands: string[];
  execution_commands: string[]; verification_commands: string[];
  executable: boolean; blocked_reasons: string[]; changes_configuration: boolean; stale: boolean;
}
export interface ApiAdviceExecution {
  execution_id: string; project_id: string; advice_id: string; agent_id: string; proposal_index: number;
  operation: string; status: string; detail: string | null; requested_by: string; created_at: string; finished_at: string | null;
  result: { status: string; verified: boolean; changed: boolean; output: string; detail: string } | null;
}
export interface ApiPolicyAdvice {
  advice_id: string;
  command_proposals?: ApiAdviceCommand[];
  target_agent?: string | null;
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
  agent_id?: string | null;
}

// API

export function requestPolicyAdvice(
  projectId: string,
  request: ApiPolicyAdviceRequest = {},
): Promise<ApiPolicyAdvice> {
  return apiRequest<ApiPolicyAdvice>(
    `/api/v1/policy/advice/${encodeURIComponent(projectId)}`,
    { method: "POST", body: request, timeoutMs: 610_000 },
  ).then(result => {
    if (!result.succeeded) errorLog.record({ summary: "AI 진단을 완료하지 못했습니다.", details: result, method: "POST", path: `/api/v1/policy/advice/${encodeURIComponent(projectId)}`, status: 200 });
    return result;
  });
}

export function listPolicyAdvices(
  projectId: string,
  options: { ruleId?: string | null; limit?: number; agentId?: string | null } = {},
): Promise<ApiPolicyAdviceList> {
  return apiRequest<ApiPolicyAdviceList>(
    `/api/v1/policy/advice/${encodeURIComponent(projectId)}`,
    {
      params: {
        rule_id: options.ruleId,
        agent_id: options.agentId,
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

export function executeAdviceCommand(projectId: string, adviceId: string, index: number, requestId: string) {
  return apiRequest<ApiAdviceExecution>(`/api/v1/policy/advice/${encodeURIComponent(projectId)}/${encodeURIComponent(adviceId)}/executions`,
    {method: "POST", body: {proposal_index:index, request_id:requestId, confirmed:true}});
}
export async function listAdviceExecutions(projectId: string, adviceId: string) {
  return (await apiRequest<{executions: ApiAdviceExecution[]}>(`/api/v1/policy/advice/${encodeURIComponent(projectId)}/${encodeURIComponent(adviceId)}/executions`)).executions;
}
