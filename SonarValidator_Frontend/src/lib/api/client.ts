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
 *   <li>개발 기본값 {@code http://localhost:3000} — Agent 가 붙는 포트와 동일</li>
 * </ol>
 *
 * <p>Vite 개발 서버(5173)와 백엔드(3000)가 다르므로 개발 중에는 CORS 가
 * 필요합니다. 백엔드는 WebSocket 핸들러가 있는 같은 앱이라
 * {@code WebMvcConfigurer} 로 CORS 를 열어 두었습니다.
 */

/** API base URL 입니다. 끝의 슬래시는 제거합니다. */
export const API_BASE_URL: string = (
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:3000"
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
 * @param path    `/api/v1/...` 로 시작하는 경로
 * @param options 메서드/본문/쿼리
 * @returns 파싱된 응답 (빈 본문이면 null)
 * @throws ApiError 실패 시 (상태 코드 보존)
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, params, credentials = "include" } = options;
  const url = `${API_BASE_URL}${path}${buildQuery(params)}`;

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      // 세션 쿠키를 주고받으려면 필수입니다. (다른 출처 + 세션 인증)
      credentials,
      headers: body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (cause) {
    // fetch 는 네트워크 실패 시에만 던집니다. 백엔드가 꺼져 있으면 여기로 옵니다.
    throw new ApiError(
      0,
      `백엔드에 연결할 수 없습니다 (${API_BASE_URL}). 서버가 실행 중인지 확인하세요.`,
      cause,
    );
  }

  const text = await response.text();
  const payload = text ? safeParse(text) : null;

  if (!response.ok) {
    const detail =
      (payload && typeof payload === "object" && "message" in payload
        ? String((payload as { message: unknown }).message)
        : null) ?? `${response.status} ${response.statusText}`;
    throw new ApiError(response.status, detail, payload);
  }

  return payload as T;
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
