/**
 * SonarValidator Agent 테스트용 봉투(envelope) WebSocket 서버.
 *
 * 실제 백엔드(Spring Boot, WebSocketConfig.java)를 띄우지 않고도 C++ 에이전트의
 * 통신 경로를 검증하기 위한 최소 구현입니다. STOMP를 쓰지 않고 순수 WebSocket
 * 텍스트 프레임 위에서 "type" 필드로 메시지를 구분합니다.
 *
 * 실제 서버와 반드시 일치해야 하는 계약:
 *   - 포트: 3000
 *   - 경로: /api/v1/management, /api/v1/telemetry
 *   - 봉투: {type, agent_id, device_type, correlation_id, payload, error}
 *   - "hello"           -> "ack"            (세션 등록)
 *   - "policy-request"  -> "policy-response" (같은 correlation_id)
 *   - "telemetry"       -> 응답 없음 (일방향)
 *   - "ack" / "error"   -> 응답 없음
 *   - 그 외              -> "error"
 *
 * 실행: npm run envelope
 */

const http = require('http');
const express = require('express');
const { WebSocketServer } = require('ws');

const PORT = 3000;
const SUPPORTED_ENDPOINTS = new Set(['/api/v1/management', '/api/v1/telemetry']);

const TYPES = {
  HELLO: 'hello',
  POLICY_REQUEST: 'policy-request',
  POLICY_RESPONSE: 'policy-response',
  TELEMETRY: 'telemetry',
  COMMAND: 'command',
  ACK: 'ack',
  ERROR: 'error',
};

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ noServer: true });

/** agent_id -> { endpoint, lastSeen, lastTelemetry } */
const sessions = new Map();

/** 테스트 제어용: 60초마다 command 봉투를 보낼지 여부 */
const AUTO_PUSH_INTERVAL_MS = Number(process.env.MOCK_PUSH_INTERVAL_MS || 0);

function sendJson(ws, message) {
  if (ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify(message));
  }
}

/**
 * 봉투를 만듭니다. 서버(Java Envelope)와 동일하게 payload 는 항상 객체입니다.
 */
function envelope(type, { agentId = '', deviceType = '', correlationId = '', payload = {}, error } = {}) {
  const message = {
    type,
    agent_id: agentId,
    device_type: deviceType,
    correlation_id: correlationId,
    payload: payload || {},
  };
  if (error !== undefined) {
    message.error = error;
  }
  return message;
}

function errorEnvelope(correlationId, text) {
  return envelope(TYPES.ERROR, { correlationId, error: text });
}

/**
 * 장치 유형별 더미 정책. docs/Agent/*_Policy_Design.md 의 배열 감싸기 규칙을 따릅니다.
 * (스칼라 값도 배열로 감싸며, C++ policy_json 은 배열/스칼라 둘 다 허용합니다.)
 */
function policyFor(deviceType, deviceId) {
  const type = (deviceType || 'VM').toUpperCase();
  const rules = {
    VM: [{ name: 'vm-link', command: ['on'], interface: ['ens33'] }],
    SWITCH: [{ name: 'sw-port', vendor: ['Arista'], enable: ['true'], port: ['Ethernet 1'] }],
    ROUTER: [{ name: 'rt-if', vendor: ['Cisco IOS XE'], command: ['on'], interface: ['GigabitEthernet0/0/1'] }],
    FIREWALL: [
      { name: 'fw-table', command: ['add'], family: ['inet'], table: ['filter'] },
      { name: 'fw-input', command: ['add'], chain: ['input'], hook: ['input'] },
      { name: 'fw-forward', command: ['add'], chain: ['forward'], hook: ['forward'] },
    ],
  };

  return {
    policy_id: `pol-${type.toLowerCase()}-0001`,
    device_type: type,
    device_id: deviceId || 'mock-device',
    valid_until: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    policies: rules[type] || rules.VM,
  };
}

/**
 * 봉투 하나를 처리하고, 응답이 있으면 반환합니다. 없으면 null.
 */
