# SonarValidator 아키텍처 다이어그램 v3.0 — 전략 패턴 리팩토링

`SonarValidator` 3계층(Agent · Backend · Frontend)의 구조를 다이어그램으로 정리한
문서입니다. 모든 내용은 실제 소스 코드와 **GNS3 PoC 랩 실측**을 기준으로 작성했습니다.

**v3.0 (2026-09-26)** — v2.0 배포 검증에서 드러난 **구조적 결함**을 해소했습니다.
기능 버그가 아니라 **변경 비용**의 문제였습니다. v2.0 대비 주요 변경은 아래와 같습니다.

- `PolicyRegistryService` 의 유형별 `switch` 4곳 → **전략 패턴**
  (`DevicePolicyStrategy` + 4 구현 + `DevicePolicyStrategies` 선택기)
- 공통 헬퍼를 `PolicyJson` / `PolicyBuildContext` 로 추출
- `AgentMessageRouterService` 의 저장 책임 → **`AgentTelemetryStore`**
- **장치 유형 판별기** `DeviceTypeResolver` 신설 (판단 3단계)
- **방화벽을 격리 대상에서 제외** (`QuarantineService.isolate`)
- 백엔드 테스트 **212 → 215** (`QuarantineServiceTest` 15 → 18)

---

## 1. 계층 개요

```mermaid
flowchart TB
    subgraph LAB["GNS3 PoC 랩 (22 노드)"]
        RT["라우터 5<br/>Alpine + FRR"]
        FW["방화벽 1<br/>Alpine + nftables"]
        SW["스위치 5<br/>Open vSwitch (Alpine)"]
        VMC["VM 컨테이너 4<br/>gns3/ubuntu"]
        VMQ["QEMU VM 4<br/>Ubuntu (sshd 없음)"]
    end

    subgraph AGENT["Agent (C++23 Prober)"]
        MW["ManagementWorker<br/>정책 수신 + 명령"]
        TW["TelemetryWorker<br/>30초 수집"]
        QH["quarantine_handler<br/>격리 적용"]
        AX["ManagementService::Apply*<br/>벤더별 정책 적용"]
    end

    subgraph WEB["Backend (Spring Boot :3000)"]
        WSC["AgentWebSocketHandler"]
        AMR["AgentMessageRouterService<br/>수신 분기"]
        ATS["AgentTelemetryStore<br/>관측값 보관"]
        REST["16 RestController"]
        PRS["PolicyRegistryService<br/>정책 조립"]
        STR["DevicePolicyStrategies<br/>전략 선택"]
        QS["QuarantineService<br/>격리"]
        DTR["DeviceTypeResolver<br/>유형 판별"]
    end

    FE["Frontend (React :5173)<br/>대시보드·정책·격리 UI"]

    RT & FW & SW & VMC & VMQ -->|CLI 조회| TW
    MW -->|command| QH
    MW -->|policies| AX
    AGENT <-->|WebSocket| WSC
    WSC --> AMR
    AMR --> PRS
    AMR --> ATS
    PRS --> STR
    QS --> AMR
    QS --> DTR
    REST --> ATS
    REST --> QS
    REST <--> FE
```

---

## 2. 클래스 다이어그램 — 전략 패턴 (v3.0 신규)

