import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Button from "../components/ui/button/Button";
import { useApi } from "../hooks/useApi";
import { useApiAction } from "../hooks/useApiAction";
import {
  getPolicyViolations,
  listQuarantined,
  pushPolicy,
  releaseQuarantine,
} from "../lib/api";
import { listProjects } from "../lib/api/projects";
import type { ApiPolicyViolations, ViolationSeverity } from "../lib/api/types";

const SEVERITY_COLOR: Record<ViolationSeverity, "error" | "warning" | "info"> = {
  CRITICAL: "error",
  MAJOR: "warning",
  MINOR: "info",
};

/**
 * 정책 관리 화면입니다.
 *
 * <h2>스텁에서 실제 기능으로</h2>
 * 이 화면은 이전에 프로필 카드만 렌더링하는 25줄 스텁이었습니다. 대응하는
 * 백엔드 {@code PolicyManagement} 도 {@code isPolicyViolated() → false} 만 있는
 * 스텁이었습니다. 이제 양쪽 모두 실제 동작합니다.
 *
 * <h2>무엇을 보여주는가</h2>
 * <ol>
 *   <li><b>위반 현황</b>: 심각도별 집계 + 반례 패킷 (BDD 가 뽑은 재현 예시)</li>
 *   <li><b>금지 조합</b>: 어떤 서브넷 쌍이 애초에 금지인지</li>
 *   <li><b>정책 푸시</b>: 검증 통과 시에만 장치로 전송</li>
 * </ol>
 *
 * <p>위반을 없애는 작업은 프로젝트 편집 화면에서 합니다. 이 화면은 <b>현황
 * 파악과 적용</b>에 집중하고, "편집" 링크로 편집 화면을 안내합니다.
 *
 * <h2>⚠️ 격리 버튼이 Agent 화면에도 있는 이유</h2>
 * <p>같은 조치를 두 곳에 둔 것은 중복이 아니라 <b>동선</b> 때문입니다.
 * 위반을 발견한 자리(이 화면)에서 바로 조치할 수 있어야 하고,
 * 장치 상태를 보는 자리(Agent 화면)에서도 조치할 수 있어야 합니다.
 * 둘 다 같은 API 를 부르고 서버가 진실을 가지므로 어긋나지 않습니다.
 */
