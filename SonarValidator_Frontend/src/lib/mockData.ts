// TODO: 추후 백엔드/WebSocket API로 교체할 더미 데이터
export type DeviceType = "Router" | "Switch" | "Firewall" | "VM";

export interface ProjectInfo {
  id: number;
  name: string;
  category: string;
  description: string;
  status: "In Progress" | "Planning" | "Completed";
  createdAt: string; // ISO date
}

export interface AgentInfo {
  id: string;
  name: string;
  ip: string;
  ipRange: string; // IP 대역대
  deviceType: DeviceType;
  status: "online" | "offline";
  projectId: number;
}

export type LogLevel = "INFO" | "WARN" | "ERROR";

export interface LogEntry {
  id: string;
  level: LogLevel;
  source: string;
  message: string;
  timestamp: string;
}

export const MOCK_PROJECTS: ProjectInfo[] = [
  {
    id: 1,
    name: "Sonar Bank Network",
    category: "Finance",
    description: "Network Topology of Sonar Bank.",
    status: "In Progress",
    createdAt: "2026-08-02T09:15:00Z",
  },
  {
    id: 2,
    name: "A Nation Defense Force Network",
    category: "Government",
    description: "Network Topology of A Nation Defense Force.",
    status: "Planning",
    createdAt: "2026-08-21T14:40:00Z",
  },
  {
    id: 3,
    name: "Campus Research Net",
    category: "Education",
    description: "University research network segmentation.",
    status: "Completed",
    createdAt: "2026-09-05T11:05:00Z",
  },
];

export const MOCK_AGENTS: AgentInfo[] = [
  // Project 3 (최신 프로젝트)
  { id: "AGT-0001", name: "RTR-Campus-01", ip: "10.30.0.1", ipRange: "10.30.0.0/24", deviceType: "Router", status: "online", projectId: 3 },
  { id: "AGT-0002", name: "RTR-Campus-02", ip: "10.30.1.1", ipRange: "10.30.1.0/24", deviceType: "Router", status: "online", projectId: 3 },
  { id: "AGT-0003", name: "SW-Lab-A", ip: "10.30.10.2", ipRange: "10.30.10.0/24", deviceType: "Switch", status: "online", projectId: 3 },
  { id: "AGT-0004", name: "SW-Lab-B", ip: "10.30.11.2", ipRange: "10.30.11.0/24", deviceType: "Switch", status: "offline", projectId: 3 },
  { id: "AGT-0005", name: "FW-Edge-01", ip: "10.30.0.254", ipRange: "10.30.0.0/24", deviceType: "Firewall", status: "online", projectId: 3 },
  { id: "AGT-0006", name: "VM-Research-01", ip: "10.30.20.11", ipRange: "10.30.20.0/24", deviceType: "VM", status: "online", projectId: 3 },
  { id: "AGT-0007", name: "VM-Research-02", ip: "10.30.20.12", ipRange: "10.30.20.0/24", deviceType: "VM", status: "online", projectId: 3 },
  // Project 1
  { id: "AGT-1001", name: "RTR-Bank-Core", ip: "192.168.10.1", ipRange: "192.168.10.0/24", deviceType: "Router", status: "online", projectId: 1 },
  { id: "AGT-1002", name: "SW-Bank-01", ip: "192.168.20.10", ipRange: "192.168.20.0/24", deviceType: "Switch", status: "online", projectId: 1 },
  { id: "AGT-1003", name: "FW-Bank-DMZ", ip: "10.0.0.254", ipRange: "10.0.0.0/24", deviceType: "Firewall", status: "online", projectId: 1 },
  { id: "AGT-1004", name: "VM-Core-Banking", ip: "172.16.0.100", ipRange: "172.16.0.0/24", deviceType: "VM", status: "offline", projectId: 1 },
  // Project 2
  { id: "AGT-2001", name: "RTR-DEF-01", ip: "172.20.0.1", ipRange: "172.20.0.0/24", deviceType: "Router", status: "online", projectId: 2 },
  { id: "AGT-2002", name: "SW-DEF-01", ip: "172.20.5.2", ipRange: "172.20.5.0/24", deviceType: "Switch", status: "online", projectId: 2 },
];

