import { useCallback, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Button from "../components/ui/button/Button";
import { useApi } from "../hooks/useApi";
// 프로젝트 목록은 projects 모듈에 있습니다. (index.ts 는 조회 API 만 모아 둔 곳)
import { listProjects } from "../lib/api/projects";
import {
  deleteNotification,
  getNotificationSummary,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  markNotificationUnread,
  type NotificationQuery,
} from "../lib/api/notifications";
import type {
  ApiNotification,
  NotificationCategory,
  NotificationSeverity,
} from "../lib/api/types";

/**
 * 알림 이력 화면입니다.
 *
 * <h2>왜 화면이 필요한가</h2>
 * 헤더의 종 아이콘(드롭다운)은 <b>최근 5건</b>만 보여줍니다. 운영자가 자리를
 * 비운 사이에 발생한 정책 푸시 거부·Agent 연결·프로젝트 삭제는 거기서
 * 밀려나면 확인할 방법이 없었습니다. 이 화면은 서버에 적재된 알림을
 * <b>필터·검색으로 되짚어 보는</b> 용도입니다.
 *
 * <pre>
 *   ┌─ 필터 바 ────────────────────────────────────────┐
 *   │ 분류 탭 · 심각도 · 프로젝트 · 읽음 상태 · 검색어      │
 *   ├─ 요약 ──────────────────────────────────────────┤
 *   │ 전체 · 안읽음 · Critical/Warning 건수               │
 *   ├─ 목록 ──────────────────────────────────────────┤
 *   │ [읽음] 심각도 | 제목 + 본문 | 분류 | 시각 | 이동/삭제  │
 *   └─────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>⚠️ 반복 횟수(repeat_count)를 눈에 띄게 표시하는 이유</h2>
 * 서버는 5분 안의 동일 알림을 하나로 합치면서 횟수를 셉니다. 이 숫자가
 * 1보다 크면 "한 번 있었던 일" 이 아니라 <b>진행 중인 문제</b>입니다.
 * 합쳐 놓고 횟수를 숨기면 사용자가 우선순위를 잘못 판단합니다.
 *
 * <h2>⚠️ 읽음 처리 실패를 삼키지 않는 이유</h2>
 * 사용자가 직접 누른 결과이므로 실패하면 알려야 합니다. 화면만 읽음으로
 * 바꾸면 새로 고침 후 되돌아와 "왜 안 읽음이지" 가 됩니다. 그래서 요청이
 * 성공한 뒤에만 로컬 상태를 바꾸고, 실패하면 오류를 표시합니다.
 */

/** 분류 탭 정의입니다. 순서가 화면 탭 순서가 됩니다. */
const CATEGORY_TABS: { value: NotificationCategory | null; label: string }[] = [
  { value: null, label: "전체" },
  { value: "POLICY", label: "정책" },
  { value: "AGENT", label: "Agent" },
  { value: "PROJECT", label: "프로젝트" },
  { value: "SECURITY", label: "보안" },
  { value: "SYSTEM", label: "시스템" },
];

/** 심각도별 배지 색입니다. 장비 로그 화면과 같은 기준을 씁니다. */
const SEVERITY_COLOR: Record<NotificationSeverity, "error" | "warning" | "info"> = {
  critical: "error",
  warning: "warning",
  info: "info",
};

/** 심각도 한글 라벨. */
const SEVERITY_LABEL: Record<NotificationSeverity, string> = {
  critical: "Critical",
  warning: "Warning",
  info: "Info",
};

/** 분류 한글 라벨. */
const CATEGORY_LABEL: Record<NotificationCategory, string> = {
  POLICY: "정책",
  AGENT: "Agent",
  PROJECT: "프로젝트",
  SECURITY: "보안",
  SYSTEM: "시스템",
};

/**
 * ISO-8601 시각을 상대 시간으로 바꿉니다.
 *
 * <p>알림 목록은 "언제" 보다 "얼마나 최근" 이 중요합니다. 절대 시각을
 * 그대로 보여주면 매번 현재 시각과 머릿속으로 빼야 합니다.
 * 다만 <b>툴팁에는 원문</b>을 남겨 정확한 시각을 확인할 수 있게 합니다.
 *
 * @param iso ISO-8601 문자열
 * @returns 상대 시간 문자열 (파싱 실패 시 원문)
 */
function relativeTime(iso: string): string {
  const then = Date.parse(iso);
  if (Number.isNaN(then)) return iso;

  const seconds = Math.floor((Date.now() - then) / 1000);
  if (seconds < 0) return "방금"; // 서버 시계가 앞선 경우
  if (seconds < 60) return `${seconds}초 전`;

  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}분 전`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;

  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}일 전`;

  // 한 달이 넘으면 상대 표현이 오히려 헷갈립니다.
  return new Date(then).toLocaleDateString();
}

