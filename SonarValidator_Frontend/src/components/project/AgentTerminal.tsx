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

    const socketUrl = new URL(`${API_BASE_URL}/api/v1/terminal/browser`);
    socketUrl.protocol = socketUrl.protocol === "https:" ? "wss:" : "ws:";
    socketUrl.searchParams.set("projectId", projectId);
    socketUrl.searchParams.set("agentId", agentId);
    const socket = new WebSocket(socketUrl);
    let disposed = false;
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
      if (!disposed) setStatus("연결 종료");
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
      if (socket.readyState === WebSocket.OPEN) socket.close(1000, "terminal closed");
      terminal.dispose();
    };
  }, [agentId, projectId]);

  return (
    <section className="mt-3 overflow-hidden rounded-xl border border-gray-700 bg-gray-900">
      <header className="flex items-center justify-between border-b border-gray-700 px-3 py-2">
        <div className="min-w-0">
          <h6 className="truncate text-xs font-semibold text-gray-100">
            Terminal · {agentId}
          </h6>
          <p className="text-xs text-gray-400">{status}</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="rounded-md px-2 py-1 text-xs text-gray-300 hover:bg-gray-700"
          aria-label="터미널 닫기"
        >
          닫기
        </button>
      </header>
      <div ref={containerRef} className="h-80 p-2" />
    </section>
  );
}
