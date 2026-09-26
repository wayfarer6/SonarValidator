/**
 * 백엔드 REST API 응답/요청 타입 정의
 *
 * <h2>왜 타입을 따로 두는가</h2>
 * 지금까지 화면들은 각자 필요한 형태를 즉석에서 만들어 썼습니다. 서버와
 * 계약이 생기면 <b>한 곳</b>에서 타입을 정의해야 응답 필드 이름이 바뀌었을 때
 * 컴파일러가 모든 사용처를 알려줍니다.
 *
 * <p>필드 이름은 서버 응답과 동일하게 snake_case 를 그대로 씁니다. camelCase 로
 * 바꾸면 두 이름 체계를 오가며 실수가 생기기 때문에, 경계에서는 서버 표기를
 * 유지하는 편이 안전합니다.
 */

// ---------------------------------------------------------------------------
// 공통
// ---------------------------------------------------------------------------

/** 서버가 내려주는 보안 등급. 프론트엔드 표기와 동일합니다. */
export type SubnetClass = "Confidential" | "Sensitive" | "Open";

/** 위반 심각도. */
export type ViolationSeverity = "CRITICAL" | "MAJOR" | "MINOR";

/** 규칙 출처. */
export type RuleOrigin = "MANUAL" | "DISCOVERED";

// ---------------------------------------------------------------------------
// 프로젝트
// ---------------------------------------------------------------------------

/** 편집기에서 다루는 서브넷 한 건. */
export interface ApiSubnet {
  id: string;
  cidr: string;
  subnet_class: SubnetClass | null;
  name: string | null;
  agent_id: string | null;
  manually_edited: boolean;
}

/** 편집기에서 다루는 연결 규칙 한 건. */
export interface ApiRule {
  id: string;
  src: string | null;
  dst: string | null;
  /** null 이면 "모든 포트" 를 뜻합니다. */
  port: number | null;
  protocol: string | null;
  origin: RuleOrigin;
  enabled: boolean;
  note: string | null;
}

/** 프로젝트 요약 (목록용). */
export interface ApiProjectSummary {
  project_id: string;
  name: string;
  category: string | null;
  description: string | null;
  status: string;
  created_at: string | null;
  updated_at: string | null;
  subnet_count: number;
  rule_count: number;
}

/** 프로젝트 상세 (편집기용). */
export interface ApiProject extends ApiProjectSummary {
  subnets: ApiSubnet[];
  rules: ApiRule[];
  /** 자동 수집 결과로 만든 초안이면 true. */
  draft?: boolean;
  draft_note?: string;
}

/** 프로젝트 목록 응답. */
export interface ApiProjectList {
  total: number;
  projects: ApiProjectSummary[];
}

/** 검증 위반 한 건. */
export interface ApiViolation {
  rule_id: string;
  src_subnet: string | null;
  dst_subnet: string | null;
  src_class: string | null;
  dst_class: string | null;
  reason: string;
  severity: ViolationSeverity;
  /** 재현 가능한 반례 패킷 (예: `10.10.131.5 -> 192.168.0.9:443`). */
  sampled_packet: string;
  sampled_src_ip: string | null;
  sampled_dst_ip: string | null;
  /** null 이면 "모든 포트". */
  sampled_port: number | null;
}

/** 검증 보고서. */
export interface ApiValidationReport {
  compliant: boolean;
  rule_count: number;
  subnet_count: number;
  violation_count: number;
  messages: string[];
  violated_rule_ids: string[];
  violations: ApiViolation[];
  /** BDD 진단 지표 (노드 수, 허용 조합 수 등). */
  metrics: Record<string, number | string>;
}

/** 규칙별 위반 묶음. */
export interface ApiRuleViolationGroup {
  rule_id: string;
  count: number;
  reasons: string[];
}

/** 정책 위반 현황 (심각도 집계 + 규칙별 묶음 포함). */
export interface ApiPolicyViolations extends ApiValidationReport {
  project_id: string;
  project_name: string;
  by_severity: Record<ViolationSeverity, number>;
  by_rule: ApiRuleViolationGroup[];
}

/** 금지 서브넷 쌍. */
export interface ApiForbiddenPair {
  src: string;
  dst: string;
}

