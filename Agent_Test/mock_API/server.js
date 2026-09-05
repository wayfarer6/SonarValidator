const http = require('http');
const express = require('express');
const { WebSocketServer } = require('ws');

// server configuration

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ noServer: true });
const port = 3000;
const supportedEndpoints = new Set([
  '/api/v1/management',
  '/api/v1/telemetry',
]);


// variables

const supportedServices = new Set(['management', 'telemetry']);


// utility function

function sendJson(ws, message) {
  ws.send(JSON.stringify(message));
}

function sendCommand(ws, service, command, payload = {}) {
  if (!supportedServices.has(service) || typeof command !== 'string' || command.length === 0) {
    throw new Error('service and command are required');
  }

  sendJson(ws, {
    type: 'command',
    service,
    command,
    payload,
  });
}

function handleTelemetryMessage(ws, text, endpoint) {
  let telemetry;
  try {
    telemetry = JSON.parse(text);
  } catch {
    console.log(`[telemetry] endpoint=${endpoint} invalid JSON:`, text);
    sendJson(ws, { type: 'error', error: 'telemetry message must be valid JSON' });
    return;
  }

  console.log(`[telemetry] endpoint=${endpoint}`, telemetry);
}

function handleManagementMessage(ws, text) {
  let message;
  try {
    message = JSON.parse(text);
  } catch {
    sendJson(ws, { type: 'error', error: 'management message must be valid JSON' });
    return;
  }

  if (typeof message.service !== 'string' || typeof message.command !== 'string') {
    sendJson(ws, { type: 'error', error: 'service and command are required' });
    return;
  }

  console.log(`[management] command=${message.command}`);
  sendJson(ws, {
    type: 'ack',
    service: 'management',
    command: message.command,
  });
}



function handleAgentMessage(ws, text) {
  let message;
  try {
    message = JSON.parse(text);
  } catch {
    sendJson(ws, { type: 'error', error: 'message must be valid JSON' });
    return;
  }

  if (typeof message.service !== 'string' || typeof message.command !== 'string') {
    sendJson(ws, { type: 'error', error: 'service and command are required' });
    return;
  }

  if (!supportedServices.has(message.service)) {
    sendJson(ws, { type: 'error', error: `unsupported service: ${message.service}` });
    return;
  }

  console.log(`[agent] service=${message.service} command=${message.command}`);
  sendJson(ws, {
    type: 'ack',
    service: message.service,
    command: message.command,
  });
}

// Init Connection
// 초기 연결 => 시스템 정보 받아오기

wss.on('connection', (ws, request) => {
  console.log('[ws] client connected');
  const endpoint = new URL(request.url, `http://${request.headers.host}`).pathname;

  if (endpoint === '/api/v1/management') {
    sendCommand(ws, 'management', 'get_sysinfo');
  }

  ws.on('message', (message) => {
    const text = message.toString();
    console.log('[ws] received:', text);

    if (endpoint === '/api/v1/telemetry') {
      handleTelemetryMessage(ws, text, endpoint);
      return;
    } 

    if (endpoint === '/api/v1/management') {
      const test_policy_kit= {
        
      }
      handleManagementMessage(ws, test_policy_kit);
      return;
    }

    handleAgentMessage(ws, text);
  });


  ws.on('close', () => {
    console.log('[ws] client disconnected');
  });
});

server.on('upgrade', (request, socket, head) => {
  const requestUrl = new URL(request.url, `http://${request.headers.host}`);

  if (!supportedEndpoints.has(requestUrl.pathname)) {
    socket.write('HTTP/1.1 404 Not Found\r\n\r\n');
    socket.destroy();
    return;
  }

  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit('connection', ws, request);
  });
});

server.listen(port, () => {
  console.log(`WebSocket Agent Test server listening on ws://localhost:${port}`);
});
