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

/**
 * 프로젝트 편집 기능의 API 모듈입니다.
 *
 * <p>화면은 이 모듈만 알면 되고, 경로 문자열은 여기에만 있습니다.
 * 서버 경로가 바뀌면 이 파일만 고치면 됩니다.
 */

/** 서버로 보낼 서브넷 본문입니다. */
export interface SubnetInput {
  id: string;
  cidr: string;
  subnet_class: SubnetClass | null;
  name?: string | null;
  agent_id?: string | null;
  manually_edited?: boolean;
}

/** 서버로 보낼 규칙 본문입니다. */
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

/** 프로젝트 목록을 조회합니다. */
export function listProjects(): Promise<ApiProjectList> {
  return apiRequest<ApiProjectList>("/api/v1/projects");
}

/**
 * 프로젝트 1건을 조회합니다.
 *
 * @param projectId 프로젝트 키
 */
export function getProject(projectId: string): Promise<ApiProject> {
  return apiRequest<ApiProject>(`/api/v1/projects/${encodeURIComponent(projectId)}`);
}

/**
 * 프로젝트를 생성합니다.
 *
 * <p>projectId 를 넘기면 그 값을 키로 쓰려고 시도하고, 중복이면 서버가
 * 새로 발급합니다. 응답의 {@code project_id} 를 이후 요청에 사용하세요.
 */
export function createProject(input: {
  project_id?: string | null;
  name?: string;
  category?: string;
  description?: string;
  status?: string;
}): Promise<ApiProject> {
  return apiRequest<ApiProject>("/api/v1/projects", { method: "POST", body: input });
}

/**
 * 프로젝트 메타데이터와 정책을 저장합니다.
 *
 * <p>subnets/rules 를 넘기면 기존 목록을 <b>전부 교체</b> 합니다. 편집기가
 * 화면 상태를 통째로 보내는 흐름과 일치합니다.
 */
export function updateProject(
  projectId: string,
  input: {
    name?: string;
    category?: string;
    description?: string;
    status?: string;
    subnets?: SubnetInput[];
    rules?: RuleInput[];
  },
): Promise<ApiProject> {
  return apiRequest<ApiProject>(`/api/v1/projects/${encodeURIComponent(projectId)}`, {
    method: "PUT",
    body: input,
  });
}

/** 프로젝트를 삭제합니다. */
export function deleteProject(projectId: string): Promise<{ deleted: boolean }> {
  return apiRequest<{ deleted: boolean }>(
    `/api/v1/projects/${encodeURIComponent(projectId)}`,
    { method: "DELETE" },
  );
}

/**
 * 저장된 프로젝트를 검증합니다.
 *
 * <p>서버에 저장된 최신 상태를 기준으로 판정하므로, 편집 내용을 저장한 뒤
 * 호출해야 결과가 일치합니다.
 */
export function validateProject(projectId: string): Promise<ApiValidationReport> {
  return apiRequest<ApiValidationReport>(
    `/api/v1/projects/${encodeURIComponent(projectId)}/validation`,
  );
}

/**
 * 저장하지 않고 전달한 정책만 검증합니다.
 *
 * <p>"저장 전 미리보기" 용도입니다. 서버가 최종 판정을 하므로 프론트엔드
 * 자체 검증과 결과가 다를 수 있고, 그 경우 서버 결과가 우선입니다.
 */
export function validateDraft(input: {
  subnets: SubnetInput[];
  rules: RuleInput[];
}): Promise<ApiValidationReport> {
  return apiRequest<ApiValidationReport>("/api/v1/projects/draft/validation", {
    method: "POST",
    body: input,
  });
}

/** 등급을 건너뛰는 서브넷 쌍 목록을 조회합니다. */
export function getForbiddenPairs(projectId: string): Promise<ApiForbiddenPairs> {
  return apiRequest<ApiForbiddenPairs>(
    `/api/v1/projects/${encodeURIComponent(projectId)}/forbidden-pairs`,
  );
}

/** Mock/서버 응답의 서브넷을 편집기 형태로 정규화합니다. */
export function toSubnetInput(subnet: ApiSubnet): SubnetInput {
  return {
    id: subnet.id,
    cidr: subnet.cidr,
    subnet_class: subnet.subnet_class,
    name: subnet.name,
    agent_id: subnet.agent_id,
    manually_edited: subnet.manually_edited,
  };
}

/** Mock/서버 응답의 규칙을 편집기 형태로 정규화합니다. */
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

/** 위반 목록을 규칙 식별자로 묶습니다. (표에서 규칙 행 강조용) */
export function violationsByRule(violations: ApiViolation[]): Map<string, ApiViolation[]> {
  const grouped = new Map<string, ApiViolation[]>();
  for (const violation of violations) {
    const existing = grouped.get(violation.rule_id);
    if (existing) existing.push(violation);
    else grouped.set(violation.rule_id, [violation]);
  }
  return grouped;
}
