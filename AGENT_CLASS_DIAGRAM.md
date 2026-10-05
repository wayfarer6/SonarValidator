# Agent (SonarValidator_Prober) 클래스 다이어그램

`SonarValidator_Prober` (C++23 프로버 에이전트)의 **전체 클래스 구조**를 PlantUML로 정리한 문서입니다.
모든 내용은 실제 소스 코드를 기준으로 작성했습니다.

- **대상**: `SonarValidator_Prober/` (에이전트 본체)
- **범위**: Linux VM(Ubuntu), Cisco 8000v(IOS-XE), Arista vEOS, Open vSwitch, nftables/Alpine 방화벽, FRR
- **작성 기준**: 현재 워킹 트리 소스
- **렌더링**: PlantUML 코드블록을 지원하는 뷰어(예: VS Code PlantUML 확장, plantuml.com)에서 확인

---

## 1. 전체 개요

에이전트는 크게 **① 초기화 → ② 3개 워커 스레드(관리/텔레메트리/DB)** 구조로 동작합니다.
관리(정책·명령), 텔레메트리(수집·전송), DB(로컬 SQLite 저장)가 스레드별로 분리되어 있습니다.

```plantuml
@startuml Agent_Overview
title Agent 전체 개요

skinparam classAttributeIconSize 0
skinparam shadowing false

package "진입점 / 초기화" {
  class main <<free function>>
  class CliOptions
  class PathManager
  class AppInitializer
  class ProberConfig
}

package "워커 스레드 (free function)" {
  class ManagementWorker <<free function>>
  class TelemetryWorker <<free function>>
  class DatabaseWorker <<free function>>
}

package "통신" {
  class ManagementService
  class TelemetryService
  class TerminalSession
}

package "정책 / 격리" {
  class policy_receiver <<namespace>>
  class quarantine <<namespace>>
}

package "텔레메트리 수집" {
  class TelemetryMonitor
  class collector <<namespace>>
  class cli_parser <<namespace>>
}

package "장치 모델" {
  class NetworkInterface
  class RoutingTable
  class Switch
  class Firewall
  class VmService
}

package "데이터 저장" {
  class DatabaseQueue
  class DatabaseService
  class telemetry_store <<namespace>>
}

package "오프라인 내보내기" {
  class offline <<namespace>>
}

main --> CliOptions : 파싱
main --> PathManager : 경로 결정
main --> AppInitializer : 런타임 준비
AppInitializer --> ProberConfig : 로드/탐지
main --> ManagementWorker : jthread
main --> TelemetryWorker : jthread
main --> DatabaseWorker : jthread

ManagementWorker --> ManagementService : 연결/정책 적용
ManagementWorker --> policy_receiver : 분기 위임
policy_receiver --> quarantine : 격리 명령 처리
ManagementService --> TerminalSession : 영속 CLI 세션

TelemetryWorker --> TelemetryMonitor : 루프 구동
TelemetryMonitor --> collector : 수집
collector ..> cli_parser : 파싱 위임
collector ..> ManagementService : 조회 명령 실행
TelemetryMonitor --> TelemetryService : 전송
TelemetryMonitor ..> offline : 실패 시 스냅샷 저장

DatabaseWorker --> DatabaseQueue : 소비
DatabaseWorker --> DatabaseService : 연결 핸들
telemetry_store --> DatabaseQueue : 저장 태스크 투입
@enduml
```

---

## 2. 진입점 / 초기화 / 설정

