import { useSearchParams, useNavigate } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import MermaidDiagram from "../components/common/MermaidDiagram";
import { exportMermaidSvg } from "../lib/mermaid";

// Physical View: 물리 장비 중심 토폴로지
const PHYSICAL_CHART = `
  flowchart TD
      Internet((Internet))
      Internet -->|red| SwitchA[Switch A]
      Internet -->|blue| SwitchB[Switch B]
      Internet -->|orange| SwitchC[Switch C]
      SwitchA --> SubnetA["Subnet A<br/>(공개망)"]
      SwitchB --> SubnetB["Subnet B<br/>(내부망)"]
      SwitchC --> SubnetC["Subnet C<br/>(기밀망)"]

      linkStyle 0 stroke:#ef4444,stroke-width:2px;
      linkStyle 1 stroke:#3b82f6,stroke-width:2px;
      linkStyle 2 stroke:#f59e0b,stroke-width:2px;
      linkStyle 3 stroke:#ef4444,stroke-width:2px;
      linkStyle 4 stroke:#3b82f6,stroke-width:2px;
      linkStyle 5 stroke:#f59e0b,stroke-width:2px;

      classDef open fill:#e0f2fe,stroke:#0284c7,stroke-width:2px;
      classDef neutral fill:#ffffff,stroke:#64748b,stroke-width:2px;
      class SubnetA,SubnetB,SubnetC open;
      class SwitchA,SwitchB,SwitchC neutral;
`;

// Logical View: CSO 존 단위 논리 토폴로지
const LOGICAL_CHART = `
  flowchart TD
      subgraph OpenZone [Open Zone]
          O1[인터넷웹감]
          O2[모바일웹감]
          O3[Open API]
      end
      subgraph SensitiveZone [Sensitive Zone]
          S1[업무포털]
          S2[VDI]
          S3[그룹웨어]
      end
      subgraph ConfidentialZone [Confidential Zone]
          C1[계정계]
          C2[수신계]
          C3[여신계]
          C4[결제계]
      end

      OpenZone --> SensitiveZone
      SensitiveZone --> ConfidentialZone

      classDef open fill:#dcfce7,stroke:#16a34a,stroke-width:2px;
      classDef sensitive fill:#f3e8ff,stroke:#9333ea,stroke-width:2px;
      classDef confidential fill:#fee2e2,stroke:#dc2626,stroke-width:2px;
      class O1,O2,O3 open;
      class S1,S2,S3 sensitive;
      class C1,C2,C3,C4 confidential;
      style OpenZone fill:#f0fdf4,stroke:#86efac;
      style SensitiveZone fill:#faf5ff,stroke:#d8b4fe;
      style ConfidentialZone fill:#fef2f2,stroke:#fca5a5;
`;

// Detail View: 서브넷 CIDR까지 포함한 상세 토폴로지
const DETAIL_CHART = `
  flowchart LR
      Internet[Internet<br/>0.0.0.0/0] --> EMZ[EMZ Switch]

      EMZ --> OW1[인터넷웹감<br/>10.10.10.0/24]
      EMZ --> OW2[모바일웹감<br/>10.10.20.0/24]
      EMZ --> OW3[Open API<br/>10.10.30.0/24]

      OW1 --> BizSW[업무망 Switch]
      OW2 --> BizSW
      OW3 --> BizSW

      BizSW --> BZ1[업무포털<br/>10.20.20.0/24]
      BizSW --> BZ2[VDI<br/>10.20.10.0/24]
      BizSW --> BZ3[그룹웨어<br/>10.20.30.0/24]

      BZ1 --> FinSW[금융망 Switch]
      BZ2 --> FinSW
      BZ3 --> FinSW

      FinSW --> CZ1[계정계<br/>10.30.10.0/24]
      FinSW --> CZ2[수신계<br/>10.30.20.0/24]
      FinSW --> CZ3[여신계<br/>10.30.30.0/24]
      FinSW --> CZ4[결제계<br/>10.30.40.0/24]

      classDef open fill:#dcfce7,stroke:#16a34a,stroke-width:2px;
      classDef sensitive fill:#f3e8ff,stroke:#9333ea,stroke-width:2px;
      classDef confidential fill:#fee2e2,stroke:#dc2626,stroke-width:2px;
      class Internet,EMZ,OW1,OW2,OW3 open;
      class BizSW,BZ1,BZ2,BZ3 sensitive;
      class FinSW,CZ1,CZ2,CZ3,CZ4 confidential;
`;

interface ViewCardProps {
  title: string;
  chart: string;
  exportName: string;
  legend?: { label: string; className: string }[];
}

function ViewCard({ title, chart, exportName, legend }: ViewCardProps) {
  return (
    <div className="flex flex-col rounded-xl border border-gray-200 bg-white p-4 shadow-theme-xs dark:border-gray-700 dark:bg-gray-800/50">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h4 className="text-sm font-semibold text-gray-800 dark:text-white/90">{title}</h4>
        <button
          type="button"
          onClick={() => exportMermaidSvg(chart, exportName)}
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
    </div>
  );
}

export default function TopologyRulePreview() {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get("project_id");
  const navigate = useNavigate();

  const handlePrevious = () => {
    navigate(`/project/create/segmentation?project_id=${projectId ?? ""}`);
  };

  const handleContinue = () => {
    console.log("Project creation wizard finished");
    navigate(`/project?project_id=${projectId ?? ""}`);
  };

  return (
    <>
      <PageMeta
        title="Network Topology and Rule Preview | TailAdmin - React.js Admin Dashboard Template"
        description="Mermaid based network topology and rule preview"
      />
      <PageBreadcrumb pageTitle="Network Topology and Rule Preview" />

      <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        {/* 상단 헤더 영역 (직전 화면들과 동일한 뱃지 스타일) */}
        <div className="mb-6 flex items-center justify-between border-b border-gray-100 pb-4 dark:border-gray-800">
          <span className="inline-block rounded-lg bg-brand-50 px-3 py-1.5 text-sm font-semibold text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">
            Network Topology and Rule Preview (Mermaid Diagram 기반)
          </span>
        </div>

        {/* 3분할 뷰 그리드 */}
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
          <ViewCard title="Physical View" chart={PHYSICAL_CHART} exportName="physical-view" />
          <ViewCard title="Logical View" chart={LOGICAL_CHART} exportName="logical-view" />
          <ViewCard
            title="Detail View"
            chart={DETAIL_CHART}
            exportName="detail-view"
            legend={[
              { label: "Open (인터넷과 연동된 공개망)", className: "text-green-600 dark:text-green-400" },
              { label: "Sensitive (사내 내부 업무망)", className: "text-purple-600 dark:text-purple-400" },
              { label: "Confidential (기밀망)", className: "text-red-600 dark:text-red-400" },
            ]}
          />
        </div>

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
