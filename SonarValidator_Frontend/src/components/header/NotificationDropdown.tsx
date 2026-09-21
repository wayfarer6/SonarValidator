import { useState } from "react";
import { Dropdown } from "../ui/dropdown/Dropdown";
import { useNavigate } from "react-router";
import { useApi } from "../../hooks/useApi";
import Badge from "../ui/badge/Badge";
import {
  listUnreadNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "../../lib/api/notifications";
import type { ApiNotification } from "../../lib/api/types";

/**
 * 헤더의 알림 드롭다운입니다.
 *
 * <h2>⚠️ 더미 데이터를 제거한 이유</h2>
 * 이 컴포넌트는 "Terry Franci 가 권한을 요청함" 같은 TailAdmin 템플릿의
 * 하드코딩된 항목을 보여주고 있었습니다. 실제 사용자/권한 개념이 없는
 * 프로젝트라 <b>사실과 무관한 내용</b>이 표시되고, 그 아래 "5분 전" 도
 * 항상 같았습니다. 이제 서버에 적재된 알림을 읽어옵니다.
 *
 * <h2>안읽음만 보여주는 이유</h2>
 * 드롭다운은 "새로 온 것" 을 알리는 자리입니다. 읽은 것까지 섞으면 최근
 * 5건이 모두 읽은 항목으로 채워져 정작 봐야 할 알림이 밀려납니다.
 * 전체 목록은 "View All Notifications" 로 가는 <b>별도 화면</b>이 담당합니다.
 *
 * <h2>⚠️ 읽음 처리 성공 후에만 새로고침하는 이유</h2>
 * 실패했는데 화면만 지우면, 다시 열었을 때 알림이 되돌아와 "왜 안 사라지지"
 * 가 됩니다. 서버 요청이 성공한 뒤 목록을 다시 읽습니다.
 */

/**
 * ISO-8601 시각을 상대 시간으로 바꿉니다.
 *
 * @param iso ISO-8601 문자열
 * @returns 상대 시간 (파싱 실패 시 원문)
 */
function relativeTime(iso: string): string {
  const then = Date.parse(iso);
  if (Number.isNaN(then)) return iso;

  const seconds = Math.floor((Date.now() - then) / 1000);
  if (seconds < 60) return "방금";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}일 전`;
  return new Date(then).toLocaleDateString();
}

export default function NotificationDropdown() {
  const [isOpen, setIsOpen] = useState(false);
  const navigate = useNavigate();

  // 드롭다운은 열려 있을 때만 최신 목록이 필요합니다. 다만 배지(안읽음 표시)는
  // 항상 보여야 하므로 처음부터 한 번 조회하고, 열 때마다 갱신합니다.
  const unread = useApi(() => listUnreadNotifications(5), []);
  const notifications = unread.data?.notifications ?? [];
  const unreadCount = unread.data?.unread ?? 0;

  const [busyId, setBusyId] = useState<string | null>(null);

  const handleViewAll = () => {
    closeDropdown();
    navigate("/notification");
  };

  function toggleDropdown() {
    const next = !isOpen;
    setIsOpen(next);
    // 열 때마다 갱신합니다. 다른 화면에서 알림이 쌓였을 수 있습니다.
    if (next) unread.reload();
  }

  function closeDropdown() {
    setIsOpen(false);
  }

  /** 알림을 눌러 읽음 처리하고, 상세 화면이 있으면 이동합니다. */
  const handleOpen = async (notification: ApiNotification) => {
    setBusyId(notification.id);
    try {
      await markNotificationRead(notification.id);
    } catch {
      // 읽음 표시가 실패해도 이동은 막지 않습니다.
    } finally {
      setBusyId(null);
    }
    closeDropdown();
    if (notification.link) {
      navigate(notification.link);
    } else {
      unread.reload();
    }
  };

  /** 모두 읽음 처리합니다. */
  const handleMarkAllRead = async () => {
    try {
      await markAllNotificationsRead();
      unread.reload();
    } catch {
      // 실패 시 목록을 그대로 둡니다. (거짓으로 사라지면 안 됩니다)
    }
  };

  return (
    <div className="relative">
      <button
        className="relative flex items-center justify-center text-gray-500 transition-colors bg-white border border-gray-200 rounded-full dropdown-toggle hover:text-gray-700 h-11 w-11 hover:bg-gray-100 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-white"
        onClick={toggleDropdown}
      >
        {/* 안읽은 알림이 있을 때만 점을 표시합니다. 하드코딩된 상태가 아닙니다. */}
        {unreadCount > 0 && (
          <span className="absolute right-0 top-0.5 z-10 h-2 w-2 rounded-full bg-orange-400 flex">
            <span className="absolute inline-flex w-full h-full bg-orange-400 rounded-full opacity-75 animate-ping"></span>
          </span>
        )}
        <svg
          className="fill-current"
          width="20"
          height="20"
          viewBox="0 0 20 20"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            fillRule="evenodd"
            clipRule="evenodd"
            d="M10.75 2.29248C10.75 1.87827 10.4143 1.54248 10 1.54248C9.58583 1.54248 9.25004 1.87827 9.25004 2.29248V2.83613C6.08266 3.20733 3.62504 5.9004 3.62504 9.16748V14.4591H3.33337C2.91916 14.4591 2.58337 14.7949 2.58337 15.2091C2.58337 15.6234 2.91916 15.9591 3.33337 15.9591H4.37504H15.625H16.6667C17.0809 15.9591 17.4167 15.6234 17.4167 15.2091C17.4167 14.7949 17.0809 14.4591 16.6667 14.4591H16.375V9.16748C16.375 5.9004 13.9174 3.20733 10.75 2.83613V2.29248ZM14.875 14.4591V9.16748C14.875 6.47509 12.6924 4.29248 10 4.29248C7.30765 4.29248 5.12504 6.47509 5.12504 9.16748V14.4591H14.875ZM8.00004 17.7085C8.00004 18.1228 8.33583 18.4585 8.75004 18.4585H11.25C11.6643 18.4585 12 18.1228 12 17.7085C12 17.2943 11.6643 16.9585 11.25 16.9585H8.75004C8.33583 16.9585 8.00004 17.2943 8.00004 17.7085Z"
            fill="currentColor"
          />
        </svg>
        {/* 안읽음 개수를 숫자로도 보여줍니다. 점만으로는 몇 건인지 알 수 없습니다. */}
        {unreadCount > 0 && (
          <span className="absolute -right-1 -top-1 z-20 flex h-4 min-w-4 items-center justify-center rounded-full bg-error-500 px-1 text-[10px] font-semibold text-white">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>
      <Dropdown
        isOpen={isOpen}
        onClose={closeDropdown}
        className="absolute -right-[240px] mt-[17px] flex h-[480px] w-[350px] flex-col rounded-2xl border border-gray-200 bg-white p-3 shadow-theme-lg dark:border-gray-800 dark:bg-gray-dark sm:w-[361px] lg:right-0"
      >
        <div className="flex items-center justify-between pb-3 mb-3 border-b border-gray-100 dark:border-gray-700">
          <div className="flex items-center gap-2">
            <h5 className="text-lg font-semibold text-gray-800 dark:text-gray-200">
              Notification
            </h5>
            {unreadCount > 0 && (
              <Badge size="sm" color="error">
                {unreadCount}
              </Badge>
            )}
          </div>
          <div className="flex items-center gap-2">
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={handleMarkAllRead}
                className="text-xs text-gray-500 transition hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              >
                모두 읽음
              </button>
            )}
            <button
              onClick={closeDropdown}
              className="text-gray-500 transition dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
            >
              <svg
                className="fill-current"
                width="24"
                height="24"
                viewBox="0 0 24 24"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  fillRule="evenodd"
                  clipRule="evenodd"
                  d="M6.21967 7.28131C5.92678 6.98841 5.92678 6.51354 6.21967 6.22065C6.51256 5.92775 6.98744 5.92775 7.28033 6.22065L11.999 10.9393L16.7176 6.22078C17.0105 5.92789 17.4854 5.92788 17.7782 6.22078C18.0711 6.51367 18.0711 6.98855 17.7782 7.28144L13.0597 12L17.7782 16.7186C18.0711 17.0115 18.0711 17.4863 17.7782 17.7792C17.4854 18.0721 17.0105 18.0721 16.7176 17.7792L11.999 13.0607L7.28033 17.7794C6.98744 18.0722 6.51256 18.0722 6.21967 17.7794C5.92678 17.4865 5.92678 17.0116 6.21967 16.7187L10.9384 12L6.21967 7.28131Z"
                  fill="currentColor"
                />
              </svg>
            </button>
          </div>
        </div>

        <ul className="flex flex-col h-auto overflow-y-auto custom-scrollbar">
          {unread.loading && (
            <li className="p-4 text-center text-xs text-gray-400">
              알림을 불러오는 중...
            </li>
          )}

          {!unread.loading && unread.error && (
            <li className="p-4 text-center text-xs text-error-500">
              {unread.offline
                ? "백엔드에 연결할 수 없습니다"
                : "알림을 불러오지 못했습니다"}
            </li>
          )}

          {!unread.loading && !unread.error && notifications.length === 0 && (
            <li className="p-6 text-center text-xs text-gray-400">
              안읽은 알림이 없습니다
            </li>
          )}

          {notifications.map((notification) => (
            <li key={notification.id}>
              <button
                type="button"
                onClick={() => handleOpen(notification)}
                disabled={busyId === notification.id}
                className="flex w-full gap-3 rounded-lg border-b border-gray-100 p-3 px-4.5 py-3 text-left transition hover:bg-gray-100 disabled:opacity-50 dark:border-gray-800 dark:hover:bg-white/5"
              >
                <span className="mt-1.5 flex shrink-0">
                  <span
                    className={`h-2.5 w-2.5 rounded-full ${
                      notification.severity === "critical"
                        ? "bg-error-500"
                        : notification.severity === "warning"
                          ? "bg-warning-500"
                          : "bg-blue-light-500"
                    }`}
                  />
                </span>

                <span className="block min-w-0">
                  <span className="mb-1.5 block text-theme-sm text-gray-500 dark:text-gray-400">
                    <span className="font-medium text-gray-800 dark:text-white/90">
                      {notification.title}
                    </span>
                    {/* 반복 횟수는 진행 중인 문제라는 신호이므로 함께 보여줍니다. */}
                    {notification.repeat_count > 1 && (
                      <span className="ml-1 text-warning-600 dark:text-orange-400">
                        ({notification.repeat_count}회)
                      </span>
                    )}
                  </span>
                  {notification.message && (
                    <span className="mb-1.5 line-clamp-2 block text-theme-xs text-gray-500 dark:text-gray-400">
                      {notification.message}
                    </span>
                  )}
                  <span className="flex items-center gap-2 text-gray-500 text-theme-xs dark:text-gray-400">
                    <span
                      className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${
                        notification.severity === "critical"
                          ? "bg-error-50 text-error-600 dark:bg-error-500/15 dark:text-error-400"
                          : notification.severity === "warning"
                            ? "bg-warning-50 text-warning-600 dark:bg-warning-500/15 dark:text-orange-400"
                            : "bg-blue-light-50 text-blue-light-600 dark:bg-blue-light-500/15"
                      }`}
                    >
                      {notification.category}
                    </span>
                    <span className="w-1 h-1 bg-gray-400 rounded-full"></span>
                    <span title={notification.occurred_at}>
                      {relativeTime(notification.occurred_at)}
                    </span>
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>

        <button
          onClick={handleViewAll}
          className="block w-full px-4 py-2 mt-3 text-sm font-medium text-center text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-100 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700"
        >
          View All Notifications
        </button>
      </Dropdown>
    </div>
  );
}
