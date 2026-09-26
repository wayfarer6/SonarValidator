import { useState, useRef, useMemo } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";
import { EventInput, DateSelectArg, EventClickArg, EventContentArg } from "@fullcalendar/core";
import { Modal } from "../components/ui/modal";
import { useModal } from "../hooks/useModal";
import PageMeta from "../components/common/PageMeta";
import { useApi } from "../hooks/useApi";
import { listNotifications } from "../lib/api/notifications";
import { listComplianceChanges } from "../lib/api";
import type {
  ApiComplianceChange,
  ApiNotification,
  NotificationSeverity,
} from "../lib/api/types";

/**
 * 운영 캘린더입니다.
 *
 * <h2>왜 서버 데이터를 쓰는가</h2>
 * 이전에는 "Event Conf." / "Meeting" / "Workshop" 이라는 <b>가짜 일정</b>을
 * 화면에 심어 두었습니다. 그 상태로는 "오늘 무슨 일이 있었나" 를 물었을 때
 * 캘린더가 실제 사건 대신 데모 데이터를 보여 주므로 운영에 쓸 수 없습니다.
 *
 * 이제 두 종류의 실제 사건을 서버에서 받아 달력에 올립니다.
 * <ul>
 *   <li>알림({@code /api/v1/notifications}) — 심각도에 따라 색이 달라집니다.</li>
 *   <li>변경 이력({@code /api/v1/compliance/changes}) — 적용/보류/거부 상태가
 *       색으로 드러납니다.</li>
 * </ul>
 *
 * 사용자가 직접 추가한 일정은 서버에 저장하는 API 가 없으므로 <b>이 브라우저
 * 세션에서만</b> 남습니다. 서버 사건과 섞이지 않도록 별도 상태로 관리합니다.
 */
interface CalendarEvent extends EventInput {
  extendedProps: {
    calendar: string;
    /** 출처 구분: 서버 알림 / 변경 이력 / 사용자가 직접 추가 */
    origin?: "notification" | "compliance" | "local";
    /** 알림이면 분류, 변경이력이면 유형입니다. (툴팁/디버깅용) */
    detail?: string;
    /** 관련 프로젝트 키. */
    projectId?: string | null;
    /** 관련 장치 식별자. */
    agentId?: string | null;
    /** 반복 횟수(알림 전용). 1보다 크면 진행 중인 문제입니다. */
    repeatCount?: number;
  };
}

/** 알림 심각도 → 달력 색상 키 */
const SEVERITY_COLOR: Record<NotificationSeverity, string> = {
  critical: "Danger",
  warning: "Warning",
  info: "Primary",
};

/** 변경 이력 상태 → 달력 색상 키 */
const CHANGE_COLOR: Record<ApiComplianceChange["status"], string> = {
  Applied: "Success",
  Rejected: "Danger",
  Pending: "Warning",
};

/** ISO 시각 문자열을 달력이 요구하는 형태로 그대로 넘깁니다. */
function toNotificationEvent(item: ApiNotification): CalendarEvent {
  return {
    id: `ntf-${item.id}`,
    title: item.repeat_count > 1 ? `${item.title} (${item.repeat_count}회)` : item.title,
    start: item.occurred_at,
    allDay: false,
    extendedProps: {
      calendar: SEVERITY_COLOR[item.severity],
      origin: "notification",
      detail: item.category,
      projectId: item.project_id,
      agentId: item.agent_id,
      repeatCount: item.repeat_count,
    },
  };
}

function toComplianceEvent(item: ApiComplianceChange): CalendarEvent {
  return {
    id: `chg-${item.id}`,
    title: item.summary,
    start: item.timestamp,
    allDay: false,
    extendedProps: {
      calendar: CHANGE_COLOR[item.status],
      origin: "compliance",
      detail: item.type,
      projectId: item.project_id,
      agentId: item.agent_id,
    },
  };
}