```plantuml
@startuml Agent_Bootstrap
title 진입점 · 초기화 · 설정

skinparam classAttributeIconSize 0
skinparam shadowing false

class main <<free function>> {
  + g_running : atomic<bool>
  + signalHandler(signum)
  + main(argc, argv) int
}

class CliOptions {
  + offline_only : bool
  + export_once : bool
  + export_stdout : bool
  + export_dir : string
  + show_help : bool
  + ParseArgs(argc, argv) CliOptions
  + printUsage(program) void
}

class PathManager <<static utility>> {
  + ResolveDataDirectory() fs::path
  + ResolveTemplatePath() fs::path
}

class AppInitializer <<static utility>> {
  + EnsureDataDirectory(path) bool
  + InitializeConfig(path, config) bool
  + InitializeDatabase(db_path, config, handle) bool
  + PrepareRuntime(data_dir, config_path, db_path, template_path, config, handle) bool
}

class ProberConfig {
  - agent_id_ : string
  - agent_name_ : string
  - kernel_name_ : string
  - distribution_name_ : string
  - product_name_ : string
  - device_type_ : DeviceType
  - memory_size_bytes_ : uint64
  - server_ipv4_ : string
  - server_port_ : uint16
  - architecture_ : string
  - management_prefixes_ : string
  + GetAgentId() string
  + GetAgentName() string
  + GetKernelName() string
  + GetDistributionName() string
  + GetProductName() string
  + GetDeviceType() DeviceType
  + GetMemorySizeBytes() uint64
  + GetServerIpv4() string
  + GetServerPort() uint16
  + GetManagementPrefixes() string
  + GetArchitecture() string
  + SetManagementPrefixes(v) void
  + SetAgentName(v) void
  + SetKernelName(v) void
  + SetProduct(v) void
  + SetDistributionName(v) void
  + SetDeviceType(v) void
  + SetMemorySizeBytes(v) void
  + SetServerIpv4(v) void
  + SetServerPort(v) void
  + SetArchitecture(v) void
  + DetectKernelName() void
  + DetectDistributionName() void
  + DetectMemorySizeBytes() void
  + DetectArchitecture() void
  + DetectServerIpv4() void
  + DetectServerPort() void
  + DetectDeviceType() bool
  + DetectProductName() void
  + DetectManagementPrefixes() void
  + DetectAgentName() string
}

class DConfHandler <<internal struct>> {
  + operator()(FILE*) void
}

enum DeviceType <<enumeration>> {
  kSwitch
  kVirtualMachine
  kFirewall
  kRouter
}

main ..> AppInitializer : PrepareRuntime
main ..> PathManager
main ..> CliOptions
AppInitializer ..> ProberConfig
AppInitializer ..> DeviceType
ProberConfig ..> DeviceType
ProberConfig ..> DConfHandler : default.conf RAII
@enduml
```

---

## 3. 워커 스레드 & 통신

