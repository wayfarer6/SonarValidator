import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { Script } from "node:vm";
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { JSDOM } from "jsdom";
import ts from "typescript";

for (const apiBase of ["", "http://localhost:3000"]) test(`terminal lifecycle and URL with API base ${apiBase || "same-origin"}`, async () => {
  const dom = new JSDOM("<div id='root'></div>", { url: "http://localhost:5173" });
  const previousGlobals = new Map();
  for (const [name, value] of Object.entries({
    window: dom.window,
    document: dom.window.document,
    IS_REACT_ACT_ENVIRONMENT: true,
  })) {
    previousGlobals.set(name, Object.getOwnPropertyDescriptor(globalThis, name));
    Object.defineProperty(globalThis, name, { value, configurable: true, writable: true });
  }

  const sockets = [];
  class MockSocket extends EventTarget {
    static CONNECTING = 0;
    static OPEN = 1;
    readyState = MockSocket.CONNECTING;
    sent = [];
    constructor(url) {
      super();
      assert.equal(new URL(url).origin, apiBase ? "ws://localhost:3000" : "ws://localhost:5173");
      assert.equal(new URL(url).pathname, "/api/v1/terminal/browser");
      sockets.push(this);
    }
    close() { this.readyState = 3; }
    send(payload) { this.sent.push(JSON.parse(payload)); }
    open() {
      assert.equal(this.readyState, MockSocket.CONNECTING);
      this.readyState = MockSocket.OPEN;
      this.dispatchEvent(new Event("open"));
    }
    receive(message) {
      const event = new Event("message");
      event.data = JSON.stringify(message);
      this.dispatchEvent(event);
    }
  }
  class MockTerminal {
    cols = 100;
    rows = 30;
    loadAddon() {}
    open() {}
    writeln() {}
    write() {}
    focus() {}
    dispose() {}
    onData() { return { dispose() {} }; }
  }
  const modules = {
    react: React,
    "@xterm/addon-fit": { FitAddon: class { fit() {} } },
    "@xterm/xterm": { Terminal: MockTerminal },
    "@xterm/xterm/css/xterm.css": {},
    "../../lib/api/client": { API_BASE_URL: apiBase },
  };
  const source = readFileSync(new URL("../src/components/project/AgentTerminal.tsx", import.meta.url), "utf8");
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.React, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const componentExports = {};
  new Script(compiled).runInNewContext({
    exports: componentExports,
    require(name) {
      assert.ok(name in modules, `Unexpected import: ${name}`);
      return modules[name];
    },
    React,
    window: dom.window,
    URL,
    WebSocket: MockSocket,
    ResizeObserver: class { observe() {} disconnect() {} },
  });
  const root = createRoot(dom.window.document.getElementById("root"));
  const render = () => React.createElement(React.StrictMode, null,
    React.createElement(componentExports.default, {
      projectId: "test-project", agentId: "test-vm", onClose() {},
    }));
  try {
    await act(async () => root.render(render()));
    assert.equal(sockets.length, 2);
    assert.equal(sockets[0].readyState, 3, "StrictMode must close its discarded CONNECTING socket");
    assert.equal(sockets[1].readyState, MockSocket.CONNECTING);

    await act(async () => sockets[1].open());
    assert.match(dom.window.document.body.textContent, /Agent 연결 대기/);
    await act(async () => sockets[1].receive({ type: "terminal-ready" }));
    assert.match(dom.window.document.body.textContent, /연결됨/);
    assert.ok(sockets[1].sent.some(message => message.type === "resize"));

    await act(async () => dom.window.document.querySelector('[aria-label="터미널 새로고침"]').click());
    assert.equal(sockets[1].readyState, 3, "Reconnect must close the OPEN socket");
    assert.equal(sockets[2].readyState, MockSocket.CONNECTING);
    await act(async () => root.unmount());
    assert.equal(sockets[2].readyState, 3, "Unmount must close a pending connection");
  } finally {
    await act(async () => root.unmount());
    dom.window.close();
    for (const [name, descriptor] of previousGlobals) {
      if (descriptor) Object.defineProperty(globalThis, name, descriptor);
      else delete globalThis[name];
    }
  }
});