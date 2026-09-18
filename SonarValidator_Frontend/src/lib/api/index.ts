import { apiRequest } from "./client";
import type {
  ApiAgentList,
  ApiComplianceChanges,
  ApiDiscoveredDevices,
  ApiForbiddenPairs,
  ApiPolicyViolations,
  ApiRouteTable,
  ApiTopology,
} from "./types";

/**
 * 대시보드/정책/네트워크 화면이 쓰는 조회 API 모음입니다.
 *
 * <p>기존에 더미 데이터를 쓰던 화면들을 이 모듈로 연결합니다. 각 함수는
 * 서버 경로를 은닉하므로 화면 코드에는 URL 문자열이 나타나지 않습니다.
 */

// ---------------------------------------------------------------------------
// Agent
// ---------------------------------------------------------------------------

/** 연결된 Agent 목록과 요약을 조회합니다. */
export function listAgents(): Promise<ApiAgentList> {
  return apiRequest<ApiAgentList>("/api/v1/agents");
}

/** 특정 Agent 의 최근 텔레메트리 원본을 조회합니다. */
export function getAgentTelemetry(agentId: string): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>(
    `/api/v1/agents/${encodeURIComponent(agentId)}/telemetry`,
  );
}

/** 벤더 파서별 변환 요약을 조회합니다. */
export function getAgentConfigs(): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>("/api/v1/agents/configs");
}

// ---------------------------------------------------------------------------
// 정책
// ---------------------------------------------------------------------------

/**
 * 프로젝트의 정책 위반 현황을 조회합니다.
 *
 * <p>심각도 집계와 규칙별 묶음이 함께 오므로, 화면에서 다시 집계할 필요가
 * 없습니다.
 */
export function getPolicyViolations(projectId: string): Promise<ApiPolicyViolations> {
  return apiRequest<ApiPolicyViolations>(
    `/api/v1/policy/violations/${encodeURIComponent(projectId)}`,
  );
}

/** 등급을 건너뛰는 서브넷 쌍 목록을 조회합니다. */
export function getPolicyForbiddenPairs(projectId: string): Promise<ApiForbiddenPairs> {
  return apiRequest<ApiForbiddenPairs>(
    `/api/v1/policy/forbidden-pairs/${encodeURIComponent(projectId)}`,
  );
}

/**
 * 검증을 통과한 정책을 장치로 푸시합니다.
 *
 * @param projectId 프로젝트 키
 * @param force 위반이 있어도 강제 전송할지 여부
 */
export function pushPolicy(
  projectId: string,
  force = false,
): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>(
    `/api/v1/policy/push/${encodeURIComponent(projectId)}`,
    { method: "POST", params: { force } },
  );
}

// ---------------------------------------------------------------------------
// 네트워크 / 토폴로지
// ---------------------------------------------------------------------------

/** 프로젝트 관점의 토폴로지(노드/간선)를 조회합니다. */
export function getTopology(projectId: string): Promise<ApiTopology> {
  return apiRequest<ApiTopology>(`/api/v1/network/topology/${encodeURIComponent(projectId)}`);
}

/** 프로젝트 서브넷 등급과 결합된 수집 장치 목록을 조회합니다. */
export function getDiscoveredDevices(projectId: string): Promise<ApiDiscoveredDevices> {
  return apiRequest<ApiDiscoveredDevices>(
    `/api/v1/network/discovered/${encodeURIComponent(projectId)}`,
  );
}

/** 전체 수집 장치 현황을 조회합니다. (프로젝트 무관) */
export function getAllDiscoveredDevices(): Promise<ApiDiscoveredDevices> {
  return apiRequest<ApiDiscoveredDevices>("/api/v1/network/discovered");
}

/** 전체 장치의 라우팅 테이블을 조회합니다. */
export function listRouteTables(protocol?: string): Promise<{
  protocol_filter: string | null;
  device_count: number;
  devices: ApiRouteTable[];
}> {
  return apiRequest("/api/v1/routes", { params: { protocol } });
}

/** 특정 장치의 라우팅 테이블을 조회합니다. */
export function getRouteTable(agentId: string, protocol?: string): Promise<ApiRouteTable> {
  return apiRequest<ApiRouteTable>(`/api/v1/routes/${encodeURIComponent(agentId)}`, {
    params: { protocol },
  });
}

// ---------------------------------------------------------------------------
// 변경 이력 (Compliance)
// ---------------------------------------------------------------------------

/** 변경 이력을 조회합니다. 둘 다 주면 장치 축이 우선입니다. */
export function listComplianceChanges(options?: {
  projectId?: string;
  agentId?: string;
}): Promise<ApiComplianceChanges> {
  return apiRequest<ApiComplianceChanges>("/api/v1/compliance/changes", {
    params: { project_id: options?.projectId, agent_id: options?.agentId },
  });
}