```plantuml
@startuml Agent_Workers
title 워커 스레드 · 통신 계층

skinparam classAttributeIconSize 0
skinparam shadowing false

class ManagementWorker <<free function>> {
  + ManagementWorker(stop_token, config)
  .. 1초 command_deadline 동안 서버 push 수신 ..
}

class TelemetryWorker <<free function>> {
  + TelemetryWorker(stop_token, config, queue, offline_dir, offline_only)
}

class DatabaseWorker <<free function>> {
  + DatabaseWorker(stop_token, db_handle, queue)
}

class MonitorWorker <<free function>> {
  + MonitorWorker(stop_token, queue)
  .. 현재 미사용(자리만 유지) ..
}

class ManagementService {
  - host_ : string
  - port_ : int
  - target_ : string
  - ioc_ : net::io_context
  - resolver_ : tcp::resolver
  - stream_ : websocket::stream<beast::tcp_stream>
  - read_buffer_ : beast::flat_buffer
  - connected_ : bool
  - hello_sent_ : bool
  - agent_id_ : string
  - cli_session_ : TerminalSession
  - cli_program_ : string
  - primary_interface_ : string
  - kResponseTimeout : seconds = 5
  - kConnectTimeout : seconds = 5
  - kHandshakeTimeout : seconds = 5
  + connect() bool
  + SetAgentId(agent_id) void
  + SendEnvelope(message) bool
  + fetchPolicy(device_type, device_id, stop_token) json
  + ReportPolicyApplied(device_type, device_id, policy_id, applied) bool
  + TryReceive(message, timeout) bool
  + RunCommand(command) bool
  + RunCommandOutput(command) string
  + ExecuteIosCli(commands) string
  + QueryAristaCli(command) string
  + ApplyOpenVSwitchPolicy(policy) bool
  + ApplyAristaSwitchPolicy(policy) bool
  + ApplyCiscoSwitchPolicy(policy) bool
  + ApplyCiscoRouterPolicy(policy) bool
  + ApplyFrrRouterPolicy(policy) bool
  + ApplyNftablesPolicy(policy) bool
  + ApplyVmPolicy(policy) bool
  - ApplyNetplanPolicy(policy, command) bool
  - ApplyAddressesWithIp(policy, ethernets, command) bool
  - HasCommand(program) bool
  - PrimaryInterface() string
  - ResolveInterfaceName(name) string
  - CliCommand(argv, command) string
  - ResolveAgentId(device_id) string
}

class TerminalHandler <<internal struct>> {
  + operator()(int fd) void
}

class TelemetryService {
  - host_ : string
  - port_ : int
  - target_ : string
  - ioc_ : net::io_context
  - resolver_ : tcp::resolver
  - stream_ : websocket::stream<beast::tcp_stream>
  - connected_ : bool
  - kConnectTimeout : seconds = 5
  - kHandshakeTimeout : seconds = 5
  - initialize(host, port, target) void
  + connect() bool
  + sendText(message) bool
  + receiveText() string
  + tryReceiveText(message, timeout) bool
  + sendRequest(request, target) bool
}

class TerminalSession {
  - master_fd_ : int = -1
  - child_pid_ : pid_t = -1
  + Open(argv) bool
  + Close() void
  + IsOpen() bool
  + Write(data) bool
  + ReadAvailable(timeout) string
  + ReadUntil(prompt, timeout) string
}

class ConnectWithTimeout <<sonar::net>> {
  + ConnectWithTimeout(stream, results, timeout) error_code
  .. non-blocking connect + poll + SO_RCVTIMEO/SO_SNDTIMEO ..
}

class envelope <<namespace>> {
  + kHello / kPolicyRequest / kPolicyResponse
  + kTelemetry / kCommand / kAck / kError
  + kActionQuarantine = "quarantine"
  + kActionRelease = "release"
  + kScopeNode / kScopeConnection / kTargetCidr
  + DeviceTypeToString(device_type) string
  + NextCorrelationId() string
  + Make(type, agent_id, device_type, correlation_id, payload) Json
  + Hello(agent_id, device_type) Json
  + PolicyRequest(agent_id, device_type, device_id) Json
  + Telemetry(agent_id, device_type, payload) Json
  + Type(message) string
  + CorrelationId(message) string
  + IsType(message, expected) bool
  + Payload(message) Json&
  + ErrorText(message) string
}

class network_structures <<components/backend_communication/network.hpp>> {
  + Subnet(subnet_id)
  + NIC(nic_id)
  + VLan = Vlan (alias)
}

ManagementWorker --> ManagementService : ctor/connect
ManagementWorker ..> policy_receiver : ReceivePolicy()
ManagementWorker ..> quarantine : HandleCommand()
ManagementWorker ..> envelope : Type()
ManagementService --> TerminalSession : cli_session_
ManagementService ..> envelope : 봉투 생성
ManagementService ..> ConnectWithTimeout : 연결 타임아웃
TelemetryWorker --> TelemetryMonitor
TelemetryWorker --> ManagementService : 조회 명령용
TelemetryService ..> ConnectWithTimeout
DatabaseWorker --> DatabaseQueue
@enduml
```

> **참고**: `TelemetryService` 는 `using CommunicationService = TelemetryService;` 별칭으로도 쓰입니다.
> `ManagementService` 와 `TelemetryService` 는 모두 `sonar::net::ConnectWithTimeout()` 으로
> 동기 connect/handshake 에 제한 시간을 겁니다 (SIGTERM 시 hang 방지).

---

## 4. 텔레메트리 수집 & 파서

