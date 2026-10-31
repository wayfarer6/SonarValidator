import { useEffect, useRef } from "react";

export function usePolling(
  callback: () => void,
  intervalMs: number = DEFAULT_POLL_INTERVAL_MS,
  enabled: boolean = true,
): void {
  // 매 렌더마다 최신 콜백으로 교체합니다. (인터벌은 이 ref 만 봅니다)
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  useEffect(() => {
    if (!enabled) {
      return;
    }

    // 탭이 보일 때만 인터벌을 유지합니다.
    let timer: ReturnType<typeof setInterval> | null = null;

    const start = () => {
      if (timer !== null) return;
      timer = setInterval(() => callbackRef.current(), intervalMs);
    };

    const stop = () => {
      if (timer === null) return;
      clearInterval(timer);
      timer = null;
    };

    const handleVisibility = () => {
      if (document.hidden) {
        stop();
        return;
      }
      // 돌아오는 즉시 한 번 갱신해야 오래된 값을 보지 않습니다.
      callbackRef.current();
      start();
    };

    if (!document.hidden) {
      start();
    }
    document.addEventListener("visibilitychange", handleVisibility);

    return () => {
      stop();
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, [intervalMs, enabled]);
}

export const DEFAULT_POLL_INTERVAL_MS = 15_000;
