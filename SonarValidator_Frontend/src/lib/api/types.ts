
// 공통

export type SubnetClass = "Confidential" | "Sensitive" | "Open";

export type ViolationSeverity = "CRITICAL" | "MAJOR" | "MINOR";

export type RuleOrigin = "MANUAL" | "DISCOVERED";

// 프로젝트

export type NetworkRole = "MANAGEMENT" | "TRANSIT" | "SERVICE" | "GUEST" | "INTERNET";

export interface ApiSubnet {
  network_role?: NetworkRole | null;
  vlan_id?: number | null;
  id: string;
  cidr: string;
  subnet_class: SubnetClass | null;
  name: string | null;
  agent_id: string | null;
  manually_edited: boolean;
  allowed_peers?: string[];
}

export interface ApiRule {
  id: string;
  src: string | null;
  dst: string | null;
  port: number | null;
  protocol: string | null;
  origin: RuleOrigin;
  enabled: boolean;
  note: string | null;
}

export interface ApiProjectSummary {
  project_id: string;
  name: string;
  category: string | null;
  description: string | null;
  status: string;
  management_server_ip: string | null;
  management_server_port: number | null;
  management_prefix?: string | null;
  created_at: string | null;
  updated_at: string | null;
  subnet_count: number;
  rule_count: number;
}

export interface ApiProject extends ApiProjectSummary {
  subnets: ApiSubnet[];
  rules: ApiRule[];
  draft?: boolean;
  draft_note?: string;
}

export interface ApiProjectList {
  total: number;
  projects: ApiProjectSummary[];
}

export interface ApiViolation {
  rule_id: string;
  src_subnet: string | null;
  dst_subnet: string | null;
  src_class: string | null;
  dst_class: string | null;
  reason: string;
  severity: ViolationSeverity;
  sampled_protocol: string;
  sampled_packet: string | null;
  evidence_type?: "DEFAULT_ROUTE_OBSERVATION" | "POLICY_MODEL_FINDING";
  sampled_src_ip: string | null;
  sampled_dst_ip: string | null;
  sampled_port: number | null;
}

export interface ApiValidationReport {
  compliant: boolean;
  model_valid?: boolean;
  input_errors?: string[];
  analysis_scope?: "POLICY_HEADER_SPACE";
  data_plane_analyzed?: boolean;
  rule_count: number;
  subnet_count: number;
  violation_count: number;
  messages: string[];
  violated_rule_ids: string[];
  violations: ApiViolation[];
  metrics: Record<string, number | string>;
}

export interface ApiRuleViolationGroup {
  rule_id: string;
  count: number;
  reasons: string[];
}

export interface ApiPolicyViolations extends ApiValidationReport {
  project_id: string;
  project_name: string;
  by_severity: Record<ViolationSeverity, number>;
  by_rule: ApiRuleViolationGroup[];
}

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

// 네트워크 / 토폴로지

export interface ApiTopologyNode {
  id: string;
  label: string;
  cidr: string;
  subnet_class: SubnetClass | null;
  level: number | null;
  agent_id: string | null;
  manually_edited: boolean;
  quarantined?: boolean;
  connected?: boolean;
  kind?: "subnet" | "internet";
}

export interface ApiTopologyEdge {
  rule_id: string;
  source: string;
  target: string;
  source_cidr?: string | null;
  target_cidr?: string | null;
  origin?: "MANUAL" | "DISCOVERED";
  observed?: boolean;
  internet?: boolean;
  agent_id?: string | null;
  port: number | null;
  protocol: string | null;
  forbidden: boolean;
  severity: "CRITICAL" | "MAJOR" | "OK" | "OBSERVED";
}

export interface ApiTopology {
  project_id: string;
  project_name: string;
  nodes: ApiTopologyNode[];
  edges: ApiTopologyEdge[];
  legend: { label: string; level: number; color: string }[];
  edge_legend?: { kind: string; label: string; dashed: boolean }[];
}

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

export interface ApiDiscoveredVlan {
  vlan_id: number;
  name: string | null;
  status: string | null;
  members: string[];
}

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

// Agent / 라우팅

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

export interface ApiAgentOverview {
  agent_id: string;
  project_id: string | null;
  device_type: string | null;
  node_type: string | null;
  expected_ip: string | null;
  registered_at: string | null;
  connected: boolean;
  telemetry_seen: boolean;
  expected: boolean;
  state: "connected" | "telemetry-only" | "silent" | "unregistered";
  api_managed?: boolean;
}

export interface ApiAgentOverviewList {
  total: number;
  expected_total: number;
  connected: number;
  silent: number;
  agents: ApiAgentOverview[];
  total_policy_requests?: number;
  server_time?: string;
}

export interface ApiExpectedAgentRegistered {
  agent_id: string;
  project_id: string | null;
  device_type: string | null;
  node_type: string | null;
  status: string;
  registered: boolean;
}

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

// 알림

export type NotificationCategory =
  | "POLICY"
  | "AGENT"
  | "PROJECT"
  | "SECURITY"
  | "SYSTEM";

export type NotificationSeverity = "critical" | "warning" | "info";

export interface ApiNotification {
  id: string;
  category: NotificationCategory;
  severity: NotificationSeverity;
  title: string;
  message: string | null;
  project_id: string | null;
  agent_id: string | null;
  source: string | null;
  occurred_at: string;
  read: boolean;
  link: string | null;
  repeat_count: number;
}

export interface ApiNotificationList {
  total: number;
  unread: number;
  notifications: ApiNotification[];
}

export interface ApiNotificationSummary {
  unread: number;
  by_category: Partial<Record<NotificationCategory, number>>;
  by_severity: Partial<Record<NotificationSeverity, number>>;
}

// 변경 이력 (Compliance)

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

// 격리 (Quarantine)

export interface ApiQuarantineState {
  agent_id: string;
  node_id?: number | null;
  project_id: string | null;
  scope?: "NODE" | "CONNECTION" | null;
  target_cidr?: string | null;
  reason: string | null;
  requested_by: string | null;
  command_delivered: boolean;
  quarantined_at: string | null;
  released_at: string | null;
  released_by: string | null;
  active: boolean;
  delivered?: boolean;
  retry?: boolean;
  applied?: boolean | null;
  applied_detail?: string | null;
  rejected?: boolean;
  hint?: string;
  device_type?: string;
  available?: unknown;
  blocked?: unknown;
  preserved?: unknown;
  connected?: boolean;
  warning?: string;
}

export interface ApiQuarantineRelease {
  agent_id: string;
  released: boolean;
  reason?: string;
}

export interface ApiQuarantineList {
  project_id: string | null;
  total: number;
  agent_ids: string[];
  quarantined: ApiQuarantineState[];
}

export interface ApiQuarantineStatus {
  agent_id: string;
  quarantined: boolean;
  history: ApiQuarantineState[];
}
