import { Fragment } from "react";
import Badge from "../ui/badge/Badge";
import { TrashBinIcon, PlusIcon, AlertIcon } from "../../icons";
import type { ApiRule, ApiSubnet, ApiViolation } from "../../lib/api/types";
import { isForbiddenPair, zoneInfo } from "../../lib/policy/zones";

interface RuleEditorProps {
  /** 편집 중인 규칙 목록. */
  rules: ApiRule[];
  /** 서브넷 목록 (드롭다운 선택지). */
  subnets: ApiSubnet[];
  /** 규칙 필드 변경 콜백. */
  onChange: (ruleId: string, patch: Partial<ApiRule>) => void;
  /** 규칙 삭제 콜백. */
  onRemove: (ruleId: string) => void;
  /** 규칙 추가 콜백. */
  onAdd: () => void;
  /** 서버 검증 결과 (규칙별 위반 표시). */
  violations?: ApiViolation[];
  /** 읽기 전용 여부. */
  readOnly?: boolean;
}

/**
 * 연결 규칙 편집 표입니다.
 *
 * <h2>기존 UI 를 재사용한 방식</h2>
 * {@code NetworkSegmentationRule.tsx} 의 "정책 설정" 표 구조(SRC/DST 드롭다운,
 * Port 입력, Add Rule / Save 버튼)를 그대로 이어받았습니다. 다만 두 가지를
 * 추가했습니다.
 *
 * <ol>
 *   <li><b>금지 조합 즉시 경고</b>: SRC/DST 를 고르는 순간 등급을 건너뛰는
 *       조합이면 행을 빨갛게 칠하고 안내합니다. 저장 후에 알려주면 이미
 *       잘못된 규칙을 만들고 난 뒤가 됩니다.</li>
 *   <li><b>활성/비활성 토글</b>: 자동 수집된 규칙을 지우지 않고 검증에서
 *       제외할 수 있습니다. 삭제하면 재수집 시 되살아나므로, "무시" 를
 *       명시적으로 관리하는 편이 안전합니다.</li>
 * </ol>
 *
 * <p>서버 위반 결과가 있으면 해당 행에 사유와 반례 패킷을 함께 보여줍니다.
 * (BDD 가 계산한 재현 가능한 예시)
 */
