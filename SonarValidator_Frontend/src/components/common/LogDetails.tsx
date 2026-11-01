import { useId, useState } from "react";
import { Modal } from "../ui/modal";
import Button from "../ui/button/Button";
import { compactText, errorLog, formatErrorDetails, type LogDetail, type RequestErrorEntry } from "../../lib/errors";

export function LogDetailsModal({ detail, onClose }: { detail: LogDetail | null; onClose: () => void }) {
  const titleId = useId();
  return <Modal ariaLabelledBy={titleId} isOpen={detail !== null} onClose={onClose} className="m-4 max-w-3xl">
    {detail && <section data-testid="log-details-modal" className="max-h-[90vh] overflow-y-auto p-6 pt-16 sm:p-8 sm:pt-16">
      <h2 id={titleId} className="text-lg font-semibold text-gray-900 dark:text-white">{detail.title}</h2>
      <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">{compactText(detail.summary)}</p>
      {detail.metadata && <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-xs">
        {Object.entries(detail.metadata).filter(([, value]) => value != null && value !== "").map(([label, value]) => <div key={label} className="contents">
          <dt className="text-gray-500 dark:text-gray-400">{label}</dt><dd className="break-all text-gray-800 dark:text-gray-200">{formatErrorDetails(value)}</dd>
        </div>)}
      </dl>}
      <h3 className="mt-5 text-sm font-semibold text-gray-800 dark:text-gray-200">상세 내용</h3>
      <pre data-testid="log-detail-content" className="custom-scrollbar mt-2 max-h-[50vh] overflow-auto whitespace-pre-wrap break-words rounded-xl bg-gray-100 p-4 font-mono text-xs leading-relaxed text-gray-800 dark:bg-gray-950 dark:text-gray-200">{formatErrorDetails(detail.details)}</pre>
      <div className="mt-5 flex justify-end"><Button size="sm" variant="outline" onClick={onClose}>닫기</Button></div>
    </section>}
  </Modal>;
}

export function ErrorDetailsButton({ title = "오류 상세", details }: { title?: string; details: unknown }) {
  const [open, setOpen] = useState(false);
  return <><button type="button" className="text-xs font-medium text-brand-600 hover:underline dark:text-brand-400" onClick={() => setOpen(true)}>상세 보기</button>
    <LogDetailsModal detail={open ? { title, summary: compactText(details, "오류가 발생했습니다."), details: formatErrorDetails(details) } : null} onClose={() => setOpen(false)} />
  </>;
}

export function RequestErrorLog({ entries, onDetails }: { entries: readonly RequestErrorEntry[]; onDetails: (detail: LogDetail) => void }) {
  return <section className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
    <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
      <div><h2 className="font-semibold text-gray-900 dark:text-white">요청 오류 기록</h2><p className="mt-1 text-xs text-gray-500 dark:text-gray-400">현재 브라우저 세션의 최근 100건입니다. 새로고침이나 로그아웃 시 초기화됩니다.</p></div>
      <Button size="sm" variant="outline" disabled={!entries.length} onClick={() => errorLog.clear()}>기록 비우기</Button>
    </div>
    {!entries.length ? <p className="py-10 text-center text-sm text-gray-500">기록된 요청 오류가 없습니다.</p> :
      <div className="overflow-x-auto"><table className="w-full text-left text-sm">
        <thead className="text-xs text-gray-500"><tr><th className="p-3">시각</th><th className="p-3">요청</th><th className="p-3">메시지</th><th className="p-3">상세</th></tr></thead>
        <tbody className="divide-y divide-gray-100 dark:divide-gray-800">{entries.map(entry => <tr key={entry.id} data-testid="request-error-row">
          <td className="whitespace-nowrap p-3 text-xs text-gray-500">{new Date(entry.time).toLocaleTimeString("ko-KR")}</td>
          <td className="max-w-64 p-3 text-xs text-gray-600 dark:text-gray-300"><span>{entry.method} {entry.status ?? ""}</span><span className="block truncate">{entry.path}</span></td>
          <td className="max-w-lg p-3 text-sm text-gray-800 dark:text-gray-200"><span>{entry.summary}</span>{entry.count > 1 && <span className="ml-2 text-xs text-gray-500">×{entry.count}</span>}</td>
          <td className="whitespace-nowrap p-3"><button type="button" className="text-xs font-medium text-brand-600 hover:underline dark:text-brand-400" onClick={() => onDetails({ title: "요청 오류 상세", summary: entry.summary, details: entry.details,
            metadata: { "시각": new Date(entry.time).toLocaleString("ko-KR"), "요청": entry.method + " " + entry.path, "상태": entry.status === 0 ? "연결 실패" : entry.status, "반복": entry.count } })}>상세 보기</button></td>
        </tr>)}</tbody>
      </table></div>}
  </section>;
}
