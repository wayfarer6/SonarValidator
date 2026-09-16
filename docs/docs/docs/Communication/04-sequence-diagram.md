---
sidebar_position: 4
---

# 시퀀스 다이어그램

## 1. 연결 + hello/ack 핸드셰이크

```mermaid
sequenceDiagram
    autonumber
    participant A as Agent (C++)
    participant H as AgentWebSocketHandler
    participant R as AgentMessageRouterService
    participant G as AgentSessionRegistry

    A->>H: WebSocket handshake<br/>GET /api/v1/management
    H-->>A: 101 Switching Protocols
    Note over H: afterConnectionEstablished<br/>ConcurrentWebSocketSessionDecorator 로 감쌈

    A->>H: {"type":"hello","agent_id":"vm-01",<br/>"device_type":"VM","correlation_id":"c-1"}
    H->>H: readValue(raw, Envelope.class)
    H->>R: handle(session, envelope)
    R->>G: register("vm-01", session)
    G-->>R: ok (total=1)
    R-->>H: ack 봉투 (correlation_id=c-1)
    H->>A: {"type":"ack","correlation_id":"c-1",<br/>"payload":{"connected_agents":1}}
```

`hello` 를 보내는 시점은 Agent 의 `fetchPolicy()` 첫 호출입니다. 즉 **정책이 필요해지는
순간 세션이 등록** 되므로 별도의 연결 관리 코드가 필요 없습니다.

## 2. 정책 요청 → 응답 (correlation_id 매칭)

이 시퀀스가 이 설계의 핵심입니다. STOMP 의 `RECEIPT` 가 하지 못했던 짝 맞추기를
애플리케이션 계층에서 수행합니다.

```mermaid
sequenceDiagram
    autonumber
    participant A as ManagementService
    participant H as AgentWebSocketHandler
    participant R as AgentMessageRouterService
    participant P as PolicyRegistryService

    A->>A: correlation_id = NextCorrelationId()  // c-2
    A->>H: {"type":"policy-request","correlation_id":"c-2",<br/>"device_type":"ROUTER","payload":{"device_id":"rt-01"}}
    H->>R: handle(session, envelope)
    R->>R: DeviceType 해석<br/>(봉투 → payload → device_id 접두사 → 기본 VM)
    R->>P: forDevice(ROUTER, "rt-01")
    P-->>R: 정책 JSON
    R-->>H: policy-response (correlation_id=c-2)
    H->>A: {"type":"policy-response","correlation_id":"c-2","payload":{...}}

    Note over A: CorrelationId(reply) == "c-2" 확인
    A->>A: Payload(reply) 를 정책으로 채택
```

Agent 측 루프는 **다른 correlation_id 의 봉투를 만나면 버리고 계속 기다립니다.**
이 덕분에 응답을 기다리는 동안 서버 푸시가 끼어들어도 요청-응답이 어긋나지 않습니다.

```mermaid
sequenceDiagram
    autonumber
    participant A as ManagementService
    participant S as 서버

    A->>S: policy-request (correlation_id=c-2)
    S-->>A: command (correlation_id=c-9, 무관한 푸시)
    Note over A: c-9 != c-2 → 로그만 남기고 폐기
    S-->>A: policy-response (correlation_id=c-2)
    Note over A: c-2 == c-2 → 채택
```

## 3. 텔레메트리 (일방향)

```mermaid
sequenceDiagram
    autonumber
    participant M as TelemetryMonitor
    participant T as TelemetryService
    participant H as AgentWebSocketHandler
    participant R as AgentMessageRouterService

    loop interval_seconds_ 마다
        M->>M: VmService::CollectNicStatus()
        M->>M: 봉투 생성 (type=telemetry)
        M->>T: sendRequest(wire, "/api/v1/telemetry")
        T->>H: {"type":"telemetry","agent_id":"vm-01",<br/>"payload":{"nic_status":[...]}}
        H->>R: handle(session, envelope)
        R->>R: lastTelemetry.put(agent_id, payload)
        Note over H,R: 응답 없음 (return null)<br/>일방향이므로 전송이 곧 완료
        M->>M: DB 큐에 저장 (EnqueueNicStatusSave)
    end
```