const Calendar: React.FC = () => {
  const [selectedEvent, setSelectedEvent] = useState<CalendarEvent | null>(
    null
  );
  const [eventTitle, setEventTitle] = useState("");
  const [eventStartDate, setEventStartDate] = useState("");
  const [eventEndDate, setEventEndDate] = useState("");
  const [eventLevel, setEventLevel] = useState("");
  /** 사용자가 직접 추가한 일정 (서버 저장 API 없음) */
  const [localEvents, setLocalEvents] = useState<CalendarEvent[]>([]);
  const calendarRef = useRef<FullCalendar>(null);
  const { isOpen, openModal, closeModal } = useModal();

  const calendarsEvents = {
    Danger: "danger",
    Success: "success",
    Primary: "primary",
    Warning: "warning",
  };

  const notificationsResult = useApi(() => listNotifications({ limit: 200 }), []);
  const changesResult = useApi(() => listComplianceChanges(), []);

  const notificationEvents = useMemo(
    () => (notificationsResult.data?.notifications ?? []).map(toNotificationEvent),
    [notificationsResult.data]
  );
  const complianceEvents = useMemo(
    () => (changesResult.data?.changes ?? []).map(toComplianceEvent),
    [changesResult.data]
  );

  /** 서버 사건 + 사용자가 추가한 일정 */
  const events = useMemo(
    () => [...notificationEvents, ...complianceEvents, ...localEvents],
    [notificationEvents, complianceEvents, localEvents]
  );

  const loading = notificationsResult.loading || changesResult.loading;
  const loadError = notificationsResult.error ?? changesResult.error;
  const reloadAll = () => {
    notificationsResult.reload();
    changesResult.reload();
  };

  const handleDateSelect = (selectInfo: DateSelectArg) => {
    resetModalFields();
    setEventStartDate(selectInfo.startStr);
    setEventEndDate(selectInfo.endStr || selectInfo.startStr);
    openModal();
  };

  const handleEventClick = (clickInfo: EventClickArg) => {
    const event = clickInfo.event;
    setSelectedEvent(event as unknown as CalendarEvent);
    setEventTitle(event.title);
    setEventStartDate(event.start?.toISOString().split("T")[0] || "");
    setEventEndDate(event.end?.toISOString().split("T")[0] || "");
    setEventLevel(event.extendedProps.calendar);
    openModal();
  };

  const handleAddOrUpdateEvent = () => {
    if (selectedEvent) {
      // 서버에서 온 사건은 수정할 수 없습니다. 서버가 원본을 갖고 있는데
      // 화면에서만 고치면 다음 새로고침에 원래대로 돌아가 혼란만 커집니다.
      if (
        selectedEvent.extendedProps.origin === "notification" ||
        selectedEvent.extendedProps.origin === "compliance"
      ) {
        closeModal();
        resetModalFields();
        return;
      }

      // 직접 추가한 일정만 수정합니다.
      setLocalEvents((prevEvents) =>
        prevEvents.map((event) =>
          event.id === selectedEvent.id
            ? {
                ...event,
                title: eventTitle,
                start: eventStartDate,
                end: eventEndDate,
                extendedProps: { ...event.extendedProps, calendar: eventLevel },
              }
            : event
        )
      );
    } else {
      // 새 일정 추가 (이 브라우저 세션에만 유지)
      const newEvent: CalendarEvent = {
        id: `local-${Date.now()}`,
        title: eventTitle,
        start: eventStartDate,
        end: eventEndDate,
        allDay: true,
        extendedProps: { calendar: eventLevel, origin: "local" },
      };
      setLocalEvents((prevEvents) => [...prevEvents, newEvent]);
    }
    closeModal();
    resetModalFields();
  };

  const resetModalFields = () => {
    setEventTitle("");
    setEventStartDate("");
    setEventEndDate("");
    setEventLevel("");
    setSelectedEvent(null);
  };

  /**
   * 지금 열린 모달이 <b>읽기 전용</b>인지 판단합니다.
   *
   * <p>서버에서 온 알림/변경 이력은 서버가 원본이므로 이 화면에서 고칠 수
   * 없습니다. 수정 버튼을 보여 주고 눌렀을 때 조용히 무시하면 사용자는
   * "저장이 안 된다" 고만 느낍니다. 그래서 버튼 자체를 숨기고 안내합니다.
   */
  const isReadOnly =
    selectedEvent?.extendedProps.origin === "notification" ||
    selectedEvent?.extendedProps.origin === "compliance";

  return (
    <>
      <PageMeta
        title="Calendar | SonarValidator"
        description="알림과 변경 이력을 날짜별로 확인하는 운영 캘린더"
      />

      {/* 출처/색상 범례 + 새로고침 */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-white/[0.03]">
        <div className="flex flex-wrap items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
          <span className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-error-500" />
            위험 알림 / 거부된 변경
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-warning-500" />
            경고 알림 / 보류된 변경
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-brand-500" />
            정보 알림
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-success-500" />
            적용된 변경
          </span>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-gray-400">
            알림 {notificationEvents.length}건 · 변경 {complianceEvents.length}건
          </span>
          <button
            type="button"
            onClick={reloadAll}
            disabled={loading}
            className="rounded-lg bg-white px-3 py-2 text-sm font-medium text-gray-700 ring-1 ring-inset ring-gray-300 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-gray-800 dark:text-gray-400 dark:ring-gray-700 dark:hover:bg-white/[0.03]"
          >
            {loading ? "불러오는 중..." : "새로고침"}
          </button>
        </div>
      </div>

      {loadError && (
        <p className="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
          서버에서 일정을 불러오지 못했습니다: {loadError}
        </p>
      )}

      <div className="rounded-2xl border  border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div className="custom-calendar">
          <FullCalendar
            ref={calendarRef}
            plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
            initialView="dayGridMonth"
            headerToolbar={{
              left: "prev,next addEventButton",
              center: "title",
              right: "dayGridMonth,timeGridWeek,timeGridDay",
            }}
            events={events}
            selectable={true}
            select={handleDateSelect}
            eventClick={handleEventClick}
            eventContent={renderEventContent}
            customButtons={{
              addEventButton: {
                text: "Add Event +",
                click: openModal,
              },
            }}
          />
        </div>
        <Modal
          isOpen={isOpen}
          onClose={closeModal}
          className="max-w-[700px] p-6 lg:p-10"
        >
          <div className="flex flex-col px-2 overflow-y-auto custom-scrollbar">
            <div>
              <h5 className="mb-2 font-semibold text-gray-800 modal-title text-theme-xl dark:text-white/90 lg:text-2xl">
                {isReadOnly
                  ? "이벤트 상세"
                  : selectedEvent
                    ? "Edit Event"
                    : "Add Event"}
              </h5>
              <p className="text-sm text-gray-500 dark:text-gray-400">
                {isReadOnly
                  ? "서버가 기록한 사건입니다. 내용은 알림/변경 이력 화면에서 확인하세요."
                  : "Plan your next big moment: schedule or edit an event to stay on track"}
              </p>
            </div>
            <div className="mt-8">
              <div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                    Event Title
                  </label>
                  <input
                    id="event-title"
                    type="text"
                    value={eventTitle}
                    onChange={(e) => setEventTitle(e.target.value)}
                    className="dark:bg-dark-900 h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 py-2.5 text-sm text-gray-800 shadow-theme-xs placeholder:text-gray-400 focus:border-brand-300 focus:outline-hidden focus:ring-3 focus:ring-brand-500/10 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90 dark:placeholder:text-white/30 dark:focus:border-brand-800"
                  />
                </div>
              </div>
              <div className="mt-6">
                <label className="block mb-4 text-sm font-medium text-gray-700 dark:text-gray-400">
                  Event Color
                </label>
                <div className="flex flex-wrap items-center gap-4 sm:gap-5">
                  {Object.entries(calendarsEvents).map(([key, value]) => (
                    <div key={key} className="n-chk">
                      <div
                        className={`form-check form-check-${value} form-check-inline`}
                      >
                        <label
                          className="flex items-center text-sm text-gray-700 form-check-label dark:text-gray-400"
                          htmlFor={`modal${key}`}
                        >
                          <span className="relative">
                            <input
                              className="sr-only form-check-input"
                              type="radio"
                              name="event-level"
                              value={key}
                              id={`modal${key}`}
                              checked={eventLevel === key}
                              onChange={() => setEventLevel(key)}
                            />
                            <span className="flex items-center justify-center w-5 h-5 mr-2 border border-gray-300 rounded-full box dark:border-gray-700">
                              <span
                                className={`h-2 w-2 rounded-full bg-white ${
                                  eventLevel === key ? "block" : "hidden"
                                }`}
                              ></span>
                            </span>
                          </span>
                          {key}
                        </label>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="mt-6">
                <label className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                  Enter Start Date
                </label>
                <div className="relative">
                  <input
                    id="event-start-date"
                    type="date"
                    value={eventStartDate}
                    onChange={(e) => setEventStartDate(e.target.value)}
                    className="dark:bg-dark-900 h-11 w-full appearance-none rounded-lg border border-gray-300 bg-transparent bg-none px-4 py-2.5 pl-4 pr-11 text-sm text-gray-800 shadow-theme-xs placeholder:text-gray-400 focus:border-brand-300 focus:outline-hidden focus:ring-3 focus:ring-brand-500/10 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90 dark:placeholder:text-white/30 dark:focus:border-brand-800"
                  />
                </div>
              </div>

              <div className="mt-6">
                <label className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                  Enter End Date
                </label>
                <div className="relative">
                  <input
                    id="event-end-date"
                    type="date"
                    value={eventEndDate}
                    onChange={(e) => setEventEndDate(e.target.value)}
                    className="dark:bg-dark-900 h-11 w-full appearance-none rounded-lg border border-gray-300 bg-transparent bg-none px-4 py-2.5 pl-4 pr-11 text-sm text-gray-800 shadow-theme-xs placeholder:text-gray-400 focus:border-brand-300 focus:outline-hidden focus:ring-3 focus:ring-brand-500/10 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90 dark:placeholder:text-white/30 dark:focus:border-brand-800"
                  />
                </div>
              </div>
            </div>
            <div className="flex items-center gap-3 mt-6 modal-footer sm:justify-end">
              <button
                onClick={closeModal}
                type="button"
                className="flex w-full justify-center rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-white/[0.03] sm:w-auto"
              >
                {isReadOnly ? "닫기" : "Close"}
              </button>
              {!isReadOnly && (
                <button
                  onClick={handleAddOrUpdateEvent}
                  type="button"
                  className="btn btn-success btn-update-event flex w-full justify-center rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 sm:w-auto"
                >
                  {selectedEvent ? "Update Changes" : "Add Event"}
                </button>
              )}
            </div>
          </div>
        </Modal>
      </div>
    </>
  );
};

const renderEventContent = (eventInfo: EventContentArg) => {
  const colorClass = `fc-bg-${String(eventInfo.event.extendedProps.calendar).toLowerCase()}`;
  return (
    <div
      className={`event-fc-color flex fc-event-main ${colorClass} p-1 rounded-sm`}
    >
      <div className="fc-daygrid-event-dot"></div>
      <div className="fc-event-time">{eventInfo.timeText}</div>
      <div className="fc-event-title">{eventInfo.event.title}</div>
    </div>
  );
};

export default Calendar;
