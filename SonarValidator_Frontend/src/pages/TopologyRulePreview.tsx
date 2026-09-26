import { useMemo } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import MermaidDiagram from "../components/common/MermaidDiagram";
import { useApi } from "../hooks/useApi";
import { getTopology, getPolicyViolations } from "../lib/api";
import { exportMermaidSvg } from "../lib/mermaid";
import {
  topologyToMermaid,
  topologyToZoneMermaid,
  topologyToDetailedMermaid,
} from "../lib/topology/mermaid";

/**
 * 마법사 마지막 단계의 토폴로지/규칙 미리보기입니다.
 *
 * <h2>더미 데이터를 걷어낸 자리</h2>
 * 이전에는 Physical/Logical/Detail 세 그림이 모두 문자열로 박혀 있었습니다.
 * {@code 업무포털}, {@code 계정계}, {@code 10.30.10.0/24} 같은 값은 실제
 * 프로젝트와 아무 관계가 없는 예시였습니다. 이제 세 그림 모두 <b>방금 만든
 * 프로젝트의 실제 서브넷과 규칙</b>으로 그립니다.
 *
 * <ul>
 *   <li><b>Physical/Detail</b> — 서브넷 CIDR 까지 포함한 상세 그림</li>
 *   <li><b>Logical</b> — 등급 존 단위로 접은 그림</li>
 *   <li><b>Detail</b> — 규칙 위반 요약 (서버 판정 결과)</li>
 * </ul>
 */

interface ViewCardProps {
  title: string;
  chart: string;
  exportName: string;
  legend?: { label: string; className: string }[];
  footnote?: string;
}

function ViewCard({ title, chart, exportName, legend, footnote }: ViewCardProps) {
  return (
    <div className="flex flex-col rounded-xl border border-gray-200 bg-white p-4 shadow-theme-xs dark:border-gray-700 dark:bg-gray-800/50">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h4 className="text-sm font-semibold text-gray-800 dark:text-white/90">{title}</h4>
        <button
          type="button"
          onClick={() => void exportMermaidSvg(chart, exportName)}
          className="rounded-lg border border-gray-300 bg-white px-3 py-1 text-xs font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
        >
          Export
        </button>
      </div>

      {legend && (
        <div className="mb-2 flex flex-wrap gap-2 text-[11px] font-medium">
          {legend.map((item) => (
            <span key={item.label} className={item.className}>
              {item.label}
            </span>
          ))}
        </div>
      )}

      <div className="min-h-[220px] flex-1 rounded-lg border border-gray-100 bg-gray-50/60 p-2 dark:border-gray-700 dark:bg-gray-900/40">
        <MermaidDiagram chart={chart} />
      </div>

      {footnote && (
        <p className="mt-2 text-[11px] text-gray-500 dark:text-gray-400">{footnote}</p>
      )}
    </div>
  );
}

