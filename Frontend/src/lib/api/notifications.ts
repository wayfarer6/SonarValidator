import { apiRequest } from "./client";
import type {
  ApiNotification,
  ApiNotificationList,
  ApiNotificationSummary,
  NotificationCategory,
  NotificationSeverity,
} from "./types";

/**
 * 알림 조회/읽음 처리 API 모듈입니다.
 *
 * <p>화면은 이 모듈만 알면 되고, 경로 문자열은 여기에만 있습니다.
 * 서버 경로가 바뀌면 이 파일만 고치면 됩니다.
 *
 * <h2>읽음 처리를 "실패해도 조용히" 하지 않는 이유</h2>
 * 읽음 표시는 사용자가 <b>직접 누른 결과</b>입니다. 요청이 실패했는데
 * 화면만 읽음으로 바뀌면, 새로 고침 후 알림이 되돌아와 "왜 안 읽음이지"
 * 가 됩니다. 그래서 호출측이 실패를 알 수 있도록 예외를 그대로 던집니다.
 * (반대로 서버 내부의 알림 <b>기록</b>은 실패해도 본 작업을 막지 않습니다)
 */

/** 알림 조회 필터입니다. */
export interface NotificationQuery {
  /** 분류 필터. 생략하면 전체. */
  category?: NotificationCategory | null;
  /** 심각도 필터. 생략하면 전체. */
  severity?: NotificationSeverity | null;
  /**
   * 읽음 상태 필터입니다.
   *
   * <p>`true` 면 안읽음만, `false` 면 읽음만, 생략하면 전체입니다.
   *
   * <p>⚠️ `true` 가 "읽음" 이 아니라 <b>"안읽음만"</b> 입니다. 이름 그대로
   * "안 읽은 것만" 이라는 뜻입니다. 두 값이 모두 boolean 이라 뒤집혀도
   * 컴파일러가 잡아주지 않으므로 실제로 이 자리에서 버그가 있었습니다.
   * 요청으로 나갈 때는 서버의 3상태 문자열(`read_state`)로 변환합니다.
   */
  unreadOnly?: boolean | null;
  /** 프로젝트 키 필터. */
  projectId?: string | null;
  /** 장치 식별자 필터. */
  agentId?: string | null;
  /** 제목/본문 검색어. */
  search?: string | null;
  /** 최대 건수 (기본 100). */
  limit?: number;
}

/**
 * 필터 조합으로 알림을 조회합니다.
 *
 * @param query 조회 필터
 * @returns 알림 목록과 안읽음 건수
 */
export function listNotifications(query: NotificationQuery = {}): Promise<ApiNotificationList> {
  return apiRequest<ApiNotificationList>("/api/v1/notifications", {
    params: {
      category: query.category ?? undefined,
      severity: query.severity ?? undefined,
      // ⚠️ unread_only 를 그대로 보내지 않습니다. 서버는 이 값을
      // "읽음 여부" 의 3상태(read_state)로 해석하므로, boolean 을 그대로
      // 넘기면 의미가 뒤집힙니다. (true 가 "읽음만" 이 되어 버립니다)
      //
      // 여기서 문자열로 변환해 의도를 분명히 합니다.
      read_state:
        query.unreadOnly === undefined || query.unreadOnly === null
          ? undefined
          : query.unreadOnly
            ? "unread"
            : "read",
      project_id: query.projectId ?? undefined,
      agent_id: query.agentId ?? undefined,
      search: query.search ?? undefined,
      limit: query.limit,
    },
  });
}

/**
 * 알림 요약(안읽음 개수 + 분류/심각도별 건수)을 조회합니다.
 *
 * <p>배지와 필터 탭 숫자를 한 번에 받아옵니다. 탭마다 요청을 보내면 화면을
 * 열 때 요청이 5~6개가 됩니다.
 */
export function getNotificationSummary(): Promise<ApiNotificationSummary> {
  return apiRequest<ApiNotificationSummary>("/api/v1/notifications/summary");
}

/**
 * 안읽음 알림만 조회합니다. (헤더 드롭다운 미리보기)
 *
 * @param limit 최대 건수 (기본 5)
 */
export function listUnreadNotifications(limit = 5): Promise<ApiNotificationList> {
  return apiRequest<ApiNotificationList>("/api/v1/notifications/unread", {
    params: { limit },
  });
}

/**
 * 알림 한 건을 읽음으로 표시합니다.
 *
 * @param notificationId 외부 식별자
 * @returns 변경된 알림과 남은 안읽음 건수
 */
export function markNotificationRead(
  notificationId: string,
): Promise<{ notification: ApiNotification; unread: number }> {
  return apiRequest(`/api/v1/notifications/${encodeURIComponent(notificationId)}/read`, {
    method: "PATCH",
  });
}

/**
 * 알림 한 건을 안읽음으로 되돌립니다.
 *
 * <p>"실수로 읽음 처리했다" 를 되돌릴 수 있어야 합니다. 되돌릴 수 없으면
 * 운영자는 중요한 알림을 놓칠까 봐 읽음 처리를 미루게 됩니다.
 *
 * @param notificationId 외부 식별자
 */
export function markNotificationUnread(
  notificationId: string,
): Promise<{ notification: ApiNotification; unread: number }> {
  return apiRequest(`/api/v1/notifications/${encodeURIComponent(notificationId)}/unread`, {
    method: "PATCH",
  });
}

/**
 * 안읽음 알림을 모두 읽음으로 표시합니다.
 *
 * @returns 변경된 건수와 남은 안읽음 건수
 */
export function markAllNotificationsRead(): Promise<{ changed: number; unread: number }> {
  return apiRequest("/api/v1/notifications/read-all", { method: "POST" });
}

/**
 * 알림 한 건을 삭제합니다.
 *
 * @param notificationId 외부 식별자
 */
export function deleteNotification(
  notificationId: string,
): Promise<{ deleted: boolean; id: string; unread: number }> {
  return apiRequest(`/api/v1/notifications/${encodeURIComponent(notificationId)}`, {
    method: "DELETE",
  });
}
