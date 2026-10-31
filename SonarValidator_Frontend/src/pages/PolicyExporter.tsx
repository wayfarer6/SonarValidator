import PolicyAnalysisScope from "../components/project/PolicyAnalysisScope";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import { useApi } from "../hooks/useApi";
import { getPolicyViolations, getPolicyForbiddenPairs } from "../lib/api";
import { getProject, listProjects } from "../lib/api/projects";
import type { ViolationSeverity } from "../lib/api/types";


const SEVERITY_COLOR: Record<ViolationSeverity, "error" | "warning" | "info"> = {
  CRITICAL: "error",
  MAJOR: "warning",
  MINOR: "info",
};

function exportJsonFile(filename: string, payload: unknown): void {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
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
  const projectDetails = useApi(
    () => (selectedProjectId ? getProject(selectedProjectId) : Promise.resolve(null)),
    [selectedProjectId],
  );

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
  const subnetCidr = (subnetId: string | null) =>
    projectDetails.data?.subnets.find((subnet) => subnet.id === subnetId)?.cidr ?? null;

  const severityCounts = useMemo(() => {
    return {
      CRITICAL: report?.by_severity?.CRITICAL ?? 0,
      MAJOR: report?.by_severity?.MAJOR ?? 0,
      MINOR: report?.by_severity?.MINOR ?? 0,
    };
  }, [report]);

  const fileBase = `policy-${selectedProject?.name ?? selectedProjectId ?? "report"}`;

  const handleExport = () => {
    if (!report) return;
    const payload = {
      project_id: report.project_id,
      project_name: report.project_name,
      generated_at: new Date().toISOString(),
      compliant: report.compliant,
      model_valid: report.model_valid,
      analysis_scope: report.analysis_scope,
      data_plane_analyzed: report.data_plane_analyzed,
      input_errors: report.input_errors,
      rule_count: report.rule_count,
      subnet_count: report.subnet_count,
      violation_count: report.violation_count,
      by_severity: severityCounts,
      messages: report.messages,
      metrics: report.metrics,
      forbidden_pairs: forbiddenPairs.map((pair) => ({
        ...pair,
        src_cidr: subnetCidr(pair.src),
        dst_cidr: subnetCidr(pair.dst),
      })),
      violations: violations.map((v) => ({
        rule_id: v.rule_id,
        src_subnet: v.src_subnet,
        src_cidr: subnetCidr(v.src_subnet),
        dst_subnet: v.dst_subnet,
        dst_cidr: subnetCidr(v.dst_subnet),
        src_class: v.src_class,
        dst_class: v.dst_class,
        severity: v.severity,
        reason: v.reason,
        sampled_protocol: v.sampled_protocol,
        sampled_packet: v.sampled_packet,
        evidence_type: v.evidence_type,
        sampled_src_ip: v.sampled_src_ip,
        sampled_dst_ip: v.sampled_dst_ip,
        sampled_port: v.sampled_port,
      })),
    };
    exportJsonFile(`${fileBase}.json`, payload);
  };

  const isExporting =
    violationsResult.loading || forbiddenResult.loading || projectDetails.loading;

  return (
    <>
      <PageMeta
        title="Export Policy | SonarValidator"
        description="정책 위반 연결의 IP 대역, 프로토콜, 포트와 판정 사유 내보내기"
      />
      <PageBreadcrumb pageTitle="Export Policy" />

      <div className="mb-6 rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">
              내보낼 프로젝트 선택
            </h3>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
              정책 위반 연결의 출발·도착 IP 대역과 실제 IP, 프로토콜·포트, 판정 사유를 추출합니다.
              JSON 파일에는 금지 서브넷 쌍도 포함됩니다.
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
            onClick={handleExport}
            data-testid="export-policy-file"
            disabled={!report || isExporting}
            className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white shadow-theme-xs transition hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isExporting ? "불러오는 중..." : "파일로 내보내기"}
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
              {report.compliant ? "정책 모델 검증 통과" : `검토 ${report.violation_count}건`}
            </Badge>
          )}
        </div>

        {report && <PolicyAnalysisScope report={report} />}
        {violationsResult.error || projectDetails.error ? (
          <p className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
            {violationsResult.error ?? projectDetails.error}
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
                        {subnetCidr(v.src_subnet) ?? v.src_subnet ?? "?"} →{" "}
                        {subnetCidr(v.dst_subnet) ?? v.dst_subnet ?? "?"}
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
