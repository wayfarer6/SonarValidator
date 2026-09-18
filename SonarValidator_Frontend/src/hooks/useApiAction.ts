import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../lib/api/client";

/**
 * 저장/전송 같은 "쓰기" 작업의 상태를 관리하는 훅입니다.
 *
 * <h2>조회 훅과 분리한 이유</h2>
 * 쓰기 작업은 (1) 사용자가 명시적으로 실행하고, (2) 성공 후 조회 데이터를
 * 갱신해야 하며, (3) 진행 중 중복 클릭을 막아야 합니다. 조회와 요구사항이
 * 달라 같은 훅으로 묶으면 양쪽이 모두 어색해집니다.
 *
 * <h2>중복 실행 방지</h2>
 * {@code submitting} 이 true 인 동안은 다시 실행하지 않습니다. 버튼 연타로
 * 같은 정책이 여러 번 저장되는 것을 막습니다.
 *
 * <h2>⚠️ 최신 action 을 ref 로 잡는 이유 (실제로 겪은 버그)</h2>
 * <p>{@code run} 을 {@code useCallback([submitting])} 로 메모이즈하면, 콜백이
 * <b>처음 렌더의 action 을 계속 붙잡습니다</b>. 호출자는 보통
 * {@code () => save(subnets, rules)} 처럼 화면 상태를 캡처한 화살표 함수를
 * 넘기므로, 그 상태가 바뀌어도 오래된 값이 전송됩니다.
 *
 * <p>증상이 조용해서 위험합니다. 저장 자체는 성공(200)하고
 * {@code updated_at} 도 갱신되지만, <b>내용은 항상 최초 값</b> 입니다.
 * 편집기에서 서브넷을 추가해도 서버에는 빈 배열이 저장되어, 검증이
 * "위반 0건" 이라고 잘못 보고합니다. (E2E 검증에서 이 문제를 발견했습니다)
 *
 * <p>그래서 {@code actionRef} 에 매 렌더마다 최신 action 을 담고, {@code run}
 * 은 ref 를 통해 호출합니다. 이렇게 하면 메모이즈를 유지하면서도 항상
 * 최신 상태를 씁니다.
 *
 * @param action 실행할 작업
 * @returns 실행 함수/진행 여부/마지막 오류/성공 결과
 */
export function useApiAction<TArgs extends unknown[], TResult>(
  action: (...args: TArgs) => Promise<TResult>,
): {
  run: (...args: TArgs) => Promise<TResult | null>;
  submitting: boolean;
  error: string | null;
  /** 마지막 성공 결과 (성공 메시지 표시용) */
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