export interface ApiForbiddenPairs {
  project_id: string;
  total: number;
  pairs: ApiForbiddenPair[];
  rule?: string;
}

// ---------------------------------------------------------------------------
// 네트워크 / 토폴로지
// ---------------------------------------------------------------------------

/** 토폴로지 노드 (서브넷). */
export interface ApiTopologyNode {
  id: string;
  label: string;
  cidr: string;
  subnet_class: SubnetClass | null;
  level: number | null;
  agent_id: string | null;
  manually_edited: boolean;
  /**
   * 이 서브넷을 관리하는 Agent 가 지금 격리 중인지.
   *
   * <p>등급 색보다 <b>우선</b>합니다. 운영자가 조치 중인 장치는 무슨 등급이든
   * 빨간색으로 보여야 "내가 이걸 껐다" 는 사실이 화면에서 확인됩니다.
   */
  quarantined?: boolean;
  /** Agent 의 WebSocket 세션이 살아 있는지. */
  connected?: boolean;
}

/** 토폴로지 간선 (연결 규칙). */
export interface ApiTopologyEdge {
  rule_id: string;
  source: string;
  target: string;
  port: number | null;
  protocol: string | null;
  forbidden: boolean;
  severity: "CRITICAL" | "MAJOR" | "OK";
}

export interface ApiTopology {
  project_id: string;
  project_name: string;
  nodes: ApiTopologyNode[];
  edges: ApiTopologyEdge[];
  legend: { label: string; level: number; color: string }[];
}

/** 수집된 장치 인터페이스. */
export interface ApiDiscoveredInterface {
  name: string;
  addresses: string[];
  access_vlan: number | null;
  trunk_vlans: number[];
  mode: string | null;
  parent: string | null;
  admin_state: string | null;
  oper_state: string | null;
  mac_address: string | null;
  subnet_class: SubnetClass | null;
}

/** 수집된 장치 VLAN. */
export interface ApiDiscoveredVlan {
  vlan_id: number;
  name: string | null;
  status: string | null;
  members: string[];
}

/** 수집된 장치 한 대. */
export interface ApiDiscoveredDevice {
  agent_id: string;
  hostname: string | null;
  format: string | null;
  vendor?: string | null;
  product?: string | null;
  device_type?: string | null;
  last_seen?: string | null;
  discovered: boolean;
  interfaces: ApiDiscoveredInterface[];
  vlans: ApiDiscoveredVlan[];
  route_count?: number;
  firewall_rule_count?: number;
  warnings?: string[];
}

export interface ApiDiscoveredDevices {
  project_id?: string;
  connected_agents?: number;
  parsed_devices?: number;
  device_count?: number;
  by_format?: Record<string, number>;
  devices: ApiDiscoveredDevice[];
  server_time?: string;
}

// ---------------------------------------------------------------------------
// Agent / 라우팅
// ---------------------------------------------------------------------------

/** 연결된 Agent 요약. */
export interface ApiAgentSummary {
  agent_id: string;
  last_seen: string | null;
  has_telemetry: boolean;
}

export interface ApiAgentList {
  connected: number;
  total_policy_requests: number;
  server_time: string;
  agents: ApiAgentSummary[];
}

/**
 * 서버가 아는 장치 한 대의 <b>통합</b> 상태입니다.
 *
 * <h2>왜 "예정" 과 "실제" 를 한 줄에 담는가</h2>
 * <p>배포 버튼을 누른 순간부터 프로버가 첫 텔레메트리를 보낼 때까지
 * 수 분의 공백이 있습니다. 그 사이에 화면이 아무것도 못 보여주면 운영자는
 * 배포가 실패했다고 판단합니다. 그래서 배포 <b>예정</b>을 서버에 남기고,
 * 그 위에 실제 연결/수신 상태를 겹쳐 한 줄로 보여줍니다.
 *
 * <h2>{@link ApiAgentSummary} 와의 차이</h2>
 * <p>{@code ApiAgentSummary} 는 "지금 연결된 Agent" 만 담습니다.
 * 이 타입은 예정 ∪ 연결 ∪ 텔레메트리의 합집합입니다.
 *
 * @property state 서버가 계산한 대표 상태
 * @property expected 배포 예정 목록에 있었는지 (false 면 예정에 없이 붙은 장치)
 */
