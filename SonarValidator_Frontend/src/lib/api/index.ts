import { bundleFileName, bundleDownloadError } from "./download";
import { apiRequest, API_BASE_URL, ApiError } from "./client";
import type {
  ApiAgentList,
  ApiMonitorSettings,
  ApiAgentOverviewList,
  ApiComplianceChanges,
  ApiDiscoveredDevices,
  ApiExpectedAgentRegistered,
  ApiForbiddenPairs,
  ApiPolicyViolations,
  ApiQuarantineList,
  ApiQuarantineRelease,
  ApiQuarantineState,
  ApiQuarantineStatus,
  ApiRouteTable,
  ApiTopology,
} from "./types";


// Agent

export function listAgents(): Promise<ApiAgentList> {
  return apiRequest<ApiAgentList>("/api/v1/agents");
}

export function getAgentTelemetry(agentId: string): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>(
    `/api/v1/agents/${encodeURIComponent(agentId)}/telemetry`,
  );
}

export function getAgentConfigs(): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>("/api/v1/agents/configs");
}

export function listAgentOverview(projectId?: string): Promise<ApiAgentOverviewList> {
  return apiRequest<ApiAgentOverviewList>("/api/v1/agents/overview", {
    params: { project_id: projectId },
  });
}

export function registerExpectedAgent(
  agentId: string,
  options?: {
    projectId?: string;
    deviceType?: string;
    nodeType?: string;
    expectedIp?: string;
    note?: string;
  },
): Promise<ApiExpectedAgentRegistered> {
  return apiRequest<ApiExpectedAgentRegistered>("/api/v1/agents/expected", {
    method: "POST",
    body: {
      agent_id: agentId,
      project_id: options?.projectId,
      device_type: options?.deviceType,
      node_type: options?.nodeType,
      expected_ip: options?.expectedIp,
      note: options?.note,
    },
  });
}

export function deleteExpectedAgent(agentId: string, projectId?: string): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>(
    `/api/v1/agents/expected/${encodeURIComponent(agentId)}`,
    { method: "DELETE", params: { project_id: projectId } },
  );
}

export function removeAgentTelemetry(
  agentId: string,
): Promise<{ agent_id: string; removed: boolean; reason: string | null }> {
  return apiRequest(`/api/v1/agents/${encodeURIComponent(agentId)}/telemetry`, {
    method: "DELETE",
  });
}

export function pruneStaleAgents(
  olderThanHours?: number,
): Promise<{ removed: number; remaining_telemetry: number; older_than_hours: number }> {
  const suffix = olderThanHours ? `?older_than_hours=${olderThanHours}` : "";
  return apiRequest(`/api/v1/agents/stale${suffix}`, { method: "DELETE" });
}

// Agent 다운로드 (설정이 미리 채워진 tar.gz)

export function getAgentDownloadInfo(
  agentId: string,
  options: { projectId: string; nodeType?: string },
): Promise<{
  agent_id: string;
  project_id: string;
  node_type: string;
  server_ip: string;
  server_port: number;
  file_name: string;
  archive_format: string;
  installer_dir: string;
  staged_assets: Record<string, boolean>;
}> {
  return apiRequest("/api/v1/agents/download/info", {
    params: {
      agent_id: agentId,
      node_type: options?.nodeType,
      project_id: options.projectId,
    },
  });
}

export async function downloadAgent(
  agentId: string,
  options: {
    projectId: string;
    nodeType?: string;
    dataDirectory?: string;
  },
): Promise<{ fileName: string; blob: Blob }> {
  const params = new URLSearchParams();
  if (options?.nodeType) params.set("node_type", options.nodeType);
  params.set("project_id", options.projectId);
  if (options?.dataDirectory) params.set("data_directory", options.dataDirectory);

  const query = params.toString();
  const path = `/api/v1/agents/download/${encodeURIComponent(agentId)}${query ? `?${query}` : ""}`;

  // apiRequest 는 JSON 을 기대하므로 tar.gz 에는 쓸 수 없습니다. 직접 fetch 합니다.
  const response = await fetch(`${API_BASE_URL}${path}`, { credentials: "include" }).catch((cause: unknown) => {
    throw new ApiError(0, "서버에 연결할 수 없습니다.", cause).record("GET", path);
  });
  if (!response.ok) {
    const text = await response.text();
    throw new ApiError(response.status, bundleDownloadError(response.status, text), text).record("GET", path);
  }

  const fileName = bundleFileName(response.headers.get("Content-Disposition") ?? "", `sonar-agent-${agentId}.tar.gz`);

  try {
    return { fileName, blob: await response.blob() };
  } catch (cause) {
    throw new ApiError(0, "다운로드 응답을 받지 못했습니다.", cause).record("GET", path);
  }
}

