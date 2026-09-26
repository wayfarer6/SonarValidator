/**
 * 백엔드 REST 호출의 얇은 래퍼입니다.
 *
 * <h2>왜 fetch 를 직접 쓰지 않는가</h2>
 * 화면마다 fetch 를 직접 쓰면 (1) 오류 응답 처리가 제각각이 되고,
 * (2) base URL 이 여기저기 하드코딩되며, (3) 404 를 "데이터 없음" 으로
 * 조용히 넘기는 실수가 생깁니다. 이 모듈에서 한 번만 규칙을 정합니다.
 *
 * <h2>base URL 결정 순서</h2>
 * <ol>
 *   <li>{@code VITE_API_BASE_URL} 환경변수 (배포 시 주입)</li>
 *   <li>개발 기본값 — {@code http://<현재 호스트>:3000}</li>
 * </ol>
 *
 * <h2>⚠️ 왜 "현재 호스트" 를 쓰는가 (localhost 하드코딩 금지)</h2>
 * <p>처음에는 {@code http://localhost:3000} 으로 고정했습니다. 그런데 브라우저가
 * {@code http://127.0.0.1:5173} 으로 접속하면 <b>호스트 이름이 다르므로</b>
 * 백엔드(3000)와 <b>cross-site</b> 가 됩니다. 세션 쿠키가
 * {@code SameSite=Lax} 라서 cross-site 요청에는 실리지 않고, 결과는
 * <b>"로그인은 되는데 그 이후 모든 요청이 401"</b> 입니다.
 *
 * <p>원격 개발 환경(VS Code 포트 포워딩)이나 {@code 127.0.0.1} 로 직접 접속하는
 * 경우가 드물지 않으므로, 현재 페이지와 <b>같은 호스트</b>를 기본으로 씁니다.
 * 그러면 localhost 로 열든 127.0.0.1 로 열든 항상 same-site 가 됩니다.
 *
 * <p>Vite 개발 서버(5173)와 백엔드(3000)는 포트가 다르므로 여전히 CORS 가
 * 필요합니다. 백엔드는 WebSocket 핸들러가 있는 같은 앱이라
 * {@code WebMvcConfigurer} 로 CORS 를 열어 두었고, localhost 와 127.0.0.1
 * <b>양쪽</b>을 허용합니다.
 */

/**
 * 현재 페이지의 호스트 이름입니다. (SSR/테스트 환경에서는 localhost)
 *
 * @returns 예: {@code "localhost"}, {@code "127.0.0.1"}
 */
function currentHostname(): string {
  if (typeof window !== "undefined" && window.location?.hostname) {
    return window.location.hostname;
  }
  return "localhost";
}

/** API base URL 입니다. 끝의 슬래시는 제거합니다. */
export const API_BASE_URL: string = (
  import.meta.env.VITE_API_BASE_URL ?? `http://${currentHostname()}:3000`
).replace(/\/+$/, "");

/**
 * API 호출 실패를 나타내는 오류입니다.
 *
 * <p>상태 코드를 보존해 화면이 "없음(404)" 과 "서버 오류(500)" 를 구분할 수
 * 있게 합니다.
 */
export class ApiError extends Error {
  /** HTTP 상태 코드입니다. 네트워크 실패면 0 입니다. */
  readonly status: number;

  /** 서버가 보낸 본문입니다 (있으면). */
  readonly body: unknown;

  /**
   * @param status HTTP 상태 코드 (네트워크 실패면 0)
   * @param message 사람이 읽는 메시지
   * @param body    서버 응답 본문
   */
  constructor(status: number, message: string, body?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }

  /** @returns 리소스가 없어서 실패했으면 true */
  get isNotFound(): boolean {
    return this.status === 404;
  }

  /** @returns 서버에 닿지 못했으면 true (백엔드 미기동 등) */
  get isNetworkError(): boolean {
    return this.status === 0;
  }
}

/** 요청 옵션입니다. */
interface RequestOptions {
  /** HTTP 메서드 (기본 GET). */
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  /** JSON 본문. */
  body?: unknown;
  /** 쿼리 파라미터 (null/undefined 는 생략). */
  params?: Record<string, string | number | boolean | null | undefined>;
  /**
   * 세션 쿠키 전송 여부입니다. 기본값은 `"include"` 입니다.
   *
   * 백엔드가 세션 기반이라 모든 API 요청에 쿠키가 필요합니다. 다른 출처
   * (5173 → 3000)로 보낼 때 브라우저는 기본적으로 쿠키를 생략하므로,
   * `include` 를 명시해야 합니다. 이 값을 빼면 "로그인은 되는데 이후 요청이
   * 전부 401" 이 되는 증상이 나타납니다.
   */
  credentials?: RequestCredentials;
  /**
   * 요청 전체 제한 시간(ms)입니다. 기본값 {@link DEFAULT_TIMEOUT_MS}.
   *
   * <p>fetch 는 기본적으로 타임아웃이 없습니다. 그래서 서버가 응답하지
   * 않으면 <b>영원히 pending</b> 이 되고, 그 promise 를 기다리는 화면은
   * 스피너에 갇힙니다. (예: "로그인 상태를 확인하는 중..." 무한 표시)
   * 여기서 시간을 끊어 오류로 바꿉니다.
   */
  timeoutMs?: number;
  /**
   * 복구 시도 횟수입니다.
   *
   * <p>기본값은 <b>GET 이면 {@link DEFAULT_RETRIES}, 그 외 메서드면 0</b>
   * 입니다. POST 는 재전송하면 중복 생성/중복 처리가 될 수 있어 자동
   * 재시도하지 않습니다.
   *
   * <p>백엔드(특히 Spring Boot)는 시작 직후 몇 초 동안 포트가 열려 있어도
   * 응답하지 못합니다. 그 순간의 한 번 실패로 화면이 굳으면 안 되므로
   * GET 은 타임아웃/일시적 서버 오류에 한해 짧게 재시도합니다.
   */
  retries?: number;
}

