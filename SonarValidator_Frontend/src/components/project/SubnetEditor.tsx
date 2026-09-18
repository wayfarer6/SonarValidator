import Badge from "../ui/badge/Badge";
import { TrashBinIcon, PlusIcon } from "../../icons";
import type { SubnetClass, ApiSubnet } from "../../lib/api/types";
import { ZONE_CLASSES, zoneInfo } from "../../lib/policy/zones";

interface SubnetEditorProps {
  /** 편집 중인 서브넷 목록. */
  subnets: ApiSubnet[];
  /** 등급 변경 콜백. */
  onChange: (subnetId: string, subnetClass: SubnetClass) => void;
  /** CIDR 변경 콜백. */
  onCidrChange?: (subnetId: string, cidr: string) => void;
  /** 삭제 콜백. */
  onRemove: (subnetId: string) => void;
  /** 새 서브넷 추가 콜백. */
  onAdd: () => void;
  /** 위반이 있는 서브넷 식별자 집합 (행 강조용). */
  violatingSubnetIds?: Set<string>;
  /** 읽기 전용 여부. */
  readOnly?: boolean;
}

/**
 * 서브넷 등급 편집 표입니다.
 *
 * <h2>기존 UI 를 재사용한 방식</h2>
 * {@code SubnetAdvanceConfiguration.tsx} 가 한 번에 서브넷 하나를 고르는
 * 방식이었다면, 여기서는 <b>전체 목록을 표로</b> 보여주고 등급만 바꿉니다.
 * 프로젝트 편집은 "여러 서브넷의 등급을 한 번에 확인하고 고치는" 작업이라
 * 표 형태가 맞습니다. (드롭다운/버튼/배지 스타일은 기존 화면과 동일하게
 * 맞췄습니다.)
 *
 * <h2>자동 수집 항목 표시</h2>
 * {@code manually_edited=false} 인 서브넷은 "수집됨" 배지로 표시합니다.
 * 운영자가 아직 등급을 확인하지 않았다는 뜻이므로, 확인을 유도합니다.
 */
export default function SubnetEditor({
  subnets,
  onChange,
  onCidrChange,
  onRemove,
  onAdd,
  violatingSubnetIds,
  readOnly = false,
}: SubnetEditorProps) {
  const selectClass =
    "w-full rounded-lg border border-gray-300 bg-transparent px-2 py-1.5 text-xs text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white";
  const inputClass =
    "w-full rounded-lg border border-gray-300 bg-transparent px-2 py-1.5 font-mono text-xs text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white";

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-theme-xs dark:border-gray-700 dark:bg-gray-800/50">
      {/* 헤더: 제목 + 요약 + 추가 버튼 */}
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h5 className="text-sm font-semibold text-gray-800 dark:text-white/90">
            서브넷 등급 설정
          </h5>
          <Badge size="sm" color="light">
            {subnets.length}개
          </Badge>
        </div>
        {!readOnly && (
          <button
            type="button"
            onClick={onAdd}
            className="inline-flex items-center gap-1 rounded-lg border border-gray-300 bg-white px-2.5 py-1 text-xs font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
          >
            <PlusIcon className="size-3" />
            Add Subnet
          </button>
        )}
      </div>

      {/* 등급 범례: 규칙을 모르는 사용자에게 기준을 알려줍니다 */}
      <div className="mb-3 flex flex-wrap gap-2 text-[11px]">
        {ZONE_CLASSES.map((zone) => (
          <span key={zone.value} className={`rounded px-2 py-0.5 ${zone.badgeClass}`}>
            {zone.label} — {zone.description}
          </span>
        ))}
      </div>

      {subnets.length === 0 ? (
        <div className="flex h-24 items-center justify-center rounded-lg border border-dashed border-gray-200 text-sm text-gray-500 dark:border-gray-700 dark:text-gray-400">
          서브넷이 없습니다. Add Subnet 으로 추가하세요.
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700">
          <table className="w-full text-left text-xs">
            <thead className="bg-gray-50 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
              <tr>
                <th className="border-b p-2 font-medium dark:border-gray-600">Subnet ID</th>
                <th className="border-b p-2 font-medium dark:border-gray-600">CIDR</th>
                <th className="border-b p-2 font-medium dark:border-gray-600">Class</th>
                <th className="border-b p-2 font-medium dark:border-gray-600">Source</th>
                {!readOnly && <th className="border-b p-2 dark:border-gray-600"></th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 text-gray-600 dark:divide-gray-700 dark:text-gray-300">
              {subnets.map((subnet) => {
                const info = zoneInfo(subnet.subnet_class);
                const violating = violatingSubnetIds?.has(subnet.id) ?? false;
                return (
                  <tr
                    key={subnet.id}
                    className={violating ? "bg-error-50 dark:bg-error-500/10" : info?.rowClass}
                  >
                    <td className="p-2 font-mono">
                      <div className="flex flex-col gap-1">
                        <span>{subnet.id}</span>
                        {subnet.name && (
                          <span className="text-[10px] text-gray-400">{subnet.name}</span>
                        )}
                      </div>
                    </td>
                    <td className="p-2">
                      {/* CIDR 은 검증의 기준이므로 직접 고칠 수 있어야 합니다.
                          자동 수집값이 틀렸거나 대역을 좁히려는 경우가 흔합니다. */}
                      <input
                        type="text"
                        value={subnet.cidr}
                        disabled={readOnly}
                        placeholder="10.0.0.0/24"
                        onChange={(e) =>
                          onCidrChange
                            ? onCidrChange(subnet.id, e.target.value)
                            : undefined
                        }
                        className={inputClass}
                      />
                    </td>
                    <td className="p-2">
                      <select
                        value={subnet.subnet_class ?? "Open"}
                        disabled={readOnly}
                        onChange={(e) => onChange(subnet.id, e.target.value as SubnetClass)}
                        className={selectClass}
                      >
                        {ZONE_CLASSES.map((zone) => (
                          <option key={zone.value} value={zone.value}>
                            {zone.label}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="p-2">
                      {subnet.manually_edited ? (
                        <Badge size="sm" color="success">
                          확인됨
                        </Badge>
                      ) : (
                        <Badge size="sm" color="warning">
                          수집됨
                        </Badge>
                      )}
                    </td>
                    {!readOnly && (
                      <td className="p-2 text-right">
                        <button
                          type="button"
                          onClick={() => onRemove(subnet.id)}
                          title="서브넷 삭제"
                          className="rounded p-1 text-gray-400 transition hover:bg-error-50 hover:text-error-500 dark:hover:bg-error-500/10"
                        >
                          <TrashBinIcon className="size-4" />
                        </button>
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
