// 로그인 세션의 유저 정보를 한 곳에서 관리하는 헬퍼 모듈.
// - 이메일: 로그인 시 react-cookie의 "username" 쿠키에 저장된 값 사용
// - 표시 이름: 백엔드 연동 전까지 더미 값 사용
// TODO: 백엔드 API 연동 후 쿠키 대신 실제 사용자 정보 조회로 교체
import { useEffect, useState } from "react";
import { useCookies } from "react-cookie";

export const DEFAULT_DISPLAY_NAME = "Daniel Seo";
export const FALLBACK_EMAIL = "shseo2023@gmail.com";

/** 쿠키에 저장된 로그인 이메일 (없으면 폴백 이메일) */
export function useUserEmail(): string {
  const [cookies] = useCookies(["username"]);
  const raw = typeof cookies.username === "string" ? cookies.username.trim() : "";
  return raw || FALLBACK_EMAIL;
}

/** 표시용 이름 (더미). 이메일 로컬 파트를 기본값으로 쓰는 경우도 대비해 함께 제공 */
export function useDisplayName(): string {
  return DEFAULT_DISPLAY_NAME;
}

/** 표시용 이름을 First / Last 로 분리 */
export function splitDisplayName(name: string): { first: string; last: string } {
  const parts = name.trim().split(/\s+/);
  return { first: parts[0] ?? "", last: parts.slice(1).join(" ") };
}

/** 이름/이메일 변경 시 다른 컴포넌트도 갱신되도록 하는 간단한 이벤트 버스 */
const USER_INFO_EVENT = "sonar:user-info-changed";

export function notifyUserInfoChanged() {
  window.dispatchEvent(new Event(USER_INFO_EVENT));
}

export function useUserInfoVersion(): number {
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const handler = () => setVersion((v) => v + 1);
    window.addEventListener(USER_INFO_EVENT, handler);
    return () => window.removeEventListener(USER_INFO_EVENT, handler);
  }, []);
  return version;
}
