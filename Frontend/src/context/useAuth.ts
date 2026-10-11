import { createContext, useContext } from "react";
import type { AuthUser } from "../lib/api/auth";

/**
 * 인증 컨텍스트의 값과 훅입니다.
 *
 * <h2>⚠️ 왜 Provider 파일과 분리했는가</h2>
 * Fast Refresh 는 한 파일이 <b>컴포넌트만</b> export 할 때 동작합니다.
 * {@code AuthProvider} 와 {@code useAuth} 를 같은 파일에 두면 훅 export 때문에
 * 핫 리로드가 전체 새로고침으로 떨어집니다.
 */

export interface AuthContextValue {
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

export const AuthContext = createContext<AuthContextValue | null>(null);

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