```mermaid
classDiagram
    direction TB

    class PolicyRegistryService {
        <<Service>>
        -ProjectRepository repository
        -DevicePolicyStrategies strategies
        -QuarantineService quarantineService
        +forAgent(agentId, type, id, vendor, product) ObjectNode
        +forDevice(type, deviceId) ObjectNode
        -projectPolicy(match, type, id, vendor, product) ObjectNode
        -connectionsOf(project, subnet, type) List
        -quarantineOverride(agentId, policy) ObjectNode
        -strategyFor(type) DevicePolicyStrategy
    }

    class DevicePolicyStrategy {
        <<interface>>
        +supports(type) boolean
        +defaultVendor() String
        +defaultProduct() String
        +defaultRule() ObjectNode
        +declarationRule(ctx) ObjectNode
        +enforcementRule(ctx) ObjectNode
    }

    class DevicePolicyStrategies {
        <<Service>>
        -Map~DeviceType, DevicePolicyStrategy~ byType
        -DevicePolicyStrategy fallback
        +of(type) DevicePolicyStrategy
        +supportedTypes() Set
    }

    class VmPolicyStrategy {
        <<Component>>
        PRIMARY_INTERFACE_TOKEN = "__primary__"
        +declarationRule(ctx) ObjectNode
        +enforcementRule(ctx) ObjectNode : null
    }

    class RouterPolicyStrategy {
        <<Component>>
        +declarationRule(ctx) ObjectNode
        +enforcementRule(ctx) ObjectNode : null
    }

    class SwitchPolicyStrategy {
        <<Component>>
        UPLINK_PORT = "eth0"
        BRIDGE_NAME = "br0"
        +declarationRule(ctx) ObjectNode
        +enforcementRule(ctx) ObjectNode
    }

    class FirewallPolicyStrategy {
        <<Component>>
        FIREWALL_TABLE = "sonar"
        +declarationRule(ctx) ObjectNode
        +enforcementRule(ctx) ObjectNode
        -chain(hook, policy) ObjectNode
    }

    class PolicyBuildContext {
        <<record>>
        +ProjectSubnet subnet
        +String vendor
        +String product
        +ConnectionView connection
        +forDeclaration(subnet, vendor, product)$
        +forConnection(subnet, vendor, product, conn)$
        +vendorOr(fallback) String
        +productOr(fallback) String
    }

    class ConnectionView {
        <<record>>
        +String ruleId
        +boolean outgoing
        +String peerId
        +String peerCidr
        +ZoneClass peerClass
        +String sourceCidr
        +String destinationCidr
        +String protocol
        +Integer port
        +boolean forbidden
        +String reason
    }

    class PolicyJson {
        <<utility>>
        LAB_HOST_OFFSET = 10
        +splitCidr(cidr)$ String[]
        +maskOf(prefix)$ String
        +hostCidrOf(cidr)$ String
        +firstNonBlank(values)$ String
        +label(zone)$ String
    }

    PolicyRegistryService --> DevicePolicyStrategies : strategyFor()
    DevicePolicyStrategies o-- DevicePolicyStrategy : 유형별 1개
    DevicePolicyStrategy <|.. VmPolicyStrategy
    DevicePolicyStrategy <|.. RouterPolicyStrategy
    DevicePolicyStrategy <|.. SwitchPolicyStrategy
    DevicePolicyStrategy <|.. FirewallPolicyStrategy
    PolicyRegistryService ..> PolicyBuildContext : 생성
    PolicyBuildContext o-- ConnectionView
    VmPolicyStrategy ..> PolicyJson : hostCidrOf
    RouterPolicyStrategy ..> PolicyJson : splitCidr
    SwitchPolicyStrategy ..> PolicyJson : hostCidrOf
    FirewallPolicyStrategy ..> PolicyJson : 분리되지 않음
```

### 2.1 ⚠️ v2.0 의 문제 — `switch` 가 네 곳에 흩어져 있었다

`PolicyRegistryService` 는 1046 줄이었고, 유형 분기가 **네 곳**에 있었습니다.

```mermaid
flowchart TB
    subgraph BEFORE["v2.0 — 유형 분기 4곳 (server 1046 줄)"]
        D1["defaultRule()<br/>switch (type)"]
        D2["declarationRule()<br/>switch (type)"]
        D3["enforcementRule()<br/>if (type == SWITCH) / if (type != FIREWALL)"]
        D4["defaultVendor() / defaultProduct()<br/>switch (type)"]
    end

    subgraph AFTER["v3.0 — 분기 0곳 (service 640 줄)"]
        S1["DevicePolicyStrategies.of(type)<br/>유형 → 전략 1회 조회"]
        S2["DevicePolicyStrategy<br/>+ Vm · Router · Switch · Firewall"]
    end

    BEFORE -->|리팩토링| AFTER
```