```plantuml
@startuml Agent_Telemetry
title 텔레메트리 모니터 · 수집기 · 파서

skinparam classAttributeIconSize 0
skinparam shadowing false

class TelemetryMonitor {
  - interval_seconds_ : atomic<int> = 30
  - offline_directory_ : string
  - offline_only_ : atomic<bool> = false
  + SetMonitorInterval(interval) void
  + GetMonitorInterval() seconds
  + SetOfflineExportDirectory(directory) void
  + SetOfflineOnly(offline_only) void
  + Run(stop_token, config, queue, mgmt) void
  + CollectSnapshotDocument(config, mgmt) Json <<static>>
}

class collector <<namespace>> {
  + BuildStateFromOutputs(device_type, product_name, outputs) CollectedState
  + CollectState(config, mgmt) CollectedState
}

class CollectedState <<struct>> {
  + snapshot : json
  + nic : json
  + route : json
  + vlan : json
  + trunk : json
  + arp : json
  + rules : json
  + topology : json
  + any_success : bool
}

class ProductKind <<internal enum>> {
  kLinux
  kFrr
  kFirewall
  kCisco
  kArista
  kOpenVSwitch
  kOther
}

class LineFacts <<internal struct>> {
  + addresses : Json
  + mac : string
  + mtu : string
  + state : string
}

class cli_parser <<namespace>> {
  + VendorFromProductName(product_name) Vendor
  + VendorName(vendor) string
  + ParseNicStatus(raw) json
  + ParseNicBrief(raw) json
  + ParseArpTable(raw, vendor) json
  + ParseRouteStatus(raw, vendor) json
  + ParseInterfaceStatus(raw, vendor) json
  + ParseOvsTopology(raw) json
  + ParseSwitchVlan(raw) json
  + ParseSwitchPorts(raw) json
  + ParseRunningConfig(raw) json
  + ParseFirewallRules(raw) json
  + ParseQueryOutput(vendor, target, raw) json
}

class Vendor <<enumeration>> {
  kOpenVSwitch
  kFrr
  kCisco
  kArista
  kNftables
  kUbuntu
  kUnknown
}

class ParseSession <<internal>> {
  .. ANTLR 입력/에러 리스너 관리 ..
}

class CollectingErrorListener <<internal>> {
  + syntaxError(...) void
}

class NicVisitor <<internal>> {
  .. IpAddrBaseVisitor ..
}

class RouteVisitor <<internal>> {
  .. FrrRouterBaseVisitor ..
}

class InterfaceVisitor <<internal>> {
  .. FrrRouterBaseVisitor ..
}

class OvsVisitor <<internal>> {
  .. OvsTopologyBaseVisitor ..
}

class NftablesVisitor <<internal>> {
  .. NftablesRuleBaseVisitor ..
}

class SwitchVisitor <<internal>> {
  .. SwitchTopologyBaseVisitor ..
}

class ArpVisitor <<internal>> {
  .. IpAddrBaseVisitor ..
}

TelemetryMonitor --> collector : CollectState
TelemetryMonitor --> TelemetryService : 스냅샷 전송
TelemetryMonitor ..> offline : 실패 시 저장
collector --> CollectedState : 생성
collector ..> ProductKind : 제품 분류
collector ..> LineFacts : 라인 파싱 보조
collector ..> cli_parser : 파싱 위임
collector ..> ManagementService : 명령 실행
cli_parser ..> Vendor : 파서 선택
cli_parser ..> ParseSession
ParseSession --> CollectingErrorListener
cli_parser ..> NicVisitor
cli_parser ..> RouteVisitor
cli_parser ..> InterfaceVisitor
cli_parser ..> OvsVisitor
cli_parser ..> NftablesVisitor
cli_parser ..> SwitchVisitor
cli_parser ..> ArpVisitor

NicVisitor --|> IpAddrBaseVisitor
ArpVisitor --|> IpAddrBaseVisitor
RouteVisitor --|> FrrRouterBaseVisitor
InterfaceVisitor --|> FrrRouterBaseVisitor
OvsVisitor --|> OvsTopologyBaseVisitor
NftablesVisitor --|> NftablesRuleBaseVisitor
SwitchVisitor --|> SwitchTopologyBaseVisitor

note bottom of cli_parser
  파싱 실패는 예외가 아니라 데이터:
  {"parsed": false, "error": "...", "raw": "..."}
end note
@enduml
```

---

## 5. 정책 적용 & 격리

