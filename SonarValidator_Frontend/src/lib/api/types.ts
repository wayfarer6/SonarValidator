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
