---
sidebar_position: 5
---

# 실행과 검증

## 서버 실행

```bash
cd SonarValidator_Backend
export JAVA_HOME=/home/osboxes/.sdkman/candidates/java/26.0.2-oracle
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

3000 포트에서 기동되는지 확인합니다.

```bash
curl -s http://localhost:3000/api/v1/agents
# {"connected":0,"total_policy_requests":0,"server_time":"...","agents":[]}
```

## Agent 테스트 실행

### 1) 봉투 단위 테스트 (서버 불필요)

```bash
cd SonarValidator_Prober
cmake -S . -B build -DCMAKE_BUILD_TYPE=Debug
cmake --build build -j4
./build/envelope_test
```

31개 검증을 수행합니다: 필드 존재, `correlation_id` 유일성, `DeviceType` 문자열 변환,
널 payload 정규화, 결측 필드 안전성, 배열/스칼라 양쪽 읽기, dump/parse 왕복.

### 2) 프로토콜 통합 테스트 (서버 필요)

서버가 없으면 SKIP(종료 코드 0)이라 CI 를 막지 않습니다.

```bash
./build/telemetry_service_test
./build/management_service_integration_test
```

### 3) 서버 푸시 수동 검증

한 터미널에서 연결을 유지한 채 대기합니다.

```bash
./build/management_service_integration_test --hold 30
```

다른 터미널에서 푸시를 보냅니다.

```bash
curl -s http://localhost:3000/api/v1/agents
curl -s -X POST http://localhost:3000/api/v1/agents/integration-vm-01/push \
     -H 'Content-Type: application/json' -d '{"monitor_interval":15}'
```

에이전트 터미널에 다음이 찍히면 성공입니다.

```
  [hold] <- type=command payload={"monitor_interval":15}
  [hold] would set monitor interval to 15s
```

## mock 서버 (Spring 없이 검증)

```bash
cd Agent_Test/mock_API
npm run envelope          # node envelope_server.js
```

mock 서버는 실제 서버와 **동일한 계약** 을 구현합니다: 포트 3000, 두 경로 모두 수락,
`hello` → `ack`, `policy-request` → 같은 `correlation_id` 의 `policy-response`,
`telemetry` 는 무응답. 덕분에 Spring 을 띄우지 않고도 Agent 를 반복 검증할 수 있습니다.

`MOCK_PUSH_INTERVAL_MS=20000` 을 주면 20초마다 `command` 봉투를 밀어 Agent 의
푸시 수신 경로를 자동으로 검증합니다.

```bash
MOCK_PUSH_INTERVAL_MS=20000 npm run envelope
```

## 전체 테스트 한 번에

```bash
cd SonarValidator_Prober/build && ctest --output-on-failure
```

```
1/5 Test #1: database_service_test ............   Passed
2/5 Test #2: routing_table_test ...............   Passed
3/5 Test #3: prober_config_test ...............   Passed
4/5 Test #4: telemetry_service_test ...........   Passed
5/5 Test #5: envelope_test ....................   Passed
100% tests passed, 0 tests failed out of 5
```

## 문제 해결

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| `connect failed` | 서버가 3000 포트에 없음 | `curl localhost:3000/api/v1/agents` 로 확인 |
| `policy response timeout` | 서버가 `correlation_id` 를 되돌려주지 않음 | 서버 로그에서 `recv policy-request` 확인 |
| `push dropped: agent ... not connected` | Agent 미연결 또는 `agent_id` 불일치 | `AGENT_NAME`/`AGENT_ID` 설정 확인 |
| `invalid JSON envelope` | 프레임이 JSON 이 아님 | Agent 가 `dump()` 결과를 그대로 보내는지 확인 |
| `unsupported message type` | `type` 오타/누락 | `envelope::k*` 상수 사용 |
| 애플리케이션 기동 실패 (`No qualifying bean`) | 설정 클래스가 생성자 파라미터를 요구 | `@Value` 로 프로퍼티 주입 |
