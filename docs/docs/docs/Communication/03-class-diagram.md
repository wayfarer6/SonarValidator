---
sidebar_position: 3
---

# 클래스 다이어그램

## 전체 구조

```mermaid
classDiagram
    direction LR

    namespace Backend {
        class WebSocketConfig {
            +MANAGEMENT_PATH : String
            +TELEMETRY_PATH : String
            +registerWebSocketHandlers(registry)
        }
        class AgentWebSocketHandler {
            -objectMapper : ObjectMapper
            -router : AgentMessageRouterService
            -registry : AgentSessionRegistry
            +afterConnectionEstablished(session)
            +handleTextMessage(session, message)
            +afterConnectionClosed(session, status)
            -wrapped(session) ConcurrentWebSocketSessionDecorator
        }
        class Envelope {
            +type : String
            +agent_id : String
            +device_type : String
            +correlation_id : String
            +payload : JsonNode
            +error : String
            +of(type) Envelope
            +replyTo(type, request, payload) Envelope
            +error(correlationId, message) Envelope
            +payloadOrEmpty() JsonNode
        }
        class Types {
            <<constants>>
            +HELLO
            +POLICY_REQUEST
            +POLICY_RESPONSE
            +TELEMETRY
            +COMMAND
            +ACK
            +ERROR
        }
        class AgentMessageRouterService {
            -registry : AgentSessionRegistry
            -policyRegistry : PolicyRegistryService
            +handle(session, envelope) Envelope
            -onHello(session, envelope, agentId) Envelope
            -onPolicyRequest(session, envelope, agentId) Envelope
            -onTelemetry(envelope, agentId) Envelope
            +lastTelemetryOf(agentId) JsonNode
        }
        class AgentSessionRegistry {
            -sessions : Map
            -sessionToAgent : Map
            -objectMapper : ObjectMapper
            +register(agentId, session)
            +unregister(session)
            +sendTo(agentId, envelope) boolean
            +broadcast(envelope) int
            +connectedAgentIds() Collection
        }
        class SessionHolder {
            -session : WebSocketSession
            -lock : Object
        }
        class PolicyRegistryService {
            +forDevice(type, deviceId) ObjectNode
            -rulesFor(type) ArrayNode
        }
        class AgentStatusController {
            +list() Map
            +telemetry(agentId) JsonNode
            +push(agentId, body) Map
            +broadcast(body) Map
            -toCommandEnvelope(body) Envelope
        }
        class DeviceType {
            <<enum>>
            SWITCH
            ROUTER
            FIREWALL
            VM
            +fromString(s) DeviceType
            +inferFromDeviceId(id) DeviceType
        }
    }

    namespace Agent {
        class envelope_ns {
            <<namespace>>
            +Hello(agentId, type) Json
            +PolicyRequest(agentId, type, deviceId) Json
            +Telemetry(agentId, type, payload) Json
            +Make(type, agentId, deviceType, correlationId, payload) Json
            +DeviceTypeToString(type) string
            +NextCorrelationId() string
        }
        class ManagementService {
            -stream_ : websocket_stream
            -read_buffer_ : flat_buffer
            -agent_id_ : string
            +connect() boolean
            +fetchPolicy(type, deviceId) Json
            +ReportPolicyApplied(type, deviceId, policyId, applied) boolean
            +TryReceive(msg, timeout) boolean
            +ApplyAristaSwitchPolicy(policy) boolean
            +ApplyCiscoRouterPolicy(policy) boolean
            +ApplyNftablesPolicy(policy) boolean
            +ApplyVmPolicy(policy) boolean
        }
        class TelemetryService {
            -stream_ : websocket_stream
            +connect() boolean
            +sendText(message) boolean
            +receiveText() string
            +tryReceiveText(msg, timeout) boolean
            +sendRequest(request, target) boolean
        }
        class TelemetryMonitor {
            -interval_seconds_ : atomic~int~
            +Run(stopToken, config, queue)
        }
        class policy_receiver {
            <<functions>>
            +ReceivePolicy(config, mgmt, policy)
            +ReceiveSwitchPolicy(config, mgmt, policy)
            +ReceiveRouterPolicy(config, mgmt, policy)
            +ReceiveFirewallPolicy(config, mgmt, policy)
            +ReceiveVmPolicy(config, mgmt, policy)
        }
        class ManagementWorker {
            <<thread entry>>
        }
    }

    WebSocketConfig --> AgentWebSocketHandler : registers
    AgentWebSocketHandler --> AgentMessageRouterService : delegates
    AgentWebSocketHandler --> AgentSessionRegistry : unregister
    AgentWebSocketHandler ..> Envelope : parses / serializes

    AgentMessageRouterService --> AgentSessionRegistry : register
    AgentMessageRouterService --> PolicyRegistryService : policy lookup
    AgentMessageRouterService ..> Envelope : builds replies
    AgentMessageRouterService ..> DeviceType : resolves

    AgentSessionRegistry --> SessionHolder : contains
    AgentStatusController --> AgentSessionRegistry : push
    AgentStatusController --> AgentMessageRouterService : read state
    AgentStatusController ..> Envelope : builds command

    Envelope --> Types : uses
    TelemetryMonitor --> TelemetryService : sends telemetry
    ManagementWorker --> ManagementService : fetchPolicy loop
    ManagementService ..> envelope_ns : builds / parses
    TelemetryService ..> envelope_ns : builds
    policy_receiver --> ManagementService : applies policy
```

