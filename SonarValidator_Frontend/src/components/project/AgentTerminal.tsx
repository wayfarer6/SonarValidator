import { useEffect, useRef, useState } from "react";
import { FitAddon } from "@xterm/addon-fit";
import { Terminal } from "@xterm/xterm";
import "@xterm/xterm/css/xterm.css";
import { API_BASE_URL } from "../../lib/api/client";

interface TerminalMessage {
  type?: string;
  data?: string;
  data_base64?: string;
  message?: string;
  status?: string;
}

interface AgentTerminalProps {
  projectId: string;
  agentId: string;
  onClose: () => void;
}

export default function AgentTerminal({ projectId, agentId, onClose }: AgentTerminalProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [connectionAttempt, setConnectionAttempt] = useState(0);
  const [status, setStatus] = useState("연결 중");

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const terminal = new Terminal({
      cursorBlink: true,
      convertEol: true,
      fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
      fontSize: 13,
      theme: { background: "#111827", foreground: "#e5e7eb" },
    });
    const fit = new FitAddon();
    terminal.loadAddon(fit);
    terminal.open(container);
    fit.fit();
    terminal.writeln(`Connecting to ${agentId}...`);

    const socketUrl = new URL(`${API_BASE_URL}/api/v1/terminal/browser`, window.location.origin);
    socketUrl.protocol = socketUrl.protocol === "https:" ? "wss:" : "ws:";
    socketUrl.searchParams.set("projectId", projectId);
    socketUrl.searchParams.set("agentId", agentId);
    const socket = new WebSocket(socketUrl);
    let disposed = false;
    let terminalErrorReceived = false;
    const sendResize = () => {
      if (socket.readyState === WebSocket.OPEN) {
        fit.fit();
        socket.send(JSON.stringify({
          type: "resize",
          cols: terminal.cols,
          rows: terminal.rows,
        }));
      }
    };
    const resizeObserver = new ResizeObserver(sendResize);
    resizeObserver.observe(container);

    socket.addEventListener("open", () => {
      if (disposed) return;
      setStatus("Agent 연결 대기");
      sendResize();
      terminal.focus();
    });
    socket.addEventListener("message", (event: MessageEvent<string>) => {
      if (disposed) return;
      try {
        const message = JSON.parse(event.data) as TerminalMessage;
        if (message.type === "terminal-output" && message.data_base64) {
          const binary = atob(message.data_base64);
          const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
          terminal.write(bytes);
        } else if (message.type === "terminal-ready") {
          setStatus("연결됨");
          terminal.write("\r\n\x1b[32mAgent shell connected\x1b[0m\r\n");
          sendResize();
        } else if (message.type === "terminal-status") {
          setStatus(message.status === "connecting" ? "셸 시작 중" : message.status ?? "연결 중");
        } else if (message.type === "terminal-error") {
          terminalErrorReceived = true;
          setStatus("오류");
          terminal.write(`\r\n\x1b[31m${message.message ?? "Terminal error"}\x1b[0m\r\n`);
        } else if (message.type === "terminal-exit") {
          setStatus("셸 종료됨");
        }
      } catch {
        terminal.write("\r\n\x1b[31mInvalid message received from terminal server\x1b[0m\r\n");
        setStatus("오류");
      }
    });
    socket.addEventListener("close", () => {
      if (!disposed) {
        if (!terminalErrorReceived) {
          setStatus("연결 종료");
          terminal.writeln("\r\n\x1b[31mTerminal connection closed. Refresh to reconnect.\x1b[0m");
        }
      }
    });
    socket.addEventListener("error", () => {
      if (!disposed) setStatus("WebSocket 연결 오류");
    });
    const inputSubscription = terminal.onData((data) => {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type: "input", data }));
      }
    });

    return () => {
      disposed = true;
      resizeObserver.disconnect();
      inputSubscription.dispose();
      if (socket.readyState === WebSocket.CONNECTING || socket.readyState === WebSocket.OPEN) {
        socket.close(1000, "terminal closed");
      }
      terminal.dispose();
    };
  }, [agentId, connectionAttempt, projectId]);

  return (
    <section className="mt-3 overflow-hidden rounded-xl border border-gray-700 bg-gray-900">
      <header className="flex items-center justify-between border-b border-gray-700 px-3 py-2">
        <div className="min-w-0">
          <h6 className="truncate text-xs font-semibold text-gray-100">
            Terminal · {agentId}
          </h6>
          <p className="text-xs text-gray-400">{status}</p>
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setConnectionAttempt((attempt) => attempt + 1)}
            className="rounded-md p-2 text-gray-300 hover:bg-gray-700 hover:text-white"
            aria-label="터미널 새로고침"
            title="터미널 다시 연결"
          >
            <svg viewBox="0 0 20 20" fill="none" className="h-4 w-4" aria-hidden="true">
              <path d="M16.5 8A6.75 6.75 0 0 0 4.8 5.3L3.5 7M3.5 7V3.8M3.5 7h3.2M3.5 12a6.75 6.75 0 0 0 11.7 2.7l1.3-1.7m0 0v3.2m0-3.2h-3.2" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md px-2 py-1 text-xs text-gray-300 hover:bg-gray-700"
            aria-label="터미널 닫기"
          >
            닫기
          </button>
        </div>
      </header>
      <div ref={containerRef} className="h-80 p-2" />
    </section>
  );
}
