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
 * <h2>BDD 검증 결과를 어떻게 보여주는가</h2>
 * 단순히 "위반 N건" 만 보여주면 운영자는 무엇을 고쳐야 할지 알 수 없습니다.
 * 그래서 세 가지를 함께 보여줍니다.
 *
 * <ol>
 *   <li><b>심각도별 집계</b>: 즉시 조치할 것(Critical)과 검토할 것(Major)을 분리</li>
 *   <li><b>반례 패킷</b>: "이 주소에서 이 주소로 가는 패킷이 위반" — 재현 가능한 예시</li>
 *   <li><b>BDD 지표</b>: 허용 조합 수와 노드 수 (분석 신뢰도 확인용)</li>
 * </ol>
 *
 * <p>{@code metrics} 는 검증이 실제로 집합 연산으로 돌았다는 증거입니다.
 * 노드 수가 규칙 수에 비해 작으면 BDD 압축이 잘 되었다는 뜻입니다.
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

      {/* BDD 지표: 검증이 집합 연산으로 돌았다는 증거 */}
      {Object.keys(report.metrics).length > 0 && (
        <details className="mt-3">
          <summary className="cursor-pointer text-[11px] font-medium text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200">
            BDD 분석 지표 보기
          </summary>
          <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-[11px] text-gray-600 dark:text-gray-300 sm:grid-cols-3">
            <MetricRow label="BDD 변수" value={report.metrics["bdd_variables"]} />
            <MetricRow label="허용 노드" value={report.metrics["bdd_nodes_allowed"]} />
            <MetricRow label="금지 노드" value={report.metrics["bdd_nodes_forbidden"]} />
            <MetricRow label="위반 노드" value={report.metrics["bdd_nodes_violating"]} />
            <MetricRow label="허용 조합" value={report.metrics["allowed_combinations"]} />
            <MetricRow label="위반 조합" value={report.metrics["violating_combinations"]} />
          </div>
          <p className="mt-2 text-[10px] text-gray-400">
            BDD(이진 결정 다이어그램)로 패킷 집합을 표현해 위반 여부를 집합 연산으로
            판정합니다. 노드 수가 규칙 수에 비해 작으면 중복 함수가 잘 공유되었다는 뜻입니다.
          </p>
        </details>
      )}
    </div>
  );
}

/** 지표 한 줄을 렌더링합니다. */
function MetricRow({ label, value }: { label: string; value: number | string | undefined }) {
  if (value === undefined) return null;
  return (
    <div className="flex justify-between gap-2">
      <span className="text-gray-400">{label}</span>
      <span className="font-mono">{String(value)}</span>
    </div>
  );
}
