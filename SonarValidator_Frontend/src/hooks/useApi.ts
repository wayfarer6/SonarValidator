import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api/client";

export function useApi<T>(
  fetcher: () => Promise<T>,
  deps: readonly unknown[] = [],
): {
  data: T | null;
  loading: boolean;
  error: string | null;
  offline: boolean;
  reload: () => void;
} {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  const mountedRef = useRef(true);
  // 최신 fetcher 를 참조해 deps 에 함수 자체를 넣지 않아도 되게 합니다.
  // (인라인 화살표 함수를 deps 에 넣으면 매 렌더마다 재호출됩니다)
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setOffline(false);

    fetcherRef
      .current()
      .then((result) => {
        if (cancelled || !mountedRef.current) return;
        setData(result);
      })
      .catch((cause: unknown) => {
        if (cancelled || !mountedRef.current) return;
        if (cause instanceof ApiError) {
          setError(cause.message);
          setOffline(cause.isNetworkError);
        } else {
          setError(cause instanceof Error ? cause.message : "알 수 없는 오류가 발생했습니다.");
        }
      })
      .finally(() => {
        if (cancelled || !mountedRef.current) return;
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
    // deps 는 호출자가 결정합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadToken]);

  const reload = useCallback(() => setReloadToken((token) => token + 1), []);

  return { data, loading, error, offline, reload };
}