export default function RuleEditor({
  rules,
  subnets,
  onChange,
  onRemove,
  onAdd,
  violations = [],
  readOnly = false,
}: RuleEditorProps) {
  const selectClass =
    "w-full rounded-lg border border-gray-300 bg-transparent px-2 py-1.5 text-xs text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white";
  const inputClass =
    "w-full rounded-lg border border-gray-300 bg-transparent px-2 py-1.5 text-xs text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white";

  /** 규칙 식별자 → 위반 목록 */
  const violationsByRule = new Map<string, ApiViolation[]>();
  for (const violation of violations) {
    const existing = violationsByRule.get(violation.rule_id);
    if (existing) existing.push(violation);
    else violationsByRule.set(violation.rule_id, [violation]);
  }

  const subnetLabel = (subnetId: string | null): string => {
    if (!subnetId) return "(미지정)";
    const subnet = subnets.find((item) => item.id === subnetId);
    if (!subnet) return subnetId;
    return `${subnet.cidr} (${subnet.subnet_class ?? "?"})`;
  };

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-theme-xs dark:border-gray-700 dark:bg-gray-800/50">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h5 className="text-sm font-semibold text-gray-800 dark:text-white/90">
            정책 설정 (Rule Configuration)
          </h5>
          <Badge size="sm" color="light">
            {rules.length}개
          </Badge>
          {violations.length > 0 && (
            <Badge size="sm" color="error">
              위반 {violations.length}
            </Badge>
          )}
        </div>
        {!readOnly && (
          <button
            type="button"
            onClick={onAdd}
            className="inline-flex items-center gap-1 rounded-lg border border-gray-300 bg-white px-2.5 py-1 text-xs font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
          >
            <PlusIcon className="size-3" />
            Add Rule
          </button>
        )}
      </div>

      {rules.length === 0 ? (
        <div className="flex h-24 items-center justify-center rounded-lg border border-dashed border-gray-200 text-sm text-gray-500 dark:border-gray-700 dark:text-gray-400">
          규칙이 없습니다. Add Rule 로 추가하세요.
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700">
          <table className="w-full text-left text-xs">
            <thead className="bg-gray-50 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
              <tr>
                <th className="border-b p-2 font-medium dark:border-gray-600">Rule ID</th>
                <th className="border-b p-2 font-medium dark:border-gray-600">SRC Subnet</th>
                <th className="border-b p-2 font-medium dark:border-gray-600">DST Subnet</th>
                <th className="border-b p-2 font-medium dark:border-gray-600">Port</th>
                <th className="border-b p-2 font-medium dark:border-gray-600">사용</th>
                {!readOnly && <th className="border-b p-2 dark:border-gray-600"></th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 text-gray-600 dark:divide-gray-700 dark:text-gray-300">
              {rules.map((rule) => {
                const sourceSubnet = subnets.find((item) => item.id === rule.src);
                const targetSubnet = subnets.find((item) => item.id === rule.dst);
                const forbidden = isForbiddenPair(
                  sourceSubnet?.subnet_class ?? null,
                  targetSubnet?.subnet_class ?? null,
                );
                const missingPort = rule.port === null;
                const ruleViolations = violationsByRule.get(rule.id) ?? [];
                const hasCritical = ruleViolations.some((item) => item.severity === "CRITICAL");
                const hasMajor = ruleViolations.some((item) => item.severity === "MAJOR");

                // 행 배경 우선순위: 서버 CRITICAL > 로컬 금지 조합 > MAJOR
                const rowClass = hasCritical || forbidden
                  ? "bg-error-50 dark:bg-error-500/10"
                  : hasMajor || missingPort
                    ? "bg-warning-50 dark:bg-warning-500/10"
                    : "";

                return (
                  <Fragment key={rule.id}>
                    <tr className={rowClass}>
                      <td className="p-2 font-mono align-top">
                        <div className="flex flex-col gap-1">
                          <span>{rule.id}</span>
                          {rule.origin === "DISCOVERED" && (
                            <Badge size="sm" color="info">
                              수집됨
                            </Badge>
                          )}
                        </div>
                      </td>
                      <td className="p-2 align-top">
                        <select
                          value={rule.src ?? ""}
                          disabled={readOnly}
                          onChange={(e) => onChange(rule.id, { src: e.target.value })}
                          className={selectClass}
                        >
                          <option value="">(선택)</option>
                          {subnets.map((subnet) => (
                            <option key={subnet.id} value={subnet.id}>
                              {subnet.cidr} ({subnet.subnet_class ?? "?"})
                            </option>
                          ))}
                        </select>
                        {rule.src && (
                          <span className="mt-0.5 block text-[10px] text-gray-400">
                            {subnetLabel(rule.src)}
                          </span>
                        )}
                      </td>
                      <td className="p-2 align-top">
                        <select
                          value={rule.dst ?? ""}
                          disabled={readOnly}
                          onChange={(e) => onChange(rule.id, { dst: e.target.value })}
                          className={selectClass}
                        >
                          <option value="">(선택)</option>
                          {subnets.map((subnet) => (
                            <option key={subnet.id} value={subnet.id}>
                              {subnet.cidr} ({subnet.subnet_class ?? "?"})
                            </option>
                          ))}
                        </select>
                        {rule.dst && (
                          <span className="mt-0.5 block text-[10px] text-gray-400">
                            {subnetLabel(rule.dst)}
                          </span>
                        )}
                      </td>
                      <td className="p-2 align-top">
                        <input
                          type="number"
                          min={1}
                          max={65535}
                          disabled={readOnly}
                          value={rule.port ?? ""}
                          placeholder="미지정"
                          onChange={(e) =>
                            onChange(rule.id, {
                              port: e.target.value === "" ? null : Number(e.target.value),
                            })
                          }
                          className={inputClass}
                        />
                        {missingPort && (
                          <span className="mt-0.5 block text-[10px] text-warning-600 dark:text-orange-400">
                            전체 포트 허용으로 해석됩니다
                          </span>
                        )}
                      </td>
                      <td className="p-2 align-top">
                        <label className="flex cursor-pointer items-center gap-1">
                          <input
                            type="checkbox"
                            checked={rule.enabled}
                            disabled={readOnly}
                            onChange={(e) => onChange(rule.id, { enabled: e.target.checked })}
                            className="size-3.5 rounded border-gray-300"
                          />
                          <span className="text-[10px]">{rule.enabled ? "검증 포함" : "제외"}</span>
                        </label>
                      </td>
                      {!readOnly && (
                        <td className="p-2 text-right align-top">
                          <button
                            type="button"
                            onClick={() => onRemove(rule.id)}
                            title="규칙 삭제"
                            className="rounded p-1 text-gray-400 transition hover:bg-error-50 hover:text-error-500 dark:hover:bg-error-500/10"
                          >
                            <TrashBinIcon className="size-4" />
                          </button>
                        </td>
                      )}
                    </tr>

                    {/* 위반/경고 상세 행 */}
                    {(ruleViolations.length > 0 || forbidden || missingPort) && (
                      <tr className={rowClass}>
                        <td colSpan={readOnly ? 5 : 6} className="px-2 pb-2 pt-0">
                          <div className="flex flex-col gap-1">
                            {ruleViolations.map((violation, index) => (
                              <div
                                key={`${rule.id}-v-${index}`}
                                className={`flex items-start gap-1.5 rounded px-2 py-1 text-[11px] ${
                                  violation.severity === "CRITICAL"
                                    ? "bg-error-100 text-error-700 dark:bg-error-500/20 dark:text-error-300"
                                    : "bg-warning-100 text-warning-700 dark:bg-warning-500/20 dark:text-orange-300"
                                }`}
                              >
                                <AlertIcon className="mt-0.5 size-3 shrink-0" />
                                <div className="flex flex-col">
                                  <span className="font-medium">
                                    [{violation.severity}] {violation.reason}
                                  </span>
                                  {violation.sampled_packet && violation.sampled_packet !== "- -> -" && (
                                    <span className="font-mono text-[10px] opacity-80">
                                      위반 예시 패킷: {violation.sampled_packet}
                                    </span>
                                  )}
                                </div>
                              </div>
                            ))}

                            {/* 서버 검증을 아직 돌리지 않았을 때의 로컬 안내 */}
                            {ruleViolations.length === 0 && (forbidden || missingPort) && (
                              <div className="flex items-center gap-1.5 rounded bg-warning-100 px-2 py-1 text-[11px] text-warning-700 dark:bg-warning-500/20 dark:text-orange-300">
                                <AlertIcon className="size-3 shrink-0" />
                                <span>
                                  {forbidden
                                    ? `${sourceSubnet?.subnet_class} ↔ ${targetSubnet?.subnet_class} 직접 연결은 금지됩니다. 저장 시 검증에서 오류가 보고됩니다.`
                                    : "허용 포트를 지정하세요. 비워두면 전체 포트가 열린 것으로 해석됩니다."}
                                </span>
                              </div>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* 금지 조합 요약: 표 아래에 한 번만 안내 */}
      {subnets.some((subnet) => zoneInfo(subnet.subnet_class)?.level === 3) &&
        subnets.some((subnet) => zoneInfo(subnet.subnet_class)?.level === 1) && (
          <p className="mt-2 text-[11px] text-gray-500 dark:text-gray-400">
            Confidential 등급 서브넷과 Open 등급 서브넷은 직접 연결할 수 없습니다.
            Sensitive 등급을 경유해야 합니다.
          </p>
        )}
    </div>
  );
}
