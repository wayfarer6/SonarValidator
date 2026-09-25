import type { ApiAgentSummary, ApiDiscoveredDevice } from "./api/types";

/**
 * 화면이 쓰는 장치 한 줄 모델입니다.
 *
 * <h2>왜 따로 만드는가</h2>
 * 서버는 "연결된 Agent 목록"({@link ApiAgentSummary})과 "수집된 장치"
 * ({@link ApiDiscoveredDevice})를 서로 다른 엔드포인트로 줍니다. 각각만 보면
 * 한쪽엔 이름이 없고 다른 쪽엔 연결 상태가 없어 표를 그릴 수 없습니다.
 * 두 응답을 {@code agent_id} 로 합쳐야 화면이 완성됩니다. 대시보드 표와
 * Compliance 화면이 같은 규칙을 쓰도록 여기에 한 번만 둡니다.
 */
export interface AgentView {
  agentId: string;
  hostname: string;
  primaryIp: string;
  deviceType: string;
  /** 현재 서버와 연결되어 있는지. 연결 목록에서 온 항목은 항상 true. */
  connected: boolean;
  /** 중립 설정을 수집해 파싱까지 끝냈는지. */
  hasTelemetry: boolean;
}

/**
 * agent_id 문자열에서 장비 종류를 추정합니다.
 *
 * <p>서버의 {@code device_type} 은 설정을 수집·파싱한 뒤에야 채워집니다.
 * 그 전에는 비어 있어, 배포 스크립트가 쓰는 이름 규칙을 보조로 씁니다.
 * 추측이 틀릴 수 있으므로 수집된 값이 있으면 항상 그쪽을 우선합니다.
 */
export function guessDeviceType(agentId: string): string {
  const id = agentId.toUpperCase();
  if (id.includes("FIREWALL") || id.includes("FW")) return "Firewall";
  if (id.includes("SWITCH") || id.includes("SW")) return "Switch";
  if (id.includes("ROUTER") || id.includes("RTR")) return "Router";
  if (id.startsWith("AGT-1") || id.includes("VM")) return "VM";
  return "—";
}

/** 수집 장치의 첫 번째 IP 주소. 없으면 "—". */
function primaryAddress(device: ApiDiscoveredDevice | undefined): string {
  return device?.interfaces.flatMap((iface) => iface.addresses)[0] ?? "—";
}

/**
 * 연결 목록과 수집 장치를 합쳐 표 한 줄씩 만듭니다.
 *
 * @param agents 서버의 연결된 Agent 목록
 * @param devices 서버의 수집 장치 목록 (없으면 이름/종류를 agent_id 로 추정)
 */
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
      deviceType: device?.device_type ?? guessDeviceType(agent.agent_id),
      connected: true,
      hasTelemetry: agent.has_telemetry,
    };
  });
}

/**
 * 수집 장치만으로 표를 만듭니다.
 *
 * <p>프로젝트 단위 화면처럼 연결 상태를 따로 받지 않는 곳에서 씁니다.
 */
export function deviceViews(devices: ApiDiscoveredDevice[]): AgentView[] {
  return devices.map((device) => ({
    agentId: device.agent_id,
    hostname: device.hostname ?? device.agent_id,
    primaryIp: primaryAddress(device),
    deviceType: device.device_type ?? guessDeviceType(device.agent_id),
    connected: true,
    hasTelemetry: device.discovered,
  }));
}

// ---------------------------------------------------------------------------
// 표시 형식
// ---------------------------------------------------------------------------

/**
 * ISO 시각을 {@code YYYY-MM-DD HH:mm} 로 바꿉니다.
 *
 * <p>숫자만 나열하면 읽기 쉽고, 표 폭도 흔들리지 않습니다.
 */
export function formatTimestamp(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}