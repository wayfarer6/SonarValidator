import type {
  ApiAgentOverview,
  ApiAgentSummary,
  ApiDiscoveredDevice,
} from "./api/types";

export interface AgentView {
  agentId: string;
  hostname: string;
  primaryIp: string;
  deviceType: string;
  connected: boolean;
  apiManaged?: boolean;
  hasTelemetry: boolean;
  state?: ApiAgentOverview["state"];
  expected?: boolean;
}

export function normalizeDeviceType(deviceType: string | null | undefined): string {
  const raw = (deviceType ?? "").trim();
  if (raw.length === 0) return "—";

  // ⚠️ 대문자로 올려 비교합니다. 서버 코드(ROUTER)와 사람 표기(Router)를
  //    같은 값으로 만드는 것이 목적입니다.
  switch (raw.toUpperCase()) {
    case "ROUTER":
      return "Router";
    case "SWITCH":
      return "Switch";
    case "FIREWALL":
      return "Firewall";
    case "VM":
    case "VIRTUALMACHINE":
    case "VIRTUAL_MACHINE":
      return "VM";
    default:



      return raw;
  }
}

export function guessDeviceType(agentId: string): string {
  const id = agentId.toUpperCase();
  if (id.includes("FIREWALL") || id.includes("FW")) return "Firewall";
  if (id.includes("SWITCH") || id.includes("SW")) return "Switch";
  if (id.includes("ROUTER") || id.includes("RTR")) return "Router";
  if (id.startsWith("AGT-1") || id.includes("VM")) return "VM";
  return "—";
}

function primaryAddress(device: ApiDiscoveredDevice | undefined): string {
  return device?.interfaces.flatMap((iface) => iface.addresses)[0] ?? "—";
}

export function buildAgentViews(
  agents: ApiAgentSummary[],
  devices: ApiDiscoveredDevice[],
): AgentView[] {
  const byId = new Map(devices.map((d) => [d.agent_id, d]));
  return agents.map((agent) => {
    const device = byId.get(agent.agent_id);
    return {
      agentId: agent.agent_id,
      hostname: device?.hostname ?? agent.agent_id,
      primaryIp: primaryAddress(device),
      // ⚠️ 서버 코드(ROUTER)를 그대로 넣으면 화면이 "ROUTER" 로 보이고
      //    필터(라우터 탭)도 못 고릅니다. 생성 시점에 표준 표기로 맞춥니다.
      deviceType: normalizeDeviceType(device?.device_type ?? guessDeviceType(agent.agent_id)),
      connected: true,
      hasTelemetry: agent.has_telemetry,
    };
  });
}

export function deviceViews(devices: ApiDiscoveredDevice[]): AgentView[] {
  return devices.map((device) => ({
    agentId: device.agent_id,
    hostname: device.hostname ?? device.agent_id,
    primaryIp: primaryAddress(device),
    deviceType: normalizeDeviceType(device.device_type ?? guessDeviceType(device.agent_id)),
    connected: true,
    hasTelemetry: device.discovered,
  }));
}

export function overviewViews(overview: ApiAgentOverview[]): AgentView[] {
  return overview.map((row) => ({
    agentId: row.agent_id,
    hostname: row.agent_id,
    primaryIp: row.expected_ip ?? "—",
    deviceType: normalizeDeviceType(row.device_type ?? guessDeviceType(row.agent_id)),
    connected: row.connected,
    apiManaged: row.api_managed === true,
    hasTelemetry: row.telemetry_seen,
    state: row.state,
    expected: row.expected,
  }));
}

export function mergeOverviewWithDevices(
  overview: ApiAgentOverview[],
  devices: ApiDiscoveredDevice[],
): AgentView[] {
  const byId = new Map(devices.map((device) => [device.agent_id, device]));
  return overviewViews(overview).map((row) => {
    const device = byId.get(row.agentId);
    if (!device) return row;
    return {
      ...row,
      hostname: device.hostname ?? row.agentId,
      primaryIp: primaryAddress(device),
      deviceType: normalizeDeviceType(device.device_type ?? row.deviceType),
      hasTelemetry: row.hasTelemetry || device.discovered,
    };
  });
}

export const AGENT_STATE_LABEL: Record<ApiAgentOverview["state"], string> = {
  connected: "연결됨",
  "telemetry-only": "수신만",
  silent: "무응답",
  unregistered: "미등록",
};

// 표시 형식

export function formatTimestamp(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