```plantuml
@startuml Agent_Policy
title 정책 수신 · 벤더 분기 · 격리

skinparam classAttributeIconSize 0
skinparam shadowing false

class policy_receiver <<namespace>> {
  + ReceivePolicy(config, mgmt, policy) void
}

class ReceiveSwitchPolicy <<internal free function>>
class ReceiveRouterPolicy <<internal free function>>
class ReceiveFirewallPolicy <<internal free function>>
class ReceiveVmPolicy <<internal free function>>

class quarantine <<namespace>> {
  + kIsolate = "quarantine"
  + kRelease = "release"
  + kDefaultManagementPrefix = "172.16.255.0/24"
  + IsQuarantineCommand(message) bool
  + Isolate(config, mgmt) Outcome
  + Release(config, mgmt) Outcome
  + HandleCommand(config, mgmt, message) bool
}

class Outcome <<struct>> {
  + ok : bool = false
  + action : string
  + affected : vector<string>
  + preserved : vector<string>
  + detail : string
}

class InterfaceAddress <<internal struct>> {
  + name : string
  + cidr : string
  + address : string
  + prefix_len : int
}

class policy_json <<namespace>> {
  + AsString(object, key, fallback) string
  + AsStringList(object, key) vector<string>
  + Has(object, key) bool
}

policy_receiver ..> ReceiveSwitchPolicy
policy_receiver ..> ReceiveRouterPolicy
policy_receiver ..> ReceiveFirewallPolicy
policy_receiver ..> ReceiveVmPolicy
ReceiveSwitchPolicy ..> ManagementService : Apply*Policy
ReceiveRouterPolicy ..> ManagementService : Apply*Policy
ReceiveFirewallPolicy ..> ManagementService : ApplyNftablesPolicy
ReceiveVmPolicy ..> ManagementService : ApplyVmPolicy

policy_receiver ..> policy_json : 필드 안전 파싱
quarantine --> Outcome : 생성
quarantine ..> InterfaceAddress : 내부 사용
quarantine ..> policy_json : action 읽기
quarantine ..> ManagementService : RunCommand / SendEnvelope
quarantine ..> envelope : ack 생성

note bottom of quarantine
  문자열 계약(반드시 일치):
  quarantine::kIsolate
    == envelope::kActionQuarantine
    == 서버 QuarantineService.ACTION_QUARANTINE
  (Release 도 동일)

  관리 경로(GetManagementPrefixes)는 절대 down 하지 않는다.
  관리 대역을 못 찾으면 ok=false 로 실패 보고(안전장치).
end note
@enduml
```

---

## 6. 장치 모델 (공통 네트워크 객체 · 라우터 · 스위치 · 방화벽 · VM)

