import type { ApiDeviceLog } from "./api/aiLogs";

export interface LogDetail {
  title: string;
  summary: string;
  details: string;
  metadata?: Record<string, string | number | null | undefined>;
}

export interface RequestErrorEntry {
  id: number;
  time: string;
  summary: string;
  details: string;
  method: string;
  path: string;
  status?: number;
  count: number;
}

const sensitiveKey = /password|passwd|secret|token|authorization|cookie|api[_-]?key|private[_-]?key/i;
const maxDetails = 32_768;

function redactText(text: string): string {
  return text
    .replace(/-----BEGIN [\w ]*PRIVATE KEY-----[\s\S]*?(?:-----END [\w ]*PRIVATE KEY-----|$)/g, "[REDACTED PRIVATE KEY]")
    .replace(/((?:authorization|set-cookie|cookie)\s*[:=]\s*)[^\r\n]+/gi, "$1[REDACTED]")
    .replace(/(https?:\/\/)[^/\s:@]+:[^@\s/]+@/gi, "$1[REDACTED]@")
    .replace(/((?:password|passwd|secret|token|cookie|api[_-]?key|private[_-]?key)[\w-]{0,64}["']?\s*[:=]\s*)(?:"[^"\r\n]*"|'[^'\r\n]*'|[^\s,;&}\]"']+)/gi,
      (match, prefix: string) => /^\s*["']?\[REDACTED/.test(match.slice(prefix.length)) ? match : prefix + "[REDACTED]")
    .replace(/\u001b\[[0-?]*[ -/]*[@-~]/g, "") // eslint-disable-line no-control-regex -- Strip terminal escape sequences.
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, ""); // eslint-disable-line no-control-regex -- Strip non-printing diagnostic controls.
}

export function formatErrorDetails(input: unknown): string {
  if (input == null) return "";
  let value = input;
  if (typeof value === "string") {
    try { value = JSON.parse(value); } catch { /* Plain text response. */ }
  }
  const seen = new WeakSet<object>();
  let text: string;
  try {
    text = typeof value === "string" ? redactText(value) : JSON.stringify(value, (key, item: unknown) => {
      if (sensitiveKey.test(key)) return "[REDACTED]";
      if (typeof item === "bigint") return String(item);
      if (typeof item === "string") return redactText(item);
      if (item && typeof item === "object") {
        if (seen.has(item)) return "[Circular]";
        seen.add(item);
        if (item instanceof Error) return { name: item.name, message: item.message, stack: item.stack, cause: "cause" in item ? item.cause : undefined };
      }
      return item;
    }, 2) ?? String(value ?? "");
  } catch { text = "상세 내용을 표시할 수 없습니다."; }
  return text.length > maxDetails ? text.slice(0, maxDetails) + "\n[표시 한도를 넘어 나머지 내용은 생략했습니다.]" : text;
}

export function compactText(value: unknown, fallback = "내용이 없습니다.", limit = 100): string {
  const text = formatErrorDetails(value).replace(/\s+/g, " ").trim();
  return text ? text.length > limit ? text.slice(0, limit - 1) + "…" : text : fallback;
}

export function requestErrorMessage(status: number, detail: string): string {
  if (status === 0 && detail.includes("이력")) return "응답을 확인하지 못했습니다. 실행 이력을 확인하세요.";
  if (status === 0) return /시간|timed? ?out|timeout/i.test(detail) ? "요청 시간이 초과됐습니다." : "서버에 연결할 수 없습니다.";
  const fallback: Record<number, string> = {
    400: "입력 내용을 확인하세요.", 401: "로그인이 필요합니다.", 403: "이 작업을 수행할 권한이 없습니다.",
    404: "요청한 항목을 찾을 수 없습니다.", 409: "현재 상태를 확인한 뒤 다시 시도하세요.",
    413: "요청한 데이터가 너무 큽니다.", 422: "입력 내용을 확인하세요.", 429: "요청이 많습니다. 잠시 후 다시 시도하세요.",
  };
  if (status >= 500) return "서버에서 요청을 처리하지 못했습니다.";
  const text = formatErrorDetails(detail).split(/\r?\n/, 1)[0].trim();
  if (!text || text.length > 100 || /[{}<>]|Exception|Traceback|\bat \w+\.|SQLSTATE|\[object Object\]/i.test(text)) {
    return fallback[status] ?? "요청을 완료하지 못했습니다.";
  }
  return text;
}

let entries: readonly RequestErrorEntry[] = [];
let nextId = 1;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach(listener => listener());

export const errorLog = {
  getSnapshot: () => entries,
  subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; },
  clear() { entries = []; emit(); },
  record(input: { summary: string; details: unknown; method: string; path: string; status?: number }) {
    const now = Date.now();
    const path = input.path.split(/[?#]/, 1)[0];
    const details = formatErrorDetails(input.details);
    const previous = entries.find(item => item.method === input.method && item.path === path && item.status === input.status && item.details === details && now - Date.parse(item.time) < 60_000);
    const entry: RequestErrorEntry = { ...input, summary: compactText(input.summary, "요청에 실패했습니다."), path, details,
      id: previous?.id ?? nextId++, time: new Date(now).toISOString(), count: (previous?.count ?? 0) + 1 };
    entries = [entry, ...entries.filter(item => item.id !== entry.id)].slice(0, 100);
    emit();
  },
};

export function deviceLogDetails(log: ApiDeviceLog): LogDetail {
  return { title: "장비 로그 상세", summary: compactText(log.message || log.raw), details: formatErrorDetails(log.raw || log.message),
    metadata: { "시각": log.logged_at, "Agent": log.agent_id, "프로젝트": log.project_id, "심각도": log.severity,
      "출처": log.source, "메시지 ID": log.message_id, "반복": log.repeat_count, "메모": log.note } };
}
