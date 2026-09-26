import Badge from "../ui/badge/Badge";
import { AlertIcon, CheckCircleIcon, InfoIcon } from "../../icons";
import type { ApiValidationReport, ViolationSeverity } from "../../lib/api/types";

interface ViolationSummaryProps {
  /** 서버 검증 보고서. null 이면 아직 검증하지 않은 상태입니다. */
  report: ApiValidationReport | null;
  /** 검증 실행 중 여부. */
  validating?: boolean;
  /** 검증 실행 콜백. 없으면 버튼을 숨깁니다. */
  onValidate?: () => void;
  /** 저장 콜백. 없으면 버튼을 숨깁니다. */
  onSave?: () => void;
  /** 저장 진행 중 여부. */
  saving?: boolean;
  /** 정책 푸시 콜백. 없으면 버튼을 숨깁니다. */
  onPush?: () => void;
  /** 푸시 진행 중 여부. */
  pushing?: boolean;
}

const SEVERITY_BADGE: Record<ViolationSeverity, { color: "error" | "warning" | "info"; label: string }> = {
  CRITICAL: { color: "error", label: "Critical" },
  MAJOR: { color: "warning", label: "Major" },
  MINOR: { color: "info", label: "Minor" },
};

/**
 * 망분리 검증 결과 요약 패널입니다.
 *
 * <h2>무엇을 보여주는가</h2>
 * 단순히 "위반 N건" 만 보여주면 운영자는 무엇을 고쳐야 할지 알 수 없습니다.
 * 그래서 세 가지를 함께 보여줍니다.
 *
 * <ol>
 *   <li><b>심각도별 집계</b>: 즉시 조치할 것(Critical)과 검토할 것(Major)을 분리</li>
 *   <li><b>금지된 연결 경로</b>: "어느 서브넷에서 어느 서브넷으로" 가 막히는지</li>
 *   <li><b>반례 패킷</b>: 그 연결이 실제로 성립하는 구체적 패킷 주소</li>
 * </ol>
 *
 * <h2>⚠️ BDD 내부 지표를 뺀 이유</h2>
 * <p>예전에는 노드 수 / 허용 조합 같은 BDD 내부 수치를 접어서 보여줬습니다.
 * 운영자에게 필요한 것은 <b>고칠 대상</b>이지, 판정 엔진이 몇 개 노드를
 * 썼는지가 아닙니다. 숫자가 많으면 오히려 "어디를 봐야 하나" 를 방해합니다.
 * 엔진 내부 수치는 서버 로그로 남기고 화면에서는 뺐습니다.
 */