export default function PolicyManagement() {
  const [searchParams] = useSearchParams();
  const projects = useApi(() => listProjects(), []);

  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(
    searchParams.get("project_id"),
  );

  // 프로젝트가 로드되면 첫 프로젝트를 기본 선택합니다.
  const activeProjectId = useMemo(() => {
    if (selectedProjectId) return selectedProjectId;
    return projects.data?.projects[0]?.project_id ?? null;
  }, [selectedProjectId, projects.data]);

  const violations = useApi(
    () =>
      activeProjectId
        ? getPolicyViolations(activeProjectId)
        : Promise.resolve(null as ApiPolicyViolations | null),
    [activeProjectId],
  );

  const pushAction = useApiAction((force: boolean) =>
    activeProjectId ? pushPolicy(activeProjectId, force) : Promise.resolve({}),
  );

  // 격리 중인 장치: 위반을 발견한 자리에서 바로 조치할 수 있게 합니다.
  const quarantine = useApi(
    () => (activeProjectId ? listQuarantined(activeProjectId) : Promise.resolve(null)),
    [activeProjectId],
  );
  /** 해제 진행 중인 Agent 식별자. */
  const [releasing, setReleasing] = useState<string | null>(null);
  /** 해제 결과/오류 메시지. */
  const [releaseNote, setReleaseNote] = useState<string | null>(null);

  /**
   * 격리를 해제합니다.
   *
   * <p>해제 후 <b>두 목록을 모두</b> 새로고침합니다. 격리 목록만 갱신하면
   * 위반 현황은 "격리됨" 을 반영하지 않은 채 남아 화면이 서로 모순됩니다.
   *
   * @param agentId 해제할 Agent
   */
  const handleRelease = async (agentId: string) => {
    if (releasing !== null) return;
    setReleasing(agentId);
    setReleaseNote(null);
    try {
      const result = await releaseQuarantine(agentId);
      setReleaseNote(
        // `released === false` 로만 "아니었다" 를 판정합니다.
        // 키가 없을 때(구버전 서버) 성공을 실패로 뒤집어 말하지 않도록.
        result.released === false
          ? `${agentId} 는 격리 중이 아니었습니다.`
          : `${agentId} 의 격리를 해제했습니다.`,
      );
      quarantine.reload();
      violations.reload();
    } catch (cause) {
      setReleaseNote(
        cause instanceof Error ? cause.message : "격리 해제에 실패했습니다.",
      );
    } finally {
      setReleasing(null);
    }
  };

  const quarantined = quarantine.data?.quarantined ?? [];

  const report = violations.data ?? null;

  return (
    <>
      <PageMeta
        title="Policy Management | SonarValidator"
        description="망분리 정책 위반 현황과 반례 패킷, 장치 적용"
      />
      <PageBreadcrumb pageTitle="Policy" />

      <div className="grid grid-cols-12 gap-6">
        {/* 왼쪽: 프로젝트 선택 */}
        <div className="col-span-12 lg:col-span-4 xl:col-span-3">
          <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
            <h3 className="mb-4 text-lg font-semibold text-gray-800 dark:text-white/90">
              Project
            </h3>

            {projects.loading && (
              <p className="text-sm text-gray-500 dark:text-gray-400">불러오는 중...</p>
            )}
            {projects.error && (
              <p className="text-xs text-error-500">{projects.error}</p>
            )}

            <ul className="flex flex-col gap-1">
              {(projects.data?.projects ?? []).map((project) => {
                const active = project.project_id === activeProjectId;
                return (
                  <li key={project.project_id}>
                    <button
                      onClick={() => setSelectedProjectId(project.project_id)}
                      className={`flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left text-sm font-medium transition ${
                        active
                          ? "bg-brand-50 text-brand-500 dark:bg-brand-500/15 dark:text-brand-400"
                          : "text-gray-700 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-white/5"
                      }`}
                    >
                      <span className="truncate">{project.name}</span>
                      <span className="shrink-0 text-xs text-gray-400">
                        {project.rule_count}규칙
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>

            {projects.data && projects.data.total === 0 && (
              <p className="text-xs text-gray-400">
                프로젝트가 없습니다. 프로젝트 목록에서 먼저 만드세요.
              </p>
            )}
          </div>
        </div>

        {/* 오른쪽: 위반 현황 */}
        <div className="col-span-12 lg:col-span-8 xl:col-span-9">
          <div className="flex flex-col gap-6 rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
            {!activeProjectId && (
              <p className="py-12 text-center text-sm text-gray-500 dark:text-gray-400">
                왼쪽에서 프로젝트를 선택하세요.
              </p>
            )}

            {activeProjectId && violations.loading && (
              <p className="py-12 text-center text-sm text-gray-500 dark:text-gray-400">
                위반 현황을 불러오는 중...
              </p>
            )}

            {activeProjectId && violations.error && (
              <div className="rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
                <p className="text-sm font-medium text-gray-800 dark:text-white/90">
                  위반 현황을 불러오지 못했습니다
                </p>
                <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">
                  {violations.error}
                </p>
                <Button
                  className="mt-3"
                  size="sm"
                  variant="outline"
                  onClick={violations.reload}
                >
                  다시 시도
                </Button>
              </div>
            )}

            {report && (
              <>
                {/* 격리 중인 장치 — 위반 목록보다 먼저 보여줍니다.
                    이미 조치한 것을 "아직 위반" 처럼 다시 보게 하지 않기 위함입니다. */}
                {quarantined.length > 0 && (
                  <div className="rounded-xl border border-red-200 bg-red-50 p-4 dark:border-red-500/30 dark:bg-red-500/10">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <h4 className="text-sm font-semibold text-red-700 dark:text-red-300">
                        🛑 격리 중인 장치 {quarantined.length}대
                      </h4>
                      <span className="text-[11px] text-red-600 dark:text-red-400">
                        관리 경로를 제외한 모든 데이터 인터페이스가 내려가 있습니다
                      </span>
                    </div>
                    <ul className="mt-3 space-y-2">
                      {quarantined.map((state) => (
                        <li
                          key={state.agent_id}
                          className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-white p-2.5 dark:bg-white/[0.03]"
                        >
                          <div className="min-w-0">
                            <span className="font-mono text-xs font-medium text-gray-800 dark:text-white/90">
                              {state.agent_id}
                            </span>
                            {state.reason && (
                              <span className="ml-2 text-[11px] text-gray-500 dark:text-gray-400">
                                {state.reason}
                              </span>
                            )}
                            <div className="mt-1 flex flex-wrap gap-1">
                              {/* 명령 전달 여부와 실제 적용 여부는 다른 사실입니다. */}
                              <Badge
                                size="sm"
                                color={state.command_delivered ? "success" : "warning"}
                              >
                                {state.command_delivered ? "명령 전달됨" : "명령 미전달"}
                              </Badge>
                              {state.applied === true && (
                                <Badge size="sm" color="success">
                                  장치 적용 확인
                                </Badge>
                              )}
                              {state.applied === false && (
                                <Badge size="sm" color="error">
                                  적용 실패
                                </Badge>
                              )}
                            </div>
                          </div>
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={releasing !== null}
                            onClick={() => handleRelease(state.agent_id)}
                          >
                            {releasing === state.agent_id ? "해제 중..." : "해제"}
                          </Button>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {releaseNote && (
                  <p className="rounded-lg border border-gray-200 bg-gray-50 p-2.5 text-xs text-gray-700 dark:border-gray-700 dark:bg-white/[0.03] dark:text-gray-300">
                    {releaseNote}
                  </p>
                )}

                {/* 헤더 + 요약 */}
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 pb-4 dark:border-gray-800">
                  <div>
                    <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                      {report.project_name} 위반 현황
                    </h3>
                    <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                      규칙 {report.rule_count}건 · 서브넷 {report.subnet_count}건 검사
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge size="sm" color={report.compliant ? "success" : "error"}>
                      {report.compliant ? "정책 준수" : `위반 ${report.violation_count}건`}
                    </Badge>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={violations.reload}
                    >
                      새로고침
                    </Button>
                    <Button
                      size="sm"
                      disabled={!report.compliant || pushAction.submitting}
                      onClick={() => pushAction.run(false)}
                      title={
                        report.compliant
                          ? "검증을 통과한 정책을 장치로 전송합니다"
                          : "위반이 있어 전송할 수 없습니다. 편집 화면에서 위반을 먼저 해결하세요."
                      }
                    >
                      {pushAction.submitting ? "전송 중..." : "정책 푸시"}
                    </Button>
                  </div>
                </div>

                {/* 심각도 집계 */}
                <div className="grid grid-cols-3 gap-3">
                  {(["CRITICAL", "MAJOR", "MINOR"] as ViolationSeverity[]).map((severity) => (
                    <div
                      key={severity}
                      className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800/50"
                    >
                      <div className="flex items-center justify-between">
                        <Badge size="sm" color={SEVERITY_COLOR[severity]}>
                          {severity}
                        </Badge>
                        <span className="text-title-sm font-bold text-gray-800 dark:text-white/90">
                          {report.by_severity[severity] ?? 0}
                        </span>
                      </div>
                      <p className="mt-2 text-[11px] text-gray-500 dark:text-gray-400">
                        {severity === "CRITICAL"
                          ? "등급을 건너뛰는 직접 연결 — 즉시 조치"
                          : severity === "MAJOR"
                            ? "포트 미지정 등 정책 약화 — 검토 필요"
                            : "참고 수준"}
                      </p>
                    </div>
                  ))}
                </div>

                {/* 메시지 */}
                {report.messages.length > 0 && (
                  <ul className="space-y-1 text-sm text-gray-700 dark:text-gray-200">
                    {report.messages.map((message) => (
                      <li key={message} className="flex items-start gap-2">
                        <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-current opacity-40" />
                        <span>{message}</span>
                      </li>
                    ))}
                  </ul>
                )}

                {/* 위반 목록 */}
                {report.violations.length > 0 ? (
                  <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-700">
                    <table className="min-w-full text-left text-xs">
                      <thead className="bg-gray-50 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
                        <tr>
                          <th className="border-b p-3 font-medium dark:border-gray-600">심각도</th>
                          <th className="border-b p-3 font-medium dark:border-gray-600">Rule</th>
                          <th className="border-b p-3 font-medium dark:border-gray-600">경로</th>
                          <th className="border-b p-3 font-medium dark:border-gray-600">사유</th>
                          <th className="border-b p-3 font-medium dark:border-gray-600">
                            반례 패킷
                          </th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-200 text-gray-600 dark:divide-gray-700 dark:text-gray-300">
                        {report.violations.map((violation, index) => (
                          <tr key={`${violation.rule_id}-${index}`}>
                            <td className="p-3">
                              <Badge size="sm" color={SEVERITY_COLOR[violation.severity]}>
                                {violation.severity}
                              </Badge>
                            </td>
                            <td className="p-3 font-mono">{violation.rule_id}</td>
                            <td className="p-3 font-mono">
                              {violation.src_subnet}
                              <br />
                              <span className="text-gray-400">
                                {violation.src_class} → {violation.dst_class}
                              </span>
                            </td>
                            <td className="p-3">{violation.reason}</td>
                            <td className="p-3 font-mono text-[11px]">
                              {violation.sampled_packet && violation.sampled_packet !== "- -> -"
                                ? violation.sampled_packet
                                : "—"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="flex flex-col items-center justify-center rounded-2xl border border-success-200 bg-success-50/50 py-10 text-center dark:border-success-500/30 dark:bg-success-500/10">
                    <p className="text-base font-medium text-gray-700 dark:text-gray-200">
                      위반이 없습니다
                    </p>
                    <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                      모든 연결 규칙이 등급 간 인접 규칙을 준수합니다.
                    </p>
                  </div>
                )}

                {/*
                  BDD 내부 지표(노드 수 / 조합 수)는 화면에서 뺐습니다.
                  운영자가 고칠 대상은 위반 테이블의 연결 경로이지,
                  판정 엔진이 몇 개 노드를 썼는지가 아닙니다.
                  엔진 수치는 서버 로그로 확인할 수 있습니다.
                */}

                {/* 푸시 결과 */}
                {pushAction.result && (
                  <div className="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
                    <h5 className="mb-2 text-xs font-semibold text-gray-700 dark:text-gray-200">
                      푸시 결과
                    </h5>
                    <pre className="overflow-x-auto rounded bg-gray-50 p-2 font-mono text-[11px] text-gray-700 dark:bg-gray-900 dark:text-gray-200">
                      {JSON.stringify(pushAction.result, null, 2)}
                    </pre>
                  </div>
                )}

                {pushAction.error && (
                  <p className="text-xs text-error-500">{pushAction.error}</p>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </>
  );
}
