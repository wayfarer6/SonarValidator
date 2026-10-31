import { apiRequest } from "./client";
import type {
  ApiForbiddenPairs,
  ApiProject,
  ApiProjectList,
  ApiRule,
  ApiSubnet,
  ApiValidationReport,
  ApiViolation,
  SubnetClass,
} from "./types";


export interface SubnetInput {
  network_role?: import("./types").NetworkRole | null;
  vlan_id?: number | null;
  id: string;
  cidr: string;
  subnet_class: SubnetClass | null;
  name?: string | null;
  agent_id?: string | null;
  manually_edited?: boolean;
  allowed_peers?: string[];
}

export interface RuleInput {
  id: string;
  src: string | null;
  dst: string | null;
  port: number | null;
  protocol: string | null;
  origin?: string;
  enabled?: boolean;
  note?: string | null;
}

export function listProjects(): Promise<ApiProjectList> {
  return apiRequest<ApiProjectList>("/api/v1/projects");
}

export function getProject(projectId: string): Promise<ApiProject> {
  return apiRequest<ApiProject>(`/api/v1/projects/${encodeURIComponent(projectId)}`);
}

export function createProject(input: {
  project_id?: string | null;
  name?: string;
  category?: string;
  description?: string;
  status?: string;
}): Promise<ApiProject> {
  return apiRequest<ApiProject>("/api/v1/projects", { method: "POST", body: input });
}

export function updateProject(
  projectId: string,
  input: {
    name?: string;
    category?: string;
    description?: string;
    status?: string;
    management_server_ip?: string | null;
    management_server_port?: number | null;
    subnets?: SubnetInput[];
    rules?: RuleInput[];
  },
): Promise<ApiProject> {
  return apiRequest<ApiProject>(`/api/v1/projects/${encodeURIComponent(projectId)}`, {
    method: "PUT",
    body: input,
  });
}

export function deleteProject(projectId: string): Promise<{ deleted: boolean }> {
  return apiRequest<{ deleted: boolean }>(
    `/api/v1/projects/${encodeURIComponent(projectId)}`,
    { method: "DELETE" },
  );
}

export function validateProject(projectId: string): Promise<ApiValidationReport> {
  return apiRequest<ApiValidationReport>(
    `/api/v1/projects/${encodeURIComponent(projectId)}/validation`,
  );
}

export function validateDraft(input: {
  subnets: SubnetInput[];
  rules: RuleInput[];
}): Promise<ApiValidationReport> {
  return apiRequest<ApiValidationReport>("/api/v1/projects/draft/validation", {
    method: "POST",
    body: input,
  });
}

export function getForbiddenPairs(projectId: string): Promise<ApiForbiddenPairs> {
  return apiRequest<ApiForbiddenPairs>(
    `/api/v1/projects/${encodeURIComponent(projectId)}/forbidden-pairs`,
  );
}

export function toSubnetInput(subnet: ApiSubnet): SubnetInput {
  return {
    id: subnet.id,
    cidr: subnet.cidr,
    subnet_class: subnet.subnet_class,
    name: subnet.name,
    agent_id: subnet.agent_id,
    manually_edited: subnet.manually_edited,
    allowed_peers: subnet.allowed_peers ?? [],
  };
}

export function toRuleInput(rule: ApiRule): RuleInput {
  return {
    id: rule.id,
    src: rule.src,
    dst: rule.dst,
    port: rule.port,
    protocol: rule.protocol,
    origin: rule.origin,
    enabled: rule.enabled,
    note: rule.note,
  };
}

export function violationsByRule(violations: ApiViolation[]): Map<string, ApiViolation[]> {
  const grouped = new Map<string, ApiViolation[]>();
  for (const violation of violations) {
    const existing = grouped.get(violation.rule_id);
    if (existing) existing.push(violation);
    else grouped.set(violation.rule_id, [violation]);
  }
  return grouped;
}
