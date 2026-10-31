import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api/client";

export function useApiAction<TArgs extends unknown[], TResult>(
  action: (...args: TArgs) => Promise<TResult>,
): {
  run: (...args: TArgs) => Promise<TResult | null>;
  submitting: boolean;
  error: string | null;
  result: TResult | null;
  reset: () => void;
} {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<TResult | null>(null);

  const mountedRef = useRef(true);
  // 매 렌더마다 최신 action 을 담아, 오래된 클로저 문제를 막습니다.
  const actionRef = useRef(action);
  actionRef.current = action;

  // 중복 실행 판정도 ref 로 봅니다. state 를 의존성으로 걸면 run 이 다시
  // 만들어지면서 호출자의 useCallback 체인이 함께 흔들립니다.
  const submittingRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const run = useCallback(async (...args: TArgs): Promise<TResult | null> => {
    if (submittingRef.current) return null;
    submittingRef.current = true;
    setSubmitting(true);
    setError(null);
    try {
      const value = await actionRef.current(...args);
      if (mountedRef.current) setResult(value);
      return value;
    } catch (cause) {
      if (mountedRef.current) {
        setError(
          cause instanceof ApiError
            ? cause.message
            : cause instanceof Error
              ? cause.message
              : "요청에 실패했습니다.",
        );
      }
      return null;
    } finally {
      submittingRef.current = false;
      if (mountedRef.current) setSubmitting(false);
    }
  }, []);

  const reset = useCallback(() => {
    setError(null);
    setResult(null);
  }, []);

  return { run, submitting, error, result, reset };
}