**무엇이 문제였나** — "VM 정책을 조금 바꾼다" 는 일이 서로 멀리 떨어진
네 곳을 고치는 일이었습니다. 한 곳을 빠뜨리면 **폴백 경로에서만 옛 동작**이
남아, 정상 배정된 장치는 새 정책을 받고 배정 없는 장치는 옛 정책을 받습니다.

**v3.0 에서는** 유형별 코드가 한 클래스에 모여 있어, "VM 을 고친다" 는
`VmPolicyStrategy` 한 파일을 여는 일입니다.

### 2.2 ⚠️ 전략은 예외를 던지지 않는다

`DevicePolicyStrategy` 의 모든 메서드는 **예외 대신 `null` 을 돌려줍니다.**

```mermaid
flowchart LR
    A["Agent 가 3초마다<br/>policy-request"] --> B{"전략이<br/>예외를 던지면?"}
    B -->|던짐| C["응답 없음"]
    C --> D["Agent 재시도만 반복"]
    D --> E["⚠️ 텔레메트리도 멈춤<br/>= 화면에서 장치가 사라짐"]
    B -->|null 반환| F["호출자가 폴백 선언 사용"]
    F --> G["정책은 최소하지만<br/>**장치는 계속 보고**"]
```

`enforcementRule` 이 `null` 인 유형(VM · Router)은 **집행 지점이 아니기**
때문입니다. `policies` 배열에 아무것도 넣지 않는 것이 정답이고,
억지로 규칙을 만들면 장치가 해석하지 못하는 필드를 받습니다.

---

## 3. 시퀀스 — 전략 디스패치 (v3.0 신규)

```mermaid
sequenceDiagram
    autonumber
    participant A as Agent (Prober)
    participant W as AgentWebSocketHandler
    participant R as AgentMessageRouterService
    participant P as PolicyRegistryService
    participant S as DevicePolicyStrategies
    participant ST as SwitchPolicyStrategy
    participant J as PolicyJson

    A->>W: policy-request<br/>{device_id:"Switch-1", device_type:"SWITCH"}
    W->>R: handle(session, envelope)
    R->>R: deviceType = fromString("SWITCH")
    R->>R: observed = telemetryStore.configOf("Switch-1")
    R->>P: forAgent(id, SWITCH, "Switch-1", vendor, product)
    P->>P: findSubnetForAgent(id) → ProjectMatch
    P->>P: connectionsOf(project, subnet, SWITCH)

    Note over P,S: 유형 분기는 여기 한 곳뿐입니다
    P->>S: of(SWITCH)
    S-->>P: SwitchPolicyStrategy

    P->>ST: declarationRule(ctx)
    ST->>J: hostCidrOf("10.20.111.0/24")
    J-->>ST: "10.20.111.10/24"
    ST-->>P: {command:create, ip_address:...}

    loop 각 연결
        P->>ST: enforcementRule(ctx)
        ST-->>P: {acl_name, source_subnet,<br/>destination_subnet, action:deny}
    end

    P-->>R: policy JSON (policies[] + intents[])
    R-->>W: policy-response
    W-->>A: 30초 뒤 적용
    A->>W: ack {action:"policy", applied:true}
```

### 3.1 왜 `intents` 는 전략 밖에 있는가

```mermaid
flowchart TB
    subgraph P["PolicyRegistryService — 벤더 중립"]
        I["intents[]<br/>rule_id · peer_cidr · action · reason"]
        SUM["summary{allowed, denied, peers}"]
        META["project_id · subnet_class · zone_level"]
    end

    subgraph ST["전략 — 벤더 종속"]
        NR["nftables rule_target"]
        AC["OVS acl_name"]
        NP["netplan network_config"]
        RT["IOS destination_prefix"]
    end

    I --> UI["프론트엔드 정책 미리보기"]
    I --> PRB["Prober 의도 확인"]
    NR & AC & NP & RT --> DEV["실제 장치 명령"]
```

