const http = require('http');
const { WebSocketServer } = require('ws');

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('heartbeat-ok');
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws) => {
  console.log('[ws] client connected');

  ws.on('message', (message) => {
    const text = message.toString();
    console.log('[ws] received:', text);

    if (text === 'hello') {
      ws.send('hello');
    }
  });

  ws.on('close', () => {
    console.log('[ws] client disconnected');
  });
});

const port = 3000;
server.listen(port, () => {
  console.log(`WebSocket heartbeat server listening on ws://localhost:${port}`);
});
