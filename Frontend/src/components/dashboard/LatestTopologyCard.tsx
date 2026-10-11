import { useMemo } from "react";
import { GlobeIcon } from "../../icons";
import MermaidDiagram from "../common/MermaidDiagram";
import { useApi } from "../../hooks/useApi";
import { getTopology } from "../../lib/api";
import { listProjects } from "../../lib/api/projects";
import { topologyToMermaid } from "../../lib/topology/mermaid";

/**
 * 가장 최근에 생성된 프로젝트의 토폴로지 카드입니다.
 *
 * <h2>더미 데이터를 걷어낸 자리</h2>
 * 이전에는 mockData 의 프로젝트/에이전트 배열을 걸러 그림을 그렸습니다.
 * 이제 서버의 프로젝트 목록에서 가장 최근 것을 고르고, 그 프로젝트의 실제
 * 서브넷/규칙을 getTopology 로 받아 그립니다.
 */

/** 생성 시각 내림차순 첫 번째를 돌려줍니다. */
function pickLatest<T extends { created_at: string | null }>(
  items: T[],
): T | null {
  if (items.length === 0) return null;
  return [...items].sort((a, b) =>
    (b.created_at ?? "").localeCompare(a.created_at ?? ""),
  )[0];
}

export default function LatestTopologyCard() {
  const projects = useApi(() => listProjects(), []);
  const latest = useMemo(
    () => pickLatest(projects.data?.projects ?? []),
    [projects.data],
  );

  const topology = useApi(
    () => (latest ? getTopology(latest.project_id) : Promise.resolve(null)),
    [latest?.project_id],
  );

  const chart = useMemo(() => topologyToMermaid(topology.data), [topology.data]);

  const subtitle = latest
    ? `${latest.name}${latest.created_at ? ` · ${new Date(latest.created_at).toLocaleDateString("ko-KR")}` : ""}`
    : projects.loading
      ? "불러오는 중..."
      : "No projects";

  return (
    <div className="flex h-full flex-col rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] md:p-6">
      <div className="mb-1 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800">
            <GlobeIcon className="size-5 text-gray-700 dark:text-white/90" />
          </div>
          <div>
            <h4 className="text-base font-semibold text-gray-800 dark:text-white/90">
              Latest Project Topology
            </h4>
            <p className="text-xs text-gray-500 dark:text-gray-400">{subtitle}</p>
          </div>
        </div>
      </div>

      {/* 등급 범례 (서버 legend 와 같은 순서) */}
      <div className="mb-2 flex flex-wrap gap-2 text-[11px] font-medium">
        <span className="rounded bg-red-100 px-2 py-0.5 text-red-700 dark:bg-red-900/40 dark:text-red-300">Confidential</span>
        <span className="rounded bg-purple-100 px-2 py-0.5 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300">Sensitive</span>
        <span className="rounded bg-green-100 px-2 py-0.5 text-green-700 dark:bg-green-900/40 dark:text-green-300">Open</span>
      </div>

      <div className="min-h-[260px] flex-1 rounded-xl border border-gray-100 bg-gray-50/60 p-2 dark:border-gray-800 dark:bg-gray-900/40">
        {topology.error ? (
          <p className="p-4 text-center text-xs text-gray-500 dark:text-gray-400">
            {topology.error}
          </p>
        ) : projects.loading || topology.loading ? (
          <p className="p-4 text-center text-xs text-gray-400">불러오는 중...</p>
        ) : !latest ? (
          <p className="p-4 text-center text-xs text-gray-400">
            프로젝트가 없습니다. 프로젝트를 먼저 생성하세요.
          </p>
        ) : (
          <MermaidDiagram chart={chart} className="py-2" />
        )}
      </div>
    </div>
  );
}
