import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api/client";

export function useApi<T>(
  fetcher: () => Promise<T>,
  deps: readonly unknown[] = [],
  refreshMs = 0,
): {
  data: T | null;
  loading: boolean;
  error: string | null;
  offline: boolean;
  updatedAt: string | null;
  reload: () => void;
} {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const previousScope = useRef<readonly unknown[] | null>(null);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const scopeChanged = previousScope.current === null || previousScope.current.length !== deps.length
      || deps.some((value, index) => !Object.is(value, previousScope.current![index]));
    previousScope.current = [...deps];
    if (scopeChanged) {
      setData(null);
      setUpdatedAt(null);
      setLoading(true);
    } else if (data === null) {
      setLoading(true);
    }
    setError(null);
    setOffline(false);

    async function load() {
      try {
        const result = await fetcherRef.current();
        if (cancelled) return;
        setData(result);
        setUpdatedAt(new Date().toISOString());
        setError(null);
        setOffline(false);
      } catch (cause: unknown) {
        if (cancelled) return;
        setError(cause instanceof Error ? cause.message : "알 수 없는 오류가 발생했습니다.");
        setOffline(cause instanceof ApiError && cause.isNetworkError);
      } finally {
        if (!cancelled) {
          setLoading(false);
          // 느린 요청이 끝난 뒤 예약하므로 중복 요청과 응답 순서 역전을 막습니다.
          if (refreshMs > 0) timer = setTimeout(() => void load(), refreshMs);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
      if (timer !== undefined) clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadToken, refreshMs]);

  const reload = useCallback(() => setReloadToken((token) => token + 1), []);
  return { data, loading, error, offline, updatedAt, reload };
}