**의도는 모든 유형에 같고, 적용 방법만 다릅니다.** 그래서 의도는 서비스가
만들고 적용 규칙만 전략이 만듭니다. 이 분리가 없으면 화면이 유형별 JSON 을
해석해야 하고, 유형이 늘 때마다 화면도 고쳐야 합니다.

---

## 4. 클래스 다이어그램 — 격리 + 유형 판별 (v3.0)

```mermaid
classDiagram
    direction TB

    class QuarantineController {
        <<RestController>>
        +isolate(agentId, body) Map
        +release(agentId, releasedBy) Map
        +list(projectId) Map
        +status(agentId) Map
    }

    class QuarantineService {
        <<Service>>
        -QuarantineStateRepository repository
        -AgentSessionRegistry registry
        -ComplianceService complianceService
        -NotificationService notificationService
        -DeviceTypeResolver deviceTypeResolver
        -Map lastAck
        +isolate(agentId, projectKey, reason, requestedBy) Map
        +release(agentId, releasedBy) Map
        -excludedResponse(agentId, projectKey, type, why) Map
        -resolveDeviceType(agentId) DeviceType
        +isQuarantined(agentId) bool
        +recordAck(agentId, payload) void
    }

    class DeviceTypeResolver {
        <<Service>>
        -ExpectedAgentRepository expectedAgentRepository
        +resolve(agentId, config) DeviceType
        +resolveWithoutRepository(agentId, config)$ DeviceType
        +isIsolatable(type)$ boolean
        +exclusionReason(type)$ String
        +nameOf(type)$ String
        -registeredType(agentId) DeviceType
    }

    class AgentTelemetryStore {
        <<Service>>
        -Map lastTelemetry
        -Map lastConfig
        -Map lastSeen
        -Set offlineOrigins
        +touch(agentId) void
        +putTelemetry(agentId, payload) void
        +putConfig(agentId, config) void
        +putOfflineConfig(agentId, config) void
        +telemetryOf(agentId) JsonNode
        +configOf(agentId) NeutralDeviceConfig
        +allConfigs() Map
        +allTelemetryAgentIds() Set
        +lastSeenOf(agentId) Instant
        +isOfflineOrigin(agentId) boolean
    }

    class DeviceType {
        <<enumeration>>
        SWITCH
        ROUTER
        FIREWALL
        VM
        +fromString(value)$ DeviceType
        +inferFromDeviceId(deviceId)$ DeviceType
    }

    QuarantineController --> QuarantineService
    QuarantineService --> DeviceTypeResolver : 격리 가능 여부
    DeviceTypeResolver ..> DeviceType
    QuarantineService ..> PolicyRegistryService : isQuarantined()
    PolicyRegistryService ..> QuarantineService : quarantineOverride
    AgentTelemetryStore ..> NeutralDeviceConfig : 보관
```

### 4.1 ⚠️ 방화벽은 격리 대상이 아니다 (v3.0 신규)

```mermaid
flowchart TB
    subgraph TOPO["랩 토폴로지 — 방화벽 eth1 트렁크"]
        FW["방화벽<br/>172.16.255.2"]
        VLAN131["VLAN 131<br/>Public / PWS"]
        VLAN132["VLAN 132<br/>DMZ"]
        VLAN133["VLAN 133<br/>Internal"]
    end

    FW --- VLAN131
    FW --- VLAN132
    FW --- VLAN133

    subgraph ACT["격리하면 무슨 일이 일어나는가"]
        DOWN["eth1 인터페이스 down"]
        RESULT["⚠️ VLAN 131·132·133 이<br/>**동시에** 끊김"]
        SCALE["격리하려던 1대가 아니라<br/>무관한 존 3개가 내려감"]
    end

    DOWN --> RESULT --> SCALE
```

방화벽은 **구역을 나누는 지점 그 자체**입니다. 그 지점을 없애면 구역이
사라집니다. 그래서 격리 대신 **프로젝트 규칙으로 해당 연결만** 막습니다.