// 정책

export function getPolicyViolations(projectId: string): Promise<ApiPolicyViolations> {
  return apiRequest<ApiPolicyViolations>(
    `/api/v1/policy/violations/${encodeURIComponent(projectId)}`,
  );
}

export function getPolicyForbiddenPairs(projectId: string): Promise<ApiForbiddenPairs> {
  return apiRequest<ApiForbiddenPairs>(
    `/api/v1/policy/forbidden-pairs/${encodeURIComponent(projectId)}`,
  );
}

export function pushPolicy(
  projectId: string,
  force = false,
): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>(
    `/api/v1/policy/push/${encodeURIComponent(projectId)}`,
    { method: "POST", params: { force } },
  );
}

// 네트워크 / 토폴로지

export function getTopology(projectId: string): Promise<ApiTopology> {
  return apiRequest<ApiTopology>(`/api/v1/network/topology/${encodeURIComponent(projectId)}`);
}

export function getDiscoveredDevices(projectId: string): Promise<ApiDiscoveredDevices> {
  return apiRequest<ApiDiscoveredDevices>(
    `/api/v1/network/discovered/${encodeURIComponent(projectId)}`,
  );
}

export function getAllDiscoveredDevices(): Promise<ApiDiscoveredDevices> {
  return apiRequest<ApiDiscoveredDevices>("/api/v1/network/discovered");
}

export function listRouteTables(protocol?: string): Promise<{
  protocol_filter: string | null;
  device_count: number;
  devices: ApiRouteTable[];
}> {
  return apiRequest("/api/v1/routes", { params: { protocol } });
}

export function getRouteTable(agentId: string, protocol?: string): Promise<ApiRouteTable> {
  return apiRequest<ApiRouteTable>(`/api/v1/routes/${encodeURIComponent(agentId)}`, {
    params: { protocol },
  });
}

// 변경 이력 (Compliance)

export function listComplianceChanges(options?: {
  projectId?: string;
  agentId?: string;
}): Promise<ApiComplianceChanges> {
  return apiRequest<ApiComplianceChanges>("/api/v1/compliance/changes", {
    params: { project_id: options?.projectId, agent_id: options?.agentId },
  });
}

// 격리 (Quarantine)

export function quarantineAgent(
  agentId: string,
  options?: { projectId?: string; reason?: string; requestedBy?: string; targetCidr?: string },
): Promise<ApiQuarantineState> {
  return apiRequest<ApiQuarantineState>(
    `/api/v1/quarantine/${encodeURIComponent(agentId)}`,
    {
      method: "POST",
      body: {
        project_id: options?.projectId,
        reason: options?.reason,
        requested_by: options?.requestedBy,
        target_cidr: options?.targetCidr,
      },
    },
  );
}

export function releaseQuarantine(
  agentId: string,
  releasedBy?: string,
  targetCidr?: string,
): Promise<ApiQuarantineRelease> {
  return apiRequest<ApiQuarantineRelease>(
    `/api/v1/quarantine/${encodeURIComponent(agentId)}`,
    { method: "DELETE", params: { released_by: releasedBy, target_cidr: targetCidr } },
  );
}

export function listQuarantined(projectId?: string): Promise<ApiQuarantineList> {
  return apiRequest<ApiQuarantineList>("/api/v1/quarantine", {
    params: { project_id: projectId },
  });
}

export function getQuarantineStatus(agentId: string): Promise<ApiQuarantineStatus> {
  return apiRequest<ApiQuarantineStatus>(
    `/api/v1/quarantine/${encodeURIComponent(agentId)}`,
  );
}

export function getMonitorSettings(agentId: string, projectId: string): Promise<ApiMonitorSettings> {
  return apiRequest(`/api/v1/agents/${encodeURIComponent(agentId)}/monitor-settings`, {
    params: { project_id: projectId },
  });
}

export function updateMonitorSettings(agentId: string, projectId: string, seconds: number, revision: number): Promise<ApiMonitorSettings> {
  return apiRequest(`/api/v1/agents/${encodeURIComponent(agentId)}/monitor-settings`, {
    method: "PUT", params: { project_id: projectId },
    body: { monitor_interval_seconds: seconds, revision },
  });
}
