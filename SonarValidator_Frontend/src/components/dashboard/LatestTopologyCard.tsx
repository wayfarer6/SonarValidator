import { GlobeIcon } from "../../icons";
import MermaidDiagram from "../common/MermaidDiagram";
import {
  MOCK_AGENTS,
  buildTopologyChart,
  getLatestProject,
} from "../../lib/mockData";

// 가장 최근에 생성된 프로젝트의 토폴로지 카드
export default function LatestTopologyCard() {
  const latest = getLatestProject();
  const agents = MOCK_AGENTS.filter((agent) => agent.projectId === latest?.id);
  const chart = buildTopologyChart(agents);

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
            <p className="text-xs text-gray-500 dark:text-gray-400">
              {latest ? (
                <>
                  {latest.name} ·{" "}
                  {new Date(latest.createdAt).toLocaleDateString("ko-KR")}
                </>
              ) : (
                "No projects"
              )}
            </p>
          </div>
        </div>
      </div>

      {/* 장비 타입 범례 */}
      <div className="mb-2 flex flex-wrap gap-2 text-[11px] font-medium">
        <span className="rounded bg-red-100 px-2 py-0.5 text-red-700 dark:bg-red-900/40 dark:text-red-300">Firewall</span>
        <span className="rounded bg-blue-100 px-2 py-0.5 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300">Router</span>
        <span className="rounded bg-green-100 px-2 py-0.5 text-green-700 dark:bg-green-900/40 dark:text-green-300">Switch</span>
        <span className="rounded bg-purple-100 px-2 py-0.5 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300">VM</span>
      </div>

      <div className="min-h-[260px] flex-1 rounded-xl border border-gray-100 bg-gray-50/60 p-2 dark:border-gray-800 dark:bg-gray-900/40">
        <MermaidDiagram chart={chart} className="py-2" />
      </div>
    </div>
  );
}