```mermaid
sequenceDiagram
    autonumber
    participant O as 운영자 (Frontend)
    participant C as QuarantineController
    participant Q as QuarantineService
    participant D as DeviceTypeResolver
    participant CS as ComplianceService

    O->>C: POST /api/v1/quarantine/GNS3.Firewall
    C->>Q: isolate("GNS3.Firewall", ...)
    Q->>D: resolve("GNS3.Firewall", null)

    Note over D: 1) 기대 목록 조회<br/>2) 텔레메트리 device_type<br/>3) 식별자 끝 토큰
    D-->>Q: FIREWALL

    Q->>Q: exclusionReason(FIREWALL) != null
    Note over Q: ⚠️ 여기서 멈춥니다<br/>행 생성 X · 명령 전송 X · 알림 X
    Q->>CS: recordQuietly("QuarantineRejected")
    Q-->>C: {rejected:true, quarantined:false,<br/>reason:"…모든 VLAN 이 함께 끊깁니다",<br/>hint:"프로젝트 규칙으로 차단하세요"}
    C-->>O: 200 + 거부 사유

    Note over O: 화면은 "격리됨" 이 아니라<br/>"격리 불가" 를 보여줍니다
```

### 4.2 ⚠️ 거부를 "성공" 으로 말하지 않는 이유

```mermaid
flowchart LR
    A["운영자가 격리 버튼"] --> B{"응답"}
    B -->|"quarantined:true"| C["운영자가 빨간 표시를 믿고<br/>**뚫린 망을 방치**"]
    B -->|"rejected:true"| D["운영자가 대안(규칙 차단)으로<br/>**다음 조치를 함**"]
```

거부 응답에는 `rejected` · `quarantined:false` · `released:false` ·
`delivered:false` · `applied:false` 를 **모두 명시**합니다. 클라이언트가
"키가 없다 = 실패" 를 추론하지 않도록, v2.0 의 `released` 버그와 같은 원칙을
적용했습니다.

**상태는 건드리지 않고 이력만 남깁니다.** 격리 행을 만들면 이후 목록에
"격리 중" 으로 보이기 때문입니다. 반면 시도 자체는 감사 대상이므로
`QuarantineRejected` 이력을 남깁니다.

---

## 5. 클래스 다이어그램 — 텔레메트리 보관 (v3.0 신규)

```mermaid
classDiagram
    direction TB

    class AgentMessageRouterService {
        <<Service>>
        -AgentSessionRegistry registry
        -PolicyRegistryService policyRegistry
        -AgentTelemetryStore telemetryStore
        -DeviceConfigService deviceConfigService
        -LogService logService
        -NotificationService notificationService
        -QuarantineService quarantineService
        +handle(session, envelope) Envelope
        -onHello(session, envelope, agentId) Envelope
        -onPolicyRequest(session, envelope, agentId) Envelope
        -onTelemetry(envelope, agentId) Envelope
        -onAck(envelope, agentId) Envelope
        +telemetryStore() AgentTelemetryStore
    }

    class AgentStatusController {
        <<RestController>>
        +list() Map
        +detail(agentId) Map
    }

    class NetworkTopologyController {
        <<RestController>>
    }

    class ExpectedAgentController {
        <<RestController>>
    }

    AgentMessageRouterService --> AgentTelemetryStore : 기록
    AgentStatusController --> AgentTelemetryStore : 조회 (직접)
    NetworkTopologyController --> AgentTelemetryStore : 조회 (직접)
    ExpectedAgentController --> AgentTelemetryStore : 조회 (직접)
```

### 5.1 ⚠️ v2.0 의 문제 — 조회 API 가 라우터를 통과했다

```mermaid
flowchart TB
    subgraph B["v2.0"]
        C1["AgentStatusController"] --> R1["AgentMessageRouterService<br/>수신 분기 **+** 값 보관"]
        C2["NetworkTopologyController"] --> R1
        C3["ExpectedAgentController"] --> R1
        C4["OPNsenseController"] --> R1
        C5["RouterController"] --> R1
    end

    subgraph A["v3.0"]
        D1["AgentStatusController"] --> S1["AgentTelemetryStore<br/>보관 + 조회"]
        D2["NetworkTopologyController"] --> S1
        D3["ExpectedAgentController"] --> S1
        D4["OPNsenseController"] --> S1
        D5["RouterController"] --> S1
        RT["AgentMessageRouterService<br/>수신 분기"] --> S1
    end
```

