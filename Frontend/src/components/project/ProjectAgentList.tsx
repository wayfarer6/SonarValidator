import { lazy, Suspense, useState } from "react";
import { useApi } from "../../hooks/useApi";
import { deleteExpectedAgent, listAgentOverview } from "../../lib/api";

const AgentTerminal = lazy(() => import("./AgentTerminal"));

export default function ProjectAgentList({ projectId }: { projectId: string }) {
  const { data, loading, error, reload } = useApi(
    () => listAgentOverview(projectId),
    [projectId],
  );
  const [removing, setRemoving] = useState<string | null>(null);
  const [removeError, setRemoveError] = useState<string | null>(null);
  const [terminalAgentId, setTerminalAgentId] = useState<string | null>(null);

  const projectAgents = (data?.agents ?? []).filter(
    (agent) => agent.expected && agent.project_id === projectId,
  );

  const removeAgent = async (agentId: string) => {
    if (
      !window.confirm(
        `${agentId}를 이 프로젝트의 Agent 목록에서 제거할까요?\n실행 중인 연결과 수집 이력은 유지됩니다.`,
      )
    ) {
      return;
    }

    setRemoving(agentId);
    setRemoveError(null);
    try {
      await deleteExpectedAgent(agentId, projectId);
      reload();
    } catch (cause) {
      setRemoveError(
        cause instanceof Error ? cause.message : "프로젝트에서 Agent를 제거하지 못했습니다.",
      );
    } finally {
      setRemoving(null);
    }
  };

  return (
    <section className="mt-5 border-t border-gray-200 pt-4 dark:border-gray-700">
      <div className="mb-3 flex items-center gap-2">
        <h5 className="text-sm font-semibold text-gray-800 dark:text-white/90">Project Agents</h5>
        {!loading && (
          <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-800 dark:text-gray-300">
            {projectAgents.length}
          </span>
        )}
        <button
          type="button"
          onClick={reload}
          disabled={loading}
          className="ml-auto rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-gray-800 disabled:opacity-50 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-white"
          aria-label="Agent 목록 새로고침"
          title="Agent 목록 새로고침"
        >
          <svg viewBox="0 0 20 20" fill="none" className="h-4 w-4" aria-hidden="true">
            <path d="M16.5 8A6.75 6.75 0 0 0 4.8 5.3L3.5 7M3.5 7V3.8M3.5 7h3.2M3.5 12a6.75 6.75 0 0 0 11.7 2.7l1.3-1.7m0 0v3.2m0-3.2h-3.2" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
      </div>

      {loading && <p className="text-xs text-gray-500">Agent 목록을 불러오는 중...</p>}
      {error && (
        <p className="text-xs text-error-600 dark:text-error-400">
          Agent 목록을 불러오지 못했습니다: {error}
        </p>
      )}
      {removeError && (
        <p className="mb-2 text-xs text-error-600 dark:text-error-400">
          {removeError}
        </p>
      )}
      {!loading && !error && projectAgents.length === 0 && (
        <p className="text-xs text-gray-500 dark:text-gray-400">
          이 프로젝트에 등록된 Agent가 없습니다. Add Agent에서 Agent를 등록하고 다운로드하세요.
        </p>
      )}

      {projectAgents.length > 0 && (
        <ul className="divide-y divide-gray-100 dark:divide-gray-800">
          {projectAgents.map((agent) => (
            <li
              key={agent.agent_id}
              className="py-2"
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate font-mono text-sm text-gray-800 dark:text-gray-200">
                    {agent.agent_id}
                  </p>
                  <p className="text-xs text-gray-500 dark:text-gray-400">
                    {agent.device_type ?? agent.node_type ?? "장치"} ·{" "}
                    {agent.connected || agent.api_managed ? "온라인" : "오프라인"}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setTerminalAgentId(
                      terminalAgentId === agent.agent_id ? null : agent.agent_id,
                    )}
                    disabled={!agent.connected}
                    className="rounded-lg border border-brand-300 px-3 py-1.5 text-xs font-medium text-brand-600 hover:bg-brand-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-brand-500/40 dark:text-brand-400 dark:hover:bg-brand-500/10"
                    title={agent.connected ? "Agent 셸 터미널 열기" : "연결된 Agent에서만 열 수 있습니다"}
                  >
                    {terminalAgentId === agent.agent_id ? "터미널 닫기" : "터미널 열기"}
                  </button>
                  <button
                    type="button"
                    onClick={() => void removeAgent(agent.agent_id)}
                    disabled={removing !== null}
                    className="rounded-lg border border-error-300 px-3 py-1.5 text-xs font-medium text-error-600 hover:bg-error-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-error-500/40 dark:text-error-400 dark:hover:bg-error-500/10"
                  >
                    {removing === agent.agent_id ? "제거 중..." : "프로젝트에서 제거"}
                  </button>
                </div>
              </div>
              {terminalAgentId === agent.agent_id && (
                <Suspense fallback={<p className="mt-3 text-xs text-gray-400">터미널 로딩 중...</p>}>
                  <AgentTerminal
                    projectId={projectId}
                    agentId={agent.agent_id}
                    onClose={() => setTerminalAgentId(null)}
                  />
                </Suspense>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
