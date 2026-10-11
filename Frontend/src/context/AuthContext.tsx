import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { fetchCurrentUser, login as loginApi, logout as logoutApi, type AuthUser } from "../lib/api/auth";

/**
 * 인증 상태를 앱 전체에 공급합니다.
 *
 * <h2>기존 쿠키 방식에서 바뀐 점</h2>
 * 이전에는 `SignInForm` 이 **아무 검증 없이** 쿠키에 이메일을 넣고
 * `App.tsx` 가 그 쿠키 존재 여부로 라우팅을 결정했습니다. 즉 누구나 쿠키를
 * 직접 만들면 통과할 수 있었습니다.
 *
 * <p>이제는 **서버 세션**이 진실의 출처이고, 프론트는 그것을 반영만 합니다.
 * 쿠키(`JSESSIONID`)는 서버가 발급하며 `HttpOnly` 이므로 JS 로 조작할 수
 * 없습니다.
 *
 * <h2>새로고침 시 상태 복원</h2>
 * 세션 쿠키는 남아 있지만 프론트 상태는 사라집니다. 그래서 마운트 시
 * `GET /api/v1/auth/me` 로 확인합니다. 이 호출이 없으면 새로고침할 때마다
 * 로그인 화면으로 튕깁니다.
 *
 * <h2>초기 로딩을 구분하는 이유</h2>
 * `loading` 을 두지 않으면, 확인이 끝나기 전에 `user === null` 이므로
 * 잠깐 로그인 화면이 보였다가 대시보드로 바뀝니다. (깜빡임)
 * 그래서 `initializing` 동안은 라우팅을 미룹니다.
 */

interface AuthContextValue {
  /** 현재 사용자 (미로그인 시 null). */
  user: AuthUser | null;
  /** 세션 확인이 끝났는지 여부. false 인 동안은 라우팅을 미룹니다. */
  initializing: boolean;
  /** 로그인 진행 중 여부. */
  submitting: boolean;
  /** 마지막 인증 오류 메시지. */
  error: string | null;
  /** 로그인합니다. 성공하면 true. */
  login: (username: string, password: string) => Promise<boolean>;
  /** 로그아웃합니다. */
  logout: () => Promise<void>;
  /** 오류 메시지를 지웁니다. */
  clearError: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [initializing, setInitializing] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 마운트 시 세션을 확인해 로그인 상태를 복원합니다.
  useEffect(() => {
    let cancelled = false;
    fetchCurrentUser()
      .then((current) => {
        if (!cancelled) setUser(current);
      })
      .finally(() => {
        if (!cancelled) setInitializing(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (username: string, password: string): Promise<boolean> => {
    setSubmitting(true);
    setError(null);
    try {
      const response = await loginApi(username, password);
      setUser({
        username: response.username,
        display_name: response.display_name ?? null,
        role: response.role ?? null,
      });
      return true;
    } catch (cause) {
      // 서버가 보낸 message 를 그대로 보여줍니다.
      // (자격증명 오류 / 잠김 / 비활성 계정을 구분해 안내)
      if (cause && typeof cause === "object" && "message" in cause) {
        const message = (cause as { message?: unknown }).message;
        setError(typeof message === "string" && message.trim()
          ? message
          : "로그인에 실패했습니다.");
      } else {
        setError("로그인에 실패했습니다.");
      }
      return false;
    } finally {
      setSubmitting(false);
    }
  }, []);

  const logout = useCallback(async () => {
    await logoutApi();
    setUser(null);
    setError(null);
  }, []);

  const clearError = useCallback(() => setError(null), []);

  return (
    <AuthContext.Provider
      value={{ user, initializing, submitting, error, login, logout, clearError }}
    >
      {children}
    </AuthContext.Provider>
  );
}

/**
 * 인증 컨텍스트를 사용합니다.
 *
 * @returns 인증 상태와 동작
 * @throws AuthProvider 밖에서 호출한 경우
 */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
