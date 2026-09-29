---
sidebar_position: 1
---

# 통신 구조 개요 (plain WebSocket + JSON 봉투)

## 1. 왜 STOMP 를 버렸는가

기존 구현은 Spring 의 `@EnableWebSocketMessageBroker` + SockJS + STOMP 였습니다.
여기에는 세 가지 치명적인 문제가 있었습니다.

| 문제 | 설명 |
| --- | --- |
| 프레임 생성 불가 | Agent 는 C++23 + Boost.Beast 로 **raw WebSocket** 만 다룹니다. STOMP 는 `CONNECT`/`SUBSCRIBE`/`SEND` 같은 텍스트 프레임 규약인데, Beast 는 이 프레임을 만들어 주지 않습니다. |
| 요청-응답 상관관계 없음 | Spring 의 `SimpleBroker` 는 `RECEIPT` 를 구현하지 않고 `ACK`/`NACK` 도 처리하지 않습니다. (실제로 처리하는 것은 `StompBrokerRelayMessageHandler` 뿐입니다.) 그래서 "이 응답이 내 요청의 응답인가" 를 알 방법이 없었습니다. |
| 배포 의존성 | SockJS 는 브라우저 폴백을 위한 계층입니다. 서버-에이전트 통신에는 불필요한 복잡도만 추가합니다. |

## 2. 대안: 봉투(envelope) 하나로 통일

프레임 계층을 없애고, **모든 메시지를 같은 JSON 객체 하나**로 표현합니다.

```json
{
  "type": "hello | policy-request | policy-response | telemetry | command | ack | error",
  "agent_id": "vm-01",
  "device_type": "VM",
  "correlation_id": "c-7",
  "payload": { },
  "error": null
}
```

핵심 아이디어는 **요청-응답 짝을 프레임이 아니라 데이터로 맞춘다** 는 것입니다.
클라이언트가 `correlation_id` 를 만들어 보내면 서버는 그 값을 그대로 되돌려줍니다.
STOMP 의 `receipt` 가 하지 못했던 일을 애플리케이션 계층에서 5줄로 해결합니다.

## 3. 엔드포인트

두 경로 모두 **같은 핸들러** 로 연결되며, 실제 분기는 봉투의 `type` 필드가 담당합니다.
경로는 의미를 위한 라벨일 뿐 라우팅 키가 아닙니다.

| 경로 | 용도 | 포트 |
| --- | --- | --- |
| `/api/v1/management` | 정책 요청/응답, 서버 푸시 수신 | 3000 |
| `/api/v1/telemetry` | 상태 보고(일방향) | 3000 |
| `/api/v1/agents` | 운영자용 REST 조회/푸시 (HTTP) | 3000 |

포트 3000 은 `SonarValidator_Prober/Installer/default.conf` 의 `SERVER_PORT=3000` 과
일치시키기 위한 것입니다. (`application.properties` 의 `server.port=3000`)

## 4. 메시지 종류 한눈에 보기

| type | 방향 | 응답 | 설명 |
| --- | --- | --- | --- |
| `hello` | Agent → 서버 | `ack` | 연결 직후 세션 등록 |
| `policy-request` | Agent → 서버 | `policy-response` | 정책 요청 |
| `policy-response` | 서버 → Agent | - | 정책 본문 |
| `telemetry` | Agent → 서버 | 없음 | 상태 보고(일방향) |
| `command` | 서버 → Agent | 없음 | 지시(예: `monitor_interval`) |
| `ack` | Agent → 서버 | 없음 | 정책 적용 완료 통보 |
| `error` | 양방향 | 없음 | 파싱/처리 실패 |

## 5. 이 구조의 장점

- **언어 중립**: C++ 는 `nlohmann/json`, Java 는 Jackson 으로 같은 객체를 만듭니다.
- **디버깅 용이**: `wscat` 이나 브라우저 개발자 도구로 그대로 읽고 쓸 수 있습니다.
- **확장 용이**: 새 메시지 종류는 `type` 상수 하나와 핸들러 분기 하나만 추가하면 됩니다.
- **무상태 전송**: `correlation_id` 는 메시지에 실려 있으므로 서버 재시작에도 견딥니다.