```plantuml
@startuml Agent_DeviceModel
title 장치 모델 계층

skinparam classAttributeIconSize 0
skinparam shadowing false

abstract class NetworkInterface {
  + {abstract} Kind() string
  + {abstract} Name() string
  + {abstract} IsInternal() bool
  + {abstract} VlanIds() VlanIdList
  + HasVlans() bool
}

class Nic {
  + name : string
  + index : int
  + parent : string
  + mac : string
  + mtu : string
  + state : string
  + flags : vector<string>
  + link_type : string
  + addresses : vector<Address>
  + Kind() string
  + Name() string
  + IsInternal() bool
  + VlanIds() VlanIdList
  + ToJson() json
}

class NicAddress <<struct>> {
  + family : string
  + address : string
  + prefix_len : string
  + scope : string
  + interface : string
}

class Port {
  + name : string
  + interface_name : string
  + access_vlans : vector<int>
  + trunk_vlans : vector<int>
  + is_internal : bool
  + Kind() string
  + Name() string
  + IsInternal() bool
  + VlanIds() VlanIdList
}

class PortInfo {
  .. Port 를 상속(생성자 상속) ..
}

class Vlan {
  - id_ : int = 0
  - name_ : string
  - mode_ : Mode = kUnknown
  + getVLANID() int
  + Id() int
  + SetId(id) void
  + Name() string
  + SetName(name) void
  + GetMode() Mode
  + SetMode(mode) void
}

class VlanMode <<enumeration>> {
  kUnknown
  kAccess
  kTrunk
  kNative
}

class RoutingTable {
  - connection_id_ : string
  - connected_ip_ : string
  - connected_node_id_ : string
  - routing_protocol_ : string
  - parsed_routing_table_ : string
  - routes_ : vector<RouteEntry>
  + GetConnectionId() string
  + SetConnectionId(v) void
  + GetConnectedIp() string
  + SetConnectedIp(v) void
  + GetConnectedNodeId() string
  + SetConnectedNodeId(v) void
  + GetRoutingProtocol() string
  + SetRoutingProtocolValue(v) void
  + AddRoute(route) void
  + GetRoutes() vector<RouteEntry>&
  + GetRouteCount() size_t
  + ClearRoutes() void
  + ParseFrrTable(raw) void
  + ParseCiscoTable(raw) void
  + ToJson() json
  + SetRoutingProtocol() bool
  + AddConnection() bool
  + RemoveConnection() bool
}

class RouteEntry <<struct>> {
  + prefix : string
  + next_hop : string
  + protocol : string
  + metric : string
  + interface_name : string
}

abstract class RoutingTableView {
  + {abstract} Render(table) string
}

class FrrRoutingTableView {
  + Render(table) string
}

class CiscoRoutingTableView {
  + Render(table) string
}

class Switch {
  - name_ : string
  - routing_table_ : RoutingTable
  - ports_ : map<string, Subnet>
  - bridges_ : vector<BridgeInfo>
  + getName() string
  + setName(name) void
  + loadTopology(raw, vendor) void
  + addRoute(destination, next_hop) void
  + addPort(destination, port) void
  + addVlan(subnet_id, port) void
  + updateRoutingTable(destination, next_hop) void
  + updatePort(destination, port) void
  + getBridges() vector<BridgeInfo>&
}

class BridgeInfo <<struct>> {
  + name : string
  + ports : vector<PortInfo>
}

class SwitchVendor <<enumeration>> {
  CiscoIosXe
  AristavEOS
  OpenVSwitch
}

abstract class TopologyParser {
  + {abstract} parse(raw) vector<BridgeInfo>
}

class OpenVSwitchTopologyParser {
  + parse(raw) vector<BridgeInfo>
}

class AristaTopologyParser {
  + parse(raw) vector<BridgeInfo>
}

class CiscoTopologyParser {
  + parse(raw) vector<BridgeInfo>
}

class Firewall {
  - tables_ : vector<NftTable>
  + parseNftablesRuleset(raw) void
  + tables() vector<NftTable>&
  + tableCount() size_t
}

class NftTable <<struct>> {
  + family : string
  + name : string
  + chains : vector<NftChain>
}

class NftChain <<struct>> {
  + family : string
  + name : string
  + type : string
  + hook : string
  + priority : string
  + policy : string
  + rules : vector<NftRule>
}

class NftRule <<struct>> {
  + raw : string
  + statement : string
  + match : string
  + action : string
  + connection_state : string
}

class VmService <<static utility>> {
  + CollectNicStatus() json
}

NetworkInterface <|-- Nic
NetworkInterface <|-- Port
Port <|-- PortInfo
Nic *-- NicAddress
NetworkInterface ..> Vlan : VlanIds() / VlanIdList
Vlan *-- VlanMode

RoutingTable *-- RouteEntry
RoutingTableView <|-- FrrRoutingTableView
RoutingTableView <|-- CiscoRoutingTableView
RoutingTable ..> RoutingTableView : 렌더링 위임
RoutingTable ..> FrrRoutingTableView
RoutingTable ..> CiscoRoutingTableView

Switch *-- RoutingTable
Switch *-- BridgeInfo
BridgeInfo *-- PortInfo
Switch ..> SwitchVendor : 파서 선택
TopologyParser <|-- OpenVSwitchTopologyParser
TopologyParser <|-- AristaTopologyParser
TopologyParser <|-- CiscoTopologyParser
Switch ..> TopologyParser : loadTopology 위임

Firewall *-- NftTable
NftTable *-- NftChain
NftChain *-- NftRule

ManagementService --> RoutingTable : 사용
ManagementService --> Switch : 사용
ManagementService --> Firewall : 사용
collector ..> VmService : (VM) 수집
@enduml
```

---

## 7. 데이터베이스 & 텔레메트리 저장