export default function Notification() {
  const navigate = useNavigate();

  // ---------------------------------------------------------------------------
  // 필터 상태
  // ---------------------------------------------------------------------------
  const [category, setCategory] = useState<NotificationCategory | null>(null);
  const [severity, setSeverity] = useState<NotificationSeverity | "">("");
  const [projectId, setProjectId] = useState("");
  const [readState, setReadState] = useState<"all" | "unread" | "read">("all");
  const [search, setSearch] = useState("");

  /**
   * 실제 조회에 쓰이는 필터입니다.
   *
   * <p>검색어만 입력할 때마다 요청하면 타이핑 중에 요청이 수십 개 나갑니다.
   * [조회] 를 눌렀을 때만 반영합니다.
   */
  const [applied, setApplied] = useState<NotificationQuery>({ limit: 200 });

  const projects = useApi(() => listProjects(), []);
  const summary = useApi(() => getNotificationSummary(), []);
  const notifications = useApi(() => listNotifications(applied), [applied]);

  /** 읽음 처리 중인 알림 식별자입니다. 중복 클릭을 막습니다. */
  const [busyId, setBusyId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const rows = useMemo(
    () => notifications.data?.notifications ?? [],
    [notifications.data],
  );

  const unreadCount = notifications.data?.unread ?? 0;

  /** 심각도별 건수 (요약 카드용) */
  const counts = useMemo(() => {
    let critical = 0;
    let warning = 0;
    let info = 0;
    for (const row of rows) {
      if (row.severity === "critical") critical++;
      else if (row.severity === "warning") warning++;
      else info++;
    }
    return { critical, warning, info };
  }, [rows]);

  /** 조회를 실행합니다. */
  const applyFilters = useCallback(() => {
    setApplied({
      category,
      severity: severity || null,
      unreadOnly: readState === "all" ? null : readState === "unread",
      projectId: projectId || null,
      search: search.trim() || null,
      limit: 200,
    });
  }, [category, severity, projectId, readState, search]);

  /** 필터를 초기화합니다. */
  const resetFilters = useCallback(() => {
    setCategory(null);
    setSeverity("");
    setProjectId("");
    setReadState("all");
    setSearch("");
    setApplied({ limit: 200 });
  }, []);

  /**
   * 읽음 상태를 바꿉니다.
   *
   * <p>⚠️ 서버 요청이 <b>성공한 뒤에만</b> 목록을 다시 읽습니다. 먼저 로컬
   * 상태를 바꾸면 실패했을 때 화면과 서버가 어긋납니다.
   */
  const toggleRead = useCallback(
    async (notification: ApiNotification) => {
      setBusyId(notification.id);
      setActionError(null);
      try {
        if (notification.read) {
          await markNotificationUnread(notification.id);
        } else {
          await markNotificationRead(notification.id);
        }
        notifications.reload();
        summary.reload();
      } catch (cause) {
        setActionError(
          cause instanceof Error ? cause.message : "읽음 처리에 실패했습니다.",
        );
      } finally {
        setBusyId(null);
      }
    },
    [notifications, summary],
  );

  /** 안읽음 알림을 모두 읽음으로 표시합니다. */
  const markAllRead = useCallback(async () => {
    setActionError(null);
    try {
      await markAllNotificationsRead();
      notifications.reload();
      summary.reload();
    } catch (cause) {
      setActionError(
        cause instanceof Error ? cause.message : "모두 읽음 처리에 실패했습니다.",
      );
    }
  }, [notifications, summary]);

  /** 알림을 삭제합니다. */
  const remove = useCallback(
    async (notification: ApiNotification) => {
      // 삭제는 되돌릴 수 없으므로 한 번 확인합니다.
      if (!window.confirm(`알림을 삭제할까요?\n\n${notification.title}`)) return;
      setBusyId(notification.id);
      setActionError(null);
      try {
        await deleteNotification(notification.id);
        notifications.reload();
        summary.reload();
      } catch (cause) {
        setActionError(
          cause instanceof Error ? cause.message : "삭제에 실패했습니다.",
        );
      } finally {
        setBusyId(null);
      }
    },
    [notifications, summary],
  );

  /**
   * 알림의 상세 화면으로 이동합니다.
   *
   * <p>이동 경로는 <b>서버가 알려 줍니다</b>({@code link}). 프론트가 분류별로
   * 경로를 만들면 분류가 늘 때마다 화면을 고쳐야 하고, 서버가 가진 문맥
   * (프로젝트 키 등)을 다시 조립해야 합니다.
   *
   * <p>이동할 때 읽음 처리도 함께 합니다. 알림을 눌러 확인했다면 읽은 것입니다.
   */
  const open = useCallback(
    async (notification: ApiNotification) => {
      if (!notification.read) {
        try {
          await markNotificationRead(notification.id);
        } catch {
          // 이동 자체는 막지 않습니다. 읽음 표시 실패는 화면에서 다시 시도할 수 있습니다.
        }
      }
      if (notification.link) {
        navigate(notification.link);
      }
    },
    [navigate],
  );

  const loading = notifications.loading;
  const error = notifications.error;
  const offline = notifications.offline;

  return (
    <>
      <PageMeta
        title="Notification | SonarValidator"
        description="정책/Agent/프로젝트 알림 이력 조회"
      />
      <PageBreadcrumb pageTitle="Notification" />

      <div className="space-y-6">
        {/* 필터 바 */}
        <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                Notification
              </h3>
              {unreadCount > 0 && (
                <Badge size="sm" color="error">
                  안읽음 {unreadCount}
                </Badge>
              )}
              {summary.data && (
                <Badge size="sm" color="light">
                  {rows.length}건 표시
                </Badge>
              )}
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="outline"
                onClick={markAllRead}
                disabled={unreadCount === 0}
                title={unreadCount === 0 ? "안읽은 알림이 없습니다" : undefined}
              >
                모두 읽음
              </Button>
              <Button size="sm" variant="outline" onClick={notifications.reload}>
                새로고침
              </Button>
            </div>
          </div>

          {/* 분류 탭 — 건수를 함께 보여 "어디에 문제가 몰려 있는지" 를 알립니다. */}
          <div className="mb-4 flex flex-wrap gap-2">
            {CATEGORY_TABS.map((tab) => {
              const active = category === tab.value;
              const count = tab.value
                ? summary.data?.by_category?.[tab.value] ?? 0
                : undefined;
              return (
                <button
                  key={tab.value ?? "all"}
                  type="button"
                  onClick={() => setCategory(tab.value)}
                  className={`rounded-lg px-3 py-1.5 text-xs font-medium transition ${
                    active
                      ? "bg-brand-500 text-white"
                      : "border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
                  }`}
                >
                  {tab.label}
                  {typeof count === "number" && count > 0 && (
                    <span className={active ? "ml-1 opacity-80" : "ml-1 text-gray-400"}>
                      {count}
                    </span>
                  )}
                </button>
              );
            })}
          </div>

          {/* 세부 필터 */}
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300">
                Severity
              </label>
              <select
                value={severity}
                onChange={(e) => setSeverity(e.target.value as NotificationSeverity | "")}
                className="h-10 w-full rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 focus:border-brand-500 focus:outline-none dark:border-gray-700 dark:text-white"
              >
                <option value="">전체</option>
                <option value="critical">Critical</option>
                <option value="warning">Warning</option>
                <option value="info">Info</option>
              </select>
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300">
                Read
              </label>
              <select
                value={readState}
                onChange={(e) =>
                  setReadState(e.target.value as "all" | "unread" | "read")
                }
                className="h-10 w-full rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 focus:border-brand-500 focus:outline-none dark:border-gray-700 dark:text-white"
              >
                <option value="all">전체</option>
                <option value="unread">안읽음만</option>
                <option value="read">읽음만</option>
              </select>
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300">
                Project
              </label>
              <select
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
                className="h-10 w-full rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 focus:border-brand-500 focus:outline-none dark:border-gray-700 dark:text-white"
              >
                <option value="">전체</option>
                {(projects.data?.projects ?? []).map((project) => (
                  <option key={project.project_id} value={project.project_id}>
                    {project.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300">
                Search
              </label>
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") applyFilters();
                }}
                placeholder="제목/본문 검색"
                className="h-10 w-full rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 focus:border-brand-500 focus:outline-none dark:border-gray-700 dark:text-white"
              />
            </div>
          </div>

          <div className="mt-4 flex justify-end gap-2">
            <Button size="sm" variant="outline" onClick={resetFilters}>
              초기화
            </Button>
            <Button size="sm" onClick={applyFilters}>
              조회
            </Button>
          </div>
        </div>

        {/* 요약 카드 */}
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800/50">
            <p className="text-xs text-gray-500 dark:text-gray-400">표시 중</p>
            <p className="mt-1 text-xl font-bold text-gray-800 dark:text-white/90">
              {rows.length}
            </p>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800/50">
            <p className="text-xs text-gray-500 dark:text-gray-400">안읽음 (전체)</p>
            <p className="mt-1 text-xl font-bold text-error-500">{unreadCount}</p>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800/50">
            <p className="text-xs text-gray-500 dark:text-gray-400">Critical</p>
            <p className="mt-1 text-xl font-bold text-error-500">{counts.critical}</p>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800/50">
            <p className="text-xs text-gray-500 dark:text-gray-400">Warning</p>
            <p className="mt-1 text-xl font-bold text-warning-500">{counts.warning}</p>
          </div>
        </div>

        {/* 액션 오류 */}
        {actionError && (
          <div className="rounded-xl border border-error-200 bg-error-50 p-3 text-xs text-error-700 dark:border-error-500/30 dark:bg-error-500/10 dark:text-error-300">
            {actionError}
          </div>
        )}

        {/* 로딩 / 오류 / 빈 상태 */}
        <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
          {loading && (
            <div className="py-12 text-center text-sm text-gray-500 dark:text-gray-400">
              알림을 불러오는 중...
            </div>
          )}

          {error && (
            <div className="rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
              <p className="text-sm font-medium text-gray-800 dark:text-white/90">
                {offline ? "백엔드에 연결할 수 없습니다" : "알림을 불러오지 못했습니다"}
              </p>
              <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">{error}</p>
              {offline && (
                <p className="mt-2 rounded bg-white/60 p-2 font-mono text-[11px] text-gray-700 dark:bg-black/20 dark:text-gray-200">
                  cd SonarValidator_Backend && ./mvnw spring-boot:run
                </p>
              )}
              <Button className="mt-3" size="sm" variant="outline" onClick={notifications.reload}>
                다시 시도
              </Button>
            </div>
          )}

          {!loading && !error && rows.length === 0 && (
            <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-gray-200 py-12 text-center dark:border-gray-800">
              <p className="text-base font-medium text-gray-600 dark:text-gray-400">
                알림이 없습니다
              </p>
              <p className="mt-1 max-w-md text-sm text-gray-400 dark:text-gray-500">
                정책 푸시 결과, Agent 연결, 프로젝트 변경 같은 사건이 발생하면
                여기에 쌓입니다. 필터를 바꿔 다시 조회해 보세요.
              </p>
            </div>
          )}

          {/* 알림 목록 */}
          {!loading && !error && rows.length > 0 && (
            <ul className="divide-y divide-gray-100 dark:divide-gray-800">
              {rows.map((notification) => (
                <li
                  key={notification.id}
                  className={`flex flex-col gap-3 py-4 sm:flex-row sm:items-start ${
                    notification.read ? "" : "bg-brand-50/40 dark:bg-brand-500/5"
                  }`}
                >
                  {/* 읽음 토글 */}
                  <div className="flex shrink-0 items-center gap-2 sm:w-24">
                    <button
                      type="button"
                      onClick={() => toggleRead(notification)}
                      disabled={busyId === notification.id}
                      title={notification.read ? "안읽음으로 되돌리기" : "읽음으로 표시"}
                      className={`h-3 w-3 rounded-full transition ${
                        notification.read
                          ? "bg-gray-300 dark:bg-gray-600"
                          : "bg-brand-500"
                      } ${busyId === notification.id ? "opacity-50" : ""}`}
                    />
                    <Badge size="sm" color={SEVERITY_COLOR[notification.severity]}>
                      {SEVERITY_LABEL[notification.severity]}
                    </Badge>
                  </div>

                  {/* 본문 */}
                  <div className="min-w-0 flex-1">
                    <div className="mb-1 flex flex-wrap items-center gap-2">
                      <button
                        type="button"
                        onClick={() => open(notification)}
                        className="text-left text-sm font-semibold text-gray-800 hover:text-brand-600 dark:text-white/90 dark:hover:text-brand-400"
                      >
                        {notification.title}
                      </button>
                      <Badge size="sm" color="light">
                        {CATEGORY_LABEL[notification.category] ?? notification.category}
                      </Badge>
                      {/* 반복 횟수 — 1보다 크면 진행 중인 문제입니다. */}
                      {notification.repeat_count > 1 && (
                        <Badge size="sm" color="warning">
                          {notification.repeat_count}회 반복
                        </Badge>
                      )}
                    </div>

                    {notification.message && (
                      <p className="text-xs text-gray-600 dark:text-gray-300">
                        {notification.message}
                      </p>
                    )}

                    <div className="mt-1.5 flex flex-wrap items-center gap-2 text-[11px] text-gray-400">
                      <span title={notification.occurred_at}>
                        {relativeTime(notification.occurred_at)}
                      </span>
                      {notification.project_id && (
                        <>
                          <span className="h-1 w-1 rounded-full bg-gray-300" />
                          <span className="font-mono">{notification.project_id}</span>
                        </>
                      )}
                      {notification.agent_id && (
                        <>
                          <span className="h-1 w-1 rounded-full bg-gray-300" />
                          <span className="font-mono">{notification.agent_id}</span>
                        </>
                      )}
                      {notification.source && (
                        <>
                          <span className="h-1 w-1 rounded-full bg-gray-300" />
                          <span>{notification.source}</span>
                        </>
                      )}
                    </div>
                  </div>

                  {/* 액션 */}
                  <div className="flex shrink-0 gap-2">
                    {notification.link && (
                      <button
                        type="button"
                        onClick={() => open(notification)}
                        className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
                      >
                        열기
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => remove(notification)}
                      disabled={busyId === notification.id}
                      className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-error-600 transition hover:bg-error-50 disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-error-400 dark:hover:bg-error-500/10"
                    >
                      삭제
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </>
  );
}
