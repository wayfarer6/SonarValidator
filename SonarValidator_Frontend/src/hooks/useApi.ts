import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api/client";

/**
 * API 호출의 로딩/오류/데이터 상태를 관리하는 훅입니다.
 *
 * <h2>왜 훅으로 묶는가</h2>
 * 화면마다 {@code useState(loading)}, {@code useState(error)} 를 반복하면
 * (1) 언마운트 후 setState 경고가 생기고, (2) 오류 처리를 빠뜨리기 쉽습니다.
 * 이 훅은 그 두 가지를 한 곳에서 처리합니다.
 *
 * <h2>언마운트 안전성</h2>
 * {@code mountedRef} 로 언마운트 여부를 확인한 뒤 상태를 갱신합니다.
 * 페이지를 빠르게 이동할 때 발생하는 경고를 막습니다.
 *
 * @param fetcher 데이터를 가져오는 함수 (의존성이 바뀌면 다시 호출됨)
 * @param deps    fetcher 를 다시 만들 기준이 되는 값들
 * @returns 데이터/로딩/오류/재시도 함수
 */
export function useApi<T>(
  fetcher: () => Promise<T>,
  deps: readonly unknown[] = [],
): {
  data: T | null;
  loading: boolean;
  error: string | null;
  /** 서버에 닿지 못한 오류인지 여부 (백엔드 미기동 안내용) */
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
