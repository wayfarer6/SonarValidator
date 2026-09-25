import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import { useApi } from "../hooks/useApi";
import { getDiscoveredDevices, listComplianceChanges } from "../lib/api";
import type { ApiComplianceChange } from "../lib/api/types";
import { listProjects } from "../lib/api/projects";
import { deviceViews, formatTimestamp } from "../lib/agentView";
import { exportComplianceReportPdf } from "../lib/pdf/compliancePdf";

const STATUS_COLOR: Record<
  ApiComplianceChange["status"],
  "success" | "warning" | "error"
> = {
  Applied: "success",
  Pending: "warning",
  Rejected: "error",
};

export default function ComplianceExporter() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  const projects = useApi(() => listProjects(), []);
  const projectList = useMemo(
    () => projects.data?.projects ?? [],
    [projects.data],
  );

  // /compliance 에서 전달받은 선택값(state) → 쿼리스트링 → 최신 프로젝트 순 폴백
  const navState = (location.state ?? {}) as {
    projectId?: string | null;
    agentId?: string | null;
  };
  const initialProjectId =
    navState.projectId ?? searchParams.get("project_id") ?? null;
  const initialAgentId =
    navState.agentId ?? searchParams.get("agent_id") ?? null;

  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(
    initialProjectId,
  );
  const [selectedAgentId, setSelectedAgentId] = useState<string | null>(
    initialAgentId,
  );

  // 선택값이 없으면 서버 목록에서 가장 최근 프로젝트로 채웁니다.
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

  const devices = useApi(
    () =>
      selectedProjectId
        ? getDiscoveredDevices(selectedProjectId)
        : Promise.resolve(null),
    [selectedProjectId],
  );
  const projectAgents = useMemo(
    () => deviceViews(devices.data?.devices ?? []),
    [devices.data],
  );
  const selectedAgent =
    projectAgents.find((a) => a.agentId === selectedAgentId) ?? null;

  const changesResult = useApi(
    () =>
      selectedProjectId
        ? listComplianceChanges({
            projectId: selectedProjectId,
            agentId: selectedAgentId ?? undefined,
          })
        : Promise.resolve(null),
    [selectedProjectId, selectedAgentId],
  );
  const changes = useMemo(
    () => changesResult.data?.changes ?? [],
    [changesResult.data],
  );

  const counts = useMemo(() => {
    const base = { total: changes.length, Applied: 0, Pending: 0, Rejected: 0 };
    changes.forEach((c) => {
      base[c.status] += 1;
    });
    return base;
  }, [changes]);

  const generatedAt = formatTimestamp(new Date().toISOString());

  const handleProjectChange = (value: string) => {
    setSelectedProjectId(value === "" ? null : value);
    setSelectedAgentId(null);
  };

  const [isExporting, setIsExporting] = useState(false);

  // 화면에 렌더링된 보고서와 동일한 데이터를 jsPDF로 직접 그려 PDF 파일 생성
  // (DOM 캡처 방식이 아니라 실제 텍스트 기반 → 선택·검색 가능, 파일 작음)
  const handleExportPdf = async () => {
    if (isExporting) return;
    setIsExporting(true);
    try {
      await exportComplianceReportPdf({
        project: selectedProject,
        agent: selectedAgent,
        changes,
        counts,
        generatedAt,
      });
    } catch (error) {
      console.error("PDF 생성 실패:", error);
      // TODO: 토스트/알림 컴포넌트로 오류 표시
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <>
      <PageMeta
        title="Export Compliance | SonarValidator"
        description="네트워크 설정 변경 내역 PDF 추출"
      />
      <PageBreadcrumb pageTitle="Export Compliance" />

      {/* 컨트롤 */}
      <div className="mb-6 rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">
              PDF 추출 대상 선택
            </h3>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
              프로젝트 단위 또는 Agent 단위로 변경 내역 보고서를 추출합니다.
            </p>
          </div>
          <div className="flex flex-wrap items-end gap-3">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                Project
              </label>
              <select
                value={selectedProjectId ?? ""}
                onChange={(e) => handleProjectChange(e.target.value)}
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
            <div>
              <label className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                Agent (선택)
              </label>
              <select
                value={selectedAgentId ?? ""}
                onChange={(e) =>
                  setSelectedAgentId(e.target.value === "" ? null : e.target.value)
                }
                disabled={!selectedProject}
                className="h-11 rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-800 focus:outline-hidden focus:ring-3 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"
              >
                <option value="">프로젝트 전체</option>
                {projectAgents.map((a) => (
                  <option key={a.agentId} value={a.agentId}>
                    {a.hostname} ({a.agentId})
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        <div className="mt-5 flex items-center justify-end gap-3 border-t border-gray-200 pt-4 dark:border-gray-800">
          <button
            onClick={() => navigate("/compliance")}
            className="rounded-lg bg-white px-4 py-2.5 text-sm font-medium text-gray-700 ring-1 ring-inset ring-gray-300 transition hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-400 dark:ring-gray-700 dark:hover:bg-white/[0.03]"
          >
            이전
          </button>
          <button
            onClick={handleExportPdf}
            disabled={selectedProjectId === null || isExporting}
            className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white shadow-theme-xs transition hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isExporting ? "PDF 생성 중..." : "PDF 내보내기"}
          </button>
        </div>
      </div>

      {/* PDF 추출 대상 보고서 */}
      <div
        id="compliance-report"
        className="rounded-2xl border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-white/[0.03] lg:p-8"
      >
        {/* 보고서 헤더 */}
        <div className="border-b border-gray-200 pb-5 dark:border-gray-800">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="text-xl font-bold text-gray-800 dark:text-white/90">
                SonarValidator Compliance Report
              </h2>
              <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                네트워크 설정 변경 내역 보고서
              </p>
            </div>
            <Badge color="primary" size="sm">
              {selectedAgent ? "Agent 단위" : "프로젝트 단위"}
            </Badge>
          </div>
          <dl className="mt-4 grid grid-cols-1 gap-x-8 gap-y-2 text-sm sm:grid-cols-2">
            <div className="flex gap-2">
              <dt className="shrink-0 text-gray-500 dark:text-gray-400">대상:</dt>
              <dd className="font-medium text-gray-800 dark:text-white/90">
                {selectedAgent
                  ? `${selectedAgent.hostname} (${selectedAgent.agentId})`
                  : selectedProject
                    ? selectedProject.name
                    : "-"}
              </dd>
            </div>
            <div className="flex gap-2">
              <dt className="shrink-0 text-gray-500 dark:text-gray-400">
                생성 일시:
              </dt>
              <dd className="font-medium text-gray-800 dark:text-white/90">
                {generatedAt}
              </dd>
            </div>
            {selectedAgent && (
              <div className="flex gap-2">
                <dt className="shrink-0 text-gray-500 dark:text-gray-400">
                  IP / 대역:
                </dt>
                <dd className="font-medium text-gray-800 dark:text-white/90">
                  {selectedAgent.primaryIp}
                </dd>
              </div>
            )}
            <div className="flex gap-2">
              <dt className="shrink-0 text-gray-500 dark:text-gray-400">
                변경 건수:
              </dt>
              <dd className="font-medium text-gray-800 dark:text-white/90">
                {counts.total}건 (Applied {counts.Applied} · Pending{" "}
                {counts.Pending} · Rejected {counts.Rejected})
              </dd>
            </div>
          </dl>
        </div>

        {/* 변경 내역 테이블 */}
        <div className="mt-5 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 text-xs uppercase tracking-wide text-gray-500 dark:border-gray-800 dark:text-gray-400">
                <th className="px-3 py-2 font-medium">ID</th>
                <th className="px-3 py-2 font-medium">구분</th>
                <th className="px-3 py-2 font-medium">유형</th>
                <th className="px-3 py-2 font-medium">변경 요약</th>
                <th className="px-3 py-2 font-medium">요청자</th>
                <th className="px-3 py-2 font-medium">일시</th>
                <th className="px-3 py-2 font-medium">상태</th>
              </tr>
            </thead>
            <tbody>
              {changes.map((change) => (
                <tr
                  key={change.id}
                  className="border-b border-gray-100 last:border-0 dark:border-gray-800/60"
                >
                  <td className="px-3 py-2.5 font-medium text-gray-800 dark:text-white/90">
                    {change.id}
                  </td>
                  <td className="px-3 py-2.5 text-gray-600 dark:text-gray-400">
                    {change.scope === "Agent" ? change.agent_id : "Project"}
                  </td>
                  <td className="px-3 py-2.5 text-gray-600 dark:text-gray-400">
                    {change.type}
                  </td>
                  <td className="max-w-[360px] px-3 py-2.5 text-gray-600 dark:text-gray-400">
                    {change.summary}
                  </td>
                  <td className="px-3 py-2.5 text-gray-600 dark:text-gray-400">
                    {change.changed_by}
                  </td>
                  <td className="whitespace-nowrap px-3 py-2.5 text-gray-600 dark:text-gray-400">
                    {formatTimestamp(change.timestamp)}
                  </td>
                  <td className="px-3 py-2.5">
                    <Badge size="sm" color={STATUS_COLOR[change.status]}>
                      {change.status}
                    </Badge>
                  </td>
                </tr>
              ))}
              {changesResult.loading && (
                <tr>
                  <td
                    colSpan={7}
                    className="px-3 py-8 text-center text-sm text-gray-400"
                  >
                    변경 내역을 불러오는 중...
                  </td>
                </tr>
              )}
              {!changesResult.loading && changes.length === 0 && (
                <tr>
                  <td
                    colSpan={7}
                    className="px-3 py-8 text-center text-sm text-gray-500 dark:text-gray-400"
                  >
                    추출할 변경 내역이 없습니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* 보고서 푸터 */}
        <p className="mt-6 border-t border-gray-200 pt-4 text-xs text-gray-400 dark:border-gray-800 dark:text-gray-500">
          본 보고서는 SonarValidator가 생성한 네트워크 설정 변경 이력입니다. ·
          Generated {generatedAt}
        </p>
      </div>
    </>
  );
}