## 서버 측 클래스 책임

| 클래스 | 한 줄 책임 |
| --- | --- |
| `WebSocketConfig` | 두 경로를 하나의 핸들러에 등록합니다. |
| `AgentWebSocketHandler` | 텍스트 프레임 ↔ `Envelope` 변환. 파싱 실패 시 `error` 봉투를 돌려줍니다. |
| `AgentMessageRouterService` | `type` 별 분기. **응답의 단일 진입점** 이며 도메인 상태의 유일한 변경자입니다. |
| `AgentSessionRegistry` | `agent_id` → 세션 매핑과 서버→Agent 푸시(직렬화 포함). |
| `SessionHolder` | 세션 1개와 그 전송 락. Spring `WebSocketSession` 은 스레드 안전하지 않기 때문입니다. |
| `PolicyRegistryService` | 장치 유형별 정책 생성(현재는 자리표시자, 향후 DB 로 교체). |
| `AgentStatusController` | HTTP 표면: 연결 현황 조회, 푸시, 브로드캐스트. |
| `Envelope` / `Types` | 계약 그 자체. Agent 의 `envelope.hpp` 와 1:1 대응. |
| `DeviceType` | 문자열 → 유형 변환 및 `device_id` 접두사 추론. |

## Agent 측 클래스 책임

| 클래스 | 한 줄 책임 |
| --- | --- |
| `envelope` (namespace) | 봉투 생성/파싱 헬퍼. **유일한 프로토콜 정의 지점** 입니다. |
| `ManagementService` | 관리 경로 연결, `policy-request` → `policy-response` 상관관계 매칭, 정책 적용. |
| `TelemetryService` | 텔레메트리 경로 연결과 전송, 서버 지시 수신. |
| `TelemetryMonitor` | 주기 수집 루프. 수신한 `command.payload.monitor_interval` 로 주기를 조정합니다. |
| `policy_receiver` | 정책 JSON 을 장치 유형별 `Apply*` 로 분배. |
| `ManagementWorker` | 관리 스레드 진입점. |

## 설계상 중요한 두 가지

### 1) 세션별 락

```mermaid
sequenceDiagram
    participant S as 정책 스케줄러 스레드
    participant R as AgentSessionRegistry
    participant H as SessionHolder
    participant WS as WebSocketSession

    S->>R: sendTo("vm-01", envelope)
    R->>H: holder 조회
    R->>H: synchronized(holder.lock)
    activate H
    R->>WS: sendMessage(TextMessage)
    WS-->>R: ok
    R->>H: lock 해제
    deactivate H
    Note over H,WS: 동시 전송이 직렬화되어 프레임이 깨지지 않습니다.
```

Spring 의 `WebSocketSession` 에 여러 스레드가 동시에 `sendMessage` 를 호출하면
프레임이 섞일 수 있습니다. `ConcurrentWebSocketSessionDecorator` 와 같은 목적이지만,
버퍼 크기/시간 제한 설정이 필요 없는 구조라 락 하나로 단순하게 해결했습니다.

### 2) 재접속 시 세션 교체

```mermaid
stateDiagram-v2
    [*] --> 미등록
    미등록 --> 등록됨 : hello / policy-request
    등록됨 --> 등록됨 : 같은 agent_id 재접속<br/>(기존 세션 close 후 교체)
    등록됨 --> 미등록 : afterConnectionClosed
    note right of 미등록
        unregister 는 computeIfPresent 로
        "내가 최신 세션일 때만" 제거합니다.
        (늦게 도착한 close 이벤트가
        새 세션을 지우는 것을 방지)
    end note
```

`unregister` 가 단순 `remove` 였다면, 재접속 직후 도착한 이전 세션의 close 이벤트가
**새 세션을 삭제** 해버립니다. `computeIfPresent` 로 "내가 아직 최신일 때만" 지우도록
했습니다.
