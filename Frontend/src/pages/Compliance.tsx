import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import { useApi } from "../hooks/useApi";
import { getDiscoveredDevices, listComplianceChanges } from "../lib/api";
import type { ApiComplianceChange } from "../lib/api/types";
import { listProjects } from "../lib/api/projects";
import { deviceViews, formatTimestamp } from "../lib/agentView";

const STATUS_COLOR: Record<
  ApiComplianceChange["status"],
  "success" | "warning" | "error"
> = {
  Applied: "success",
  Pending: "warning",
  Rejected: "error",
};

const DEVICE_COLOR: Record<string, "info" | "success" | "error" | "primary"> = {
  Router: "info",
  Switch: "success",
  Firewall: "error",
  VM: "primary",
};

export default function Compliance() {
  const navigate = useNavigate();

  const projects = useApi(() => listProjects(), []);
  const projectList = useMemo(
    () => projects.data?.projects ?? [],
    [projects.data],
  );

  // 기본 선택: 가장 최근 프로젝트. 서버 목록이 바뀌면 자동으로 교정합니다.
  const latestProjectId = useMemo(
    () =>
      [...projectList].sort((a, b) =>
        (b.created_at ?? "").localeCompare(a.created_at ?? ""),
      )[0]?.project_id ?? null,
    [projectList],
  );

  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [selectedAgentId, setSelectedAgentId] = useState<string | null>(null);

  // 서버 응답이 처음 도착하면 기본 프로젝트를 선택합니다.
  useEffect(() => {
    if (selectedProjectId === null && latestProjectId) {
      setSelectedProjectId(latestProjectId);
    }
  }, [latestProjectId, selectedProjectId]);

  const selectedProject =
    projectList.find((p) => p.project_id === selectedProjectId) ?? null;

  // 프로젝트에 속한 장치를 서버에서 받아옵니다(수집 장치 기준).
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

  // 현재 선택(프로젝트 or Agent)에 따른 변경 내역 — 서버 조회
  const changes = useApi(
    () =>
      selectedProjectId
        ? listComplianceChanges({
            projectId: selectedProjectId,
            agentId: selectedAgentId ?? undefined,
          })
        : Promise.resolve(null),
    [selectedProjectId, selectedAgentId],
  );
  const changeList = changes.data?.changes ?? [];

  const handleSelectProject = (id: string) => {
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
              {projectList.map((project) => {
                const active = project.project_id === selectedProjectId;
                return (
                  <li key={project.project_id}>
                    <button
                      onClick={() => handleSelectProject(project.project_id)}
                      className={`flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left text-sm font-medium transition ${
                        active
                          ? "bg-brand-50 text-brand-500 dark:bg-brand-500/15 dark:text-brand-400"
                          : "text-gray-700 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-white/5"
                      }`}
                    >
                      <span className="truncate">{project.name}</span>
                      <span className="shrink-0 text-xs text-gray-400 dark:text-gray-500">
                        {project.subnet_count} subnet
                      </span>
                    </button>
                  </li>
                );
              })}

              {projects.loading && (
                <li className="px-3 py-2 text-xs text-gray-400">불러오는 중...</li>
              )}
              {!projects.loading && projectList.length === 0 && (
                <li className="px-3 py-2 text-xs text-gray-400">
                  프로젝트가 없습니다.
                </li>
              )}
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
                    ? `${selectedAgent.hostname} 변경 내역`
                    : selectedProject
                      ? `${selectedProject.name} 변경 내역`
                      : "프로젝트를 선택하세요"}
                </h3>
                <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                  {selectedAgent
                    ? `Agent ${selectedAgent.agentId} · ${selectedAgent.primaryIp}`
                    : selectedProject
                      ? `Project ${selectedProject.name} · 변경 ${changeList.length}건`
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
                      key={agent.agentId}
                      onClick={() => setSelectedAgentId(agent.agentId)}
                      className="flex items-center justify-between gap-2 rounded-xl border border-gray-200 px-3 py-2 text-left transition hover:border-brand-300 hover:bg-brand-50/50 dark:border-gray-800 dark:hover:border-brand-500/40 dark:hover:bg-white/5"
                    >
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium text-gray-800 dark:text-white/90">
                          {agent.hostname}
                        </span>
                        <span className="block truncate text-xs text-gray-500 dark:text-gray-400">
                          {agent.agentId} · {agent.primaryIp}
                        </span>
                      </span>
                      <Badge size="sm" color={DEVICE_COLOR[agent.deviceType] ?? "info"}>
                        {agent.deviceType}
                      </Badge>
                    </button>
                  ))}
                  {devices.loading && (
                    <p className="col-span-full text-xs text-gray-400">
                      장치를 불러오는 중...
                    </p>
                  )}
                  {!devices.loading && projectAgents.length === 0 && (
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
                  {changeList.map((change) => (
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
                      <td className="max-w-[320px] px-3 py-2.5 text-gray-600 dark:text-gray-400">
                        <span className="line-clamp-2">{change.summary}</span>
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
                  {changes.loading && (
                    <tr>
                      <td
                        colSpan={7}
                        className="px-3 py-8 text-center text-sm text-gray-400"
                      >
                        변경 내역을 불러오는 중...
                      </td>
                    </tr>
                  )}
                  {!changes.loading && changeList.length === 0 && (
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
