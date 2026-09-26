# 부록 — Agent SIGTERM 미종료 결함 분석

**발견일**: 2026-09-26 (RVI 통합 테스트 중)
**대상**: SonarValidator Prober (C++23) 종료 경로
**심각도**: 중간~높음 — 정상 종료 경로가 사실상 없어 **매 종료가 `kill -9`** 이 됩니다

---

## 1. 요약

프로버에 `SIGTERM` 을 보내도 **프로세스가 종료되지 않습니다.** 이전 통합 테스트 결과 보고서는
이를 "SIGTERM 후 종료 지연"으로 기록했으나, 이번 실측에서 **영구 미종료**임을 확인했습니다.

| 항목 | 실측 |
| --- | --- |
| 시그널 처리 | 정상 (`SigPnd: 0`, `SigCgt` 에 `SIGTERM` 포함, `SigBlk: 0`) |
| 반복 SIGTERM | 효과 없음 (여러 번 보내도 동일) |
| main 스레드 | `futex_do_wait` (join 대기) |
| 남은 워커 | `poll_schedule_timeout` (소켓 폴링) — 1개 |
| 서버 소켓 | `ESTAB` 유지 (`127.0.0.1:57628 → 3000`) |
| 종료 방법 | `kill -9` 만 가능 |

---

## 2. 재현 절차

```bash
P=/home/osboxes/SonarValidator/SonarValidator_Prober
rm -rf /tmp/sonar-hang && mkdir -p /tmp/sonar-hang/data

cat > /tmp/dbg.conf <<'EOF'
SERVER_IP=127.0.0.1;
SERVER_PORT=3000;
NODE_TYPE=VM;
AGENT_NAME=Dbg-VM;
EOF

cd "$P"
nohup env SONAR_DATA_DIR=/tmp/sonar-hang/data \
  SONAR_TEMPLATE_PATH="$P/Installer/default_template.sqlite" \
  SONAR_CONFIG_PATH=/tmp/dbg.conf \
  ./build/sonar_validator_prober > /tmp/hang.log 2>&1 &

sleep 75                       # 정책 수신 + 텔레메트리 전송이 시작될 때까지
PID=$(pgrep -f "build/sonar_validator_prober" | head -1)

kill -TERM "$PID"; sleep 15
kill -0 "$PID" 2>/dev/null && echo "미종료 재현" || echo "정상 종료"
```

재현 스크립트: `/tmp/hang_repro.sh`

### 실측 출력 (재현 성공)

```
prober pid=36926
=== SIGTERM 전송 ===
RESULT: 미종료 재현
=== 스레드 wchan ===
  tid=36926    wchan=futex_do_wait
  tid=36939    wchan=poll_schedule_timeout.constprop.0
=== 소켓 상태 ===
1
ESTAB 0  0  127.0.0.1:57628  127.0.0.1:3000  users:(("sonar_validator",pid=36926,fd=14))
```

> **관찰**: SIGTERM 직후에도 **서버와의 TCP 연결이 ESTAB 로 유지**됩니다. 즉 워커가
> 정지 요청을 받지 못했거나, 소켓 대기에서 깨어나지 못했습니다.

---

## 3. 코드 분석

### 3.1 시그널 핸들러와 종료 시퀀스 (`main.cpp`)

```cpp
std::atomic<bool> g_running{true};
void signalHandler(int) { g_running = false; }

// ...
std::signal(SIGINT,  signalHandler);
std::signal(SIGTERM, signalHandler);
// ...
while (g_running.load()) { std::this_thread::sleep_for(100ms); }

database_queue.Close();
telemetry_thread.request_stop();
management_thread.request_stop();
database_thread.request_stop();
// 여기서 jthread 소멸자가 join → 워커가 안 끝나면 영원히 대기
```

**main 은 정상적으로 정지 요청까지 도달**합니다. 그런데도 끝나지 않으므로 **워커가 정지 요청에
응답하지 않는 것**이 원인입니다.

### 3.2 후보 ① — `connect()` / `handshake()` 에 타임아웃 없음 (가장 유력)

`websocket::stream<beast::tcp_stream>` 를 쓰고 있으므로 `beast::tcp_stream` 의
**`expires_after()` 를 설정해야** connect/handshake/read/write 가 제한 시간을 갖습니다.
현재 코드에는 이 설정이 **어디에도 없습니다.**

```cpp
// management_service.cpp (telemetry_service.cpp 도 동일)

bool ManagementService::connect()
{
    try
    {
        if (host_.empty() || port_ <= 0) return false;

        auto const results = resolver_.resolve(host_, std::to_string(port_));

        // ⚠️ expires_after() 없음 → 시스템 기본 TCP 타임아웃까지 블록(수 분)
        beast::get_lowest_layer(stream_).connect(results);

        // ⚠️ expires_after() 없음 → 핸드셰이크 응답이 없으면 무한 블록 가능
        stream_.handshake(host_, target_);

        connected_ = true;
        return true;
    }
    catch (...) { connected_ = false; return false; }
}
```

`TryReceive()` / `tryReceiveText()` 는 **자체 deadline 루프**가 있어 안전하지만,
`connect()`/`handshake()` 는 **그런 보호가 전혀 없습니다.**

