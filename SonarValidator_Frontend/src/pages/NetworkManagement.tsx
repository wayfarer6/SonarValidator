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
import CollectedSubnetsPanel from "../components/network/CollectedSubnetsPanel";

export default function NetworkManagement() {
  const [searchParams] = useSearchParams();
  const projects = useApi(() => listProjects(), []);
  const routes = useApi(() => listRouteTables(), [], 2000);

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
    2000,
  );

  const chart = useMemo(() => topologyToMermaid(topology.data), [topology.data]);

  const deviceRoutes = routes.data?.devices ?? [];

  return (
    <>
      <PageMeta
        title="Network Management | SonarValidator"
        description="프로젝트 토폴로지와 장치별 라우팅 테이블"
      />
      <PageBreadcrumb pageTitle="네트워크" />

      <div className="space-y-6">
        <p role="status" data-testid="topology-refresh-status" className="text-xs text-gray-500">
          {topology.error ? `갱신 실패 · 마지막 수신 결과 표시: ${topology.error}` : "토폴로지 · 2초마다 자동 갱신"}
        </p>
        {/* 프로젝트 선택 */}
        <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">
              프로젝트 선택
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
              네트워크 연결 상태
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

              {topology.data.nodes.length === 0 && (
                <p className="mt-3 rounded-lg bg-gray-50 px-3 py-2.5 text-xs text-gray-600 dark:bg-white/[0.03] dark:text-gray-300">
                  이 프로젝트에 등록된 <b>서브넷이 없어</b> 그릴 것이 없습니다. 서브넷은 장치에서
                  수집된 대역이 자동으로 반영되거나, 아래 편집 화면에서 직접 추가합니다.
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

              {/* 간선 종류 범례 — 실선 규칙과 점선 관측 연결을 구분합니다. */}
              <p className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
                <span className="inline-flex items-center gap-1.5">
                  <svg width="34" height="8" aria-hidden="true">
                    <line x1="0" y1="4" x2="34" y2="4" stroke="#6b7280" strokeWidth="2" />
                  </svg>
                  정책 규칙
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <svg width="34" height="8" aria-hidden="true">
                    <line x1="0" y1="4" x2="34" y2="4" stroke="#64748b" strokeWidth="2" strokeDasharray="5 4" />
                  </svg>
                  수집된 경로·VLAN 연결 (통신 성공 여부는 별도 검사)
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <svg width="34" height="8" aria-hidden="true">
                    <line x1="0" y1="4" x2="34" y2="4" stroke="#2563eb" strokeWidth="2" strokeDasharray="5 4" />
                  </svg>
                  인터넷 연결 (원형 = Internet)
                </span>
              </p>

              {/* 노드/간선 요약 */}
              <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
                <Stat label="서브넷" value={topology.data.nodes.filter((node) => node.kind === "subnet").length} />
                <Stat
                  label="연결 규칙"
                  value={topology.data.edges.filter((edge) => edge.observed !== true).length}
                />
                <Stat
                  label="수집된 연결"
                  value={topology.data.edges.filter((edge) => edge.observed === true).length}
                />
                <Stat
                  label="금지 연결"
                  value={topology.data.edges.filter((edge) => edge.forbidden).length}
                />
              </div>
            </>
          )}
        </div>

        {/* 수집된 IP 대역 — 토폴로지가 비었을 때 이유를 가리킵니다. */}
        <CollectedSubnetsPanel
          projectId={activeProjectId}
          topology={topology.data}
          onRefreshTopology={topology.reload}
        />

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

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-3 text-center dark:border-gray-700 dark:bg-gray-800/50">
      <p className="text-[11px] text-gray-500 dark:text-gray-400">{label}</p>
      <p className="mt-1 text-lg font-bold text-gray-800 dark:text-white/90">{value}</p>
    </div>
  );
}
