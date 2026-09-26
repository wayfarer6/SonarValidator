import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Button from "../components/ui/button/Button";
import MermaidDiagram from "../components/common/MermaidDiagram";
import { useApi } from "../hooks/useApi";
import { listRouteTables, getTopology } from "../lib/api";
import { listProjects } from "../lib/api/projects";
import type { ApiTopology } from "../lib/api/types";
import { topologyToMermaid } from "../lib/topology/mermaid";

/**
 * 네트워크 관리 화면입니다.
 *
 * <h2>스텁에서 실제 기능으로</h2>
 * 이 화면은 이전에 "View Network" 제목만 있는 25줄 스텁이었습니다. 이제
 * 프로젝트의 서브넷/규칙을 토폴로지로 그리고, 장치별 라우팅 테이블을 함께
 * 보여줍니다.
 *
 * <h2>토폴로지를 Mermaid 로 그리는 이유</h2>
 * 기존 화면들({@code NetworkTopologyMermaid}, {@code TopologyRulePreview})이
 * 이미 Mermaid 를 쓰고 있습니다. 같은 표현 방식을 유지해야 화면 간 인지
 * 부담이 없고, Export 기능도 그대로 쓸 수 있습니다.
 *
 * <h2>노드 모양으로 등급을 구분하는 이유</h2>
 * 색만 다르면 흑백 인쇄나 색각 이상에서 구분이 어렵습니다. 등급별로
 * <b>모양</b>까지 다르게 해서 색 없이도 읽히게 했습니다.
 *
 * <h2>변환 로직을 공용 모듈로 옮긴 이유</h2>
 * 같은 변환이 {@code lib/topology/mermaid.ts} 에도 있습니다. 두 벌을 두면
 * 등급 색/모양 규칙이 조금씩 어긋나 "같은 데이터인데 다른 그림" 이 됩니다.
 * 이제는 공용 함수만 호출합니다.
 */