**미종료 시나리오**: SIGTERM 시점에 워커가 `connect()` 안에 있으면
→ `handshake()` 블록이 끝나지 않음 → `request_stop()` 무효
→ main 의 join 이 끝나지 않음 → **미종료**.

이 시나리오는 관측(ESTAB 소켓 유지)과 일치합니다.

### 3.3 후보 ② — `fetchPolicy()` 에 `stop_token` 이 없음

```cpp
// ManagementWorker.hpp
while (!stop_token.stop_requested()) {
    const Json policy = management_service.fetchPolicy(config.GetDeviceType(), agent_id);  // stop_token 미전달
    // ...
    std::this_thread::sleep_for(3s);   // 최대 1회 3초 지연
}
```

`fetchPolicy()` 는 `kResponseTimeout{5}` deadline 루프를 돌지만 **`stop_token` 을 받지 않아**
정지 요청을 확인하지 않습니다. 단독으로는 최대 ~8초 지연에 그칩니다(영구 미종료는 아님).
다만 **`TryReceive` 내부에서 `connect()` 가 호출되면 후보 ①과 결합해 영구 미종료**가 됩니다.

```cpp
bool TryReceive(std::string& message, std::chrono::milliseconds timeout)
{
    if (!connected_) return false;
    // ...
    // fetchPolicy 는 connected_ 가 false 면 connect() 를 부르고,
    // connect() 에는 타임아웃이 없다
}

// fetchPolicy
if (!connected_ && !connect()) { return {}; }
```

### 3.4 후보 ③ — `DatabaseQueue::Pop` (배제)

`Pop` 은 `std::condition_variable::wait(lock, stop_token, pred)` 로 **stop_token 을 지원**하고
`Close()` 가 `notify_all()` 을 호출합니다. **정상**입니다.

```cpp
const bool awakened = condition_.wait(lock, stop_token,
                                      [this] { return closed_ || !tasks_.empty(); });
```

`DatabaseWorker` 도 `while (database_queue.Pop(stop_token, task))` 로 정지 토큰을 넘깁니다. 정상.

### 3.5 후보 ④ — `TelemetryMonitor::Run` 간격 대기 (배제)

안쪽 대기 루프가 `stop_token.stop_requested()` 를 확인합니다.

```cpp
while (!stop_token.stop_requested() &&
       std::chrono::steady_clock::now() < deadline) { ... }
```

---

## 4. 결론

| 후보 | 판정 | 근거 |
| --- | --- | --- |
| ① `connect()`/`handshake()` 타임아웃 없음 | **유력 원인** | `expires_after()` 부재, 소켓 ESTAB 유지, `poll_schedule_timeout` |
| ② `fetchPolicy()` stop_token 미전달 | 기여 요인 | 최대 8초 지연 + ①의 진입 경로 |
| ③ `DatabaseQueue::Pop` | 배제 | stop_token 지원 wait, `Close()` 에서 notify |
| ④ `TelemetryMonitor::Run` | 배제 | 대기 루프가 stop_token 확인 |

### 영향

- **정상 종료 경로가 사실상 없음** → 운영/배포 스크립트가 항상 `kill -9` 에 의존합니다.
- `kill -9` 는 SQLite **hot journal** 을 남겨 다음 기동이 `Runtime initialization failed` 로
  실패할 수 있습니다. 배포 스크립트(`deploy_poc_lab.sh`, `restart_in_container.sh`)가
  **DB 복구 로직과 크기 검증을 넣은 이유가 이 결함입니다.**
- 즉 이 결함은 **다른 계층의 방어 코드로 가려져 있었고**, 근본 원인이 남아 있습니다.

### 권장 수정

1. `connect()` 진입부에 소켓 타임아웃을 설정한다.

```cpp
auto& lowest = beast::get_lowest_layer(stream_);
lowest.expires_after(std::chrono::seconds(5));   // connect / handshake 공통
lowest.connect(results);
stream_.handshake(host_, target_);
```

2. `fetchPolicy()` 와 `SendEnvelope()` 에 `stop_token` 을 전달해 각 루프에서 확인한다.
3. `read_some` / `write` 경로에도 `expires_after()` 를 적용해 정지 요청 시 즉시 깨어나게 한다.
4. 회귀 테스트 추가: **"정책 수신 중 SIGTERM → N초 내 종료"** 를 `ctest` 로 고정.

> 이 결함을 방치하면 방화벽 정책이 `DROP` 인 랩(실제 망분리 환경)에서 프로버가
> **영원히 종료되지 않아** 재배포·재기동 자동화가 모두 막힙니다.

---

## 5. 부수 관찰 — 정책 폴백 경로의 `__primary__`

로그에 `Cannot find device "__primary__"` 가 반복 출력됩니다.

```
[INFO] Management worker received policy: {"policies":[{"command":["on"],
        "interface":["__primary__"],"product":["Ubuntu Linux"],...}]}
Cannot find device "__primary__"
```

기존 통합 테스트 보고서의 **결함 #1(VM 정책 폴백 경로의 `__primary__` 미치환)** 수정이
`netplan` 경로에는 적용됐으나, **`on/off` 폴백 경로는 여전히 원문을 그대로 전달**하는
것으로 보입니다(`product=Ubuntu Linux` 분기). 별도 확인이 필요합니다.

---

*부록 — Agent SIGTERM 미종료 결함 분석 (2026-09-26)*