import { useCallback, useEffect, useState } from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Button from "../components/ui/button/Button";
import AiProviderSettings from "../components/ai/AiProviderSettings";
import { useApi } from "../hooks/useApi";
// 프로젝트 목록은 projects 모듈에 있습니다. (index.ts 는 조회 API 만 모아 둔 곳)
import { listProjects } from "../lib/api/projects";
import type { ApiProjectSummary } from "../lib/api/types";
import {
  analyzeLogs,
  getLogAnalysis,
  getLogFilterOptions,
  ingestLogs,
  listEnabledAiProviders,
  listLogAnalyses,
  listLogs,
  riskColor,
  severityColor,
  updateLogFlags,
  type ApiDeviceLog,
  type ApiLogAnalysis,
  type ApiLogQuery,
} from "../lib/api/aiLogs";
import { ApiError } from "../lib/api/client";

/**
 * 로그 관리 + AI 분석 화면입니다.
 *
 * <h2>화면 구성</h2>
 * <pre>
 *   ┌─ 필터 바 ────────────────────────────────────────────┐
 *   │ 프로젝트 · 장비 · 기간(from~to) · 심각도 · 검색어      │
 *   ├─ 요약 ─────────────────────────────────────────────┤
 *   │ 심각도별 건수 · 전체 건수 · 잘림 경고                  │
 *   ├─ 로그 목록 ────────────────────────────────────────┤
 *   │ [선택] 시각 | agent | 심각도 | 메시지                  │
 *   ├─ AI 분석 ─────────────────────────────────────────┤
 *   │ 공급자 선택 · 추가 지시 · [선택 로그 분석] [필터 전체 분석]│
 *   │ → 결과: 위험도 · 요약 · 원인 · 권장조치                │
 *   └────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>⚠️ 사용자가 반드시 알아야 하는 3가지</h2>
 * <ol>
 *   <li><b>잘림(truncated)</b> — 로그는 상한이 있어 전부 보이지 않을 수 있습니다.
 *       "일부만 분석했다" 를 표시하지 않으면 사용자가 결론을 과신합니다.</li>
 *   <li><b>분석 실패도 200</b> — 서버가 실패를 200 + {@code succeeded=false} 로
 *       돌려줍니다. HTTP 상태만 보면 실패를 성공으로 오해합니다.</li>
 *   <li><b>공급자 미등록</b> — 분석을 누르기 전에 공급자가 있는지 확인해
 *       안내합니다. (없으면 서버가 사유를 돌려주지만, 미리 아는 편이 낫습니다)</li>
 * </ol>
 */
