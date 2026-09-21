/**
 * nginx 프록시를 경유한 Agent WebSocket 연결 검증.
 *
 * 목적:
 *   C++ Prober 는 nginx(frontend)를 통해 /api/v1/management, /api/v1/telemetry
 *   로 붙을 수 있어야 한다. nginx 의 Upgrade/Connection 헤더 처리와
 *   proxy_read_timeout 이 실제로 동작하는지 확인한다.
 *
 * 검증 항목:
 *   1. 프록시 경유로 WebSocket 핸드셰이크가 성공하는가
 *   2. hello 봉투를 보내면 ack 봉투가 돌아오는가
 *   3. 8KB 를 넘는 큰 telemetry 프레임이 거부되지 않는가
 *      (Tomcat 기본 버퍼 8192 를 WebSocketConfig 에서 늘렸는지 확인)
 *
 * ⚠️ Node 22+ 의 내장 WebSocket(WHATWG 표준)을 쓰므로 `ws` 패키지가 필요 없다.
 *    외부 의존성이 없어 어느 호스트/컨테이너에서든 바로 실행된다.
 *
 * 실행: node ws_proxy_check.js [ws://localhost/api/v1/management]
 */
const url = process.argv[2] || "ws://localhost/api/v1/management";
const results = [];

function check(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? "  (" + detail + ")" : ""}`);
}

const ws = new WebSocket(url);

// 핸드셰이크가 5초 안에 안 되면 실패로 본다.
const handshakeTimer = setTimeout(() => {
  check("handshake", false, "timeout 5s");
  try { ws.close(); } catch (_) { /* ignore */ }
  finish();
}, 5000);

let step = 0;

ws.addEventListener("open", () => {
  clearTimeout(handshakeTimer);
  check("handshake", true, url);

  // --- 1단계: hello -> ack ---
  ws.send(
    JSON.stringify({
      type: "hello",
      agent_id: "docker-e2e-check",
      device_type: "linux_vm",
      correlation_id: "c-hello",
      payload: { product: "LinuxVM", vendor: "Ubuntu" },
    })
  );
});

ws.addEventListener("message", (event) => {
  const env = JSON.parse(event.data.toString());

  if (step === 0 && env.type === "ack") {
    step = 1;
    check("hello -> ack", env.correlation_id === "c-hello", `type=${env.type}`);

    // --- 2단계: 8KB 초과 telemetry 프레임 ---
    // FRR 라우터 텔레메트리가 실측 11.8KB 였다. 그보다 큰 40KB 를 보낸다.
    // Tomcat 기본 버퍼(8192)가 그대로면 close 1009 로 끊긴다.
    ws.send(
      JSON.stringify({
        type: "telemetry",
        agent_id: "docker-e2e-check",
        device_type: "linux_vm",
        correlation_id: "c-big",
        payload: {
          product: "LinuxVM",
          vendor: "Ubuntu",
          nic_status: { interfaces: [{ name: "x".repeat(40000) }] },
        },
      })
    );

    // telemetry 는 일방향이라 응답이 없다. 잠시 기다린 뒤
    // 소켓이 살아 있으면(close 1009 가 안 났으면) 성공으로 판정한다.
    setTimeout(() => {
      check(
        "40KB telemetry frame accepted",
        ws.readyState === WebSocket.OPEN,
        `readyState=${ws.readyState}`
      );
      ws.close(1000, "done");
    }, 2500);
  }
});

ws.addEventListener("close", (event) => {
  const reason = event.reason || "-";
  // 1009 = message too big. 이게 나오면 버퍼 설정이 안 먹은 것이다.
  check("no close 1009", event.code !== 1009, `code=${event.code} reason=${reason}`);
  finish();
});

ws.addEventListener("error", () => {
  // 표준 WebSocket 은 오류 상세를 노출하지 않는다. close 이벤트가 원인을 알려준다.
  check("no socket error", false, "error event");
  finish();
});

let finished = false;
function finish() {
  if (finished) return;
  finished = true;
  const failed = results.filter((r) => !r.ok);
  console.log("");
  console.log(`총 ${results.length}건, 실패 ${failed.length}건`);
  process.exit(failed.length === 0 ? 0 : 1);
}