`AgentTelemetryStore` 는 `setAll` 같은 **변이만** 합니다. 상태가 한 곳에만
있으므로 "화면은 A 를 보고 정책은 B 를 보는" 불일치가 생길 수 없습니다.

### 5.2 ⚠️ 오프라인 출처 구분

```mermaid
flowchart LR
    ON["온라인 텔레메트리"] -->|putConfig| K["lastConfig"]
    K --> RM["offlineOrigins.remove"]
    OFF["파일 업로드"] -->|putOfflineConfig| K2["lastConfig"]
    K2 --> ADD["offlineOrigins.add"]

    RM & ADD --> UI["화면: 연결됨 / 연결끊김+파일업로드"]
```

파일 업로드 경로는 세션이 없으므로 `connectedAgentIds()` 에 나타나지 않습니다.
출처를 구분하지 않으면 그 장치가 화면에서 **통째로 사라집니다.**

---

## 6. 시퀀스 — 장치 유형 판별 (v3.0 신규)

```mermaid
sequenceDiagram
    autonumber
    participant Caller as 호출자 (격리/정책/요약)
    participant D as DeviceTypeResolver
    participant ERepo as ExpectedAgentRepository
    participant Store as AgentTelemetryStore

    Caller->>D: resolve("Gateway-Router", config)

    D->>ERepo: findByAgentId("Gateway-Router")
    alt 기대 목록에 등록됨
        ERepo-->>D: ExpectedAgent{device_type}
        D->>D: DeviceType.fromString(device_type)
        D-->>Caller: ROUTER
    else 등록 없음
        ERepo-->>D: Optional.empty()
        D->>D: resolveWithoutRepository(agentId, config)
        alt 텔레메트리가 유형을 보고함
            D->>D: fromString(config.deviceType)
            D-->>Caller: ROUTER
        else 보고 없음
            D->>D: inferFromDeviceId("Gateway-Router")
            Note over D: 마지막 토큰 "ROUTER" 판정<br/>⚠️ 접두사만 보면 VM 으로 오판
            D-->>Caller: ROUTER
        end
    end
```

### 6.1 ⚠️ 왜 판단 순서가 중요한가

```mermaid
flowchart TB
    A["운영자가 'edge-box-1 은 방화벽'<br/>이라고 등록"] --> B{"등록을 먼저 보는가?"}
    B -->|"본다 (v3.0)"| C["FIREWALL — 정상<br/>이름에 유형이 없어도 동작"]
    B -->|"안 본다 (식별자만)"| D["VM 으로 추론<br/>⚠️ 방화벽이 격리 대상이 됨"]
```

식별자 추론은 어디까지나 **관례**입니다. 운영자가 명시적으로 등록한 값이
항상 우선해야 합니다.

### 6.2 ⚠️ 결과를 캐시하지 않는 이유

```mermaid
flowchart LR
    A["resolve()"] --> B["기대 목록 조회<br/>(요청당 1회)"]
    B --> C["캐시하면?"]
    C --> D["운영자가 유형을 바꿔도<br/>옛 값 유지"]
    D --> E["⚠️ 방화벽이 갑자기 격리되거나<br/>라우터에 VM 정책이 나감"]
```

조회는 요청당 몇 번뿐이라 비용이 문제되지 않습니다. 잘못된 캐시는
디버깅이 불가능한 형태로 드러납니다.

---

## 7. 클래스 다이어그램 — 전체 요약 (v3.0)