export const MOCK_LOGS: LogEntry[] = [
  { id: "LOG-9001", level: "ERROR", source: "FW-Edge-01", message: "Policy violation blocked: Confidential → Open (Rule-0007)", timestamp: "2026-09-13T08:42:11Z" },
  { id: "LOG-9002", level: "WARN", source: "SW-Lab-B", message: "Agent heartbeat lost for 120s", timestamp: "2026-09-13T08:35:02Z" },
  { id: "LOG-9003", level: "INFO", source: "RTR-Campus-01", message: "Routing table synchronized (48 entries)", timestamp: "2026-09-13T08:20:47Z" },
  { id: "LOG-9004", level: "INFO", source: "Prober", message: "Network discovery completed: 7 nodes detected", timestamp: "2026-09-13T07:58:19Z" },
  { id: "LOG-9005", level: "WARN", source: "VM-Core-Banking", message: "Segmentation rule missing allowed port", timestamp: "2026-09-12T23:11:05Z" },
  { id: "LOG-9006", level: "INFO", source: "Backend", message: "Telemetry snapshot stored (project 3)", timestamp: "2026-09-12T22:40:33Z" },
  { id: "LOG-9007", level: "ERROR", source: "RTR-Bank-Core", message: "VXLAN tunnel negotiation failed on port 4789", timestamp: "2026-09-12T21:05:58Z" },
];

/** 생성일 기준 가장 최근 프로젝트 */
export function getLatestProject(projects: ProjectInfo[] = MOCK_PROJECTS): ProjectInfo | undefined {
  return [...projects].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  )[0];
}

/** 장비 타입별 mermaid 노드 모양 */
function nodeShape(agent: AgentInfo): string {
  const label = `${agent.name}<br/>${agent.ip}`;
  switch (agent.deviceType) {
    case "Router":
      return `${agent.id.replace(/-/g, "")}{${label}}`; // 마름모
    case "Switch":
      return `${agent.id.replace(/-/g, "")}[${label}]`; // 사각형
    case "Firewall":
      return `${agent.id.replace(/-/g, "")}[[${label}]]`; // 서브루틴
    case "VM":
      return `${agent.id.replace(/-/g, "")}(${label})`; // 둥근 사각형
  }
}

const nodeId = (agent: AgentInfo) => agent.id.replace(/-/g, "");

/** 에이전트 목록 → 토폴로지 mermaid 차트 생성 */
export function buildTopologyChart(agents: AgentInfo[]): string {
  if (agents.length === 0) return "flowchart TD\n  Empty[No agents detected]";

  const firewalls = agents.filter((a) => a.deviceType === "Firewall");
  const routers = agents.filter((a) => a.deviceType === "Router");
  const switches = agents.filter((a) => a.deviceType === "Switch");
  const vms = agents.filter((a) => a.deviceType === "VM");

  const lines: string[] = ["flowchart TD", '  Internet((Internet))'];

  // Internet → Firewall(없으면 Router, 그것도 없으면 첫 장비) → ...
  if (firewalls.length > 0) {
    firewalls.forEach((fw) => lines.push(`  Internet --> ${nodeId(fw)}`));
    firewalls.forEach((fw) =>
      routers.forEach((rt) => lines.push(`  ${nodeId(fw)} --> ${nodeId(rt)}`)),
    );
  } else if (routers.length > 0) {
    routers.forEach((rt) => lines.push(`  Internet --> ${nodeId(rt)}`));
  } else {
    const first = switches[0] ?? vms[0];
    if (first) lines.push(`  Internet --> ${nodeId(first)}`);
  }

  routers.forEach((rt, index) => {
    const target = switches[index % Math.max(switches.length, 1)];
    if (target) lines.push(`  ${nodeId(rt)} --> ${nodeId(target)}`);
  });
  switches.forEach((sw, index) => {
    const target = vms[index % Math.max(vms.length, 1)];
    if (target) lines.push(`  ${nodeId(sw)} --> ${nodeId(target)}`);
  });

  // 노드 정의
  agents.forEach((agent) => lines.push(`  ${nodeShape(agent)}`));

  // 스타일
  lines.push(
    "  classDef router fill:#e0f2fe,stroke:#0284c7,stroke-width:2px;",
    "  classDef switch fill:#dcfce7,stroke:#16a34a,stroke-width:2px;",
    "  classDef firewall fill:#fee2e2,stroke:#dc2626,stroke-width:2px;",
    "  classDef vm fill:#f3e8ff,stroke:#9333ea,stroke-width:2px;",
    "  classDef offline stroke-dasharray:4 3,opacity:0.55;",
  );
  routers.forEach((a) => lines.push(`  class ${nodeId(a)} router;`));
  switches.forEach((a) => lines.push(`  class ${nodeId(a)} switch;`));
  firewalls.forEach((a) => lines.push(`  class ${nodeId(a)} firewall;`));
  vms.forEach((a) => lines.push(`  class ${nodeId(a)} vm;`));
  agents
    .filter((a) => a.status === "offline")
    .forEach((a) => lines.push(`  class ${nodeId(a)} offline;`));

  return lines.join("\n");
}