export interface ApiAgentOverview {
  agent_id: string;
  project_id: string | null;
  device_type: string | null;
  node_type: string | null;
  expected_ip: string | null;
  registered_at: string | null;
  /** 지금 WebSocket 세션이 살아 있는지. */
  connected: boolean;
  /** 텔레메트리를 한 번이라도 보냈는지. */
  telemetry_seen: boolean;
  expected: boolean;
  state: "connected" | "telemetry-only" | "silent" | "unregistered";
  /**
   * 프로버(Agent)를 올릴 수 없어 **REST API 로만** 관리되는 장치인지.
   *
   * <p>OPNsense 가 해당합니다. WebSocket 세션이 없으므로 `connected=false`,
   * `state=silent` 이지만 **정상 동작 중**입니다. 화면이 이를 "무응답" 으로
   * 보여 주지 않도록 이 플래그를 봅니다.
   */
  api_managed?: boolean;
}

/** 통합 장치 현황 응답. 기존 요약 키를 함께 담습니다. */
export interface ApiAgentOverviewList {
  total: number;
  expected_total: number;
  connected: number;
  silent: number;
  agents: ApiAgentOverview[];
  /** 기존 키 호환 — 연결된 Agent 식별자 목록. */
  total_policy_requests?: number;
  server_time?: string;
}

/** 배포 예정 등록 결과. */
export interface ApiExpectedAgentRegistered {
  agent_id: string;
  project_id: string | null;
  device_type: string | null;
  node_type: string | null;
  status: string;
  registered: boolean;
}

/** 라우팅 테이블 한 줄. */
export interface ApiRoute {
  protocol: string | null;
  prefix: string | null;
  next_hop: string | null;
  next_hop_interface: string | null;
  metric: number | null;
  selected: boolean | null;
  default_route: boolean | null;
}

export interface ApiRouteTable {
  agent_id: string;
  hostname: string | null;
  product: string | null;
  route_count: number;
  routes: ApiRoute[];
}

// ---------------------------------------------------------------------------
// 알림
// ---------------------------------------------------------------------------

/**
 * 알림 분류입니다. 화면의 필터 탭과 1:1 로 대응합니다.
 *
 * <p>서버가 허용 값 집합으로 좁혀 두었으므로({@code NotificationService})
 * 프론트에서도 같은 집합을 씁니다. 자유 문자열로 두면 오타 분류가 생겨
 * 필터 탭이 비어 보입니다.
 */
export type NotificationCategory =
  | "POLICY"
  | "AGENT"
  | "PROJECT"
  | "SECURITY"
  | "SYSTEM";

/** 알림 심각도. 장비 로그와 같은 표기를 씁니다. */
export type NotificationSeverity = "critical" | "warning" | "info";

/** 알림 한 건. */
export interface ApiNotification {
  /** 외부 식별자 (예: `NTF-3F9A21B4`). */
  id: string;
  category: NotificationCategory;
  severity: NotificationSeverity;
  title: string;
  message: string | null;
  /** 관련 프로젝트 키. 무관하면 null. */
  project_id: string | null;
  /** 관련 장치 식별자. 무관하면 null. */
  agent_id: string | null;
  /** 발생 주체 (`system`, `scheduler`, 사용자 id …). */
  source: string | null;
  /** 발생 시각 (ISO-8601, UTC). */
  occurred_at: string;
  /** 읽음 여부. */
  read: boolean;
  /** 상세 화면 경로 (예: `/project/editor/PRJ-1`). */
  link: string | null;
  /**
   * 같은 원인이 반복된 횟수입니다.
   *
   * <p>서버가 5분 안의 동일 알림을 하나로 합치면서 횟수를 셉니다.
   * 1보다 크면 "한 번 있었던 일" 이 아니라 <b>진행 중인 문제</b>입니다.
   */
  repeat_count: number;
}

/** 알림 목록 응답. */
export interface ApiNotificationList {
  total: number;
  /** 전체 안읽음 건수 (필터와 무관한 값). */
  unread: number;
  notifications: ApiNotification[];
}

