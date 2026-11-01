import { errorLog } from "../errors";
import { apiRequest } from "./client";


export interface AuthUser {
  username: string;
  display_name: string | null;
  role: string | null;
  last_login_at?: string | null;
}

export interface LoginResponse {
  authenticated: boolean;
  username: string;
  display_name?: string | null;
  role?: string | null;
}

interface ErrorBody {
  message?: string;
}

export function login(username: string, password: string): Promise<LoginResponse> {
  return apiRequest<LoginResponse>("/api/v1/auth/login", {
    method: "POST",
    body: { username, password },
    credentials: "include",
  });
}

export async function logout(): Promise<void> {
  try {
    await apiRequest<{ message: string }>("/api/v1/auth/logout", {
      method: "POST",
      credentials: "include",
    });
  } catch {
    // 프론트의 인증 상태는 초기화합니다.
  } finally {
    errorLog.clear();
  }
}

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

export function messageOf(body: unknown, fallback: string): string {
  if (body && typeof body === "object" && "message" in body) {
    const message = (body as ErrorBody).message;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }
  return fallback;
}

export function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<{ message: string }> {
  return apiRequest<{ message: string }>("/api/v1/users/me/password", {
    method: "POST",
    body: { currentPassword, newPassword },
  });
}

export interface ManagedUser {
  username: string;
  display_name: string | null;
  role: string | null;
  enabled: boolean;
  last_login_at: string | null;
  created_at: string | null;
}

export function listUsers(): Promise<{ total: number; users: ManagedUser[] }> {
  return apiRequest("/api/v1/users");
}

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

export function setUserEnabled(
  username: string,
  enabled: boolean,
): Promise<{ username: string; enabled: boolean }> {
  return apiRequest(`/api/v1/users/${encodeURIComponent(username)}/enabled`, {
    method: "PUT",
    params: { enabled },
  });
}