:::note 일방향인 이유
텔레메트리에 응답을 보내면 Agent 의 수신 버퍼에 응답이 쌓여, 이후 `policy-response`
매칭이 밀리게 됩니다. 상태 보고는 **보내고 잊는 것** 이 맞습니다.
:::

## 4. 서버 → Agent 푸시

### 4-1. HTTP 로 트리거

```mermaid
sequenceDiagram
    autonumber
    participant O as 운영자 (curl/UI)
    participant C as AgentStatusController
    participant G as AgentSessionRegistry
    participant H as SessionHolder
    participant A as Agent

    O->>C: POST /api/v1/agents/vm-01/push<br/>{"monitor_interval":15}
    C->>C: toCommandEnvelope(body)<br/>type 을 강제로 "command" 로 고정
    C->>G: sendTo("vm-01", envelope)
    G->>G: sessions.get("vm-01")
    alt Agent 연결됨
        G->>H: synchronized(lock)
        H->>A: {"type":"command","payload":{"monitor_interval":15}}
        G-->>C: true
        C-->>O: {"delivered":true}
    else 미연결
        G-->>C: false
        C-->>O: {"delivered":false,"reason":"agent not connected"}
    end
```

운영자가 임의 `type` 을 밀어 넣어 Agent 상태를 깨뜨리지 못하도록 **항상 `command` 로
고정** 합니다. 또한 본문이 짧은 payload(`{"monitor_interval":15}`)든 완전한 봉투든
모두 받도록 `toCommandEnvelope` 가 처리합니다.

### 4-2. Agent 측 수신과 주기 변경

```mermaid
sequenceDiagram
    autonumber
    participant A as Agent (TelemetryMonitor)
    participant S as 서버

    loop 대기 간격 동안 500ms 마다 폴링
        A->>S: (대기 중)
        S-->>A: command 봉투 (payload.monitor_interval=15)
        A->>A: payload.monitor_interval 확인
        A->>A: interval_seconds_.store(15)
        Note over A: 다음 루프부터 15초 간격으로 전송
    end
```

대기 시간 동안 소켓을 논블로킹으로 잠시 전환해 `read_some` 으로 폴링하므로,
긴 간격(예: 300초)에서도 지시를 즉시 받을 수 있습니다.

## 5. 연결 종료와 재접속

```mermaid
sequenceDiagram
    autonumber
    participant A as Agent
    participant H as AgentWebSocketHandler
    participant G as AgentSessionRegistry

    A--xH: 연결 끊김 (네트워크/종료)
    H->>G: unregister(session)
    G->>G: sessionToAgent.remove(sessionId)
    G->>G: computeIfPresent(key, holder == session ? null : holder)
    Note over G: 내가 최신 세션일 때만 제거<br/>(재접속으로 교체된 경우 유지)

    A->>H: 재접속 + hello (같은 agent_id)
    H->>G: register("vm-01", newSession)
    G->>G: 이전 SessionHolder 발견
    G->>G: closeQuietly(previous.session)
    Note over G: 매핑은 새 세션으로 교체
```

## 6. 잘못된 프레임 처리

```mermaid
sequenceDiagram
    autonumber
    participant A as Agent
    participant H as AgentWebSocketHandler
    participant R as AgentMessageRouterService

    A->>H: "not json at all"
    H->>H: readValue 실패 (RuntimeException)
    H->>A: {"type":"error","error":"invalid JSON envelope: ..."}

    A->>H: {"type":"something-else"}
    H->>R: handle(session, envelope)
    R-->>H: {"type":"error","error":"unsupported message type: something-else"}
    H->>A: error 봉투
    Note over A,H: 연결은 유지됩니다.<br/>프레임 하나가 잘못됐다고 세션을 끊지 않습니다.
```

파싱 실패나 미지원 `type` 은 **연결을 끊지 않고** `error` 봉투로 알립니다.
Agent 가 재연결 루프를 도는 비용보다, 오류를 로그로 남기고 계속 쓰는 편이 낫습니다.
