import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import MermaidDiagram from "../components/common/MermaidDiagram";
import { useApi } from "../hooks/useApi";
import { getTopology } from "../lib/api";
import { listProjects } from "../lib/api/projects";
import { topologyToMermaid } from "../lib/topology/mermaid";

/**
 * 프로젝트의 실제 서브넷/규칙으로 그리는 CSO 토폴로지입니다.
 *
 * <h2>더미 데이터를 걷어낸 자리</h2>
 * 이전에는 {@code 192.168.1.0/24} ~ {@code 192.168.4.0/24} 와 H1~H4/SW1~SW4/R1~R4
 * 같은 가짜 장비를 문자열로 박아 넣고 그렸습니다. 이제는 서버가 판정한
 * <b>실제</b> 토폴로지를 그립니다. 그림의 근거가 되는 값은 오직 서버 응답입니다:
 * <ul>
 *   <li>{@code GET /api/v1/network/topology/{projectId}} → 노드(서브넷) + 간선(규칙)</li>
 *   <li>등급(level)은 서버가 서브넷 CIDR 과 프로젝트 등급을 맞춰 계산합니다.</li>
 *   <li>금지 조합 여부({@code forbidden})도 서버가 판정합니다. 프론트는 색만 칠합니다.</li>
 * </ul>
 * 즉 이 화면은 <b>판정하지 않고 표시만</b> 합니다.
 */

interface NetworkTopologyMermaidProps {
  /**
   * 그릴 프로젝트 키. 넘기지 않으면 {@code ?project_id=} 쿼리스트링을 읽고,
   * 그것도 없으면 프로젝트 선택 드롭다운을 보여줍니다.
   */
  projectId?: string | null;
  /** 상단 프로젝트 선택 UI 를 숨길지 여부. (마법사 단계에서 이미 정해진 경우) */
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

  /**
   * 그릴 프로젝트를 정합니다.
   * 우선순위: prop > 쿼리스트링 > 사용자가 드롭다운에서 고른 값 > 목록 첫 번째.
   */
  const activeProjectId = useMemo(() => {
    if (queryProjectId) return queryProjectId;
    if (selectedProjectId) return selectedProjectId;
    return projects.data?.projects[0]?.project_id ?? null;
  }, [queryProjectId, selectedProjectId, projects.data]);

  const topology = useApi(
    () => (activeProjectId ? getTopology(activeProjectId) : Promise.resolve(null)),
    [activeProjectId],
  );

  const chart = useMemo(() => topologyToMermaid(topology.data), [topology.data]);

  /**
   * 격리 중인 서브넷 개수입니다.
   *
   * <p>0 이면 범례/안내를 숨깁니다. 항상 보이면 "격리 기능이 있긴 한가" 를
   * 매번 읽어야 하고, 진짜 격리가 생겼을 때 눈에 띄지 않습니다.
   */
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
              ? `${topology.data.project_name} · 서브넷 ${topology.data.nodes.length}개 · 규칙 ${topology.data.edges.length}개`
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
          🛑 점선 = 격리 중
        </span>
      </div>

      {/* 격리 안내 — 격리된 장치는 연결이 끊긴 것처럼 보이므로 미리 설명합니다. */}
      {quarantinedCount > 0 && (
        <p className="rounded-lg border border-red-200 bg-red-50 px-2.5 py-1.5 text-[11px] text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
          서브넷 {quarantinedCount}개가 격리 중입니다. 해당 장치는 관리 경로를 제외한 모든
          데이터 인터페이스가 내려가 있어 <b>연결이 끊긴 것처럼</b> 보입니다.
        </p>
      )}

      {/* 본문 */}
      <div className="rounded-xl bg-white py-4 dark:bg-gray-900">
        {topology.error ? (
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