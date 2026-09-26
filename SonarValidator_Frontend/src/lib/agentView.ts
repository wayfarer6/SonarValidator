import type {
  ApiAgentOverview,
  ApiAgentSummary,
  ApiDiscoveredDevice,
} from "./api/types";

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
  /**
   * 서버가 계산한 대표 상태입니다.
   *
   * <p>통합 현황({@code /agents/overview})에서 온 행에만 채워집니다.
   * 목록/장치 응답만 쓴 경우에는 {@code undefined} 이고, 화면은
   * {@code connected} 로만 판단합니다.
   */
  state?: ApiAgentOverview["state"];
  /**
   * 배포 예정 목록에 있었는지.
   *
   * <p>{@code false} 는 "예정에 없이 붙어 온 장치" 입니다. 오류는 아니지만
   * 누가 배포했는지 알 수 없으므로 화면에 표시를 남깁니다.
   */
  expected?: boolean;
}

/**
 * 장비 종류의 <b>표준 표기</b>입니다.
 *
 * <h2>⚠️ 왜 정규화가 필요한가 — 화면에서 실제로 발생한 버그</h2>
 * <p>서버는 {@code device_type} 을 <b>대문자 코드</b>로 보냅니다.
 * <pre>
 *   AlpineFirewallConfigParser  → "FIREWALL"
 *   AristaSwitchConfigParser    → "SWITCH"
 *   CiscoRouterConfigParser     → "ROUTER"
 *   LinuxVmConfigParser         → "VM"
 * </pre>
 *
 * <p>그런데 대시보드의 필터 탭은 사람이 읽는 <b>Title Case</b>({@code "Router"})
 * 를 썼고, 비교가 {@code ===} 였습니다. 그래서
 * {@code "ROUTER" === "Router"} 이 거짓이 되어
 * <ul>
 *   <li>{@code All} 탭에서만 목록이 보이고,</li>
 *   <li>{@code Router} 탭을 누르면 <b>항상 빈 표</b>가 나왔습니다.</li>
 * </ul>
 * 오류가 나지 않으므로 &quot;장비가 없나 보다&quot; 로 읽힙니다.
 *
 * <p>그래서 <b>비교하기 전에 반드시 이 함수를 거칩니다.</b> 세 곳(필터 판정·
 * 배지 표시·색상 선택)이 같은 값을 쓰게 하려고 한 곳에 뒀습니다 —
 * 한 곳만 정규화하면 &quot;배지는 Router 인데 필터는 못 골라내는&quot; 상태가 됩니다.
 *
 * @param deviceType 서버 코드 또는 추정값 (null 허용)
 * @returns 표준 표기 ({@code Router}/{@code Switch}/{@code Firewall}/{@code VM}),
 *          알 수 없으면 원본 문자열(공백 제거), 비었으면 {@code "—"}
 */
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
      // ⚠️ 모르는 유형을 "—" 로 뭉개지 않습니다.
      //    그러면 새 장비 유형이 추가됐을 때 조용히 사라집니다.
      //    원본을 그대로 보여줘야 운영자가 "분류가 필요하다" 를 알 수 있습니다.
      return raw;
  }
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
      // ⚠️ 서버 코드(ROUTER)를 그대로 넣으면 화면이 "ROUTER" 로 보이고
      //    필터(라우터 탭)도 못 고릅니다. 생성 시점에 표준 표기로 맞춥니다.
      deviceType: normalizeDeviceType(device?.device_type ?? guessDeviceType(agent.agent_id)),
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
    deviceType: normalizeDeviceType(device.device_type ?? guessDeviceType(device.agent_id)),
    connected: true,
    hasTelemetry: device.discovered,
  }));
}

/**
 * 서버의 <b>통합 현황</b>을 표 한 줄씩으로 바꿉니다.
 *
 * <h2>왜 연결 목록 대신 이걸 쓰는가</h2>
 * {@link buildAgentViews} 는 "지금 붙어 있는 Agent" 만 받습니다. 그래서
 * 배포 직후(프로버가 첫 접속을 하기 전)나 네트워크 단절 중에는 표가
 * <b>비어 보입니다.</b> 운영자는 그것을 배포 실패로 읽습니다.
 * 통합 현황은 배포 예정까지 알고 있으므로 그 공백을 상태 문자열로 채웁니다.
 *
 * @param overview 서버의 통합 장치 현황 행들
 */
export function overviewViews(overview: ApiAgentOverview[]): AgentView[] {
  return overview.map((row) => ({
    agentId: row.agent_id,
    hostname: row.agent_id,
    primaryIp: row.expected_ip ?? "—",
    deviceType: normalizeDeviceType(row.device_type ?? guessDeviceType(row.agent_id)),
    connected: row.connected,
    hasTelemetry: row.telemetry_seen,
    state: row.state,
    expected: row.expected,
  }));
}

/**
 * 통합 현황 행에 수집된 장치 정보를 덧붙입니다.
 *
 * <p>통합 현황은 <b>누가 있나</b>를 알고, 수집 장치는 <b>무엇으로 보이나</b>를
 * 압니다(호스트명, 주소, 인터페이스 수). 둘을 합쳐야 표가 완성됩니다.
 *
 * @param overview 통합 현황 행들
 * @param devices  수집된 장치 목록
 */
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
      // ⚠️ 여기가 원래 버그의 지점입니다.
      //    `device.device_type ?? row.deviceType` 은 수집된 값을 **그대로**
      //    넣었고, 서버가 주는 값은 대문자 코드(ROUTER)입니다.
      //    결과: All 탭은 되고 나머지 탭은 모두 빈 표.
      deviceType: normalizeDeviceType(device.device_type ?? row.deviceType),
      hasTelemetry: row.hasTelemetry || device.discovered,
    };
  });
}

/**
 * 상태 문자열을 사람이 읽는 라벨로 바꿉니다.
 *
 * <p>서버 상태값을 그대로 노출하면 화면마다 다른 번역이 생깁니다.
 * (예: {@code silent} 를 "조용함" / "무응답" 으로 각각 쓰는 일)
 */
export const AGENT_STATE_LABEL: Record<ApiAgentOverview["state"], string> = {
  connected: "연결됨",
  "telemetry-only": "수신만",
  silent: "무응답",
  unregistered: "미등록",
};

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