```mermaid
classDiagram
    direction LR

    class AgentWebSocketHandler
    class AgentMessageRouterService
    class AgentTelemetryStore
    class PolicyRegistryService
    class DevicePolicyStrategies
    class DevicePolicyStrategy
    class QuarantineService
    class DeviceTypeResolver
    class ProjectService
    class SegmentationBddEngine
    class ExpectedAgentService
    class AgentBundleService
    class NotificationService
    class ComplianceService

    AgentWebSocketHandler --> AgentMessageRouterService
    AgentMessageRouterService --> AgentTelemetryStore
    AgentMessageRouterService --> PolicyRegistryService
    AgentMessageRouterService --> QuarantineService
    PolicyRegistryService --> DevicePolicyStrategies
    DevicePolicyStrategies o-- DevicePolicyStrategy
    PolicyRegistryService --> QuarantineService
    QuarantineService --> DeviceTypeResolver
    ProjectService --> SegmentationBddEngine
    ProjectService --> NotificationService
    QuarantineService --> NotificationService
    QuarantineService --> ComplianceService
    ExpectedAgentService ..> DeviceTypeResolver
    AgentBundleService ..> ExpectedAgentService
```

---

## 8. 실측 검증 결과 (v3.0)

### 8.1 랩 배포 현황 — **14대 연결** (v2.0 과 동일)

| 유형 | 연결 | 장치 |
| --- | --- | --- |
| 라우터 | 5 | Gateway-Router, DMZ-Router, C4I-Network-Router, Survillance-Network-Router, VDI-Router |
| 방화벽 | 1 | GNS3.Firewall |
| 스위치 | 4 | Switch-0, Switch-1, Switch-3, Switch-4 |
| VM 컨테이너 | 4 | TOD-Cam, UAV, VDI-1, VDI-2 |
| **합계** | **14** | |

미연결: Switch-2(허브 링크 없음), ATICS/KNCCS/AFCCS/PWS(방화벽 VLAN 인터페이스 미구성)

### 8.2 테스트

| 항목 | 기준선 | v3.0 | 비고 |
| --- | --- | --- | --- |
| Backend | 212 | **215** | `QuarantineServiceTest` 15 → 18 |
| Prober | 13 | **13** | 변경 없음 |
| Frontend tsc | clean | **clean** | 변경 없음 |

신규 테스트 3건:
- `firewallIsNotIsolatable` — 명령 미전송 · 행 미생성 확인
- `firewallRejectionExplainsWhy` — 사유 문장 + 대안 안내 + 이력 기록
- `firewallRejectionDoesNotNotify` — 알림 미생성

### 8.3 리팩토링 효과 (실측)

| 파일 | v2.0 | v3.0 | 변화 |
| --- | --- | --- | --- |
| `Service/PolicyRegistryService.java` | 1046 | **646** | **-400** (switch 4곳 제거) |
| `Service/AgentMessageRouterService.java` | 643 | 678 | +35 (저장소 위임 Javadoc) |
| `Service/QuarantineService.java` | 643 | 643 | ±0 (거부 경로 추가) |
| `Policy/strategy/DevicePolicyStrategy.java` | — | 89 | 신규 · 인터페이스 |
| `Policy/strategy/DevicePolicyStrategies.java` | — | 97 | 신규 · 선택기 |
| `Policy/strategy/PolicyBuildContext.java` | — | 109 | 신규 · 컨텍스트 record |
| `Policy/strategy/PolicyJson.java` | — | 150 | 신규 · 공통 헬퍼 |
| `Policy/strategy/VmPolicyStrategy.java` | — | 126 | 신규 · 구현 4 |
| `Policy/strategy/RouterPolicyStrategy.java` | — | 105 | 신규 |
| `Policy/strategy/SwitchPolicyStrategy.java` | — | 160 | 신규 |
| `Policy/strategy/FirewallPolicyStrategy.java` | — | 152 | 신규 |
| `Service/AgentTelemetryStore.java` | — | 89 (본문) / 165 (주석 포함) | 신규 · 보관 책임 |
| `Service/DeviceTypeResolver.java` | — | 96 (본문) / 168 (주석 포함) | 신규 · 유형 판별 |