export default function NetworkManagement() {
  const [searchParams] = useSearchParams();
  const projects = useApi(() => listProjects(), []);
  const routes = useApi(() => listRouteTables(), []);

  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(
    searchParams.get("project_id"),
  );

  const activeProjectId = useMemo(() => {
    if (selectedProjectId) return selectedProjectId;
    return projects.data?.projects[0]?.project_id ?? null;
  }, [selectedProjectId, projects.data]);

  const topology = useApi(
    () =>
      activeProjectId
        ? getTopology(activeProjectId)
        : Promise.resolve(null as ApiTopology | null),
    [activeProjectId],
  );

  /** 토폴로지를 Mermaid flowchart 소스로 변환합니다. (공용 모듈) */
  const chart = useMemo(() => topologyToMermaid(topology.data), [topology.data]);

  const deviceRoutes = routes.data?.devices ?? [];

  return (
    <>
      <PageMeta
        title="Network Management | SonarValidator"
        description="프로젝트 토폴로지와 장치별 라우팅 테이블"
      />
      <PageBreadcrumb pageTitle="Networks" />

      <div className="space-y-6">
        {/* 프로젝트 선택 */}
        <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">
              View Network
            </h3>
            <div className="ml-auto flex flex-wrap gap-2">
              {(projects.data?.projects ?? []).map((project) => (
                <button
                  key={project.project_id}
                  onClick={() => setSelectedProjectId(project.project_id)}
                  className={`rounded-lg px-3 py-1.5 text-xs font-medium transition ${
                    project.project_id === activeProjectId
                      ? "bg-brand-500 text-white"
                      : "border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200"
                  }`}
                >
                  {project.name}
                </button>
              ))}
              {projects.data && projects.data.total === 0 && (
                <span className="text-xs text-gray-400">프로젝트가 없습니다</span>
              )}
            </div>
          </div>
        </div>

        {/* 토폴로지 */}
        <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <h4 className="text-sm font-semibold text-gray-800 dark:text-white/90">
              Network Topology (CSO Security Zones)
            </h4>
            {topology.data && (
              <div className="flex flex-wrap gap-2 text-[11px]">
                <span className="rounded bg-red-100 px-2 py-0.5 text-red-800 dark:bg-red-900/40 dark:text-red-300">
                  Confidential — 기밀망
                </span>
                <span className="rounded bg-orange-100 px-2 py-0.5 text-orange-800 dark:bg-orange-900/40 dark:text-orange-300">
                  Sensitive — 내부 업무망
                </span>
                <span className="rounded bg-blue-100 px-2 py-0.5 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300">
                  Open — 공개망
                </span>
              </div>
            )}
          </div>

          {!activeProjectId && (
            <p className="py-12 text-center text-sm text-gray-500 dark:text-gray-400">
              프로젝트를 선택하면 토폴로지가 표시됩니다.
            </p>
          )}

          {activeProjectId && topology.loading && (
            <p className="py-12 text-center text-sm text-gray-500 dark:text-gray-400">
              토폴로지를 불러오는 중...
            </p>
          )}

          {activeProjectId && topology.error && (
            <div className="rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
              <p className="text-sm text-gray-700 dark:text-gray-200">{topology.error}</p>
              <Button className="mt-3" size="sm" variant="outline" onClick={topology.reload}>
                다시 시도
              </Button>
            </div>
          )}

          {topology.data && (
            <>
              <div className="min-h-[300px] rounded-lg border border-gray-100 bg-gray-50/60 p-3 dark:border-gray-700 dark:bg-gray-900/40">
                <MermaidDiagram chart={chart} />
              </div>

              {topology.data.edges.some((edge) => edge.forbidden) && (
                <p className="mt-3 rounded-lg bg-error-50 px-3 py-2 text-xs text-error-700 dark:bg-error-500/15 dark:text-error-300">
                  빨간 굵은 화살표는 등급을 건너뛰는 직접 연결입니다. 프로젝트 편집
                  화면에서 해당 규칙을 수정하세요.
                  {activeProjectId && (
                    <Link
                      to={`/project/editor/${encodeURIComponent(activeProjectId)}`}
                      className="ml-1 underline"
                    >
                      편집으로 이동
                    </Link>
                  )}
                </p>
              )}

              {/* 노드/간선 요약 */}
              <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
                <Stat label="서브넷" value={topology.data.nodes.length} />
                <Stat label="연결 규칙" value={topology.data.edges.length} />
                <Stat
                  label="금지 연결"
                  value={topology.data.edges.filter((edge) => edge.forbidden).length}
                />
                <Stat
                  label="포트 미지정"
                  value={topology.data.edges.filter((edge) => edge.port === null).length}
                />
              </div>
            </>
          )}
        </div>

        {/* 라우팅 테이블 */}
        <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <h4 className="text-sm font-semibold text-gray-800 dark:text-white/90">
              라우팅 테이블 (수집된 장치)
            </h4>
            <Button size="sm" variant="outline" onClick={routes.reload}>
              새로고침
            </Button>
          </div>

          {routes.loading && (
            <p className="py-8 text-center text-sm text-gray-500 dark:text-gray-400">
              라우팅 테이블을 불러오는 중...
            </p>
          )}

          {routes.error && (
            <p className="text-xs text-error-500">{routes.error}</p>
          )}

          {!routes.loading && !routes.error && deviceRoutes.length === 0 && (
            <p className="py-8 text-center text-sm text-gray-500 dark:text-gray-400">
              수집된 라우팅 테이블이 없습니다. 라우터 Agent 가 연결되면 표시됩니다.
            </p>
          )}

          {deviceRoutes.length > 0 && (
            <div className="space-y-4">
              {deviceRoutes.map((device) => {
                // 경로가 많은 라우터는 접어 둡니다.
                const interesting = device.routes.filter(
                  (route) => route.default_route || !route.selected,
                );
                const shown = interesting.length > 0 ? interesting : device.routes.slice(0, 20);
                return (
                  <details
                    key={device.agent_id}
                    className="rounded-lg border border-gray-200 dark:border-gray-700"
                    open={deviceRoutes.length === 1}
                  >
                    <summary className="cursor-pointer px-4 py-3 text-sm font-medium text-gray-800 dark:text-gray-200">
                      {device.hostname ?? device.agent_id}
                      <span className="ml-2 text-xs font-normal text-gray-400">
                        {device.product ?? ""} · 경로 {device.route_count}건
                      </span>
                    </summary>
                    <div className="overflow-x-auto border-t border-gray-200 p-3 dark:border-gray-700">
                      <table className="min-w-full text-left text-[11px]">
                        <thead className="text-gray-600 dark:text-gray-300">
                          <tr>
                            <th className="p-2 font-medium">프로토콜</th>
                            <th className="p-2 font-medium">대역</th>
                            <th className="p-2 font-medium">Next Hop</th>
                            <th className="p-2 font-medium">인터페이스</th>
                            <th className="p-2 font-medium">선택됨</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100 font-mono text-gray-600 dark:divide-gray-800 dark:text-gray-300">
                          {shown.map((route, index) => (
                            <tr key={`${device.agent_id}-${index}`}>
                              <td className="p-2">
                                {route.default_route ? (
                                  <Badge size="sm" color="info">
                                    default
                                  </Badge>
                                ) : (
                                  (route.protocol ?? "—")
                                )}
                              </td>
                              <td className="p-2">{route.prefix ?? "—"}</td>
                              <td className="p-2">{route.next_hop ?? "직접 연결"}</td>
                              <td className="p-2">{route.next_hop_interface ?? "—"}</td>
                              <td className="p-2">{route.selected ? "✓" : ""}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                      {shown.length < device.routes.length && (
                        <p className="mt-2 text-[10px] text-gray-400">
                          기본 경로와 미선택 경로만 표시했습니다. (전체 {device.routes.length}건)
                        </p>
                      )}
                    </div>
                  </details>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </>
  );
}

/** 작은 통계 표시입니다. */
function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-3 text-center dark:border-gray-700 dark:bg-gray-800/50">
      <p className="text-[11px] text-gray-500 dark:text-gray-400">{label}</p>
      <p className="mt-1 text-lg font-bold text-gray-800 dark:text-white/90">{value}</p>
    </div>
  );
}
