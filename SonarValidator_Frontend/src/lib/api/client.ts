import { errorLog, formatErrorDetails, requestErrorMessage } from "../errors";

function currentHostname(): string {
  if (typeof window !== "undefined" && window.location?.hostname) {
    return window.location.hostname;
  }
  return "localhost";
}

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;
const localApiHost = currentHostname();

export const API_BASE_URL: string = (
  configuredApiBaseUrl &&
  (localApiHost === "localhost" || localApiHost === "127.0.0.1") &&
  /^https?:\/\/(localhost|127\.0\.0\.1)(?::\d+)?$/i.test(configuredApiBaseUrl)
    ? configuredApiBaseUrl.replace(/^(https?:\/\/)(localhost|127\.0\.0\.1)/i, `$1${localApiHost}`)
    : configuredApiBaseUrl ?? `http://${localApiHost}:3000`
).replace(/\/+$/, "");

export class ApiError extends Error {
  readonly status: number;

  readonly body: unknown;

  readonly details: string;

  constructor(status: number, message: string, body?: unknown) {
    super(requestErrorMessage(status, message));
    this.name = "ApiError";
    this.status = status;
    this.body = body;
    this.details = formatErrorDetails(body ?? message);
  }

  record(method: string, path: string): this {
    if (!(this.status === 401 && path === "/api/v1/auth/me")) {
      errorLog.record({ summary: this.message, details: this.details, status: this.status, method, path });
    }
    return this;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get isNetworkError(): boolean {
    return this.status === 0;
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  params?: Record<string, string | number | boolean | null | undefined>;
  credentials?: RequestCredentials;
  timeoutMs?: number;
  retries?: number;
}

const DEFAULT_TIMEOUT_MS = 8_000;

const DEFAULT_RETRIES = 2;

const RETRY_BASE_DELAY_MS = 400;

class RequestTimeoutError extends Error {
  constructor() {
    super("request timed out");
    this.name = "RequestTimeoutError";
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function buildQuery(params?: RequestOptions["params"]): string {
  if (!params) return "";
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === "") continue;
    search.append(key, String(value));
  }
  const query = search.toString();
  return query ? `?${query}` : "";
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const {
    method = "GET",
    body,
    params,
    credentials = "include",
    timeoutMs = DEFAULT_TIMEOUT_MS,
    // POST/PUT/PATCH/DELETE 는 중복 부작용을 피하려고 자동 재시도하지 않습니다.
    retries = method === "GET" ? DEFAULT_RETRIES : 0,
  } = options;
  const url = `${API_BASE_URL}${path}${buildQuery(params)}`;
  const headers = body === undefined ? undefined : { "Content-Type": "application/json" };
  const payloadBody = body === undefined ? undefined : JSON.stringify(body);

  const attempts = Math.max(0, retries) + 1;

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    // 요청마다 새 컨트롤러가 필요합니다. abort 된 컨트롤러는 재사용할 수 없습니다.
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(new RequestTimeoutError()), timeoutMs);

    let response: Response;
    let text: string;
    try {
      response = await fetch(url, {
        method,
        // 세션 쿠키를 주고받으려면 필수입니다. (다른 출처 + 세션 인증)
        credentials,
        headers,
        body: payloadBody,
        signal: controller.signal,
      });
      text = await response.text();
    } catch (cause) {
      clearTimeout(timer);
      if (attempt < attempts) {
        await delay(RETRY_BASE_DELAY_MS * attempt);
        continue;
      }
      const message = method !== "GET" ? "응답을 확인하지 못했습니다. 실행 이력을 확인하세요." : controller.signal.aborted ? "요청 시간이 초과됐습니다." : "서버에 연결할 수 없습니다.";
      throw new ApiError(0, message, { message, cause }).record(method, path);
    }
    clearTimeout(timer);

    const payload = text ? safeParse(text) : null;

    if (!response.ok) {
      const detail =
        (payload && typeof payload === "object" && ("message" in payload || "detail" in payload)
          ? String((payload as { message?: unknown; detail?: unknown }).message ?? (payload as { detail?: unknown }).detail)
          : null) ?? `${response.status} ${response.statusText}`;
      // 500/502/503/504 는 백엔드가 기동 중일 때 흔하므로 재시도합니다.
      // 4xx 는 요청 자체의 문제라 다시 보내도 같은 결과입니다.
      if (response.status >= 500 && attempt < attempts) {
        await delay(RETRY_BASE_DELAY_MS * attempt);
        continue;
      }
      throw new ApiError(response.status, detail, payload ?? text).record(method, path);
    }

    if (/\/auth\/(login|logout)$/.test(path)) errorLog.clear();
    return payload as T;
  }

  // 위 루프는 항상 return 하거나 throw 합니다. 여기는 타입 만족용입니다.
  throw new ApiError(0, "요청을 완료하지 못했습니다.").record(method, path);
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
