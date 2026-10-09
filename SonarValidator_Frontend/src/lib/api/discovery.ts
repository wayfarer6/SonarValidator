import { getAllDiscoveredDevices, getDiscoveredDevices, listAgentOverview } from "./index";
import { apiRequest } from "./client";
import type { ApiProject } from "./types";

export async function getProjectDevices(projectId?: string | null) {
  if (!projectId) return (await getAllDiscoveredDevices()).devices;
  const [discovered, overview] = await Promise.all([
    getDiscoveredDevices(projectId), listAgentOverview(projectId),
  ]);
  const ids = new Set(overview.agents.filter(a => a.expected && a.project_id === projectId).map(a => a.agent_id));
  return discovered.devices.filter(d => ids.has(d.agent_id));
}

export function getProjectForEditing(projectId: string): Promise<ApiProject> {
  return apiRequest<ApiProject>(`/api/v1/projects/${encodeURIComponent(projectId)}/editing`);
}
