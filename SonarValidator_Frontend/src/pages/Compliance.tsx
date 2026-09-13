import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import {
  MOCK_AGENTS,
  MOCK_PROJECTS,
  formatTimestamp,
  getChangesByAgent,
  getChangesByProject,
  type ComplianceChange,
  type DeviceType,
} from "../lib/mockData";

const STATUS_COLOR: Record<
  ComplianceChange["status"],
  "success" | "warning" | "error"
> = {
  Applied: "success",
  Pending: "warning",
  Rejected: "error",
};

const DEVICE_COLOR: Record<DeviceType, "info" | "success" | "error" | "primary"> =
  {
    Router: "info",
    Switch: "success",
    Firewall: "error",
    VM: "primary",
  };

export default function Compliance() {
  const navigate = useNavigate();

  // 기본 선택: 가장 최근 프로젝트
  const latestProjectId = useMemo(
    () =>
      [...MOCK_PROJECTS].sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]
        ?.id ?? null,
    [],
  );

  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(
    latestProjectId,
  );
  const [selectedAgentId, setSelectedAgentId] = useState<string | null>(null);

  const selectedProject =
    MOCK_PROJECTS.find((p) => p.id === selectedProjectId) ?? null;
  const projectAgents = useMemo(
    () => MOCK_AGENTS.filter((a) => a.projectId === selectedProjectId),
    [selectedProjectId],
  );
  const selectedAgent =
    projectAgents.find((a) => a.id === selectedAgentId) ?? null;

  // 현재 선택(프로젝트 or Agent)에 따른 변경 내역
  const changes = useMemo(() => {
    if (selectedAgentId) return getChangesByAgent(selectedAgentId);
    if (selectedProjectId !== null) return getChangesByProject(selectedProjectId);
    return [];
  }, [selectedProjectId, selectedAgentId]);

  const handleSelectProject = (id: number) => {
    setSelectedProjectId(id);
    setSelectedAgentId(null);
  };

  // Agent 선택 → Agent 목록으로, Agent 미선택 → 프로젝트 미선택 상태로
  const handleBack = () => {
    if (selectedAgentId) setSelectedAgentId(null);
    else setSelectedProjectId(null);
  };

  // PDF 추출은 별도 라우트(/compliance/export)에서 수행
  const handleExport = () => {
    navigate("/compliance/export", {
      state: { projectId: selectedProjectId, agentId: selectedAgentId },
    });
  };

  return (
    <>
      <PageMeta
        title="Compliance | SonarValidator"
        description="Project / Agent 단위 네트워크 설정 변경 내역 조회"
      />
      <PageBreadcrumb pageTitle="Compliance" />

      <div className="grid grid-cols-12 gap-6">
        {/* 왼쪽: Project 리스트 */}
        <div className="col-span-12 lg:col-span-4 xl:col-span-3">
          <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
            <h3 className="mb-4 text-lg font-semibold text-gray-800 dark:text-white/90">
              Project Name
            </h3>
            <ul className="flex flex-col gap-1">
              {MOCK_PROJECTS.map((project) => {
                const active = project.id === selectedProjectId;
                return (
                  <li key={project.id}>
                    <button
                      onClick={() => handleSelectProject(project.id)}
                      className={`flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left text-sm font-medium transition ${
                        active
                          ? "bg-brand-50 text-brand-500 dark:bg-brand-500/15 dark:text-brand-400"
                          : "text-gray-700 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-white/5"
                      }`}
                    >
                      <span className="truncate">{project.name}</span>
                      <span className="shrink-0 text-xs text-gray-400 dark:text-gray-500">
                        #{project.id}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
        </div>

        {/* 오른쪽: Agent 리스트 + 변경 내역 */}
        <div className="col-span-12 lg:col-span-8 xl:col-span-9">
          <div className="flex flex-col gap-6 rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
            {/* 헤더 */}
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                  {selectedAgent
                    ? `${selectedAgent.name} 변경 내역`
                    : selectedProject
                      ? `${selectedProject.name} 변경 내역`
                      : "프로젝트를 선택하세요"}
                </h3>
                <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                  {selectedAgent
                    ? `Agent ${selectedAgent.id} · ${selectedAgent.ip}`
                    : selectedProject
                      ? `Project #${selectedProject.id} · 변경 ${changes.length}건`
                      : "왼쪽 목록에서 프로젝트를 선택하면 Agent 목록이 표시됩니다."}
                </p>
              </div>
              <Badge
                color={selectedAgent ? "primary" : "info"}
                size="sm"
              >
                {selectedAgent ? "Agent 단위" : "프로젝트 단위"}
              </Badge>
            </div>

            {/* Agent 리스트 (프로젝트 선택 & Agent 미선택 시) */}
            {selectedProject && !selectedAgent && (
              <div>
                <h4 className="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-300">
                  Agent List
                </h4>
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-3">
                  {projectAgents.map((agent) => (
                    <button
                      key={agent.id}
                      onClick={() => setSelectedAgentId(agent.id)}
                      className="flex items-center justify-between gap-2 rounded-xl border border-gray-200 px-3 py-2 text-left transition hover:border-brand-300 hover:bg-brand-50/50 dark:border-gray-800 dark:hover:border-brand-500/40 dark:hover:bg-white/5"
                    >
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium text-gray-800 dark:text-white/90">
                          {agent.name}
                        </span>
                        <span className="block truncate text-xs text-gray-500 dark:text-gray-400">
                          {agent.id} · {agent.ip}
                        </span>
                      </span>
                      <Badge size="sm" color={DEVICE_COLOR[agent.deviceType]}>
                        {agent.deviceType}
                      </Badge>
                    </button>
                  ))}
                  {projectAgents.length === 0 && (
                    <p className="col-span-full rounded-lg bg-gray-100 px-3 py-2 text-sm text-gray-500 dark:bg-white/5 dark:text-gray-400">
                      이 프로젝트에 등록된 Agent가 없습니다.
                    </p>
                  )}
                </div>
              </div>
            )}

            {/* 변경 내역 테이블 */}
            <div className="overflow-x-auto">
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
                        {change.scope === "Agent" ? change.agentId : "Project"}
                      </td>
                      <td className="px-3 py-2.5 text-gray-600 dark:text-gray-400">
                        {change.type}
                      </td>
                      <td className="max-w-[320px] px-3 py-2.5 text-gray-600 dark:text-gray-400">
                        <span className="line-clamp-2">{change.summary}</span>
                      </td>
                      <td className="px-3 py-2.5 text-gray-600 dark:text-gray-400">
                        {change.changedBy}
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
                  {changes.length === 0 && (
                    <tr>
                      <td
                        colSpan={7}
                        className="px-3 py-8 text-center text-sm text-gray-500 dark:text-gray-400"
                      >
                        {selectedProjectId === null
                          ? "왼쪽에서 프로젝트를 선택하세요."
                          : "변경 내역이 없습니다."}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* 하단 버튼: 이전 / PDF 내보내기 */}
            <div className="flex items-center justify-end gap-3 border-t border-gray-200 pt-4 dark:border-gray-800">
              <button
                onClick={handleBack}
                disabled={selectedProjectId === null}
                className="rounded-lg bg-white px-4 py-2.5 text-sm font-medium text-gray-700 ring-1 ring-inset ring-gray-300 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-gray-800 dark:text-gray-400 dark:ring-gray-700 dark:hover:bg-white/[0.03]"
              >
                이전
              </button>
              <button
                onClick={handleExport}
                disabled={selectedProjectId === null}
                className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white shadow-theme-xs transition hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
              >
                PDF 내보내기
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