export default function TopologyRulePreview() {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get("project_id");
  const navigate = useNavigate();

  const topology = useApi(
    () => (projectId ? getTopology(projectId) : Promise.resolve(null)),
    [projectId],
  );

  const violations = useApi(
    () => (projectId ? getPolicyViolations(projectId) : Promise.resolve(null)),
    [projectId],
  );

  const detailedChart = useMemo(
    () => topologyToDetailedMermaid(topology.data),
    [topology.data],
  );
  const zoneChart = useMemo(
    () => topologyToZoneMermaid(topology.data),
    [topology.data],
  );
  const overallChart = useMemo(
    () => topologyToMermaid(topology.data),
    [topology.data],
  );

  /** 서버가 판정한 결과를 한 줄로 요약합니다. */
  const verdict = violations.data
    ? violations.data.compliant
      ? `검증 통과 — 규칙 ${violations.data.rule_count}건 / 서브넷 ${violations.data.subnet_count}개`
      : `위반 ${violations.data.violation_count}건 — 심각도별 CRITICAL ${violations.data.by_severity?.CRITICAL ?? 0} / MAJOR ${violations.data.by_severity?.MAJOR ?? 0} / MINOR ${violations.data.by_severity?.MINOR ?? 0}`
    : undefined;

  const handlePrevious = () => {
    navigate(`/project/create/segmentation?project_id=${projectId ?? ""}`);
  };

  const handleContinue = () => {
    navigate(`/project?project_id=${projectId ?? ""}`);
  };

  return (
    <>
      <PageMeta
        title="Network Topology and Rule Preview | SonarValidator"
        description="프로젝트의 실제 서브넷과 규칙으로 그린 토폴로지 미리보기"
      />
      <PageBreadcrumb pageTitle="Network Topology and Rule Preview" />

      <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        {/* 상단 헤더 영역 (직전 화면들과 동일한 뱃지 스타일) */}
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-gray-100 pb-4 dark:border-gray-800">
          <span className="inline-block rounded-lg bg-brand-50 px-3 py-1.5 text-sm font-semibold text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">
            Network Topology and Rule Preview (Mermaid Diagram 기반)
          </span>
          {topology.data && (
            <span className="text-xs text-gray-500 dark:text-gray-400">
              {topology.data.project_name} · 서브넷 {topology.data.nodes.length}개 · 규칙{" "}
              {topology.data.edges.length}개
            </span>
          )}
        </div>

        {/* 검증 결과 요약 (서버 판정) */}
        {verdict && (
          <div
            className={`mb-5 rounded-xl border p-3 text-xs ${
              violations.data?.compliant
                ? "border-green-200 bg-green-50 text-green-700 dark:border-green-500/30 dark:bg-green-500/10 dark:text-green-300"
                : "border-red-200 bg-red-50 text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300"
            }`}
          >
            {verdict}
          </div>
        )}

        {/* 로딩/오류 */}
        {topology.error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
            {topology.error}
          </div>
        ) : topology.loading ? (
          <p className="p-6 text-center text-sm text-gray-400">불러오는 중...</p>
        ) : !projectId ? (
          <div className="rounded-xl border border-orange-200 bg-orange-50 p-4 text-sm text-orange-700 dark:border-orange-500/30 dark:bg-orange-500/10 dark:text-orange-300">
            프로젝트가 지정되지 않았습니다. 이전 단계에서 프로젝트를 먼저 만드세요.
          </div>
        ) : (
          /* 3분할 뷰 그리드 — 모두 같은 서버 데이터에서 파생된 그림입니다 */
          <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
            <ViewCard
              title="Detail View (서브넷 CIDR 포함)"
              chart={detailedChart}
              exportName="detail-view"
            />
            <ViewCard
              title="Logical View (등급 존 단위)"
              chart={zoneChart}
              exportName="logical-view"
              legend={[
                { label: "Open", className: "text-green-600 dark:text-green-400" },
                { label: "Sensitive", className: "text-purple-600 dark:text-purple-400" },
                { label: "Confidential", className: "text-red-600 dark:text-red-400" },
              ]}
            />
            <ViewCard
              title="Rule View (규칙 + 금지 조합)"
              chart={overallChart}
              exportName="rule-view"
              footnote={verdict}
            />
          </div>
        )}

        {/* 하단 우측: Continue / Previous */}
        <div className="mt-6 flex items-center justify-end gap-3 border-t border-gray-100 pt-4 dark:border-gray-800">
          <button
            type="button"
            onClick={handleContinue}
            className="rounded-xl border border-gray-300 bg-white px-6 py-2.5 text-sm font-semibold text-gray-800 shadow-sm transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white dark:hover:bg-gray-700"
          >
            Continue
          </button>
          <button
            type="button"
            onClick={handlePrevious}
            className="rounded-xl border border-gray-300 bg-white px-6 py-2.5 text-sm font-semibold text-gray-800 shadow-sm transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white dark:hover:bg-gray-700"
          >
            Previous
          </button>
        </div>
      </div>
    </>
  );
}
