---
sidebar_position: 2
---

# 봉투 스키마

서버의 `Envelope.java` 와 Agent 의 `envelope.hpp` 는 **필드명이 정확히 일치** 해야 합니다.
모든 필드는 `snake_case` 입니다.

## 필드 정의

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `type` | string | 예 | 메시지 종류. 미지정/공백이면 `unknown` 으로 정규화됩니다. |
| `agent_id` | string | 아니오 | Agent 식별자. `ProberConfig` 의 `AGENT_ID` 에서 옵니다. |
| `device_type` | string | 아니오 | `SWITCH` / `ROUTER` / `FIREWALL` / `VM` 중 하나(대문자). |
| `correlation_id` | string | 아니오 | 요청-응답 짝. Agent 는 `c-1`, `c-2` 형식으로 생성합니다. |
| `payload` | object | 아니오 | 본문. 서버는 항상 객체를 보장합니다(널이면 `{}`). |
| `error` | string | 아니오 | `type == "error"` 일 때의 설명. |

:::note 왜 payload 를 항상 객체로 두는가
Agent 의 `policy_json` 헬퍼와 서버의 JSON 처리 코드가 "payload 는 객체" 를 전제로
`contains()` / `find()` 를 호출합니다. 널을 허용하면 모든 호출 지점에 방어 코드가
필요해지므로, **생성 시점에 `{}` 로 정규화** 하는 편이 훨씬 단순합니다.
:::

## 실제 프레임 예시

### 1) 연결 직후 인사

```json
{
  "type": "hello",
  "agent_id": "vm-01",
  "device_type": "VM",
  "correlation_id": "c-1",
  "payload": { "agent_name": "vm-01", "device_type": "VM" }
}
```

서버 응답:

```json
{
  "type": "ack",
  "agent_id": "vm-01",
  "device_type": "VM",
  "correlation_id": "c-1",
  "payload": {
    "agent_id": "vm-01",
    "server_time": "2026-09-16T21:23:56.565424732Z",
    "connected_agents": 1
  }
}
```

### 2) 정책 요청

```json
{
  "type": "policy-request",
  "agent_id": "rt-01",
  "device_type": "ROUTER",
  "correlation_id": "c-2",
  "payload": { "device_id": "rt-01" }
}
```

### 3) 정책 응답

```json
{
  "type": "policy-response",
  "agent_id": "rt-01",
  "device_type": "ROUTER",
  "correlation_id": "c-2",
  "payload": {
    "policy_id": "pol-router-0001",
    "device_type": "ROUTER",
    "device_id": "rt-01",
    "valid_until": "2026-09-17T21:19:00Z",
    "policies": [
      {
        "name": "rt-if",
        "vendor": ["Cisco IOS XE"],
        "command": ["on"],
        "interface": ["GigabitEthernet0/0/1"]
      }
    ]
  }
}
```

:::warning 스칼라를 배열로 감싸는 규칙
`docs/Agent/*_Policy_Design.md` 의 정책 스키마는 `"command": ["on"]` 처럼
**스칼라도 배열로 감쌉니다**. 이유는 같은 필드가 단일 값과 다중 값을 모두 가질 수
있기 때문입니다. Agent 의 `policy_json::AsString` 은 배열과 스칼라를 **둘 다** 읽도록
되어 있으므로 어느 쪽이 와도 안전합니다.
:::

### 4) 텔레메트리 (일방향)

```json
{
  "type": "telemetry",
  "agent_id": "vm-01",
  "device_type": "VM",
  "correlation_id": "c-3",
  "payload": {
    "agent": "vm-01",
    "kernel": "6.8.0",
    "nic_status": [
      { "name": "ens33", "state": "up" }
    ]
  }
}
```

### 5) 서버 푸시 (지시)

```json
{
  "type": "command",
  "agent_id": "vm-01",
  "device_type": "",
  "correlation_id": "c-9",
  "payload": { "monitor_interval": 15 }
}
```

### 6) 오류

```json
{
  "type": "error",
  "correlation_id": "c-4",
  "error": "unsupported message type: something-else"
}
```

## 파싱 규칙 (Agent 측)

| 헬퍼 | 동작 |
| --- | --- |
| `envelope::Type(msg)` | `type` 이 문자열이 아니면 빈 문자열 반환 |
| `envelope::CorrelationId(msg)` | 없으면 빈 문자열 반환 |
| `envelope::Payload(msg)` | 객체가 아니면 정적 빈 객체 참조 반환 |
| `envelope::ErrorText(msg)` | 없으면 빈 문자열 반환 |
| `envelope::IsType(msg, t)` | `Type(msg) == t` |

이들은 모두 **결측 필드에 안전** 합니다. 스키마 진화(필드 추가/삭제)에도 파싱이
깨지지 않게 하기 위한 설계입니다.
