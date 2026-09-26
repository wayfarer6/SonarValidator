import { useEffect, useRef } from "react";

/**
 * 주기적으로 콜백을 호출합니다. (경고 전파용 폴링)
 *
 * <h2>왜 폴링인가 (WebSocket/SSE 가 아니라)</h2>
 * 백엔드는 이미 Agent 와 순수 WebSocket 으로 통신합니다. 하지만 그 채널은
 * <b>Agent ↔ 서버</b> 전용이고, 브라우저는 그 소켓에 붙을 수 없습니다.
 * 브라우저까지 실시간으로 밀어 넣으려면 STOMP 나 SSE 를 새로 열어야 합니다.
 *
 * <p>그런데 이 화면이 필요로 하는 신선도는 <b>10~15초</b> 입니다. 위반 알림은
 * 사람이 읽고 판단하는 정보라 1초 차이가 의미가 없습니다. 반면 실시간 채널을
 * 새로 만드는 비용(프록시 설정, 재연결 로직, 인증 전파, 배포 설정)은 큽니다.
 * 그 비용을 감당할 만한 이득이 없으므로 <b>가벼운 쪽을 고릅니다.</b>
 *
 * <h2>⚠️ 탭이 숨겨지면 멈춥니다</h2>
 * <p>운영자가 다른 탭을 보고 있는데도 서버를 계속 두드리면, 아무도 안 보는
 * 데이터를 위해 DB 와 네트워크를 씁니다. 다시 탭이 보이면 <b>즉시 한 번</b>
 * 호출하고 주기를 새로 시작합니다 — 돌아왔을 때 오래된 값을 보지 않도록.
 *
 * <h2>⚠️ reload 의 정체성에 의존하지 않습니다</h2>
 * <p>{@code reload} 는 보통 {@code useCallback} 으로 안정적이지만, 화면이
 * 인라인 함수를 넘기면 매 렌더마다 새 함수가 됩니다. 그러면 인터벌이
 * 계속 해체/재생성되어 <b>영원히 발화하지 않을</b> 수 있습니다. 그래서
 * 최신 함수를 ref 에 담아 참조하고, 인터벌은 마운트 시 한 번만 만듭니다.
 *
 * @param callback 매 주기마다 호출할 함수 (보통 {@code useApi().reload})
 * @param intervalMs 주기(ms). 기본 {@link DEFAULT_POLL_INTERVAL_MS}
 * @param enabled 폴링 사용 여부. 기본 true
 */
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

/**
 * 기본 폴링 주기(ms)입니다.
 *
 * <p>15초로 정한 이유: 위반 감지는 사람이 읽고 조치하는 흐름이라 분 단위면
 * 충분하고, 10초 미만은 서버 부하 대비 얻는 것이 없습니다.
 */
export const DEFAULT_POLL_INTERVAL_MS = 15_000;