export default function LogManagement() {
  // ---------------------------------------------------------------------------
  // 필터 상태
  // ---------------------------------------------------------------------------

  const [projectId, setProjectId] = useState("");
  const [agentId, setAgentId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [severity, setSeverity] = useState("warning");
  const [search, setSearch] = useState("");
  const [highlightedOnly, setHighlightedOnly] = useState(false);

  /** 실제 조회에 쓰이는 필터입니다. [조회] 를 눌러야 반영됩니다. */
  const [applied, setApplied] = useState<ApiLogQuery>({
    severity: "warning",
    limit: 200,
  });

  const projects = useApi(() => listProjects(), []);
  const filterOptions = useApi(() => getLogFilterOptions(), []);
  const providers = useApi(() => listEnabledAiProviders(), []);

  const logs = useApi(() => listLogs(applied), [applied]);

  /** 선택한 로그 ID 집합입니다. */
  const [selected, setSelected] = useState<Set<number>>(new Set());

  const [providerId, setProviderId] = useState<number | "">("");
  const [prompt, setPrompt] = useState("");

  const [analysis, setAnalysis] = useState<ApiLogAnalysis | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [analysisError, setAnalysisError] = useState<string | null>(null);

  /** 수집(붙여넣기) 영역. */
  const [showIngest, setShowIngest] = useState(false);
  const [showProviders, setShowProviders] = useState(false);

  // 공급자 목록이 오면 기본 공급자를 미리 선택해 둡니다.
  useEffect(() => {
    if (providerId !== "" || !providers.data) return;
    const preset =
      providers.data.providers.find((p) => p.is_default) ??
      providers.data.providers[0];
    if (preset) setProviderId(preset.id);
  }, [providers.data, providerId]);

  /** 조회를 실행합니다. */
  const applyFilters = useCallback(() => {
    setSelected(new Set());
    setApplied({
      projectId: projectId || null,
      agentId: agentId || null,
      from: toIso(from, false),
      to: toIso(to, true),
      severity: severity || null,
      search: search || null,
      highlightedOnly,
      limit: 200,
    });
  }, [projectId, agentId, from, to, severity, search, highlightedOnly]);

  /** 필터를 초기화합니다. */
  const resetFilters = useCallback(() => {
    setProjectId("");
    setAgentId("");
    setFrom("");
    setTo("");
    setSeverity("warning");
    setSearch("");
    setHighlightedOnly(false);
    setSelected(new Set());
    setApplied({ severity: "warning", limit: 200 });
  }, []);

  const rows = logs.data?.logs ?? [];

  /** 전체 선택/해제 상태입니다. */
  const allSelected = rows.length > 0 && rows.every((log) => selected.has(log.id));

  const toggleOne = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleAll = () => {
    setSelected(allSelected ? new Set() : new Set(rows.map((log) => log.id)));
  };

  // ---------------------------------------------------------------------------
  // AI 분석
  // ---------------------------------------------------------------------------

  /**
   * 분석을 실행합니다.
   *
   * <p>⚠️ 서버는 실패도 200 으로 돌려줍니다. 그래서 {@code succeeded} 를
   * 반드시 확인해야 합니다. 확인하지 않으면 실패를 성공으로 표시하게 됩니다.
   */
  const runAnalysis = async (scope: "selected" | "filter") => {
    if (providerId === "") {
      setAnalysisError("AI 공급자를 먼저 선택하세요.");
      return;
    }

    if (scope === "selected" && selected.size === 0) {
      setAnalysisError("분석할 로그를 목록에서 선택하세요.");
      return;
    }

    setAnalyzing(true);
    setAnalysisError(null);
    setAnalysis(null);

    try {
      const result = await analyzeLogs({
        log_ids: scope === "selected" ? [...selected] : undefined,
        agent_id: scope === "filter" ? (applied.agentId ?? null) : null,
        project_id: scope === "filter" ? (applied.projectId ?? null) : null,
        from: scope === "filter" ? (applied.from ?? null) : null,
        to: scope === "filter" ? (applied.to ?? null) : null,
        severity: scope === "filter" ? (applied.severity ?? null) : null,
        scope,
        provider_id: providerId,
        prompt: prompt || null,
      });

      setAnalysis(result);

      // 서버가 실패를 200 으로 돌려주므로 여기서 다시 확인합니다.
      if (!result.succeeded) {
        setAnalysisError(result.error_message ?? "분석에 실패했습니다.");
      }
    } catch (cause) {
      setAnalysisError(
        cause instanceof ApiError
          ? cause.message
          : cause instanceof Error
            ? cause.message
            : "분석 요청에 실패했습니다.",
      );
    } finally {
      setAnalyzing(false);
    }
  };

  /** 로그를 표시(highlighted) 상태로 토글합니다. */
  const toggleHighlight = async (log: ApiDeviceLog) => {
    try {
      await updateLogFlags(log.id, { highlighted: !log.highlighted });
      logs.reload();
    } catch (cause) {
      setAnalysisError(
        cause instanceof ApiError ? cause.message : "표시 변경에 실패했습니다.",
      );
    }
  };

  const enabledProviders = providers.data?.providers ?? [];

  return (
    <>
      <PageMeta
        title="Log Management | SonarValidator"
        description="장비 로그 조회와 AI 분석"
      />
      <PageBreadcrumb pageTitle="Log Management" />

      <div className="space-y-5">
        {/* 공급자 미등록 경고 — 분석을 누르기 전에 알려 줍니다. */}
        {!providers.loading && enabledProviders.length === 0 && (
          <div className="rounded-xl border border-warning-200 bg-warning-50 p-4 dark:border-warning-500/30 dark:bg-warning-500/10">
            <p className="text-sm font-medium text-gray-800 dark:text-white/90">
              AI 공급자가 등록되어 있지 않습니다
            </p>
            <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">
              로그 분석을 사용하려면 OpenAI 호환 API 공급자를 먼저 등록하세요.
              로컬 Ollama 라면 주소만 넣고 API Key 는 비워 두면 됩니다.
            </p>
            <Button
              className="mt-3"
              size="sm"
              onClick={() => setShowProviders(true)}
            >
              AI 공급자 설정 열기
            </Button>
          </div>
        )}

        {/* ─────────────── 필터 바 ─────────────── */}
        <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-gray-100 pb-3 dark:border-gray-800">
            <div className="flex items-center gap-2">
              <h3 className="text-base font-semibold text-gray-800 dark:text-white/90">
                로그 조회
              </h3>
              {logs.data && (
                <>
                  <Badge size="sm" color="light">
                    전체 {logs.data.total}건
                  </Badge>
                  <Badge size="sm" color="info">
                    표시 {logs.data.returned}건
                  </Badge>
                </>
              )}
            </div>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setShowIngest((prev) => !prev)}
            >
              {showIngest ? "직접 입력 닫기" : "로그 직접 입력"}
            </Button>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
            <FilterField label="프로젝트">
              <select
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
                className={selectClass}
              >
                <option value="">전체</option>
                {(projects.data?.projects ?? []).map((project: ApiProjectSummary) => (
                  <option key={project.project_id} value={project.project_id}>
                    {project.name} ({project.project_id})
                  </option>
                ))}
              </select>
            </FilterField>

            <FilterField label="Agent (장비)">
              <select
                value={agentId}
                onChange={(e) => setAgentId(e.target.value)}
                className={selectClass}
              >
                <option value="">전체</option>
                {(filterOptions.data?.agents ?? []).map((id) => (
                  <option key={id} value={id}>
                    {id}
                  </option>
                ))}
              </select>
            </FilterField>

            <FilterField label="심각도 (최소 등급)">
              <select
                value={severity}
                onChange={(e) => setSeverity(e.target.value)}
                className={selectClass}
              >
                <option value="">전체</option>
                {(filterOptions.data?.severities ?? []).map((item) => (
                  <option key={item.name} value={item.name}>
                    {item.name} 이상
                  </option>
                ))}
              </select>
            </FilterField>

            <FilterField label="기간 시작">
              <input
                type="datetime-local"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
                className={selectClass}
              />
            </FilterField>

            <FilterField label="기간 끝">
              <input
                type="datetime-local"
                value={to}
                onChange={(e) => setTo(e.target.value)}
                className={selectClass}
              />
            </FilterField>

            <FilterField label="본문 검색">
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={(e) => {
                  // 검색어는 Enter 로 바로 조회하는 것이 자연스럽습니다.
                  if (e.key === "Enter") applyFilters();
                }}
                placeholder="예: BGP, link down"
                className={selectClass}
              />
            </FilterField>
          </div>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 pt-3 dark:border-gray-800">
            <label className="flex cursor-pointer items-center gap-2 text-xs text-gray-600 dark:text-gray-300">
              <input
                type="checkbox"
                checked={highlightedOnly}
                onChange={(e) => setHighlightedOnly(e.target.checked)}
                className="size-3.5 rounded border-gray-300"
              />
              표시해 둔 로그만
            </label>

            <div className="flex gap-2">
              <Button size="sm" variant="outline" onClick={resetFilters}>
                초기화
              </Button>
              <Button size="sm" onClick={applyFilters}>
                조회
              </Button>
            </div>
          </div>
        </div>

        {/* 로그 직접 입력 (붙여넣기) */}
        {showIngest && <IngestPanel onDone={() => logs.reload()} />}

        {/* ─────────────── 잘림 경고 ─────────────── */}
        {logs.data?.truncated && (
          <div className="rounded-xl border border-warning-200 bg-warning-50 p-3 dark:border-warning-500/30 dark:bg-warning-500/10">
            <p className="text-xs text-gray-700 dark:text-gray-300">
              ⚠️ 전체 {logs.data.total}건 중 {logs.data.returned}건만 표시하고
              있습니다. 기간이나 심각도를 좁혀서 정확히 보세요.
              <span className="ml-1 text-gray-500">
                (AI 분석도 이 상한까지만 사용합니다)
              </span>
            </p>
          </div>
        )}

        {/* ─────────────── 로그 목록 ─────────────── */}
        <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <h4 className="text-sm font-semibold text-gray-800 dark:text-white/90">
                로그 목록
              </h4>
              {selected.size > 0 && (
                <Badge size="sm" color="primary">
                  {selected.size}건 선택
                </Badge>
              )}
            </div>
            <div className="flex gap-2">
              {selected.size > 0 && (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setSelected(new Set())}
                >
                  선택 해제
                </Button>
              )}
              <Button size="sm" variant="outline" onClick={logs.reload}>
                새로고침
              </Button>
            </div>
          </div>

          {logs.loading && (
            <p className="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
              로그를 불러오는 중...
            </p>
          )}

          {logs.error && (
            <div className="rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
              <p className="text-sm text-gray-800 dark:text-white/90">
                {logs.offline
                  ? "백엔드에 연결할 수 없습니다"
                  : "로그를 불러오지 못했습니다"}
              </p>
              <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">
                {logs.error}
              </p>
            </div>
          )}

          {!logs.loading && !logs.error && rows.length === 0 && (
            <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-gray-200 py-10 text-center dark:border-gray-800">
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                조건에 맞는 로그가 없습니다
              </p>
              <p className="mt-1 max-w-lg text-xs text-gray-400 dark:text-gray-500">
                심각도를 낮추거나 기간을 넓혀 보세요. 프로버가 텔레메트리에 로그를
                실어 보내면 여기에 쌓입니다.
              </p>
            </div>
          )}

          {!logs.loading && rows.length > 0 && (
            <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-700">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-gray-50 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
                  <tr>
                    <th className="w-10 border-b p-3 dark:border-gray-600">
                      <input
                        type="checkbox"
                        checked={allSelected}
                        onChange={toggleAll}
                        className="size-3.5 rounded border-gray-300"
                        title="전체 선택"
                      />
                    </th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">
                      시각
                    </th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">
                      Agent
                    </th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">
                      심각도
                    </th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">
                      로그
                    </th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">
                      표시
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 text-gray-600 dark:divide-gray-700 dark:text-gray-300">
                  {rows.map((log) => (
                    <tr
                      key={log.id}
                      className={
                        selected.has(log.id)
                          ? "bg-brand-50/50 dark:bg-brand-500/10"
                          : undefined
                      }
                    >
                      <td className="p-3 align-top">
                        <input
                          type="checkbox"
                          checked={selected.has(log.id)}
                          onChange={() => toggleOne(log.id)}
                          className="size-3.5 rounded border-gray-300"
                        />
                      </td>
                      <td className="whitespace-nowrap p-3 align-top font-mono text-[11px]">
                        {formatTime(log.logged_at)}
                      </td>
                      <td className="p-3 align-top">
                        <div className="flex flex-col">
                          <span className="font-mono text-[11px] text-gray-800 dark:text-white/90">
                            {log.agent_id}
                          </span>
                          {log.product && (
                            <span className="text-[10px] text-gray-400">
                              {log.product}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="p-3 align-top">
                        <div className="flex flex-wrap items-center gap-1">
                          <Badge size="sm" color={severityColor(log.severity)}>
                            {log.severity}
                          </Badge>
                          {log.repeat_count != null && log.repeat_count > 1 && (
                            <Badge size="sm" color="light">
                              ×{log.repeat_count}
                            </Badge>
                          )}
                        </div>
                      </td>
                      <td className="max-w-[560px] p-3 align-top">
                        {log.message_id && (
                          <span className="mr-1.5 font-mono text-[11px] text-brand-600 dark:text-brand-400">
                            {log.message_id}
                          </span>
                        )}
                        <span className="break-words text-xs">
                          {log.message || log.raw}
                        </span>
                        {log.note && (
                          <span className="mt-1 block text-[10px] text-gray-400">
                            메모: {log.note}
                          </span>
                        )}
                      </td>
                      <td className="p-3 align-top">
                        <button
                          type="button"
                          onClick={() => toggleHighlight(log)}
                          title={
                            log.highlighted
                              ? "표시 해제"
                              : "표시해 두기 (나중에 모아 보기)"
                          }
                          className={`text-lg leading-none ${
                            log.highlighted
                              ? "text-warning-500"
                              : "text-gray-300 hover:text-warning-400 dark:text-gray-600"
                          }`}
                        >
                          {log.highlighted ? "★" : "☆"}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* ─────────────── AI 분석 ─────────────── */}
        <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-gray-100 pb-3 dark:border-gray-800">
            <div className="flex items-center gap-2">
              <h3 className="text-base font-semibold text-gray-800 dark:text-white/90">
                AI 로그 분석
              </h3>
              <Badge size="sm" color="light">
                {logs.data ? `${logs.data.returned}줄 대상` : "—"}
              </Badge>
            </div>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setShowProviders((prev) => !prev)}
            >
              {showProviders ? "공급자 설정 닫기" : "공급자 설정"}
            </Button>
          </div>

          <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
            <FilterField label="AI 공급자">
              <select
                value={providerId}
                onChange={(e) =>
                  setProviderId(e.target.value === "" ? "" : Number(e.target.value))
                }
                className={selectClass}
                disabled={enabledProviders.length === 0}
              >
                {enabledProviders.length === 0 && (
                  <option value="">등록된 공급자 없음</option>
                )}
                {enabledProviders.map((provider) => (
                  <option key={provider.id} value={provider.id}>
                    {provider.name} · {provider.model}
                    {provider.is_default ? " (기본)" : ""}
                  </option>
                ))}
              </select>
            </FilterField>

            <div className="lg:col-span-2">
              <FilterField
                label="추가 지시 (선택)"
                hint="예: BGP 세션 끊김의 원인에 집중해 주세요"
              >
                <input
                  type="text"
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  placeholder="분석 관점을 지정할 수 있습니다"
                  className={selectClass}
                />
              </FilterField>
            </div>
          </div>

          <div className="mt-4 flex flex-wrap gap-2 border-t border-gray-100 pt-4 dark:border-gray-800">
            <Button
              size="sm"
              onClick={() => runAnalysis("selected")}
              disabled={analyzing || selected.size === 0}
              title={
                selected.size === 0
                  ? "목록에서 로그를 선택하면 활성화됩니다"
                  : `${selected.size}건만 분석합니다`
              }
            >
              {analyzing ? "분석 중..." : `선택한 ${selected.size}건 분석`}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => runAnalysis("filter")}
              disabled={analyzing || rows.length === 0}
              title="현재 필터 조건에 맞는 로그 전체를 분석합니다"
            >
              필터 결과 전체 분석
            </Button>
          </div>

          <p className="mt-2 text-[11px] text-gray-400 dark:text-gray-500">
            선택 분석은 고른 로그만, 전체 분석은 위 필터 조건에 맞는 로그를
            오래된 순서로 보냅니다. (인과관계 파악을 위해 시간순으로 전달)
          </p>

          {/* 분석 오류 */}
          {analysisError && (
            <div className="mt-4 rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
              <p className="text-sm font-medium text-gray-800 dark:text-white/90">
                분석에 실패했습니다
              </p>
              <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">
                {analysisError}
              </p>
            </div>
          )}

          {/* 분석 결과 */}
          {analysis && (
            <div className="mt-4">
              <AnalysisResult analysis={analysis} />
            </div>
          )}
        </div>

        {/* 분석 이력 */}
        <AnalysisHistory projectId={applied.projectId ?? null} agentId={applied.agentId ?? null} />

        {/* 공급자 설정 */}
        {showProviders && <AiProviderSettings />}
      </div>
    </>
  );
}

/** 분석 결과 카드입니다. */
function AnalysisResult({ analysis }: { analysis: ApiLogAnalysis }) {
  // 구조화 파싱에 실패하면 원문만 보여줍니다.
  // (파싱 실패를 오류로 처리하지 않는 이유: 원문에도 쓸 만한 내용이 있습니다)
  if (!analysis.succeeded) {
    return (
      <div className="rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
        <div className="flex flex-wrap items-center gap-2">
          <Badge size="sm" color="error">
            실패
          </Badge>
          <span className="font-mono text-[11px] text-gray-500">
            {analysis.analysis_id}
          </span>
          {analysis.provider_name && (
            <span className="text-[11px] text-gray-500">
              {analysis.provider_name} · {analysis.model}
            </span>
          )}
        </div>
        <p className="mt-2 text-xs text-gray-700 dark:text-gray-300">
          {analysis.error_message}
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-gray-200 p-4 dark:border-gray-700">
      <div className="flex flex-wrap items-center gap-2">
        <Badge size="sm" color={riskColor(analysis.risk_level)}>
          위험도 {analysis.risk_level ?? "미상"}
        </Badge>
        <span className="font-mono text-[11px] text-gray-500">
          {analysis.analysis_id}
        </span>
        <span className="text-[11px] text-gray-500">
          {analysis.provider_name} · {analysis.model}
        </span>
        {analysis.elapsed_ms != null && (
          <span className="text-[11px] text-gray-400">
            {(analysis.elapsed_ms / 1000).toFixed(1)}초
          </span>
        )}
        <span className="text-[11px] text-gray-400">
          로그 {analysis.included_log_count ?? 0}줄 사용
        </span>
        {/* ⚠️ 잘렸으면 반드시 알립니다. 일부만 보고 내린 결론을 과신하면 위험합니다. */}
        {analysis.truncated && (
          <Badge size="sm" color="warning">
            전체 {analysis.total_log_count}건 중 일부만 사용
          </Badge>
        )}
      </div>

      {analysis.summary && (
        <div className="mt-3">
          <h5 className="text-xs font-semibold text-gray-700 dark:text-gray-300">
            요약
          </h5>
          <p className="mt-1 whitespace-pre-wrap text-sm text-gray-700 dark:text-gray-300">
            {analysis.summary}
          </p>
        </div>
      )}

      {analysis.root_cause && (
        <div className="mt-3">
          <h5 className="text-xs font-semibold text-gray-700 dark:text-gray-300">
            추정 원인
          </h5>
          <p className="mt-1 whitespace-pre-wrap text-sm text-gray-700 dark:text-gray-300">
            {analysis.root_cause}
          </p>
        </div>
      )}

      {analysis.recommendations.length > 0 && (
        <div className="mt-3">
          <h5 className="text-xs font-semibold text-gray-700 dark:text-gray-300">
            권장 조치
          </h5>
          <ol className="mt-1 list-decimal space-y-1 pl-5 text-sm text-gray-700 dark:text-gray-300">
            {analysis.recommendations.map((item, index) => (
              <li key={`${index}-${item}`}>{item}</li>
            ))}
          </ol>
        </div>
      )}

      {/* 구조화 파싱 실패 시 원문을 그대로 보여줍니다. */}
      {!analysis.structured && analysis.raw_response && (
        <div className="mt-3">
          <h5 className="text-xs font-semibold text-gray-700 dark:text-gray-300">
            모델 응답 (구조화 파싱 실패 — 원문)
          </h5>
          <pre className="custom-scrollbar mt-1 max-h-[300px] overflow-auto whitespace-pre-wrap rounded-lg bg-gray-50 p-3 text-[11px] text-gray-700 dark:bg-gray-900/50 dark:text-gray-300">
            {analysis.raw_response}
          </pre>
        </div>
      )}
    </div>
  );
}

/** 분석 이력 목록입니다. */
function AnalysisHistory({
  projectId,
  agentId,
}: {
  projectId: string | null;
  agentId: string | null;
}) {
  const history = useApi(
    () => listLogAnalyses({ projectId, agentId, limit: 20 }),
    [projectId, agentId],
  );

  const [openId, setOpenId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ApiLogAnalysis | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const openDetail = async (analysisId: string) => {
    if (openId === analysisId) {
      setOpenId(null);
      setDetail(null);
      return;
    }

    setOpenId(analysisId);
    setDetailLoading(true);
    try {
      setDetail(await getLogAnalysis(analysisId));
    } catch {
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const rows = history.data?.analyses ?? [];

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
      <div className="mb-3 flex items-center gap-2 border-b border-gray-100 pb-3 dark:border-gray-800">
        <h3 className="text-base font-semibold text-gray-800 dark:text-white/90">
          분석 이력
        </h3>
        <Badge size="sm" color="light">
          {rows.length}건
        </Badge>
        <Button
          className="ml-auto"
          size="sm"
          variant="outline"
          onClick={history.reload}
        >
          새로고침
        </Button>
      </div>

      {rows.length === 0 && (
        <p className="py-6 text-center text-xs text-gray-400 dark:text-gray-500">
          아직 분석 이력이 없습니다.
        </p>
      )}

      {rows.length > 0 && (
        <ul className="space-y-2">
          {rows.map((item) => (
            <li
              key={item.analysis_id}
              className="rounded-lg border border-gray-200 dark:border-gray-700"
            >
              <button
                type="button"
                onClick={() => openDetail(item.analysis_id)}
                className="flex w-full flex-wrap items-center gap-2 p-3 text-left"
              >
                {item.succeeded ? (
                  <Badge size="sm" color={riskColor(item.risk_level)}>
                    {item.risk_level ?? "미상"}
                  </Badge>
                ) : (
                  <Badge size="sm" color="error">
                    실패
                  </Badge>
                )}
                <span className="min-w-0 flex-1 truncate text-xs text-gray-700 dark:text-gray-300">
                  {item.summary ?? item.error_message ?? "(요약 없음)"}
                </span>
                <span className="text-[10px] text-gray-400">
                  {item.agent_id ?? "전체"} · {formatTime(item.created_at)}
                </span>
                <span className="text-[10px] text-gray-400">
                  {openId === item.analysis_id ? "▲" : "▼"}
                </span>
              </button>

              {openId === item.analysis_id && (
                <div className="border-t border-gray-100 p-3 dark:border-gray-700">
                  {detailLoading && (
                    <p className="text-[11px] text-gray-400">불러오는 중...</p>
                  )}
                  {!detailLoading && detail && <AnalysisResult analysis={detail} />}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** 로그 직접 입력 패널입니다. */
function IngestPanel({ onDone }: { onDone: () => void }) {
  const [agentId, setAgentId] = useState("");
  const [product, setProduct] = useState("");
  const [text, setText] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const handleSubmit = async () => {
    if (!agentId.trim()) {
      setMessage("Agent 식별자를 입력하세요.");
      return;
    }
    if (!text.trim()) {
      setMessage("로그 내용을 붙여넣으세요.");
      return;
    }

    setSaving(true);
    setMessage(null);
    try {
      const result = await ingestLogs({
        agent_id: agentId.trim(),
        product: product.trim() || undefined,
        source: "manual",
        text,
      });
      setMessage(
        `저장 ${result.inserted}건 · 중복 ${result.duplicated}건 · 건너뜀 ${result.skipped}건`,
      );
      setText("");
      onDone();
    } catch (cause) {
      setMessage(
        cause instanceof ApiError ? cause.message : "저장에 실패했습니다.",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
      <h4 className="mb-3 text-sm font-semibold text-gray-800 dark:text-white/90">
        로그 직접 입력
      </h4>
      <p className="mb-3 text-xs text-gray-500 dark:text-gray-400">
        장비에 접속할 수 없을 때 로그를 붙여넣어 저장할 수 있습니다. 각 줄을
        자동으로 심각도별 분류합니다.
      </p>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <FilterField label="Agent 식별자" required>
          <input
            type="text"
            value={agentId}
            onChange={(e) => setAgentId(e.target.value)}
            placeholder="예: c8000v-1"
            className={selectClass}
          />
        </FilterField>
        <FilterField label="제품명 (선택)" hint="심각도 해석 힌트">
          <input
            type="text"
            value={product}
            onChange={(e) => setProduct(e.target.value)}
            placeholder="예: Cisco 8000v"
            className={selectClass}
          />
        </FilterField>
      </div>

      <div className="mt-3">
        <FilterField label="로그 내용">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={6}
            placeholder={"*Sep 19 08:12:33: %SYS-5-CONFIG_I: Configured from console\n%LINEPROTO-4-UPDOWN: Line protocol on Interface Gi2, changed state to down"}
            className={`${selectClass} resize-y font-mono text-xs`}
          />
        </FilterField>
      </div>

      {message && (
        <p className="mt-3 rounded-lg bg-gray-50 p-2.5 text-xs text-gray-700 dark:bg-gray-800 dark:text-gray-300">
          {message}
        </p>
      )}

      <div className="mt-3 flex justify-end">
        <Button size="sm" onClick={handleSubmit} disabled={saving}>
          {saving ? "저장 중..." : "저장"}
        </Button>
      </div>
    </div>
  );
}

/** 필터 입력 한 칸입니다. */
function FilterField({
  label,
  required,
  hint,
  children,
}: {
  label: string;
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
        {label}
        {required && <span className="ml-0.5 text-error-500">*</span>}
      </label>
      {children}
      {hint && (
        <p className="mt-0.5 text-[10px] text-gray-400 dark:text-gray-500">{hint}</p>
      )}
    </div>
  );
}

const selectClass =
  "w-full rounded-lg border border-gray-300 bg-transparent px-3 py-2 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white";

/**
 * `datetime-local` 값을 서버가 이해하는 ISO-8601 로 바꿉니다.
 *
 * <p>⚠️ {@code new Date(value)} 를 그대로 쓰면 안 되는 이유:
 * {@code datetime-local} 은 타임존이 없는 문자열({@code 2026-09-19T08:00})이라
 * 브라우저가 <b>로컬 시간으로 해석</b>합니다. 그대로 보내면 서버(UTC 저장)와
 * 시간대가 어긋나 조회 결과가 밀립니다.
 *
 * <p>종료 시각은 <b>그 날의 끝</b>으로 올립니다. 사용자가 날짜만 고르면
 * 시각이 00:00 이 되어 하루 전체를 포함하지 못하기 때문입니다.
 *
 * @param value  datetime-local 값
 * @param endOfDay 종료 시각이면 true (23:59:59.999 로 올림)
 * @returns ISO-8601 문자열 (값이 없으면 null)
 */
function toIso(value: string, endOfDay: boolean): string | null {
  if (!value) return null;

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;

  if (endOfDay) {
    date.setHours(23, 59, 59, 999);
  }

  return date.toISOString();
}

/** 시각을 짧게 표시합니다. */
function formatTime(value: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}
