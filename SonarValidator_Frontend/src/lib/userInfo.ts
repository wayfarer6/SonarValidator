// 로그인 세션의 사용자 정보를 한 곳에서 관리하는 헬퍼 모듈.
//
// 이전에는 react-cookie 의 "username" 쿠키를 읽었습니다. 그 쿠키는 프론트가
// 임의로 만들 수 있었으므로 신뢰할 수 없었습니다. 이제 서버 세션이 진실의
// 출처이고, AuthContext 가 그 값을 들고 있습니다.
import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";

/** 표시 이름을 찾지 못했을 때의 폴백. */
export const DEFAULT_DISPLAY_NAME = "User";

/** 이메일을 찾지 못했을 때의 폴백. */
export const FALLBACK_EMAIL = "-";

/**
 * 현재 로그인한 사용자의 아이디를 돌려줍니다.
 *
 * @returns 사용자 아이디 (미로그인 시 폴백)
 */
export function useUserEmail(): string {
  const { user } = useAuth();
  return user?.username?.trim() || FALLBACK_EMAIL;
}

/**
 * 표시용 이름을 돌려줍니다.
 *
 * <p>서버가 준 `display_name` 을 우선 쓰고, 없으면 아이디를 씁니다.
 * 더미 값을 넣지 않는 이유: 잘못된 이름이 표시되면 "다른 사람으로
 * 로그인했나?" 라는 혼란을 만듭니다.
 *
 * @returns 표시 이름
 */
export function useDisplayName(): string {
  const { user } = useAuth();
  return user?.display_name?.trim() || user?.username?.trim() || DEFAULT_DISPLAY_NAME;
}

/**
 * 현재 사용자의 권한을 돌려줍니다.
 *
 * @returns 권한 문자열 (미로그인 시 null)
 */
export function useUserRole(): string | null {
  const { user } = useAuth();
  return user?.role ?? null;
}

/** 표시용 이름을 First / Last 로 분리합니다. */
export function splitDisplayName(name: string): { first: string; last: string } {
  const parts = name.trim().split(/\s+/);
  return { first: parts[0] ?? "", last: parts.slice(1).join(" ") };
}

/** 이름/이메일 변경 시 다른 컴포넌트도 갱신되도록 하는 간단한 이벤트 버스 */
const USER_INFO_EVENT = "sonar:user-info-changed";

/** 사용자 정보 변경을 알립니다. */
export function notifyUserInfoChanged() {
  window.dispatchEvent(new Event(USER_INFO_EVENT));
}

/**
 * 사용자 정보 변경 알림을 구독합니다.
 *
 * <p>서버에서 프로필을 바꾼 뒤 화면을 갱신할 때 씁니다.
 *
 * @returns 변경 횟수 (의존성 배열에 넣어 재렌더를 유도)
 */
export function useUserInfoVersion(): number {
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const handler = () => setVersion((v) => v + 1);
    window.addEventListener(USER_INFO_EVENT, handler);
    return () => window.removeEventListener(USER_INFO_EVENT, handler);
  }, []);
  return version;
}