export default function ViolationSummary({
  report,
  validating = false,
  onValidate,
  onSave,
  saving = false,
  onPush,
  pushing = false,
}: ViolationSummaryProps) {
  // 아직 검증하지 않은 상태
  if (!report) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-theme-xs dark:border-gray-700 dark:bg-gray-800/50">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <InfoIcon className="size-4 text-gray-400" />
            <h5 className="text-sm font-semibold text-gray-800 dark:text-white/90">
              망분리 검증
            </h5>
          </div>
          <div className="flex gap-2">
            {onValidate && (
              <button
                type="button"
                onClick={onValidate}
                disabled={validating}
                className="rounded-lg bg-brand-500 px-3 py-1.5 text-xs font-semibold text-white shadow-sm transition hover:bg-brand-600 disabled:opacity-50"
              >
                {validating ? "검증 중..." : "검증 실행"}
              </button>
            )}
            {onSave && (
              <button
                type="button"
                onClick={onSave}
                disabled={saving}
                className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200"
              >
                {saving ? "저장 중..." : "저장"}
              </button>
            )}
          </div>
        </div>
        <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
          아직 검증하지 않았습니다. 검증 실행을 누르면 서버가 BDD 로 망분리 규칙을
          판정하고, 위반이 있으면 재현 가능한 반례 패킷을 함께 알려줍니다.
        </p>
      </div>
    );
  }

  const critical = report.violations.filter((v) => v.severity === "CRITICAL").length;
  const major = report.violations.filter((v) => v.severity === "MAJOR").length;
  const minor = report.violations.filter((v) => v.severity === "MINOR").length;

  return (
    <div
      className={`rounded-xl border p-4 shadow-theme-xs ${
        report.compliant
          ? "border-success-200 bg-success-50/50 dark:border-success-500/30 dark:bg-success-500/10"
          : "border-error-200 bg-error-50/50 dark:border-error-500/30 dark:bg-error-500/10"
      }`}
    >
      {/* 헤더: 상태 + 액션 버튼 */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          {report.compliant ? (
            <CheckCircleIcon className="size-5 text-success-500" />
          ) : (
            <AlertIcon className="size-5 text-error-500" />
          )}
          <h5 className="text-sm font-semibold text-gray-800 dark:text-white/90">
            망분리 검증 {report.compliant ? "통과" : "실패"}
          </h5>
          <Badge size="sm" color={report.compliant ? "success" : "error"}>
            위반 {report.violation_count}건
          </Badge>
        </div>
        <div className="flex gap-2">
          {onValidate && (
            <button
              type="button"
              onClick={onValidate}
              disabled={validating}
              className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200"
            >
              {validating ? "검증 중..." : "다시 검증"}
            </button>
          )}
          {onSave && (
            <button
              type="button"
              onClick={onSave}
              disabled={saving}
              className="rounded-lg bg-brand-500 px-3 py-1.5 text-xs font-semibold text-white shadow-sm transition hover:bg-brand-600 disabled:opacity-50"
            >
              {saving ? "저장 중..." : "저장"}
            </button>
          )}
          {onPush && (
            <button
              type="button"
              onClick={onPush}
              disabled={pushing || !report.compliant}
              title={report.compliant ? "장치로 정책 전송" : "위반이 있어 전송할 수 없습니다"}
              className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200"
            >
              {pushing ? "전송 중..." : "정책 푸시"}
            </button>
          )}
        </div>
      </div>

      {/* 검사 규모 */}
      <p className="mt-2 text-xs text-gray-600 dark:text-gray-300">
        규칙 {report.rule_count}건 · 서브넷 {report.subnet_count}건 검사
      </p>

      {/* 심각도별 집계 */}
      {report.violation_count > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {critical > 0 && (
            <Badge size="sm" color={SEVERITY_BADGE.CRITICAL.color}>
              Critical {critical} — 즉시 조치 필요
            </Badge>
          )}
          {major > 0 && (
            <Badge size="sm" color={SEVERITY_BADGE.MAJOR.color}>
              Major {major} — 검토 필요
            </Badge>
          )}
          {minor > 0 && (
            <Badge size="sm" color={SEVERITY_BADGE.MINOR.color}>
              Minor {minor} — 참고
            </Badge>
          )}
        </div>
      )}

      {/* 사람이 읽는 메시지 */}
      {report.messages.length > 0 && (
        <ul className="mt-3 space-y-1 text-xs text-gray-700 dark:text-gray-200">
          {report.messages.map((message) => (
            <li key={message} className="flex items-start gap-1.5">
              <span className="mt-1 size-1 shrink-0 rounded-full bg-current opacity-50" />
              <span>{message}</span>
            </li>
          ))}
        </ul>
      )}

      {/* 금지된 연결 경로 — 운영자가 고쳐야 할 대상 */}
      {report.violations.length > 0 && (
        <ul className="mt-3 space-y-1.5">
          {report.violations.map((violation, index) => (
            <li
              key={`${violation.rule_id}-${index}`}
              className="flex items-start gap-2 rounded-lg bg-white/70 px-2.5 py-2 text-xs dark:bg-black/20"
            >
              <Badge size="sm" color={SEVERITY_BADGE[violation.severity].color}>
                {violation.severity}
              </Badge>
              <div className="min-w-0 flex-1">
                {/* 사유가 "Subnet A -> Subnet B 연결은 허용되지 않습니다" 형태입니다. */}
                <p className="font-medium text-gray-800 dark:text-white/90">
                  {violation.reason}
                </p>
                <p className="mt-0.5 font-mono text-[10px] text-gray-500 dark:text-gray-400">
                  {violation.rule_id}
                  {violation.sampled_packet && violation.sampled_packet !== "- -> -"
                    ? ` · ${violation.sampled_packet}`
                    : ""}
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
