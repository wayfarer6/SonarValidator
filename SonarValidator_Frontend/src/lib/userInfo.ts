import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";

export const DEFAULT_DISPLAY_NAME = "User";

export const FALLBACK_EMAIL = "-";

export function useUserEmail(): string {
  const { user } = useAuth();
  return user?.username?.trim() || FALLBACK_EMAIL;
}

export function useDisplayName(): string {
  const { user } = useAuth();
  return user?.display_name?.trim() || user?.username?.trim() || DEFAULT_DISPLAY_NAME;
}

export function useUserRole(): string | null {
  const { user } = useAuth();
  return user?.role ?? null;
}

export function splitDisplayName(name: string): { first: string; last: string } {
  const parts = name.trim().split(/\s+/);
  return { first: parts[0] ?? "", last: parts.slice(1).join(" ") };
}

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
