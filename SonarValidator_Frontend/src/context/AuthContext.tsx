import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { fetchCurrentUser, login as loginApi, logout as logoutApi, type AuthUser } from "../lib/api/auth";


interface AuthContextValue {
  user: AuthUser | null;
  initializing: boolean;
  submitting: boolean;
  error: string | null;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => Promise<void>;
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

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