```plantuml
@startuml Agent_Database
title 데이터베이스 · 저장 계층

skinparam classAttributeIconSize 0
skinparam shadowing false

class DbHandler <<struct>> {
  + operator()(sqlite3*) void
}

class DbHandle <<alias>> {
  .. unique_ptr<sqlite3, DbHandler> ..
}

class DatabaseResult <<struct>> {
  + db_task_result : vector<string>
  + sql_task : string
}

class DatabaseTask <<struct>> {
  + execute : function<DatabaseResult(DbHandle&)>
  + result : promise<DatabaseResult>
}

class DatabaseQueue {
  - mutex_ : mutex
  - condition_ : condition_variable_any
  - tasks_ : queue<DatabaseTask>
  - closed_ : bool = false
  + Push(task) bool
  + Pop(stop_token, task) bool
  + Close() void
}

class DatabaseService {
  - implementation_ : unique_ptr<Impl>
  + Start() void
  + UpdateStatus(value) void
  + SaveConfig(key, value) void
  - Worker(stop_token) void
}

class Impl <<internal>> {
  .. pimpl 실제 sqlite3 워커 상태 ..
}

class db_helpers <<free function>> {
  + RunDatabaseTask(queue, execute) DatabaseResult
  + RunDatabaseWrite(queue, execute) bool
  + SaveSetting(queue, key, value) bool
  + LoadSetting(queue, key) string
}

class telemetry_store <<namespace>> {
  + CurrentUtcTimestamp() string
  + EnqueueRouteStatusSave(queue, agent, route, at) bool
  + EnqueueNicInfoSave(queue, agent, nic, at) bool
  + EnqueueVlanStatusSave(queue, agent, vlan, at) bool
  + EnqueueTrunkStatusSave(queue, agent, trunk, at) bool
  + EnqueueArpTableSave(queue, agent, arp, at) bool
}

class ColumnValue <<internal struct>> {
  - kind : Kind = kText
  - text : string
  - number : long long
  - present : bool
  + Text(value) ColumnValue
  + Integer(value) ColumnValue
}

class ColumnKind <<internal enum>> {
  kText
  kInteger
}

class database_schema <<namespace>> {
  + CreateTablesSql() string&
}

DatabaseService *-- Impl
DatabaseQueue *-- DatabaseTask
DatabaseTask *-- DatabaseResult
DbHandle ..> DbHandler
db_helpers ..> DatabaseQueue
db_helpers ..> DatabaseResult
telemetry_store ..> DatabaseQueue : 태스크 투입
telemetry_store ..> ColumnValue : 바인딩 값
ColumnValue *-- ColumnKind
database_schema ..> AppInitializer : DDL 단일 진실
DatabaseWorker --> DatabaseQueue : Pop
DatabaseWorker --> DbHandle : 소유
@enduml
```

---

## 8. 오프라인 내보내기

```plantuml
@startuml Agent_Offline
title 오프라인 스냅샷 내보내기

skinparam classAttributeIconSize 0
skinparam shadowing false

class offline <<namespace>> {
  + kSchemaName = "sonar.offline.snapshot"
  + kSchemaVersion = 1
  + kFilePrefix = "sonar_snapshot_"
  + kFileExtension = ".json"
  + ExportOnce(dir, agent_id, snapshot, export_stdout) int
  + SanitizeForFileName(text) string
  + ResolveExportDirectory(data_dir, override_dir) string
  + BuildSnapshotFileName(agent_id, collected_at) string
  + BuildSnapshotDocument(...) Json
  + WriteSnapshot(directory, file_name, document) ExportResult
  + ExportSnapshot(directory, agent_id, collected_at, document) ExportResult
  + ListPendingSnapshots(directory) vector<string>
  + ReadSnapshot(path) Json
  + RemoveSnapshot(path) bool
}

class ExportResult <<struct>> {
  + saved : bool = false
  + skipped : bool = false
  + path : string
  + message : string
}

class reason <<namespace>> {
  + kServerUnreachable = "server-unreachable"
  + kSendFailed = "send-failed"
  + kForcedOffline = "offline-mode"
  + kManualExport = "manual-export"
}

offline --> ExportResult : 생성
offline ..> reason : 오프라인 추출 사유
offline ..> TelemetryMonitor : CollectSnapshotDocument 결과 사용
main ..> offline : ExportOnce / ResolveExportDirectory
TelemetryMonitor ..> offline : 전송 실패 시 저장
@enduml
```

---

## 9. 열거형 요약