function handleEnvelope(ws, endpoint, message) {
  const type = typeof message.type === 'string' ? message.type : '';
  const correlationId = typeof message.correlation_id === 'string' ? message.correlation_id : '';
  const agentId = typeof message.agent_id === 'string' ? message.agent_id : '';
  const deviceType = typeof message.device_type === 'string' ? message.device_type : '';

  if (agentId) {
    const previous = sessions.get(agentId);
    sessions.set(agentId, {
      endpoint,
      lastSeen: Date.now(),
      lastTelemetry: previous ? previous.lastTelemetry : null,
    });
  }

  switch (type) {
    case TYPES.HELLO: {
      console.log(`[hello] agent_id=${agentId} device_type=${deviceType} endpoint=${endpoint}`);
      return envelope(TYPES.ACK, {
        agentId,
        deviceType,
        correlationId,
        payload: {
          server_time: new Date().toISOString(),
          connected_agents: sessions.size,
        },
      });
    }

    case TYPES.POLICY_REQUEST: {
      const deviceId =
        (message.payload && message.payload.device_id) || agentId || 'mock-device';
      const effectiveType = deviceType || 'VM';
      console.log(
        `[policy-request] agent_id=${agentId} device_type=${effectiveType} device_id=${deviceId} correlation_id=${correlationId}`
      );
      return envelope(TYPES.POLICY_RESPONSE, {
        agentId,
        deviceType: effectiveType,
        correlationId,
        payload: policyFor(effectiveType, deviceId),
      });
    }

    case TYPES.TELEMETRY: {
      const session = sessions.get(agentId);
      if (session) {
        session.lastTelemetry = message.payload || {};
        session.lastSeen = Date.now();
      }
      console.log(`[telemetry] agent_id=${agentId} payload=${JSON.stringify(message.payload || {})}`);
      return null; // 일방향
    }

    case TYPES.ACK:
    case TYPES.ERROR: {
      console.log(`[${type}] agent_id=${agentId} payload=${JSON.stringify(message.payload || {})}`);
      return null;
    }

    default: {
      console.log(`[error] unsupported type=${type || '(missing)'}`);
      return errorEnvelope(correlationId, `unsupported message type: ${type || '(missing)'}`);
    }
  }
}

wss.on('connection', (ws, request) => {
  const endpoint = new URL(request.url, `http://${request.headers.host}`).pathname;
  console.log(`[ws] client connected endpoint=${endpoint}`);

  // 서버 -> 에이전트 푸시 경로 검증용. MOCK_PUSH_INTERVAL_MS=45000 등으로 켭니다.
  let pushTimer = null;
  if (AUTO_PUSH_INTERVAL_MS > 0) {
    pushTimer = setInterval(() => {
      console.log('[push] command monitor_interval');
      sendJson(
        ws,
        envelope(TYPES.COMMAND, {
          correlationId: `push-${Date.now()}`,
          payload: { monitor_interval: 15 },
        })
      );
    }, AUTO_PUSH_INTERVAL_MS);
  }

  ws.on('message', (raw) => {
    const text = raw.toString();
    console.log('[ws] received:', text);

    let message;
    try {
      message = JSON.parse(text);
    } catch {
      sendJson(ws, errorEnvelope('', 'invalid JSON envelope'));
      return;
    }

    const reply = handleEnvelope(ws, endpoint, message);
    if (reply) {
      sendJson(ws, reply);
    }
  });

  ws.on('close', () => {
    if (pushTimer) {
      clearInterval(pushTimer);
    }
    console.log(`[ws] client disconnected endpoint=${endpoint}`);
  });
});

server.on('upgrade', (request, socket, head) => {
  const requestUrl = new URL(request.url, `http://${request.headers.host}`);

  if (!SUPPORTED_ENDPOINTS.has(requestUrl.pathname)) {
    console.log(`[ws] rejected upgrade path=${requestUrl.pathname}`);
    socket.write('HTTP/1.1 404 Not Found\r\n\r\n');
    socket.destroy();
    return;
  }

  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit('connection', ws, request);
  });
});

server.listen(PORT, () => {
  console.log(`Envelope mock server listening on ws://localhost:${PORT}`);
  console.log(`  endpoints: ${[...SUPPORTED_ENDPOINTS].join(', ')}`);
  if (AUTO_PUSH_INTERVAL_MS > 0) {
    console.log(`  auto command push every ${AUTO_PUSH_INTERVAL_MS}ms`);
  }
});
