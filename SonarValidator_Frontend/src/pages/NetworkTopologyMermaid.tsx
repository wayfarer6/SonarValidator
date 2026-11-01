import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import MermaidDiagram from "../components/common/MermaidDiagram";
import { useApi } from "../hooks/useApi";
import { getTopology } from "../lib/api";
import { listProjects } from "../lib/api/projects";
import { topologyToMermaid } from "../lib/topology/mermaid";


interface NetworkTopologyMermaidProps {
  projectId?: string | null;
  hideSelector?: boolean;
}

export default function NetworkTopologyMermaid({
  projectId,
  hideSelector = false,
}: NetworkTopologyMermaidProps) {
  const [searchParams] = useSearchParams();
  const queryProjectId = projectId ?? searchParams.get("project_id");

  const projects = useApi(() => listProjects(), []);
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);

  const activeProjectId = useMemo(() => {
    if (queryProjectId) return queryProjectId;
    if (selectedProjectId) return selectedProjectId;
    return projects.data?.projects[0]?.project_id ?? null;
  }, [queryProjectId, selectedProjectId, projects.data]);

  const topology = useApi(
    () => (activeProjectId ? getTopology(activeProjectId) : Promise.resolve(null)),
    [activeProjectId],
    2000,
  );

  const chart = useMemo(() => topologyToMermaid(topology.data), [topology.data]);

  const quarantinedCount = useMemo(
    () => (topology.data?.nodes ?? []).filter((node) => node.quarantined).length,
    [topology.data],
  );

  const showSelector =
    !hideSelector && !queryProjectId && (projects.data?.projects.length ?? 0) > 0;

  return (
    <div className="flex flex-col gap-3">
      {/* 프로젝트 선택 + 새로고침 */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        {showSelector ? (
          <label className="flex items-center gap-2 text-xs text-gray-600 dark:text-gray-300">
            프로젝트
            <select
              value={activeProjectId ?? ""}
              onChange={(event) => setSelectedProjectId(event.target.value || null)}
              className="rounded-lg border border-gray-300 bg-white px-2 py-1 text-xs text-gray-800 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
            >
              {(projects.data?.projects ?? []).map((project) => (
                <option key={project.project_id} value={project.project_id}>
                  {project.name}
                </option>
              ))}
            </select>
          </label>
        ) : (
          <span className="text-xs text-gray-500 dark:text-gray-400">
            {topology.data
              ? `${topology.data.project_name} · 서브넷 ${topology.data.nodes.filter((node) => node.kind !== "device" && node.kind !== "internet").length}개 · 규칙 ${topology.data.edges.length}개`
              : ""}
          </span>
        )}

        <button
          type="button"
          onClick={() => topology.reload()}
          className="rounded-lg border border-gray-300 bg-white px-2.5 py-1 text-xs font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
        >
          새로고침
        </button>
      </div>

      {/* 등급 범례 */}
      <div className="flex flex-wrap gap-2 text-[11px] font-medium">
        <span className="rounded bg-red-100 px-2 py-0.5 text-red-700 dark:bg-red-900/40 dark:text-red-300">
          Confidential
        </span>
        <span className="rounded bg-purple-100 px-2 py-0.5 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300">
          Sensitive
        </span>
        <span className="rounded bg-green-100 px-2 py-0.5 text-green-700 dark:bg-green-900/40 dark:text-green-300">
          Open
        </span>
        <span className="rounded bg-white px-2 py-0.5 text-gray-500 ring-1 ring-gray-300 dark:bg-gray-800 dark:text-gray-400 dark:ring-gray-600">
          굵은 빨간 선 = 등급 건너뛰기(금지)
        </span>
        <span className="rounded border border-dashed border-red-900 bg-red-200 px-2 py-0.5 text-red-900 dark:bg-red-900/60 dark:text-red-100">
          빨간 점선 = 격리 적용 확인
        </span>
      </div>

      {/* 격리 안내 — 격리된 장치는 연결이 끊긴 것처럼 보이므로 미리 설명합니다. */}
      {quarantinedCount > 0 && (
        <p className="rounded-lg border border-red-200 bg-red-50 px-2.5 py-1.5 text-[11px] text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
          서브넷 {quarantinedCount}개가 격리 중입니다. 장비가 차단 적용을 확인한 상태입니다. 인터페이스 차단은 관측 연결선에,
          ACL·방화벽 차단은 연결선의 차단 표시에 반영됩니다.
        </p>
      )}

      <p className="text-xs text-gray-500" role="status" data-testid="topology-refresh-status">
        {topology.error ? `갱신 실패 · 마지막 수신 결과 표시: ${topology.error}`
          : `2초마다 자동 갱신 · ${topology.updatedAt ? new Date(topology.updatedAt).toLocaleTimeString("ko-KR") : "대기 중"}`}
      </p>
      {/* 본문 */}
      <div className="rounded-xl bg-white py-4 dark:bg-gray-900">
        {topology.error && !topology.data ? (
          <p className="p-4 text-center text-xs text-gray-500 dark:text-gray-400">
            {topology.error}
          </p>
        ) : projects.loading || topology.loading ? (
          <p className="p-4 text-center text-xs text-gray-400">불러오는 중...</p>
        ) : !activeProjectId ? (
          <p className="p-4 text-center text-xs text-gray-400">
            프로젝트가 없습니다. 프로젝트를 먼저 생성하세요.
          </p>
        ) : (
          <MermaidDiagram chart={chart} />
        )}
      </div>
    </div>
  );
}
