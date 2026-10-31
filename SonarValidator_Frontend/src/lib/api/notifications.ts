import { apiRequest } from "./client";
import type {
  ApiNotification,
  ApiNotificationList,
  ApiNotificationSummary,
  NotificationCategory,
  NotificationSeverity,
} from "./types";


export interface NotificationQuery {
  category?: NotificationCategory | null;
  severity?: NotificationSeverity | null;
  unreadOnly?: boolean | null;
  projectId?: string | null;
  agentId?: string | null;
  search?: string | null;
  limit?: number;
}

export function listNotifications(query: NotificationQuery = {}): Promise<ApiNotificationList> {
  return apiRequest<ApiNotificationList>("/api/v1/notifications", {
    params: {
      category: query.category ?? undefined,
      severity: query.severity ?? undefined,
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

export function getNotificationSummary(): Promise<ApiNotificationSummary> {
  return apiRequest<ApiNotificationSummary>("/api/v1/notifications/summary");
}

export function listUnreadNotifications(limit = 5): Promise<ApiNotificationList> {
  return apiRequest<ApiNotificationList>("/api/v1/notifications/unread", {
    params: { limit },
  });
}

export function markNotificationRead(
  notificationId: string,
): Promise<{ notification: ApiNotification; unread: number }> {
  return apiRequest(`/api/v1/notifications/${encodeURIComponent(notificationId)}/read`, {
    method: "PATCH",
  });
}

export function markNotificationUnread(
  notificationId: string,
): Promise<{ notification: ApiNotification; unread: number }> {
  return apiRequest(`/api/v1/notifications/${encodeURIComponent(notificationId)}/unread`, {
    method: "PATCH",
  });
}

export function markAllNotificationsRead(): Promise<{ changed: number; unread: number }> {
  return apiRequest("/api/v1/notifications/read-all", { method: "POST" });
}

export function deleteNotification(
  notificationId: string,
): Promise<{ deleted: boolean; id: string; unread: number }> {
  return apiRequest(`/api/v1/notifications/${encodeURIComponent(notificationId)}`, {
    method: "DELETE",
  });
}
