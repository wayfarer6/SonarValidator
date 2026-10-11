import { useMemo } from "react";
import { FileTextIcon } from "../../icons";
import { useApi } from "../../hooks/useApi";
import { getLogSummary, listLogs } from "../../lib/api/aiLogs";

/** 화면에 쓰는 3단계 묶음. 서버는 8단계 심각도를 씁니다. */
type LevelKey = "ERROR" | "WARN" | "INFO";

const LEVEL_STYLE: Record<LevelKey, string> = {
  ERROR: "bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400",
  WARN: "bg-orange-50 text-orange-600 dark:bg-orange-500/10 dark:text-orange-400",
  INFO: "bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400",
};

/** 서버 심각도 이름을 화면 3단계로 접습니다. */
function levelOf(severity: string | null): LevelKey {
  const name = (severity ?? "").toLowerCase();
  if (["emergency", "alert", "critical", "error"].includes(name)) return "ERROR";
  if (name === "warning") return "WARN";
  return "INFO";
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleString("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

// 로그 현황 카드 (레벨별 집계 + 최근 로그)
export default function LogStatusCard() {
  const summary = useApi(() => getLogSummary(), []);
  const logs = useApi(() => listLogs({ limit: 20 }), []);

  // 서버가 준 심각도별 집계를 3단계로 접습니다.
  const counts = useMemo(() => {
    const acc: Record<LevelKey, number> = { ERROR: 0, WARN: 0, INFO: 0 };
    for (const row of summary.data?.summary ?? []) {
      acc[levelOf(row.severity)] += row.count;
    }
    return acc;
  }, [summary.data]);

  const entries = logs.data?.logs ?? [];

  return (
    <div className="flex h-full flex-col rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] md:p-6">
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800">
            <FileTextIcon className="size-5 text-gray-700 dark:text-white/90" />
          </div>
          <h4 className="text-base font-semibold text-gray-800 dark:text-white/90">
            Log Status
          </h4>
        </div>
        <div className="flex gap-2 text-xs font-semibold">
          <span className="rounded-full bg-red-50 px-2.5 py-1 text-red-600 dark:bg-red-500/10 dark:text-red-400">
            ERROR {counts.ERROR}
          </span>
          <span className="rounded-full bg-orange-50 px-2.5 py-1 text-orange-600 dark:bg-orange-500/10 dark:text-orange-400">
            WARN {counts.WARN}
          </span>
          <span className="rounded-full bg-blue-50 px-2.5 py-1 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400">
            INFO {counts.INFO}
          </span>
        </div>
      </div>

      <div className="flex-1 space-y-2.5 overflow-y-auto pr-1" style={{ maxHeight: 320 }}>
        {entries.map((log) => {
          const level = levelOf(log.severity);
          return (
            <div
              key={log.id}
              className="rounded-xl border border-gray-100 p-3 dark:border-gray-800"
            >
              <div className="flex items-center justify-between gap-2">
                <span
                  className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${LEVEL_STYLE[level]}`}
                >
                  {log.severity.toUpperCase()}
                </span>
                <span className="text-xs font-medium text-gray-700 dark:text-gray-300">
                  {log.source}
                </span>
                <span className="ml-auto shrink-0 text-[11px] text-gray-400 dark:text-gray-500">
                  {formatTime(log.logged_at)}
                </span>
              </div>
              <p className="mt-1.5 text-xs leading-snug text-gray-500 dark:text-gray-400">
                {log.message ?? log.raw}
              </p>
            </div>
          );
        })}

        {logs.loading && (
          <p className="py-6 text-center text-xs text-gray-400">불러오는 중...</p>
        )}

        {!logs.loading && entries.length === 0 && (
          <p className="py-6 text-center text-xs text-gray-400">로그가 없습니다.</p>
        )}
      </div>
    </div>
  );
}
