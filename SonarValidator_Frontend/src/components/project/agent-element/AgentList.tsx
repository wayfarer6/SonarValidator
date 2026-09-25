import { BoxIconLine, GroupIcon } from "../../../icons";
import { useApi } from "../../../hooks/useApi";
import { listAgents } from "../../../lib/api";
import { listProjects } from "../../../lib/api/projects";

/**
 * 대시보드 상단 지표 카드입니다.
 *
 * <h2>하드코딩 상수를 걷어낸 자리</h2>
 * 이전에는 Projects=3, Connected Agents=30 이 코드에 박혀 있었고
 * "나중에 API 로 교체" 라는 주석만 남아 있었습니다. 이제 서버에서 실제 값을
 * 받아옵니다. 값이 0 이면 0 을 그대로 보여줍니다 — 0 을 가리는 것이 가장
 * 흔한 거짓말입니다.
 */
export default function AgentList() {
  const projects = useApi(() => listProjects(), []);
  const agents = useApi(() => listAgents(), []);

  // 로딩 중에는 숫자 대신 점을 보여 "0" 으로 오해하지 않게 합니다.
  const projectsValue = projects.loading ? "…" : (projects.data?.total ?? "–");
  const connectedValue = agents.loading ? "…" : (agents.data?.connected ?? "–");

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:gap-6">
      {/* <!-- Metric Item Start --> */}
      <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] md:p-6">
        <div className="flex items-center justify-center w-12 h-12 bg-gray-100 rounded-xl dark:bg-gray-800">
          <GroupIcon className="text-gray-800 size-6 dark:text-white/90" />
        </div>

        <div className="flex items-end justify-between mt-5">
          <div>
            <span className="text-sm text-gray-500 dark:text-gray-400">
              Projects
            </span>
            <h4 className="mt-2 font-bold text-gray-800 text-title-sm dark:text-white/90">
              {projectsValue}
            </h4>
          </div>
        </div>
      </div>
      {/* <!-- Metric Item End --> */}

      {/* <!-- Metric Item Start --> */}
      <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] md:p-6">
        <div className="flex items-center justify-center w-12 h-12 bg-gray-100 rounded-xl dark:bg-gray-800">
          <BoxIconLine className="text-gray-800 size-6 dark:text-white/90" />
        </div>
        <div className="flex items-end justify-between mt-5">
          <div>
            <span className="text-sm text-gray-500 dark:text-gray-400">
              Connected Agents 
            </span>
            <h4 className="mt-2 font-bold text-gray-800 text-title-sm dark:text-white/90">
              {connectedValue}
            </h4>
          </div>
        </div>
      </div>
      {/* <!-- Metric Item End --> */}
    </div>
  );
}