| 열거형                                 | 정의 위치                                                | 값                                                                                            | 용도                 |
| -------------------------------------- | -------------------------------------------------------- | --------------------------------------------------------------------------------------------- | -------------------- |
| `DeviceType`                         | `components/device/device_type.hpp`                    | `kSwitch`, `kVirtualMachine`, `kFirewall`, `kRouter`                                  | 정책/명령 분기 기준  |
| `cli_parser::Vendor`                 | `components/parser/cli_output_parser.hpp`              | `kOpenVSwitch`, `kFrr`, `kCisco`, `kArista`, `kNftables`, `kUbuntu`, `kUnknown` | 파서 출력 형식 선택  |
| `collector::ProductKind`             | `module/telemetry_module/command_collector.cpp` (내부) | `kLinux`, `kFrr`, `kFirewall`, `kCisco`, `kArista`, `kOpenVSwitch`, `kOther`    | 조회 명령 선택       |
| `SwitchVendor`                       | `components/device/switch/switch_interface/switch.hpp` | `CiscoIosXe`, `AristavEOS`, `OpenVSwitch`                                               | 토폴로지 파서 선택   |
| `Vlan::Mode`                         | `components/network_object/vlan.hpp`                   | `kUnknown`, `kAccess`, `kTrunk`, `kNative`                                            | VLAN 취급 모드       |
| `telemetry_store::ColumnValue::Kind` | `database/telemetry_store.cpp` (내부)                  | `kText`, `kInteger`                                                                       | SQL 바인딩 타입 구분 |

---

## 10. 주요 설계 계약 (문자열 · 안전장치)

- **격리 문자열 계약**: `quarantine::kIsolate` == `envelope::kActionQuarantine` == 서버 `QuarantineService.ACTION_QUARANTINE` (Release 동일). 한 글자만 달라도 격리 버튼이 조용히 무시됩니다.
- **관리 경로 보존**: 격리는 `ProberConfig::GetManagementPrefixes()` 대역을 절대 내리지 않습니다. 관리 대역을 찾지 못하면 `ok=false`로 실패 보고(원격 장치 복구 불가 방지).
- **연결 격리(`scope=connection`)**: 에이전트는 인터페이스를 건드리지 않습니다(방화벽 서버 규칙 처리). 이중 안전장치.
- **오프라인/온라인 동일 payload**: 두 경로가 같은 파서를 공유하도록 payload 구조를 일치시킵니다.
- **파서 실패 = 데이터**: `cli_parser`는 예외를 던지지 않고 `{"parsed": false, ...}`를 반환합니다(수집 루프 보호).
- **동기 연결 타임아웃**: `sonar::net::ConnectWithTimeout()`이 없으면 SIGTERM 후에도 워커가 join에서 멈춥니다.

---

## 11. 내부(익명 네임스페이스) 헬퍼 목록

다이어그램의 `<<internal>>` 요소들은 `.cpp` 내부(익명 네임스페이스/파일 지역)에서만 쓰이는 구현 세부입니다.

| 심볼                                                        | 위치                                                | 역할                          |
| ----------------------------------------------------------- | --------------------------------------------------- | ----------------------------- |
| `DConfHandler`                                            | `module/configuration_module/prober_config.cpp`   | `FILE*` RAII 삭제자         |
| `TerminalHandler`                                         | `module/management_module/management_service.cpp` | pty`master_fd_` RAII 삭제자 |
| `PipeCloser`                                              | `components/device/vm/ubuntu/vm_service.cpp`      | `popen` 파이프 RAII         |
| `ProductKind`, `LineFacts`                              | `module/telemetry_module/command_collector.cpp`   | 제품 분류 / 라인 파싱 보조    |
| `InterfaceAddress`                                        | `components/policy/quarantine_handler.cpp`        | 격리 대상 주소 표현           |
| `ColumnValue`, `Kind`                                   | `database/telemetry_store.cpp`                    | SQL 바인딩 값 표현            |
| `DatabaseService::Impl`                                   | `database/database_service.hpp`                   | pimpl                         |
| `ParseSession`, `CollectingErrorListener`, `*Visitor` | `components/parser/cli_output_parser.cpp`         | ANTLR 파싱 세션/방문자        |
| `ReceiveSwitch/Router/Firewall/VmPolicy`                  | `components/policy/policy_receiver.cpp`           | 벤더별 정책 분기              |

> `MonitorWorker` 는 현재 사용되지 않는 자리(placeholder)이며 `main.cpp`에서 기동하지 않습니다.