/** 요청 제한 시간 기본값(ms). 이 시간 안에 응답이 없으면 끊습니다. */
const DEFAULT_TIMEOUT_MS = 8_000;

/** GET 의 복구 시도 기본 횟수. 최초 시도를 포함해 최대 {@code retries + 1} 번 요청합니다. */
const DEFAULT_RETRIES = 2;

/** 재시도 사이 대기 시간(ms). */
const RETRY_BASE_DELAY_MS = 400;

/**
 * 타임아웃 전용 표식 오류입니다.
 *
 * <p>{@code AbortController} 를 쓰면 "호출자가 취소" 와 "시간 초과" 를
 * 구분할 수 없습니다. 그래서 시간이 다 되면 이 표식으로 abort 하고,
 * catch 에서 이 표식 여부로 재시도 가능성을 판단합니다.
 */
class RequestTimeoutError extends Error {
  constructor() {
    super("request timed out");
    this.name = "RequestTimeoutError";
  }
}

/** {@code ms} 만큼 기다립니다. 재시도 백오프에 씁니다. */
function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 쿼리 문자열을 만듭니다.
 *
 * @param params 파라미터 맵
 * @returns `?a=1&b=2` 형태, 값이 없으면 빈 문자열
 */
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

/**
 * 백엔드에 JSON 요청을 보냅니다.
 *
 * <p>타임아웃과 재시도를 포함합니다. 서버가 응답하지 않을 때 promise 가
 * 영원히 pending 이 되지 않도록 반드시 시간을 끊습니다.
 *
 * @param path    `/api/v1/...` 로 시작하는 경로
 * @param options 메서드/본문/쿼리/타임아웃
 * @returns 파싱된 응답 (빈 본문이면 null)
 * @throws ApiError 실패 시 (상태 코드 보존, 시간 초과/연결 실패는 0)
 */
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
  let lastError: ApiError | null = null;

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    // 요청마다 새 컨트롤러가 필요합니다. abort 된 컨트롤러는 재사용할 수 없습니다.
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(new RequestTimeoutError()), timeoutMs);

    let response: Response;
    try {
      response = await fetch(url, {
        method,
        // 세션 쿠키를 주고받으려면 필수입니다. (다른 출처 + 세션 인증)
        credentials,
        headers,
        body: payloadBody,
        signal: controller.signal,
      });
    } catch (cause) {
      clearTimeout(timer);
      // 시간 초과(AbortError + 표식) 또는 네트워크 실패입니다.
      lastError = new ApiError(
        0,
        `백엔드에 연결할 수 없습니다 (${API_BASE_URL}). 서버가 실행 중인지 확인하세요.`,
        cause,
      );
      if (attempt < attempts) {
        await delay(RETRY_BASE_DELAY_MS * attempt);
        continue;
      }
      throw lastError;
    }
    clearTimeout(timer);

    const text = await response.text();
    const payload = text ? safeParse(text) : null;

    if (!response.ok) {
      const detail =
        (payload && typeof payload === "object" && "message" in payload
          ? String((payload as { message: unknown }).message)
          : null) ?? `${response.status} ${response.statusText}`;
      // 500/502/503/504 는 백엔드가 기동 중일 때 흔하므로 재시도합니다.
      // 4xx 는 요청 자체의 문제라 다시 보내도 같은 결과입니다.
      if (response.status >= 500 && attempt < attempts) {
        lastError = new ApiError(response.status, detail, payload);
        await delay(RETRY_BASE_DELAY_MS * attempt);
        continue;
      }
      throw new ApiError(response.status, detail, payload);
    }

    return payload as T;
  }

  // 위 루프는 항상 return 하거나 throw 합니다. 여기는 타입 만족용입니다.
  throw lastError ?? new ApiError(0, `요청에 실패했습니다 (${API_BASE_URL}).`);
}

/**
 * 본문을 JSON 으로 파싱하되, 실패하면 원문을 돌려줍니다.
 *
 * <p>오류 응답이 JSON 이 아닐 수 있으므로 파싱 실패로 원인을 잃지 않게 합니다.
 *
 * @param text 원문
 * @returns 파싱 결과 또는 원문
 */
function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
