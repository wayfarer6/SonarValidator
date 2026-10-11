import { apiRequest } from "./client";

/**
 * 인증 API 모듈입니다.
 *
 * <h2>세션 쿠키를 함께 보내야 하는 이유</h2>
 * 백엔드는 세션 기반(<code>JSESSIONID</code> 쿠키)입니다. 브라우저는
 * <b>다른 출처</b>(5173 → 3000)로 요청할 때 기본적으로 쿠키를 보내지
 * 않습니다. 그래서 모든 요청에 <code>credentials: "include"</code> 가
 * 필요합니다.
 *
 * <p>이 설정을 빠뜨리면 "로그인은 성공(200)하는데 다음 요청이 전부 401"
 * 이라는 혼란스러운 증상이 나옵니다.
 */

/** 로그인한 사용자 정보입니다. */
export interface AuthUser {
  username: string;
  display_name: string | null;
  role: string | null;
  last_login_at?: string | null;
}

/** 로그인 응답입니다. */
export interface LoginResponse {
  authenticated: boolean;
  username: string;
  display_name?: string | null;
  role?: string | null;
}

/** 서버가 돌려주는 오류 본문입니다. */
interface ErrorBody {
  message?: string;
}

/**
 * 로그인합니다.
 *
 * @param username 아이디 (화면에서는 Email 로 표시)
 * @param password 평문 비밀번호
 * @returns 사용자 정보
 * @throws ApiError 실패 시 (401 자격증명 오류 / 423 잠김 / 403 비활성)
 */
export function login(username: string, password: string): Promise<LoginResponse> {
  return apiRequest<LoginResponse>("/api/v1/auth/login", {
    method: "POST",
    body: { username, password },
    credentials: "include",
  });
}

/**
 * 로그아웃합니다.
 *
 * <p>서버 세션을 무효화하므로, 실패해도 프론트에서는 로그아웃 처리합니다.
 * (네트워크 문제로 로그아웃이 막히면 사용자가 갇히게 됩니다)
 */
export async function logout(): Promise<void> {
  try {
    await apiRequest<{ message: string }>("/api/v1/auth/logout", {
      method: "POST",
      credentials: "include",
    });
  } catch {
    // 무시합니다. 프론트의 인증 상태는 어차피 초기화합니다.
  }
}

/**
 * 현재 로그인한 사용자를 조회합니다. (새로고침 후 상태 복원)
 *
 * @returns 사용자 정보, 로그인하지 않았으면 null
 */
export async function fetchCurrentUser(): Promise<AuthUser | null> {
  try {
    const body = await apiRequest<AuthUser & { authenticated: boolean }>("/api/v1/auth/me", {
      credentials: "include",
    });
    if (!body.authenticated) return null;
    return {
      username: body.username,
      display_name: body.display_name ?? null,
      role: body.role ?? null,
      last_login_at: body.last_login_at ?? null,
    };
  } catch {
    // 401 이면 정상적인 "로그인 안 됨" 상태입니다.
    return null;
  }
}

/**
 * 서버 오류 본문에서 사람이 읽을 메시지를 꺼냅니다.
 *
 * @param body ApiError 의 body
 * @param fallback 기본 메시지
 * @returns 표시할 메시지
 */
export function messageOf(body: unknown, fallback: string): string {
  if (body && typeof body === "object" && "message" in body) {
    const message = (body as ErrorBody).message;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }
  return fallback;
}

/**
 * 본인 비밀번호를 변경합니다.
 *
 * <p>현재 비밀번호를 함께 보내야 합니다. 서버가 그것을 확인하므로,
 * 세션만 탈취해서는 비밀번호를 바꿀 수 없습니다.
 *
 * @param currentPassword 현재 비밀번호
 * @param newPassword     새 비밀번호 (8자 이상)
 * @throws ApiError 실패 시 (401 현재 비밀번호 불일치 / 400 길이 미달)
 */
export function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<{ message: string }> {
  return apiRequest<{ message: string }>("/api/v1/users/me/password", {
    method: "POST",
    body: { currentPassword, newPassword },
  });
}

/**
 * 사용자 관리 API 입니다. (ADMIN 전용 기능 포함)
 */
export interface ManagedUser {
  username: string;
  display_name: string | null;
  role: string | null;
  enabled: boolean;
  last_login_at: string | null;
  created_at: string | null;
}

/** 전체 사용자 목록을 조회합니다. (ADMIN) */
export function listUsers(): Promise<{ total: number; users: ManagedUser[] }> {
  return apiRequest("/api/v1/users");
}

/** 사용자를 생성합니다. (ADMIN) */
export function createUser(input: {
  username: string;
  password: string;
  displayName?: string;
  role?: string;
}): Promise<ManagedUser> {
  return apiRequest("/api/v1/users", {
    method: "POST",
    body: {
      username: input.username,
      password: input.password,
      displayName: input.displayName,
      role: input.role,
    },
  });
}

/** 계정 활성 여부를 바꿉니다. (ADMIN) */
export function setUserEnabled(
  username: string,
  enabled: boolean,
): Promise<{ username: string; enabled: boolean }> {
  return apiRequest(`/api/v1/users/${encodeURIComponent(username)}/enabled`, {
    method: "PUT",
    params: { enabled },
  });
}
