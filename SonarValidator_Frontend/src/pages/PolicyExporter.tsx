import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import { useApi } from "../hooks/useApi";
import { getPolicyViolations, getPolicyForbiddenPairs } from "../lib/api";
import { listProjects } from "../lib/api/projects";
import type { ViolationSeverity } from "../lib/api/types";

/**
 * 정책 검증 결과 내보내기 화면입니다.
 *
 * <h2>빈 스텁에서 실제 기능으로</h2>
 * 이 화면은 이전에 제목만 있는 껍데기였고, 안에는 "박스 왼쪽 최상단에
 * Create Project" 라는 주석 한 줄뿐이었습니다. 이제 서버가 판정한 위반
 * 내역을 JSON/CSV 로 내려받습니다.
 *
 * <h2>왜 서버 값을 그대로 내보내는가</h2>
 * 프론트에서 다시 계산하면 서버 판정과 어긋날 수 있습니다. 위반 여부와
 * 심각도, 반례 패킷({@code sampled_packet})은 모두 서버 응답을 그대로
 * 옮깁니다.
 */

const SEVERITY_COLOR: Record<ViolationSeverity, "error" | "warning" | "info"> = {
  CRITICAL: "error",
  MAJOR: "warning",
  MINOR: "info",
};

/** CSV 한 칸을 안전하게 감쌉니다. (쉼표/따옴표/줄바꿈 포함 시 이중 인용부호) */
function csvCell(value: unknown): string {
  const text = value === null || value === undefined ? "" : String(value);
  if (/[",\n\r]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

/** Blob 을 파일로 내려받습니다. */
function downloadBlob(content: string, mime: string, filename: string): void {
  const blob = new Blob([content], { type: `${mime};charset=utf-8` });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export default function PolicyExporter() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const projects = useApi(() => listProjects(), []);
  const projectList = useMemo(() => projects.data?.projects ?? [], [projects.data]);

  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(
    searchParams.get("project_id"),
  );

  // 선택이 없으면 가장 최근 프로젝트로 채웁니다.
  const latestProjectId = useMemo(
    () =>
      [...projectList].sort((a, b) =>
        (b.created_at ?? "").localeCompare(a.created_at ?? ""),
      )[0]?.project_id ?? null,
    [projectList],
  );
  useEffect(() => {
    if (selectedProjectId === null && latestProjectId) {
      setSelectedProjectId(latestProjectId);
    }
  }, [latestProjectId, selectedProjectId]);

  const selectedProject =
    projectList.find((p) => p.project_id === selectedProjectId) ?? null;

  const violationsResult = useApi(
    () => (selectedProjectId ? getPolicyViolations(selectedProjectId) : Promise.resolve(null)),
    [selectedProjectId],
  );
  const forbiddenResult = useApi(
    () =>
      selectedProjectId ? getPolicyForbiddenPairs(selectedProjectId) : Promise.resolve(null),
    [selectedProjectId],
  );

  const report = violationsResult.data;
  const violations = report?.violations ?? [];
  const forbiddenPairs = forbiddenResult.data?.pairs ?? [];

  const severityCounts = useMemo(() => {
    return {
      CRITICAL: report?.by_severity?.CRITICAL ?? 0,
      MAJOR: report?.by_severity?.MAJOR ?? 0,
      MINOR: report?.by_severity?.MINOR ?? 0,
    };
  }, [report]);

  const fileBase = `policy-${selectedProject?.name ?? selectedProjectId ?? "report"}`;

  /** 서버 응답 전체를 JSON 으로 내보냅니다. (원본 보존) */
  const handleExportJson = () => {
    if (!report) return;
    const payload = {
      project_id: report.project_id,
      project_name: report.project_name,
      generated_at: new Date().toISOString(),
      compliant: report.compliant,
      rule_count: report.rule_count,
      subnet_count: report.subnet_count,
      violation_count: report.violation_count,
      by_severity: severityCounts,
      messages: report.messages,
      metrics: report.metrics,
      forbidden_pairs: forbiddenPairs,
      violations: violations.map((v) => ({
        rule_id: v.rule_id,
        src_subnet: v.src_subnet,
        dst_subnet: v.dst_subnet,
        src_class: v.src_class,
        dst_class: v.dst_class,
        severity: v.severity,
        reason: v.reason,
        sampled_packet: v.sampled_packet,
        sampled_src_ip: v.sampled_src_ip,
        sampled_dst_ip: v.sampled_dst_ip,
        sampled_port: v.sampled_port,
      })),
    };
    downloadBlob(
      JSON.stringify(payload, null, 2),
      "application/json",
      `${fileBase}.json`,
    );
  };

  /** 위반 목록을 스프레드시트용 CSV 로 내보냅니다. */
  const handleExportCsv = () => {
    if (!report) return;
    const header = [
      "rule_id",
      "severity",
      "src_subnet",
      "src_class",
      "dst_subnet",
      "dst_class",
      "port",
      "reason",
      "sampled_packet",
    ];
    const rows = violations.map((v) =>
      [
        v.rule_id,
        v.severity,
        v.src_subnet ?? "",
        v.src_class ?? "",
        v.dst_subnet ?? "",
        v.dst_class ?? "",
        v.sampled_port ?? "",
        v.reason,
        v.sampled_packet,
      ]
        .map(csvCell)
        .join(","),
    );
    // BOM 을 붙여야 Excel 에서 한글이 깨지지 않습니다.
    const csv = `\uFEFF${[header.join(","), ...rows].join("\r\n")}\r\n`;
    downloadBlob(csv, "text/csv", `${fileBase}.csv`);
  };

  const isExporting = violationsResult.loading || forbiddenResult.loading;

  return (
    <>
      <PageMeta
        title="Export Policy | SonarValidator"
        description="정책 검증 위반 내역 JSON/CSV 내보내기"
      />
      <PageBreadcrumb pageTitle="Export Policy" />

      <div className="mb-6 rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">
              내보낼 프로젝트 선택
            </h3>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
              서버가 판정한 위반 내역과 금지 서브넷 쌍을 파일로 내려받습니다.
            </p>
          </div>
          <div className="flex flex-wrap items-end gap-3">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                Project
              </label>
              <select
                value={selectedProjectId ?? ""}
                onChange={(e) =>
                  setSelectedProjectId(e.target.value === "" ? null : e.target.value)
                }
                className="h-11 rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-800 focus:outline-hidden focus:ring-3 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
              >
                <option value="">선택 안 함</option>
                {projectList.map((p) => (
                  <option key={p.project_id} value={p.project_id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>
            <button
              type="button"
              onClick={() => violationsResult.reload()}
              disabled={!selectedProjectId}
              className="h-11 rounded-lg bg-white px-4 text-sm font-medium text-gray-700 ring-1 ring-inset ring-gray-300 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-gray-800 dark:text-gray-400 dark:ring-gray-700 dark:hover:bg-white/[0.03]"
            >
              새로고침
            </button>
          </div>
        </div>

        <div className="mt-5 flex items-center justify-end gap-3 border-t border-gray-200 pt-4 dark:border-gray-800">
          <button
            type="button"
            onClick={() => navigate("/policy")}
            className="rounded-lg bg-white px-4 py-2.5 text-sm font-medium text-gray-700 ring-1 ring-inset ring-gray-300 transition hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-400 dark:ring-gray-700 dark:hover:bg-white/[0.03]"
          >
            이전
          </button>
          <button
            type="button"
            onClick={handleExportCsv}
            disabled={!report || isExporting}
            className="rounded-lg bg-white px-4 py-2.5 text-sm font-medium text-gray-700 ring-1 ring-inset ring-gray-300 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-gray-800 dark:text-gray-400 dark:ring-gray-700 dark:hover:bg-white/[0.03]"
          >
            CSV 내보내기
          </button>
          <button
            type="button"
            onClick={handleExportJson}
            disabled={!report || isExporting}
            className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white shadow-theme-xs transition hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isExporting ? "불러오는 중..." : "JSON 내보내기"}
          </button>
        </div>
      </div>

      {/* 미리보기 */}
      <div className="rounded-2xl border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-white/[0.03] lg:p-8">
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-gray-200 pb-5 dark:border-gray-800">
          <div>
            <h2 className="text-xl font-bold text-gray-800 dark:text-white/90">
              SonarValidator Policy Report
            </h2>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
              네트워크 분리 정책 검증 결과
            </p>
          </div>
          {report && (
            <Badge color={report.compliant ? "success" : "error"} size="sm">
              {report.compliant ? "검증 통과" : `위반 ${report.violation_count}건`}
            </Badge>
          )}
        </div>

        {violationsResult.error ? (
          <p className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
            {violationsResult.error}
          </p>
        ) : (
          <>
            <dl className="mt-4 grid grid-cols-1 gap-x-8 gap-y-2 text-sm sm:grid-cols-2">
              <div className="flex gap-2">
                <dt className="shrink-0 text-gray-500 dark:text-gray-400">대상:</dt>
                <dd className="font-medium text-gray-800 dark:text-white/90">
                  {selectedProject?.name ?? "-"}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt className="shrink-0 text-gray-500 dark:text-gray-400">규칙/서브넷:</dt>
                <dd className="font-medium text-gray-800 dark:text-white/90">
                  {report ? `${report.rule_count}건 / ${report.subnet_count}개` : "-"}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt className="shrink-0 text-gray-500 dark:text-gray-400">심각도:</dt>
                <dd className="font-medium text-gray-800 dark:text-white/90">
                  CRITICAL {severityCounts.CRITICAL} · MAJOR {severityCounts.MAJOR} · MINOR{" "}
                  {severityCounts.MINOR}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt className="shrink-0 text-gray-500 dark:text-gray-400">금지 쌍:</dt>
                <dd className="font-medium text-gray-800 dark:text-white/90">
                  {forbiddenPairs.length}건
                </dd>
              </div>
            </dl>

            {/* 위반 테이블 */}
            <div className="mt-5 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-gray-200 text-xs uppercase tracking-wide text-gray-500 dark:border-gray-800 dark:text-gray-400">
                    <th className="px-3 py-2 font-medium">규칙</th>
                    <th className="px-3 py-2 font-medium">심각도</th>
                    <th className="px-3 py-2 font-medium">출발 → 도착</th>
                    <th className="px-3 py-2 font-medium">사유</th>
                    <th className="px-3 py-2 font-medium">반례 패킷</th>
                  </tr>
                </thead>
                <tbody>
                  {violations.map((v, index) => (
                    <tr
                      key={`${v.rule_id}-${index}`}
                      className="border-b border-gray-100 last:border-0 dark:border-gray-800/60"
                    >
                      <td className="px-3 py-2.5 font-medium text-gray-800 dark:text-white/90">
                        {v.rule_id}
                      </td>
                      <td className="px-3 py-2.5">
                        <Badge size="sm" color={SEVERITY_COLOR[v.severity]}>
                          {v.severity}
                        </Badge>
                      </td>
                      <td className="px-3 py-2.5 text-gray-600 dark:text-gray-400">
                        {v.src_class ?? "?"} → {v.dst_class ?? "?"}
                      </td>
                      <td className="max-w-[360px] px-3 py-2.5 text-gray-600 dark:text-gray-400">
                        {v.reason}
                      </td>
                      <td className="whitespace-nowrap px-3 py-2.5 font-mono text-xs text-gray-600 dark:text-gray-400">
                        {v.sampled_packet}
                      </td>
                    </tr>
                  ))}
                  {violationsResult.loading && (
                    <tr>
                      <td colSpan={5} className="px-3 py-8 text-center text-sm text-gray-400">
                        불러오는 중...
                      </td>
                    </tr>
                  )}
                  {!violationsResult.loading && report && violations.length === 0 && (
                    <tr>
                      <td colSpan={5} className="px-3 py-8 text-center text-sm text-gray-400">
                        위반이 없습니다. 내보낼 항목이 비어 있습니다.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </>
  );
}