> **⚠️ 총 줄 수는 늘었습니다** (전략 패키지 합계 988줄 vs 서비스 400줄 감소).
> 리팩토링의 목적은 줄 수가 아니라 **변경 지역화**입니다. v2.0 은 "VM 정책을
> 고친다" 는 일이 1046줄 안의 네 곳을 찾아다니는 일이었고, v3.0 은 파일 하나를
> 여는 일입니다. 인터페이스 자체가 계약 문서 역할을 하므로 Javadoc 이
> 두꺼워진 것도 의도된 비용입니다.

**새 유형 추가 비용** — v2.0 은 `PolicyRegistryService` 의 네 곳을 고쳐야
했고 한 곳을 빠뜨리면 폴백 경로에서만 옛 동작이 남았습니다. v3.0 은
`DevicePolicyStrategy` 를 구현한 `@Component` 하나를 추가하면 끝입니다
(`supports()` 로 자기 담당을 선언하므로 선택기도 고치지 않습니다).

### 8.4 방화벽 격리 거부 (API 응답 실측)

```json
{
  "agent_id": "GNS3.Firewall",
  "device_type": "FIREWALL",
  "quarantined": false,
  "delivered": false,
  "applied": false,
  "rejected": true,
  "released": false,
  "reason": "방화벽은 격리 대상이 아닙니다 — 트렁크(eth1)에 연결된 모든 VLAN 이 함께 끊깁니다. 대신 프로젝트 규칙으로 해당 연결만 차단하세요.",
  "hint": "프로젝트 규칙에서 해당 연결만 차단하세요."
}
```

---

## 9. 알려진 제약 (실측)

| 제약 | 영향 | 우회 |
| --- | --- | --- |
| QEMU VM 4대는 sshd 없음 | 텔레메트리 미수집 | 콘솔 전용 운영 |
| 방화벽 SSH 키 미등록 | 자동 배포 불가 | `docker exec` 로 수동 배포 |
| Switch-2 허브 링크 없음 | 14대 중 1대 미연결 | 불필요 (토폴로지상 단독) |
| 방화벽 VLAN 인터페이스 미구성 | 4대 미연결 | 후속 작업 |
| 텔레메트리 저장이 메모리 | 서버 재시작 시 소실 | DB 도입 예정 |
| 라우터 ACL 경로 없음 | 라우터는 선언만 | 방화벽/스위치가 차단 담당 |

---

## 10. v2.0 → v3.0 변경 요약

| 항목 | v2.0 | v3.0 |
| --- | --- | --- |
| 유형별 분기 | `switch` 4곳 (1046줄 서비스) | **전략 패턴** (전략당 1파일) |
| 공통 헬퍼 | `PolicyRegistryService` private | `PolicyJson` / `PolicyBuildContext` |
| 텔레메트리 보관 | 라우터가 겸함 | `AgentTelemetryStore` |
| 장치 유형 판별 | 각자 추론 | `DeviceTypeResolver` 3단계 |
| 방화벽 격리 | 가능 (⚠️ 무관 존 전체 다운) | **거부 + 대안 안내** |
| 테스트 | 212 | **215** |
| 새 유형 추가 비용 | 서비스 4곳 수정 | 빈 1개 추가 |

---

## 11. 남은 리팩토링 후보 (측정 기준)

| 파일 | 줄 수 | 검토 방향 |
| --- | --- | --- |
| `QuarantineService.java` | 643 | ack 처리 → `QuarantineAckTracker` 분리 |
| `ProjectService.java` | 492 | 위반 경고 → `ViolationWarner` 분리 |
| `NotificationService.java` | 471 | 조회 쿼리 빌더 분리 |
| `LogController.java` | 405 | 응답 매핑 분리 |
| `PolicyManagement.java` | 374 | (프론트엔드) |

**우선순위 근거** — 위 파일들은 모두 공개 계약이 안정적이고 테스트가
있습니다. 즉 리팩토링 위험이 낮습니다. 반면 `AgentBundleService`(346)는
배포 경로라 지금 건드리지 않습니다.