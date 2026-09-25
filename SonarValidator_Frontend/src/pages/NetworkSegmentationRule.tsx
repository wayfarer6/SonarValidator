import { useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import NetworkTopologyMermaid from "./NetworkTopologyMermaid";
import {
  useProjectWizard,
  type SubnetClass,
  type WizardSubnet,
} from "../context/ProjectWizardContext";

interface RuleRow {
  id: string;
  src: string; // 서브넷 id
  dst: string; // 서브넷 id
  port: string;
}

// ---------------------------------------------------------------------------
// 아래는 서버 연동 이전에 쓰던 더미 규칙입니다.
// 지우지 않고 남겨 둡니다 — 서버가 내려주는 규칙 형태와 비교하거나,
// 백엔드 없이 화면만 확인할 때 되살려 쓰기 위해서입니다.
// (되살릴 때는 InitRULES/useState 초기값을 이 배열로 바꾸면 됩니다)
//
// 주의: 아래 src/dst(Subnet-0001..0005)는 서버가 만드는 서브넷 id 와 다를 수
// 있어서, 그대로 쓰면 validateRules 가 "존재하지 않는 서브넷이 선택되었습니다."
// 위반으로 보고합니다. 그래서 지금은 빈 목록에서 시작합니다.
// ---------------------------------------------------------------------------
// const INITIAL_RULES: RuleRow[] = [
//   { id: "Rule-0001", src: "Subnet-0004", dst: "Subnet-0002", port: "" },
// ];

// const INITIAL_AUTO_RULES: RuleRow[] = [
//   { id: "Rule-9001", src: "Subnet-0001", dst: "Subnet-0005", port: "443" },
// ];

type MessageType = "error" | "success" | "warning";

interface FeedbackMessage {
  type: MessageType;
  title: string;
  body: string;
}

interface Toast extends FeedbackMessage {
  id: number;
}

interface Violation {
  ruleId: string;
  reason: string;
}

interface ValidationResult {
  messages: FeedbackMessage[];
  violations: Violation[];
}

// CSO 정책: Confidential ↔ Open 직접 연결 금지
function isForbiddenPair(srcClass: SubnetClass, dstClass: SubnetClass): boolean {
  return (
    (srcClass === "Confidential" && dstClass === "Open") ||
    (srcClass === "Open" && dstClass === "Confidential")
  );
}

// 규칙 목록 검증 → 메시지 + 위반 규칙 번호 목록 계산
function validateRules(rules: RuleRow[], subnets: WizardSubnet[]): ValidationResult {
  const messages: FeedbackMessage[] = [];
  const violations: Violation[] = [];

  const classOf = (subnetId: string): SubnetClass | undefined =>
    subnets.find((subnet) => subnet.id === subnetId)?.subnetClass;

  for (const rule of rules) {
    const srcClass = classOf(rule.src);
    const dstClass = classOf(rule.dst);

    if (!srcClass || !dstClass) {
      violations.push({ ruleId: rule.id, reason: "존재하지 않는 서브넷이 선택되었습니다." });
      continue;
    }

    if (isForbiddenPair(srcClass, dstClass)) {
      violations.push({
        ruleId: rule.id,
        reason: `${srcClass} ↔ ${dstClass} 직접 연결은 허용되지 않습니다.`,
      });
    }
    if (rule.port.trim() === "") {
      violations.push({ ruleId: rule.id, reason: "허용 포트가 설정되지 않았습니다." });
    }
  }

  const hasForbidden = violations.some((v) => v.reason.includes("직접 연결"));
  const hasNoPort = violations.some((v) => v.reason.includes("포트"));

  if (hasForbidden) {
    messages.push({
      type: "error",
      title: "오류: 네트워크 연결 제한",
      body: "Confidential lv의 Subnet과 Public(Open) lv의 Subnet의 연결은 논리적으로 허용하지 않습니다.",
    });
  }
  if (hasNoPort) {
    messages.push({
      type: "warning",
      title: "경고: 포트 설정 확인",
      body: "허용 포트가 지정되지 않은 규칙이 있습니다. Subnet 간에 허용된 포트가 없습니다.",
    });
  }
  if (rules.length > 0 && violations.length === 0) {
    messages.push({
      type: "success",
      title: "성공: 규칙 설정 완료",
      body: "지정된 규칙이 설정 되었습니다. 정책을 준수합니다.",
    });
  }

  return { messages, violations };
}

export default function NetworkSegmentationRule() {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get("project_id");
  const navigate = useNavigate();
  // 서브넷과 규칙 모두 백엔드에서 옵니다.
  const {
    subnets,
    rules: serverRules,
    loading,
    error,
    offline,
    reload,
  } = useProjectWizard();

  const [rules, setRules] = useState<RuleRow[]>([]);
  const [toasts, setToasts] = useState<Toast[]>([]);
  // Save 시점에 계산된 위반 규칙 목록 (정책 설정 테이블 아래에 표시)
  const [violations, setViolations] = useState<Violation[]>([]);
  const toastSeq = useRef(0);

  // Prober 가 탐지한 기존 연결에서 서버가 만든 규칙입니다. (읽기 전용)
  // 검토 전까지 비활성(enabled=false) 으로 오고, src/dst 는 채워지지 않을 수
  // 있습니다. 근거가 되는 원문 규칙은 note 에 들어 있습니다.
  const autoRules = useMemo(
    () =>
      serverRules
        .filter((rule) => rule.origin === "DISCOVERED")
        .map((rule) => ({
          id: rule.id,
          src: rule.src ?? "",
          dst: rule.dst ?? "",
          port: rule.port === null ? "" : String(rule.port),
          note: rule.note ?? "",
        })),
    [serverRules],
  );

  // 메시지 식별 키 (중복 토스트 방지용)
  const keyOf = (msg: FeedbackMessage) => `${msg.type}:${msg.title}`;

  // 토스트는 4초 뒤 자동으로 사라짐
  useEffect(() => {
    if (toasts.length === 0) return;
    const timers = toasts.map((toast) =>
      setTimeout(() => {
        setToasts((prev) => prev.filter((item) => item.id !== toast.id));
      }, 4000),
    );
    return () => timers.forEach(clearTimeout);
  }, [toasts]);

  const dismissToast = (id: number) => {
    setToasts((prev) => prev.filter((item) => item.id !== id));
  };

  // 규칙 편집 중에는 검증을 하지 않음 (Save 시에만 검증)
  const updateRule = (id: string, patch: Partial<RuleRow>) => {
    setRules((prev) => prev.map((rule) => (rule.id === id ? { ...rule, ...patch } : rule)));
  };

  const addRule = () => {
    setRules((prev) => [
      ...prev,
      {
        id: `Rule-${String(prev.length + 1).padStart(4, "0")}`,
        src: subnets[0]?.id ?? "",
        dst: subnets[0]?.id ?? "",
        port: "",
      },
    ]);
  };

  const removeRule = (id: string) => {
    setRules((prev) => prev.filter((rule) => rule.id !== id));
  };

  // Save: 정책 검증 → 토스트 메시지 표시 + 위반 규칙 번호 목록 갱신
  const handleSave = () => {
    const { messages, violations: found } = validateRules(rules, subnets);
    setViolations(found);

    const stamped = messages.map((msg) => {
      toastSeq.current += 1;
      return { ...msg, id: toastSeq.current };
    });
    setToasts((prev) => {
      const existing = new Set(prev.map(keyOf));
      return [...prev, ...stamped.filter((msg) => !existing.has(keyOf(msg)))];
    });
  };

  const handleContinue = () => {
    console.log("Proceeding to next step...", rules);
    navigate(`/project/create/preview?project_id=${projectId ?? ""}`);
  };

  // 서버가 만든 규칙은 src/dst 가 비어 있을 수 있습니다.
  // 존재하지 않는 id 를 그대로 찍으면 오해를 사므로 미지정으로 표시합니다.
  const subnetLabel = (subnetId: string) => {
    if (subnetId === "") return "(미지정)";
    const subnet = subnets.find((s) => s.id === subnetId);
    return subnet ? `${subnet.cidr} (${subnet.subnetClass})` : subnetId;
  };

  const selectClass =
    "w-full rounded-lg border border-gray-300 bg-transparent px-2 py-1.5 text-xs text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white";

  return (
    <>
      <PageMeta
        title="Add Network Segmentation Rule | TailAdmin"
        description="Network Segmentation Rule Configuration with CSO (Confidential, Sensitive, Open)"
      />
      <PageBreadcrumb
        pageTitle="Add Network Segmentation Rule"
        parentName="Project"
        parentPath="/project"
      />

      <div className="space-y-6 rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        {/* 상단 헤더 영역 (직전 화면들과 동일한 뱃지 + 우측 Continue 버튼) */}
        <div className="flex items-center justify-between border-b border-gray-100 pb-4 dark:border-gray-800">
          <span className="inline-block rounded-lg bg-brand-50 px-3 py-1.5 text-sm font-semibold text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">
            Add Network Segmentation Rule
          </span>

          <button
            type="button"
            onClick={handleContinue}
            className="rounded-xl border border-gray-300 bg-white px-6 py-2.5 text-sm font-semibold text-gray-800 shadow-sm transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white dark:hover:bg-gray-700"
          >
            Continue
          </button>
        </div>

        {/* 서버 상태 안내. 서브넷/규칙은 백엔드에서 오므로 조회 실패를 알립니다. */}
        {!loading && error && (
          <div className="rounded-xl border border-red-200 bg-red-50 p-3 dark:border-red-500/30 dark:bg-red-500/10">
            <p className="text-sm font-medium text-red-800 dark:text-red-300">
              {offline ? "백엔드에 연결할 수 없습니다" : "서브넷/규칙 정보를 불러오지 못했습니다"}
            </p>
            <p className="mt-1 text-xs text-red-600 dark:text-red-300/80">{error}</p>
            <button
              type="button"
              onClick={reload}
              className="mt-2 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-800 transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white/90 dark:hover:bg-gray-700"
            >
              다시 시도
            </button>
          </div>
        )}

        {!loading && !error && subnets.length === 0 && (
          <div className="rounded-xl border border-orange-200 bg-orange-50 p-3 text-xs text-orange-700 dark:border-orange-500/30 dark:bg-orange-500/10 dark:text-orange-300">
            등록된 서브넷이 없습니다. 이전 단계에서 서브넷 등급을 먼저 지정하세요.
          </div>
        )}

        {/* 상단 파트: 토로지 (Mermaid) 및 정책 설정 테이블 */}
        <div className="flex flex-col gap-6 xl:flex-row">
          {/* 왼쪽: Mermaid 기반 CSO 토폴로지 영역 */}
          <div className="flex-1 rounded-xl border border-gray-200 bg-gray-50/50 p-5 dark:border-gray-700 dark:bg-gray-800/40">
            <div className="mb-3 flex items-center justify-between">
              <h4 className="text-sm font-semibold text-gray-700 dark:text-gray-300">
                Network Topology (CSO Security Zones)
              </h4>
              <div className="flex gap-2 text-[11px]">
                <span className="rounded bg-blue-100 px-2 py-0.5 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300">Open</span>
                <span className="rounded bg-orange-100 px-2 py-0.5 text-orange-800 dark:bg-orange-900/40 dark:text-orange-300">Sensitive</span>
                <span className="rounded bg-red-100 px-2 py-0.5 text-red-800 dark:bg-red-900/40 dark:text-red-300">Confidential</span>
              </div>
            </div>

            {/* Mermaid 다이어그램 렌더링 컴포넌트 삽입 */}
            <NetworkTopologyMermaid />
          </div>

          {/* 오른쪽: 정책 설정 및 자동 생성 규칙 테이블 영역 */}
          <div className="w-full space-y-5 xl:w-[440px]">
            {/* 정책 설정 테이블 (편집 가능) */}
            <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-theme-xs dark:border-gray-700 dark:bg-gray-800/50">
              <div className="mb-3 flex items-center justify-between">
                <h5 className="text-sm font-semibold text-gray-800 dark:text-white/90">
                  정책 설정 (Rule Configuration)
                </h5>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={addRule}
                    className="rounded-lg border border-gray-300 bg-white px-2.5 py-1 text-xs font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
                  >
                    + Add Rule
                  </button>
                  <button
                    type="button"
                    onClick={handleSave}
                    className="rounded-lg bg-brand-500 px-3 py-1 text-xs font-semibold text-white shadow-sm transition hover:bg-brand-600"
                  >
                    Save
                  </button>
                </div>
              </div>

              <div className="overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700">
                <table className="w-full text-left text-xs">
                  <thead className="bg-gray-50 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
                    <tr>
                      <th className="border-b p-2 font-medium dark:border-gray-600">Rule ID</th>
                      <th className="border-b p-2 font-medium dark:border-gray-600">SRC Subnet</th>
                      <th className="border-b p-2 font-medium dark:border-gray-600">DST Subnet</th>
                      <th className="border-b p-2 font-medium dark:border-gray-600">Port</th>
                      <th className="border-b p-2 dark:border-gray-600"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200 text-gray-600 dark:divide-gray-700 dark:text-gray-300">
                    {rules.map((rule) => (
                      <tr key={rule.id}>
                        <td className="p-2 font-mono">{rule.id}</td>
                        <td className="p-2">
                          <select
                            value={rule.src}
                            onChange={(e) => updateRule(rule.id, { src: e.target.value })}
                            className={selectClass}
                          >
                            {subnets.map((subnet) => (
                              <option key={subnet.id} value={subnet.id}>
                                {subnet.cidr} ({subnet.subnetClass})
                              </option>
                            ))}
                          </select>
                        </td>
                        <td className="p-2">
                          <select
                            value={rule.dst}
                            onChange={(e) => updateRule(rule.id, { dst: e.target.value })}
                            className={selectClass}
                          >
                            {subnets.map((subnet) => (
                              <option key={subnet.id} value={subnet.id}>
                                {subnet.cidr} ({subnet.subnetClass})
                              </option>
                            ))}
                          </select>
                        </td>
                        <td className="p-2">
                          <input
                            type="text"
                            value={rule.port}
                            onChange={(e) => updateRule(rule.id, { port: e.target.value })}
                            placeholder="e.g. 443"
                            className="w-16 rounded-lg border border-gray-300 bg-transparent px-2 py-1.5 text-xs text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
                          />
                        </td>
                        <td className="p-2 text-right">
                          <button
                            type="button"
                            onClick={() => removeRule(rule.id)}
                            className="text-gray-400 transition hover:text-red-500"
                            aria-label={`Delete ${rule.id}`}
                          >
                            ✕
                          </button>
                        </td>
                      </tr>
                    ))}
                    {rules.length === 0 && (
                      <tr>
                        <td colSpan={5} className="p-3 text-center text-gray-400">
                          설정된 규칙이 없습니다. + Add Rule로 추가하세요.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              {/* Save 시 위반된 규칙 번호 목록 */}
              {violations.length > 0 && (
                <div className="mt-3 rounded-lg border border-red-200 bg-red-50 p-3 dark:border-red-500/30 dark:bg-red-500/10">
                  <p className="mb-1.5 text-xs font-semibold text-red-700 dark:text-red-400">
                    위반된 규칙 (Violations)
                  </p>
                  <ul className="space-y-1">
                    {violations.map((violation, index) => (
                      <li
                        key={`${violation.ruleId}-${index}`}
                        className="text-xs text-red-600 dark:text-red-300"
                      >
                        <span className="font-mono font-semibold">
                          위반된 규칙 번호 : {violation.ruleId}
                        </span>
                        <span className="ml-1.5 text-red-500/80 dark:text-red-400/80">
                          — {violation.reason}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              <p className="mt-3 text-[11px] leading-relaxed text-gray-500 dark:text-gray-400">
                <span className="font-semibold">Advance config:</span> 해당 규칙에서는 서브넷 간의
                연결에서 특정 포트만 통신할 수 있습니다. Confidential 서브넷과 Open(Public) 서브넷의
                직접 연결은 허용되지 않습니다. Save를 누르면 정책 검사가 실행됩니다.
              </p>
            </div>

            {/* Prober 자동 생성 규칙 테이블 (읽기 전용) */}
            <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-theme-xs dark:border-gray-700 dark:bg-gray-800/50">
              <h5 className="mb-3 text-sm font-semibold text-gray-800 dark:text-white/90">
                Prober 자동 생성 규칙 (Auto-generated)
              </h5>
              <div className="overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700">
                <table className="w-full text-left text-xs">
                  <thead className="bg-gray-50 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
                    <tr>
                      <th className="border-b p-2 font-medium dark:border-gray-600">Rule ID</th>
                      <th className="border-b p-2 font-medium dark:border-gray-600">SRC Subnet</th>
                      <th className="border-b p-2 font-medium dark:border-gray-600">DST Subnet</th>
                      <th className="border-b p-2 font-medium dark:border-gray-600">Port</th>
                      <th className="border-b p-2 font-medium dark:border-gray-600">
                        수집 원문 (Note)
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200 text-gray-600 dark:divide-gray-700 dark:text-gray-300">
                    {autoRules.map((rule) => (
                      <tr key={rule.id}>
                        <td className="p-2 font-mono">{rule.id}</td>
                        <td className="p-2">{subnetLabel(rule.src)}</td>
                        <td className="p-2">{subnetLabel(rule.dst)}</td>
                        <td className="p-2 font-mono">{rule.port}</td>
                        <td className="max-w-[220px] truncate p-2 text-[11px] text-gray-500 dark:text-gray-400">
                          {rule.note === "" ? "—" : rule.note}
                        </td>
                      </tr>
                    ))}
                    {autoRules.length === 0 && (
                      <tr>
                        <td colSpan={5} className="p-3 text-center text-gray-400">
                          {loading
                            ? "불러오는 중..."
                            : "탐지된 기존 연결이 없습니다. Agent 가 수집한 방화벽 규칙이 여기에 나타납니다."}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
              <p className="mt-2 text-[11px] text-gray-500 dark:text-gray-400">
                Prober가 탐지한 기존 네트워크 연결 기반으로 자동 생성된 규칙입니다. (읽기 전용)
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* 하단 중앙 임시 토스트 (Message Type / Status Feedback) */}
      <div
        className="pointer-events-none fixed bottom-6 left-1/2 z-99999 flex w-full max-w-sm -translate-x-1/2 flex-col gap-2 px-4"
        aria-live="polite"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`pointer-events-auto flex items-start gap-3 rounded-xl border p-3.5 shadow-lg backdrop-blur-sm ${
              toast.type === "error"
                ? "border-red-300 bg-red-50/95 text-red-800 dark:border-red-500/40 dark:bg-red-500/15 dark:text-red-200"
                : toast.type === "warning"
                  ? "border-orange-300 bg-orange-50/95 text-orange-800 dark:border-orange-500/40 dark:bg-orange-500/15 dark:text-orange-200"
                  : "border-green-300 bg-green-50/95 text-green-800 dark:border-green-500/40 dark:bg-green-500/15 dark:text-green-200"
            }`}
          >
            <span className="mt-0.5 text-sm leading-none">
              {toast.type === "error" ? "🚨" : toast.type === "warning" ? "⚠️" : "✅"}
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-bold">{toast.title}</p>
              <p className="mt-0.5 text-[11px] leading-snug opacity-90">{toast.body}</p>
            </div>
            <button
              type="button"
              onClick={() => dismissToast(toast.id)}
              className="text-xs opacity-60 transition hover:opacity-100"
              aria-label="Dismiss message"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </>
  );
}