/** 알림 요약 응답 (배지 + 필터 탭 숫자). */
export interface ApiNotificationSummary {
  unread: number;
  by_category: Partial<Record<NotificationCategory, number>>;
  by_severity: Partial<Record<NotificationSeverity, number>>;
}

// ---------------------------------------------------------------------------
// 변경 이력 (Compliance)
// ---------------------------------------------------------------------------

export interface ApiComplianceChange {
  id: string;
  scope: "Project" | "Agent";
  project_id: string | null;
  agent_id: string | null;
  type: string;
  summary: string;
  changed_by: string;
  timestamp: string;
  status: "Applied" | "Pending" | "Rejected";
}

export interface ApiComplianceChanges {
  project_id: string | null;
  agent_id: string | null;
  total: number;
  changes: ApiComplianceChange[];
}

// ---------------------------------------------------------------------------
// 격리 (Quarantine)
// ---------------------------------------------------------------------------

/**
 * 격리 상태 한 건입니다.
 *
 * <h2>{@code command_delivered} 와 {@code applied} 는 다른 값입니다</h2>
 * <p>{@code command_delivered} 는 서버가 <b>소켓에 써 넣었는지</b>,
 * {@code applied} 는 Agent 가 <b>실제로 인터페이스를 내렸는지</b> 입니다.
 * 서버는 장치 내부를 볼 수 없으므로 이 둘을 구분해 보여줍니다.
 * 둘이 다르면 "장치는 살아 있는데 서버는 격리됐다고 믿는" 위험한 상태입니다.
 *
 * @property applied ack 로 확인된 적용 결과 ({@code null} = 아직 ack 없음)
 * @property rejected 서버가 격리 자체를 <b>거부</b>했는지 (방화벽 등)
 */
export interface ApiQuarantineState {
  agent_id: string;
  project_id: string | null;
  reason: string | null;
  requested_by: string | null;
  command_delivered: boolean;
  quarantined_at: string | null;
  released_at: string | null;
  released_by: string | null;
  active: boolean;
  /** 이번 요청에서 명령이 전달됐는지 (isolate 응답에만 있음). */
  delivered?: boolean;
  /** 이미 격리 중이었는지 (isolate 응답에만 있음). */
  retry?: boolean;
  /** Agent ack 로 확인된 실제 적용 결과. {@code null} 이면 미확인. */
  applied?: boolean | null;
  applied_detail?: string | null;
  /**
   * 서버가 요청을 <b>거부</b>했는지.
   *
   * <p>방화벽은 격리 대상이 아닙니다 — 트렁크(eth1)에 VLAN
   * 131/132/133 이 동시에 붙어 있어, 인터페이스를 내리면 무관한 존
   * 전체가 끊깁니다. 이 경우 서버는 행을 만들지 않고
   * {@code rejected: true} 와 사유를 돌려줍니다.
   *
   * <p>⚠️ {@code rejected} 를 확인하지 않고 "성공" 으로 표시하면
   * 운영자는 뚫린 망을 방치합니다.
   */
  rejected?: boolean;
  /** 거부 사유 (사람이 읽는 문장). */
  hint?: string;
  /** 서버가 판별한 장치 유형 (거부 응답에만 있음). */
  device_type?: string;
  available?: unknown;
  blocked?: unknown;
  preserved?: unknown;
  connected?: boolean;
  /** 명령 미전달 시 이유 설정 (isolate 응답에만 있음). */
  warning?: string;
}

/** 격리하면서 해제한 결과입니다. */
export interface ApiQuarantineRelease {
  agent_id: string;
  /** 격리 중이 아니어서 아무것도 하지 않았으면 {@code false}. */
  released: boolean;
  reason?: string;
}

/** 현재 격리 중인 목록. */
export interface ApiQuarantineList {
  project_id: string | null;
  total: number;
  agent_ids: string[];
  quarantined: ApiQuarantineState[];
}

/** 한 Agent 의 격리 여부 + 이력. */
export interface ApiQuarantineStatus {
  agent_id: string;
  quarantined: boolean;
  history: ApiQuarantineState[];
}
