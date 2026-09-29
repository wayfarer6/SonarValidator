---
title: 클래스 다이어그램 (Prober + Backend) v1
sidebar_position: 1
---

# 클래스 다이어그램 v1

프로버(C++23)와 백엔드(Spring Boot / Java 26)의 <b>실제 클래스</b>를 정리한
버전 1 입니다. 설계 의도가 아니라 <b>지금 코드에 있는 것</b>을 그렸습니다.

## 이 문서를 읽는 순서

1. [전체 조감도](#1-전체-조감도) — 두 프로세스가 어디서 만나는지
2. [프로버](#2-프로버-c23) — 수집 → 저장 → 전송 파이프라인
3. [백엔드](#3-백엔드-spring-boot) — 수신 → 변환 → 검증 → 노출
4. [접점 계약](#4-접점-계약-envelope) — 양쪽이 공유하는 유일한 계약
5. [DB 스키마](#5-db-스키마) — 엔티티와 테이블

---

## 1. 전체 조감도

두 프로세스는 <b>WebSocket 하나</b>로만 만납니다. 프로버는 DB(SQLite)를
로컬에, 백엔드는 별도 DB(PostgreSQL)를 가지며 서로의 DB를 직접 보지 않습니다.

```mermaid
flowchart LR
    subgraph DEV["네트워크 장비 (스위치 / 라우터 / 방화벽 / VM)"]
        CLI["CLI · pty<br/>ip a / show ip route / ovs-vsctl …"]
    end

    subgraph PROBER["SonarValidator_Prober (C++23)"]
        COL["collector<br/>CollectState()"]
        TEL["telemetry<br/>TelemetryMonitor"]
        DBQ["database<br/>DatabaseQueue → SQLite"]
        MGMT["ManagementService<br/>WebSocket 클라이언트"]
    end

    subgraph BACKEND["SonarValidator_Backend (Java 26)"]
        H["AgentWebSocketHandler"]
        R["AgentMessageRouterService"]
        CFG["DeviceConfigService<br/>+ 벤더 파서 6종"]
        BDD["SegmentationBddEngine<br/>BDD 망분리 검증"]
        RDB[("PostgreSQL")]
    end

    FE["프론트엔드 (React / nginx)"]

    CLI -->|"명령 실행"| COL
    COL --> TEL
    TEL -->|"Enqueue*Save"| DBQ
    TEL -->|"telemetry 봉투"| MGMT
    MGMT <-->|"raw WebSocket + JSON<br/>/api/v1/management · /api/v1/telemetry"| H
    H --> R
    R --> CFG
    R --> RDB
    CFG --> RDB
    BDD --> RDB
    RDB --> FE
```

<b>핵심</b>: 프로버는 <b>능동적으로 밀어 넣고</b>(telemetry), 백엔드는
<b>능동적으로 밀어 넣습니다</b>(command). 요청-응답이 필요한 경우(정책)만
애플리케이션 레벨 `correlation_id` 로 짝을 맞춥니다.

---

## 2. 프로버 (C++23)

### 2-1. 진입점과 런타임 준비

```mermaid
classDiagram
    direction TB

    class DeviceType {
        <<enumeration>>
        kSwitch
        kVirtualMachine
        kFirewall
        kRouter
    }

    class ProberConfig {
        -string agent_id_
        -string agent_name_
        -string kernel_name_
        -string distribution_name_
        -string product_name_
        -DeviceType device_type_
        -uint64 memory_size_bytes_
        -string server_ipv4_
        -uint16 server_port_
        -string architecture_
        +GetAgentId() string
        +GetProductName() string
        +GetDeviceType() DeviceType
        +DetectKernelName()
        +DetectDistributionName()
        +DetectMemorySizeBytes()
        +DetectArchitecture()
        +DetectServerIpv4()
        +DetectServerPort()
        +DetectDeviceType() bool
        +DetectProductName()
    }

    class AppInitializer {
        <<utility>>
        +EnsureDataDirectory(path) bool
        +InitializeConfig(path, config) bool
        +InitializeDatabase(path, config, handle) bool
        +PrepareRuntime(dir, cfg, db, tpl, config, handle) bool
    }

    class TerminalSession {
        -int master_fd_
        -pid_t child_pid_
        +Open(argv) bool
        +Write(line) bool
        +ReadUntilIdle(timeout) string
        +Close()
    }

    ProberConfig --> DeviceType : device_type_
    AppInitializer ..> ProberConfig : 채우고 검증
```

`ProberConfig` 는 <b>세 가지 출처</b>를 합칩니다: 설정 파일, 로컬 시스템 탐지
(`Detect*`), 그리고 실행 인자. `DetectProductName()` 이 벤더 파서 선택의
근거가 되므로(백엔드가 이 값으로 파서를 고름) 틀리면 수집 전체가 무의미해집니다.

### 2-2. 수집과 저장

```mermaid
classDiagram
    direction TB

    class CollectedState {
        +json snapshot
        +json nic
        +json route
        +json vlan
        +json trunk
        +json arp
        +json rules
        +json topology
        +bool any_success
    }

    class ManagementService {
        -string host_
        -int port_
        -string target_
        -bool connected_
        -string agent_id_
        -TerminalSession cli_session_
        +Connect() bool
        +RunCommand(cmd) bool
        +RunCommandOutput(cmd) string
        +ExecuteIosCli(cmds) string
        +QueryAristaCli(cmd) string
        +SendEnvelope(msg) bool
        +fetchPolicy(type, id) json
        +TryReceive(msg, timeout) bool
        +ReportPolicyApplied(type, id, pid, ok) bool
        +ApplyOpenVSwitchPolicy(policy) bool
        +ApplyAristaSwitchPolicy(policy) bool
        +ApplyFrrRouterPolicy(policy) bool
        +ApplyNftablesPolicy(policy) bool
        +ApplyVmPolicy(policy) bool
    }

    class BuildStateFromOutputs {
        <<free function>>
        +BuildStateFromOutputs(type, product, outputs) CollectedState
    }

    class CollectState {
        <<free function>>
        +CollectState(config, mgmt) CollectedState
    }

    class TelemetryMonitor {
        -atomic~int~ interval_seconds_ = 30
        -string offline_directory_
        -atomic~bool~ offline_only_
        +SetMonitorInterval(interval)
        +SetOfflineExportDirectory(dir)
        +SetOfflineOnly(flag)
        +Run(stop_token, config, queue, mgmt)
        +CollectSnapshotDocument(config, mgmt) json
    }

    class DatabaseService {
        -unique_ptr~Impl~ implementation_
        +Start()
        +UpdateStatus(value)
        +SaveConfig(key, value)
        -Worker(stop_token)
    }

    class DatabaseQueue {
        -mutex mutex_
        -condition_variable_any condition_
        -queue~DatabaseTask~ tasks_
        -bool closed_
        +Push(task) bool
        +Pop(stop_token, task) bool
        +Close()
    }

    class DatabaseTask {
        +function~DatabaseResult(DbHandle&)~ execute
        +promise~DatabaseResult~ result
    }

    class DatabaseResult {
        +vector~string~ db_task_result
        +string sql_task
    }

    class DbHandler {
        <<functor>>
        +operator()(sqlite3* db)
    }

    class TelemetryStore {
        <<free functions>>
        +CurrentUtcTimestamp() string
        +EnqueueRouteStatusSave(queue, …) bool
        +EnqueueNicInfoSave(queue, …) bool
        +EnqueueVlanStatusSave(queue, …) bool
        +EnqueueTrunkStatusSave(queue, …) bool
        +EnqueueArpTableSave(queue, …) bool
    }

    class DatabaseSchema {
        <<free function>>
        +CreateTablesSql() string
    }

    CollectState ..> ManagementService : 명령 실행
    CollectState ..> BuildStateFromOutputs : 출력 파싱 위임
    TelemetryMonitor ..> CollectState : 30초마다 호출
    TelemetryMonitor ..> TelemetryStore : 수집 결과 큐에 적재
    TelemetryMonitor ..> ManagementService : telemetry 봉투 전송
    TelemetryStore ..> DatabaseQueue : Push
    DatabaseQueue o-- DatabaseTask
    DatabaseTask o-- DatabaseResult
    DatabaseService --> DatabaseQueue : Pop 후 실행
    DbHandler ..> DatabaseSchema : 최초 기동 시 DDL
```

<b>설계 포인트</b>: 수집은 <b>두 갈래</b>로 나갑니다.

- `snapshot` 은 서버로 (JSON 한 덩어리)
- `nic` / `route` / `vlan` / `trunk` / `arp` 는 SQLite 로 (테이블별 행)

같은 수집 결과를 목적에 따라 다르게 씁니다. 서버는 분석용, SQLite 는
로컬 이력·오프라인 재전송용입니다.

### 2-3. 벤더별 정책 적용과 토폴로지 파싱

```mermaid
classDiagram
    direction TB

    class Firewall {
        -vector~NftTable~ tables_
        +parseNftablesRuleset(raw)
        +tableCount() size_t
    }

    class NftTable {
        +string family
        +string name
        +vector~NftChain~ chains
    }

    class NftChain {
        +string family
        +string name
        +string type
        +string hook
        +string priority
        +string policy
        +vector~NftRule~ rules
    }

    class NftRule {
        +string raw
        +string statement
        +string match
        +string action
        +string connection_state
    }

    class TopologyParser {
        <<interface>>
        +parse(raw) vector~BridgeInfo~
    }

    class OpenVSwitchTopologyParser {
        +parse(raw) vector~BridgeInfo~
    }

    class CiscoTopologyParser {
        +parse(raw) vector~BridgeInfo~
    }

    class AristaTopologyParser {
        +parse(raw) vector~BridgeInfo~
    }

    class Switch {
        -SwitchVendor vendor_
        -vector~BridgeInfo~ bridges_
    }

    class SwitchVendor {
        <<enumeration>>
        kUnknown
        kOpenVSwitch
        kCisco
        kArista
    }

    class BridgeInfo {
        +string name
        +vector~PortInfo~ ports
    }

    class PortInfo {
        +string name
        +string interface_name
        +vector~int~ access_vlans
        +vector~int~ trunk_vlans
    }

    class RoutingTable {
        -string connection_id_
        -string connected_ip_
        -string connected_node_id_
        -string routing_protocol_
        -vector~RouteEntry~ routes_
        +AddRoute(entry)
        +GetRouteCount() size_t
        +ParseFrrTable(raw)
        +GetRoutingTabe(router_id)
    }

    class RouteEntry {
        +string prefix
        +string next_hop
        +string protocol
        +string metric
        +string interface_name
    }

    class RoutingTableView {
        <<interface>>
        +Render(table) string
    }

    class FrrRoutingTableView {
        +Render(table) string
    }

    class CiscoRoutingTableView {
        +Render(table) string
    }

    class VmService {
        +Run() bool
    }

    class Switch {
        +ApplyPolicy(policy) bool
    }

    TopologyParser <|-- OpenVSwitchTopologyParser
    TopologyParser <|-- CiscoTopologyParser
    TopologyParser <|-- AristaTopologyParser
    RoutingTableView <|-- FrrRoutingTableView
    RoutingTableView <|-- CiscoRoutingTableView
    Switch --> SwitchVendor
    Switch o-- BridgeInfo
    BridgeInfo o-- PortInfo
    RoutingTable o-- RouteEntry
    Firewall o-- NftTable
    NftTable o-- NftChain
    NftChain o-- NftRule
```

<b>Strategy 패턴이 두 곳</b>에 있습니다: `TopologyParser`(L2 토폴로지)와
`RoutingTableView`(라우팅 출력 렌더링). 새 벤더는 파서 하나만 추가하면 되고
호출부는 바뀌지 않습니다.

### 2-4. 정책 수신 (서버 → 장비)

```mermaid
classDiagram
    direction TB

    class PolicyReceiver {
        <<component>>
        +Handle(policy) bool
    }

    class PolicyJson {
        <<free functions>>
        +Extract(key, doc) vector~string~
    }

    class ManagementService {
        +ApplyOpenVSwitchPolicy(policy) bool
        +ApplyAristaSwitchPolicy(policy) bool
        +ApplyCiscoSwitchPolicy(policy) bool
        +ApplyCiscoRouterPolicy(policy) bool
        +ApplyFrrRouterPolicy(policy) bool
        +ApplyNftablesPolicy(policy) bool
        +ApplyVmPolicy(policy) bool
    }

    class Envelope {
        <<free functions>>
        +kHello
        +kPolicyRequest
        +kPolicyResponse
        +kTelemetry
        +kCommand
        +kAck
        +kError
        +DeviceTypeToString(type) string
        +NextCorrelationId() string
        +Make(type, agent_id, type, correlation, payload) json
    }

    PolicyReceiver ..> PolicyJson : 조건 추출
    PolicyReceiver --> ManagementService : 벤더별 Apply 위임
    ManagementService ..> Envelope : 봉투 생성
```

`ManagementService` 가 <b>7개의 Apply*</b> 를 모두 들고 있는 것은 이 버전의
약점입니다. 벤더가 늘면 God Class 가 됩니다. (v2 검토 대상)

---

## 3. 백엔드 (Spring Boot)

### 3-1. WebSocket 수신 계층

```mermaid
classDiagram
    direction TB

    class WebSocketConfig {
        +MANAGEMENT_PATH = "/api/v1/management"
        +TELEMETRY_PATH = "/api/v1/telemetry"
        +registerWebSocketHandlers(registry)
        +createWebSocketContainer() ServletServerContainerFactoryBean
    }

    class AgentWebSocketHandler {
        -ObjectMapper objectMapper
        -AgentMessageRouterService router
        -AgentSessionRegistry registry
        +afterConnectionEstablished(session)
        +handleTextMessage(session, message)
        +afterConnectionClosed(session, status)
        +handleTransportError(session, ex)
    }

    class AgentMessageRouterService {
        -AgentSessionRegistry registry
        -PolicyRegistryService policyRegistry
        -DeviceConfigService deviceConfigService
        -LogService logService
        -NotificationService notificationService
        -Map~String,JsonNode~ lastTelemetry
        -Map~String,NeutralDeviceConfig~ lastConfig
        +onEnvelope(session, envelope)
        -onHello(envelope, agentId) Envelope
        -onPolicyRequest(envelope, agentId) Envelope
        -onTelemetry(envelope, agentId) Envelope
        +lastConfigOf(agentId) NeutralDeviceConfig
        +acceptOfflineTelemetry(agentId, product, payload) NeutralDeviceConfig
    }

    class AgentSessionRegistry {
        -ObjectMapper objectMapper
        -Map~String,SessionHolder~ sessions
        +register(agentId, session)
        +remove(session)
        +send(agentId, envelope) bool
        +broadcast(envelope) int
        +agentIds() Set~String~
    }

    class Envelope {
        +string type
        +string agent_id
        +string device_type
        +string correlation_id
        +JsonNode payload
        +string error
        +of(type) Envelope
        +reply(type, correlationId, payload) Envelope
        +replyTo(type, request, payload) Envelope
        +error(correlationId, msg) Envelope
        +payloadOrEmpty() JsonNode
        +Types HELLO, POLICY_REQUEST, …
        +Types
    }

    class Types {
        +HELLO = "hello"
        +POLICY_REQUEST = "policy-request"
        +POLICY_RESPONSE = "policy-response"
        +TELEMETRY = "telemetry"
        +COMMAND = "command"
        +ACK = "ack"
        +ERROR = "error"
        +UNKNOWN = "unknown"
    }

    WebSocketConfig --> AgentWebSocketHandler : 등록
    AgentWebSocketHandler --> AgentMessageRouterService : type 분기 위임
    AgentWebSocketHandler --> AgentSessionRegistry
    AgentMessageRouterService --> AgentSessionRegistry : 푸시 전송
    AgentWebSocketHandler ..> Envelope : 역직렬화
    Envelope ..> Types
```

<b>분기의 단일 진입점</b>이 `AgentMessageRouterService` 입니다. 봉투의 `type`
하나로 갈라지고, `telemetry`/`ack`/`error` 는 <b>응답하지 않습니다</b>(null 반환).

### 3-2. 텔레메트리 → 설정 변환

```mermaid
classDiagram
    direction TB

    class DeviceConfigService {
        -List~DeviceConfigParser~ parsers
        +parserFor(product, deviceType) Optional~DeviceConfigParser~
        +parse(hostname, product, payload) NeutralDeviceConfig
        +formats() List~string~
    }

    class DeviceConfigParser {
        <<interface>>
        +supports(product, deviceType) bool
        +parse(hostname, product, payload) NeutralDeviceConfig
        +format() string
    }

    class AbstractDeviceConfigParser {
        <<abstract>>
        #newConfig(hostname, product, payload) NeutralDeviceConfig
        #applyNicStatus(config, payload)
        #applyRouteStatus(config, payload)
        #applyArpTable(config, payload)
        #applyVlanStatus(config, payload)
        #applyTrunkStatus(config, payload)
    }

    class CiscoRouterConfigParser {
        +supports(product, deviceType) bool
        +format() string
    }
    class FrrRouterConfigParser {
        +supports(product, deviceType) bool
    }
    class AlpineFirewallConfigParser {
        +supports(product, deviceType) bool
    }
    class OpenVSwitchConfigParser {
        +supports(product, deviceType) bool
    }
    class LinuxVmConfigParser {
        +supports(product, deviceType) bool
    }
    class AristaSwitchConfigParser {
        +supports(product, deviceType) bool
    }

    class NeutralDeviceConfig {
        -string hostname
        -string vendor
        -string product
        -string deviceType
        -string kernel
        -string format
        -Map~string,InterfaceConfig~ interfaces
        -List~RouteConfig~ routes
        -Map~int,VlanConfig~ vlans
        -List~ArpEntry~ arpEntries
        -List~string~ firewallRules
        -List~string~ warnings
        -List~string~ bridges
        -Map~string,string~ metadata
        +interfaceOrCreate(name) InterfaceConfig
        +vlanOrCreate(vlanId) VlanConfig
        +addressesByInterface() Map
        +isEmpty() bool
    }

    class InterfaceConfig {
        +string name
        +string description
        +string macAddress
        +Integer mtu
        +string adminState
        +string operState
        +List~string~ addresses
        +Integer accessVlan
        +List~Integer~ trunkVlans
        +string mode
        +string parent
        +List~string~ flags
    }

    class RouteConfig {
        +string protocol
        +string prefix
        +string nextHop
        +string nextHopInterface
        +Long metric
        +Boolean selected
        +Boolean defaultRoute
    }

    class VlanConfig {
        +Integer vlanId
        +string name
        +string status
        +List~string~ members
    }

    class ArpEntry {
        +string address
        +string mac
        +string interfaceName
        +string state
    }

    DeviceConfigParser <|.. AbstractDeviceConfigParser
    AbstractDeviceConfigParser <|-- CiscoRouterConfigParser
    AbstractDeviceConfigParser <|-- FrrRouterConfigParser
    AbstractDeviceConfigParser <|-- AlpineFirewallConfigParser
    AbstractDeviceConfigParser <|-- OpenVSwitchConfigParser
    AbstractDeviceConfigParser <|-- LinuxVmConfigParser
    AbstractDeviceConfigParser <|-- AristaSwitchConfigParser
    DeviceConfigService o-- DeviceConfigParser : List 주입
    DeviceConfigService ..> NeutralDeviceConfig : 산출
    NeutralDeviceConfig *-- InterfaceConfig
    NeutralDeviceConfig *-- RouteConfig
    NeutralDeviceConfig *-- VlanConfig
    NeutralDeviceConfig *-- ArpEntry
```

<b>파서는 예외를 던지지 않습니다.</b> 텔레메트리 경로에서 예외는 수집 유실로
이어지므로, 실패는 `warnings` 에 담고 빈 설정을 돌려줍니다.

### 3-3. BDD 망분리 검증 엔진

```mermaid
classDiagram
    direction TB

    class BddManager {
        -int variableCount
        -List~BddNode~ nodes
        -Map~UniqueKey,BddNode~ uniqueTable
        -Map~int,BddNode~ notCache
        -Map~ApplyKey,BddNode~ applyCache
        +zero() BddNode
        +one() BddNode
        +variable(index) BddNode
        +and(a, b) BddNode
        +or(a, b) BddNode
        +not(a) BddNode
        +anySat(node) Map~int,Boolean~
        +nodeCount() int
    }

    class BddNode {
        -int id
        -int variable
        -BddNode low
        -BddNode high
        +FALSE
        +TRUE
        +id() int
        +variable() int
        +low() BddNode
        +high() BddNode
        +isTerminal() bool
    }

    class PacketVariables {
        <<utility>>
        +IP_BITS = 32
        +PORT_BITS = 16
        +SRC_IP_OFFSET = 0
        +DST_IP_OFFSET = 32
        +PORT_OFFSET = 64
        +TOTAL_BITS = 80
        +ANY_PORT = -1
        +newManager() BddManager
        +cidr(manager, cidr, offset) BddNode
        +port(manager, port) BddNode
        +parseIp(ip) int
        +parseCidr(cidr) ParsedCidr
        +extractSourceIp(assignment) string
        +extractTargetIp(assignment) string
    }

    class SegmentationBddEngine {
        -BddManager manager
        +validate(subnets, rules) Report
        +checkPair(source, target, port) Optional~PolicyViolation~
    }

    class Report {
        +bool compliant
        +int ruleCount
        +int subnetCount
        +int violationCount
    }

    class PolicySubnet {
        -string id
        -string cidr
        -ZoneClass zoneClass
        -string name
        -string agentId
        -bool manuallyEdited
        +toPolicySubnet() PolicySubnet
    }

    class PolicyRule {
        -string id
        -string source
        -string destination
        -int port
        -bool enabled
        -string note
        +Origin
    }

    class PolicyViolation {
        <<record>>
        +string ruleId
        +string source
        +string target
        +Severity severity
        +string sampledSourceIp
        +string sampledTargetIp
        +sampledPacket() string
        +groupByRule(violations) Map
        +Severity
    }

    class ZoneClass {
        <<enumeration>>
        OPEN
        SENSITIVE
        CONFIDENTIAL
        +label() string
        +level() int
        +fromString(value) ZoneClass
        +allowsDirectConnection(a, b) bool
        +forbidsDirectConnection(a, b) bool
    }

    SegmentationBddEngine --> BddManager
    SegmentationBddEngine ..> PacketVariables : 조건 → BDD 변환
    SegmentationBddEngine ..> PolicyViolation : 반례 생성
    SegmentationBddEngine ..> PolicySubnet
    SegmentationBddEngine ..> PolicyRule
    BddManager o-- BddNode
    PacketVariables ..> BddManager
    PacketVariables ..> ParsedCidr
    PolicySubnet --> ZoneClass
    PolicyViolation --> ZoneClass : Severity 는 별도
```

<b>왜 BDD 인가</b>: 주소 공간이 2^32 라 열거가 불가능합니다. BDD 는
접두사로 정의된 집합을 <b>구조적으로</b> 다루므로, 허용집합 ∩ 금지집합이
비어 있지 않은지 O(노드 수) 로 판정하고 `anySat()` 으로 <b>반례 패킷</b>까지
뽑아냅니다. 사용자는 "위반 3건" 과 함께 `10.0.0.0 → 10.0.1.0:443` 을 봅니다.

### 3-4. REST 계층과 서비스

```mermaid
classDiagram
    direction TB

    class ProjectController {
        -ProjectService projectService
    }
    class PolicyManagement {
        -ProjectService projectService
        -AgentSessionRegistry registry
        -NotificationService notificationService
    }
    class NetworkTopologyController {
        -ProjectService projectService
        -AgentMessageRouterService router
        -AgentSessionRegistry registry
    }
    class RouterController {
        -AgentMessageRouterService router
    }
    class AgentStatusController {
        -AgentSessionRegistry registry
        -AgentMessageRouterService router
        -DeviceConfigService deviceConfigService
    }
    class OfflineImportController {
        -OfflineSnapshotService snapshotService
        -AgentMessageRouterService router
    }
    class LogController {
        -LogService logService
        -LogAnalysisEngine analysisEngine
    }
    class OPNsenseController {
        -OPNsenseCredentialService credentialService
        -OPNsenseCredentialRepository repository
        -OPNsenseApiClient apiClient
        -AgentSessionRegistry registry
        -AgentMessageRouterService router
    }
    class AuthController {
        -AuthenticationManager authenticationManager
        -UserService userService
    }
    class UserController {
        -UserService userService
    }
    class NotificationController {
        -NotificationService notificationService
    }
    class ComplianceController {
        -ComplianceService complianceService
    }
    class AiProviderController {
        -AiProviderService providerService
    }

    class ProjectService {
        -ProjectRepository repository
        -ComplianceService complianceService
        -NotificationService notificationService
        +createProject(dto) Project
        +updateProject(key, dto) Project
        +deleteProject(key)
        +validate(key) Report
    }
    class ComplianceService {
        -ComplianceChangeRepository repository
        +record(change) ComplianceChange
    }
    class NotificationService {
        -NotificationRepository repository
        +notify(...) Notification
        +markRead(id)
    }
    class UserService {
        -AppUserRepository repository
        -PasswordEncoder passwordEncoder
        +create(...) AppUser
        +changePassword(...)
        +delete(id)
    }
    class LogService {
        -DeviceLogRepository repository
        -LogNormalizer normalizer
        +ingest(...) DeviceLog
    }
    class LogAnalysisEngine {
        -LogService logService
        -AiProviderService providerService
        -OpenAiCompatibleClient client
        -LogAnalysisRepository analysisRepository
        +analyze(...) LogAnalysis
    }
    class OfflineSnapshotService {
        -DeviceConfigService deviceConfigService
        -AgentMessageRouterService router
        +importFile(file) SnapshotResult
    }
    class OPNsenseCredentialService {
        -OPNsenseCredentialRepository repository
        -SecretCipher secretCipher
        -OPNsenseApiClient apiClient
        +save(agentId, …) Map
    }
    class AiProviderService {
        -AiProviderRepository repository
        -SecretCipher secretCipher
        -OpenAiCompatibleClient client
    }
    class SecretCipher {
        -SecretKeySpec key
        +encrypt(plain) string
        +decrypt(cipher) string
    }
    class OpenAiCompatibleClient {
        -WebClient client
        +chat(...) string
    }
    class OPNsenseApiClient {
        -WebClient client
        +checkConnection() Result
        +interfaces() Result
        +rules() Result
    }
    class LogNormalizer {
        <<utility>>
        +normalize(raw) DeviceLog
        +severityOf(head) int
    }
    class ProjectView {
        <<utility>>
        +subnetsFromDevice(agentId, config) List~PolicySubnet~
        +normalizeCidr(address) string
    }

    ProjectController --> ProjectService
    PolicyManagement --> ProjectService
    PolicyManagement --> AgentSessionRegistry
    PolicyManagement --> NotificationService
    NetworkTopologyController --> ProjectService
    NetworkTopologyController --> AgentMessageRouterService
    NetworkTopologyController --> AgentSessionRegistry
    RouterController --> AgentMessageRouterService
    AgentStatusController --> AgentSessionRegistry
    AgentStatusController --> AgentMessageRouterService
    AgentStatusController --> DeviceConfigService
    OfflineImportController --> OfflineSnapshotService
    LogController --> LogService
    LogController --> LogAnalysisEngine
    OPNsenseController --> OPNsenseCredentialService
    OPNsenseController --> OPNsenseApiClient
    AuthController --> UserService
    UserController --> UserService
    NotificationController --> NotificationService
    ComplianceController --> ComplianceService
    AiProviderController --> AiProviderService

    ProjectService --> ComplianceService
    ProjectService --> NotificationService
    LogService --> LogNormalizer
    LogAnalysisEngine --> LogService
    LogAnalysisEngine --> AiProviderService
    LogAnalysisEngine --> OpenAiCompatibleClient
    OfflineSnapshotService --> DeviceConfigService
    OfflineSnapshotService --> AgentMessageRouterService
    OPNsenseCredentialService --> SecretCipher
    OPNsenseCredentialService --> OPNsenseApiClient
    AiProviderService --> SecretCipher
    AiProviderService --> OpenAiCompatibleClient
    ProjectView ..> ProjectService
```

### 3-5. 보안 / 설정

```mermaid
classDiagram
    direction TB

    class SecurityConfig {
        +passwordEncoder() PasswordEncoder
        +userDetailsService(repo) UserDetailsService
        +authenticationProvider(uds, encoder) DaoAuthenticationProvider
        +authenticationManager(config) AuthenticationManager
        +filterChain(http) SecurityFilterChain
    }
    class WebMvcConfig {
        -String[] allowedOriginPatterns
        +addCorsMappings(registry)
    }
    class WebSocketConfig {
        +createWebSocketContainer() ServletServerContainerFactoryBean
        +registerWebSocketHandlers(registry)
    }
    class AdminAccountInitializer {
        -AppUserRepository repository
        -PasswordEncoder encoder
        +run(args)
    }
    class AppUser {
        -Long id
        -String username
        -String passwordHash
        -Role role
        -bool locked
        -int failedAttempts
        +Role
    }

    AdminAccountInitializer --> AppUser : 최초 admin 생성
    AdminAccountInitializer --> SecurityConfig : PasswordEncoder 재사용
    SecurityConfig ..> AppUser : UserDetailsService 로 조회
```

<b>인증 예외</b>: `/api/v1/management`, `/api/v1/telemetry` 는 `permitAll`
이어야 합니다. C++ 프로버는 세션 쿠키를 가질 수 없습니다.

---

## 4. 접점 계약 (Envelope)

양쪽이 공유하는 <b>유일한 계약</b>입니다. 필드는 전부 snake_case 이고,
타입 문자열은 7개로 고정입니다.

```mermaid
classDiagram
    direction LR

    class Prober_Envelope {
        <<C++ free functions>>
        kHello = "hello"
        kPolicyRequest = "policy-request"
        kPolicyResponse = "policy-response"
        kTelemetry = "telemetry"
        kCommand = "command"
        kAck = "ack"
        kError = "error"
    }

    class Backend_Envelope {
        <<Java DTO>>
        +String type
        +String agent_id
        +String device_type
        +String correlation_id
        +JsonNode payload
        +String error
    }

    Prober_Envelope ..> Backend_Envelope : "raw WebSocket + JSON"
```

### 타입별 방향과 응답

| type | 방향 | 응답 | 비고 |
| --- | --- | --- | --- |
| `hello` | 프로버 → 백엔드 | `ack` | 최초 연결 1회, 알림 기록 |
| `policy-request` | 프로버 → 백엔드 | `policy-response` | `correlation_id` 로 짝 |
| `policy-response` | 백엔드 → 프로버 | — | 정책 본문 |
| `telemetry` | 프로버 → 백엔드 | 없음 | 일방향. 실패해도 재전송 안 함 |
| `command` | 백엔드 → 프로버 | 없음 | 서버 푸시 |
| `ack` | 프로버 → 백엔드 | 없음 | 정책 적용 결과 |
| `error` | 양방향 | 없음 | |

<b>주의</b>: `telemetry` 는 8KB 를 넘으면 Tomcat 이 close 1009 로 끊습니다.
(FRR 라우터 텔레메트리가 11.8KB) `WebSocketConfig.createWebSocketContainer()`
에서 1MB 로 올려 두었습니다. 프로퍼티로는 설정되지 않습니다.

---

## 5. DB 스키마

### 5-1. 엔티티 관계

```mermaid
erDiagram
    project ||--o{ project_subnet : "project_id (ordinal)"
    project ||--o{ project_rule : "project_id (ordinal)"
    configuration ||--o{ network_interface : "node_id"
    configuration ||--o| rest_api_node_config : "node_id (UNIQUE)"
    project ||--o{ compliance_change : "project_key"
    project ||--o{ notification : "project_key"
    ai_provider ||--o{ log_analysis : "provider_id"

    project {
        bigint id PK
        varchar project_key UK
        varchar name
        varchar category
    }
    project_subnet {
        bigint id PK
        varchar subnet_id
        varchar cidr
        varchar zone_class
        varchar agent_id
    }
    project_rule {
        bigint id PK
        varchar rule_id
        varchar source
        varchar destination
        int port
    }
    configuration {
        int node_id PK
        varchar _hostname
        smallint _device_type
        smallint _configuration_format
    }
    network_interface {
        bigint interface_id PK
        varchar member_name
        int node_id FK
    }
    rest_api_node_config {
        bigint id PK
        int node_id FK
        varchar apikey
        varchar baseurl
    }
    notification {
        bigint id PK
        varchar notification_id UK
        varchar category
        varchar severity
        varchar project_key
        varchar agent_id
        varchar dedupe_key
        int repeat_count
        boolean is_read
    }
    device_log {
        bigint id PK
        varchar agent_id
        varchar severity
        varchar fingerprint
        varchar logged_at
    }
    log_analysis {
        bigint id PK
        varchar analysis_id
        bigint provider_id FK
        text result
    }
    compliance_change {
        bigint id PK
        varchar project_key
        varchar change_type
    }
    app_user {
        bigint id PK
        varchar username UK
        varchar password_hash
        varchar role
        boolean locked
    }
    ai_provider {
        bigint id PK
        varchar name
        varchar base_url
        text api_key_encrypted
    }
    opnsense_credential {
        bigint id PK
        varchar agent_id UK
        text secret_encrypted
    }
    opnsense_firewall {
        bigint id PK
        varchar agent_id
        varchar management_ip
    }
```

### 5-2. 양쪽 DB 의 역할 분담

| | 프로버 SQLite | 백엔드 PostgreSQL |
| --- | --- | --- |
| 목적 | 로컬 수집 이력 · 오프라인 재전송 | 분석 · 검증 · 화면 |
| 단위 | 스냅샷(`collected_at` 묶음) | 엔티티 |
| 스키마 | `route_table`, `nic_info`, `nic_address`, `vlan_status`, `trunk_status`, `arp_table` | 14개 테이블 (§5-1) |
| 스키마 정의 | `database/schema.cpp` 단일 소스 | JPA 엔티티 + `ddl-auto` |
| 시간 | ISO-8601 **문자열** | ISO-8601 **문자열** (동일) |

<b>설계 의도</b>: 같은 문제를 두 번 풀지 않습니다. 프로버는 <b>원본을 그대로</b>
남기고, 해석·판정은 백엔드가 합니다. 그래서 프로버 DB 에는 판정 결과가 없고,
백엔드 DB 에는 원본 로그 대신 정규화된 `DeviceLog` 가 들어갑니다.

### 5-3. 스키마 진화 시 주의

- 프로버: `CreateTablesSql()` 은 `CREATE TABLE IF NOT EXISTS` 라 <b>기존
  테이블의 컬럼을 바꾸지 못합니다</b>. 컬럼 변경 시 마이그레이션이 필요합니다.
- 백엔드: `local` 은 `update`, `postgres` 는 `validate` 입니다. 빈 DB 로 처음
  띄울 때는 `JPA_DDL_AUTO=update` 로 한 번 생성한 뒤 `validate` 로 되돌립니다.
- 운영에서는 Flyway/Liquibase 로 옮겨야 합니다(현재 없음).

---

## 6. v1 에서 확인된 약점 (v2 검토 대상)

| # | 위치 | 문제 | 영향 |
| --- | --- | --- | --- |
| 1 | `ManagementService` | `Apply*Policy` 7개를 한 클래스가 보유 | 벤더 추가 시 God Class. Strategy 분리 필요 |
| 2 | `ManagementService` / `TelemetryService` | WebSocket 클라이언트 코드 중복 | 전송 버그를 두 번 고쳐야 함 |
| 3 | `AgentMessageRouterService` | `lastConfig` 가 <b>인메모리 캐시</b> | 재기동하면 모든 장비 설정 소실 |
| 4 | `Configuration._hostname` | unique 제약 없음 | 같은 장비가 여러 행으로 쌓임 |
| 5 | `Configuration` | 인터페이스/VRF/ACL/VLAN 이 `@Transient` | SQL 로 조회 불가. 분석은 인메모리만 |
| 6 | `Model.Ip` / `Ip6` / `Prefix` | 최근 값 객체로 정리 (v1 반영) | — |
| 7 | `AbstractRoute` | 인터페이스 필드 = `static final` | 모든 라우트가 값 공유. 구현체 사용 불가 |
| 8 | `AgentMessageFirewallService` / `AgentMessageSwitchService` | 빈 클래스 | 죽은 코드 |
| 9 | ~~`OpenSenseApiService`~~ | ~~하드코딩된 URL/키~~ | **수리 완료** — 삭제 |
| 10 | ~~`Model/entitiy` (오타 패키지)~~ | ~~`Model/entity` 와 공존~~ | **수리 완료** — 통합 |
| 11 | `OPNSenseFirewall` | `OPNsenseCredential` 과 사실상 중복 (테이블 0행) | 미정 — 통합 검토 |
| 12 | `Service/opnsense` | `@Value` 로 전역 설정을 읽는 경로 없음 | DB 경로만 존재 (의도) |

## 7. 수리 이력

### 2026-09-21 — 오타 패키지 통합

`Model/entitiy`(오타) 를 `Model/entity` 로 이동했습니다. `git mv` 를 써서 이력이
보존됩니다.

```
Model/entitiy/OPNSenseFirewall.java  →  Model/entity/OPNSenseFirewall.java
```

수정한 참조 3곳: 패키지 선언, `OPNSenseFirewallRepository`, `OPNSenseFirewallRepositoryTest`.

### 2026-09-21 — 하드코딩 URL/키 제거

참조가 0건인 죽은 코드를 삭제했습니다.

| 삭제 | 하드코딩되어 있던 값 |
| --- | --- |
| `Service/OpenSenseApiService` | `new OPNSenseClientConfig("http://test.com", "apiKey-spxxxxx")` |
| `Client/OPNSenseClientConfig` | `@Value` 기본값 `https://test.local:8000` |
| `Service/RestApiClient/OPNSenseClientService` | 위 설정 의존 |
| `Service/RestApiClient/OPNSenseEndpoint` | 경로 중복 정의 |

대체 경로는 `Service/opnsense/` 3종이며, 접속 정보는 <b>DB(장치별) + 환경변수
(암호화 키)</b> 로만 들어옵니다. 자세한 내용은
[OPNsense 설정 주입 경로](./OPNsense%20Configuration.md) 를 보세요.

빈 디렉터리 `Client/`, `Service/RestApiClient/` 도 함께 제거했습니다.
