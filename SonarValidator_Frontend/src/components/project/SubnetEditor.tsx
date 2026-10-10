import Badge from "../ui/badge/Badge";
import { TrashBinIcon, PlusIcon } from "../../icons";
import type { SubnetClass, ApiSubnet } from "../../lib/api/types";
import { ZONE_CLASSES, zoneInfo } from "../../lib/policy/zones";

interface AgentOption {
  agent_id: string;
  label?: string | null;
}

interface SubnetEditorProps {
  /** 편집 중인 서브넷 목록. */
  subnets: ApiSubnet[];
  /** 등급 변경 콜백. */
  onChange: (subnetId: string, subnetClass: SubnetClass | null) => void;
  /** CIDR 변경 콜백. */
  onNameChange?: (subnetId: string, name: string) => void;
  onCidrChange?: (subnetId: string, cidr: string) => void;
  /** Agent 담당자 변경 콜백. */
  onAgentChange?: (subnetId: string, agentId: string | null) => void;
  /**
   * 허용 대상 변경 콜백.
   *
   * <p>값이 바뀌면 그 서브넷의 연결 허용 목록 전체가 교체됩니다.
   * (추가/삭제를 별도 콜백으로 나누지 않는 이유: 목록이 짧고, 화면에서
   *  체크박스로 토글하는 동작이 곧 "전체 집합" 의 변경이라 교체가 더 단순합니다)
   */
  onAllowedPeersChange?: (subnetId: string, peers: string[]) => void;
  /** 프로젝트에서 선택 가능한 Agent. */
  agents?: AgentOption[];
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
  onNameChange,
  onCidrChange,
  onAgentChange,
  onAllowedPeersChange,
  agents = [],
  onRemove,
  onAdd,
  violatingSubnetIds,
  readOnly = false,
}: SubnetEditorProps) {
  const selectClass =
    "w-full rounded-lg border border-gray-300 bg-transparent px-2 py-1.5 text-xs text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white";
  const inputClass =
    "w-full rounded-lg border border-gray-300 bg-transparent px-2 py-1.5 font-mono text-xs text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white";

  /**
   * 허용 목록에 넣을 수 있는 후보입니다.
   *
   * <p>프로젝트의 다른 서브넷들 + 인터넷입니다. 자기 자신은 넣지 않습니다 —
   * 같은 서브넷 안의 통신은 라우터를 지나지 않으므로 ACL 로 막을 수 없고,
   * 목록에 넣어 두면 "막을 수 있는데 안 막았다" 는 오해를 만듭니다.
   *
   * <p>⚠️ 인터넷을 넣는 이유: 기밀망이 인터넷으로 나가면 안 된다는 요구는
   * 대역으로 표현할 수 없습니다(인터넷 대역이 없음). 그래서 특수 토큰
   * {@code internet} 을 두고, 체크하지 않으면 기본 경로 전체를 막습니다.
   */
  const peers: { value: string; label: string; title: string; internet: boolean }[] = [
    ...subnets.map((candidate) => ({
      value: candidate.id,
      label: candidate.vlan_id ? `VLAN ${candidate.vlan_id}` : candidate.id,
      title: `${candidate.cidr || "대역 미수집"} (${candidate.subnet_class ?? "미분류"})`,
      internet: false,
    })),
    {
      value: "internet",
      label: "인터넷",
      title: "체크하지 않으면 기본 경로(0.0.0.0/0)를 차단합니다",
      internet: true,
    },
  ];

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
                <th className="border-b p-2 font-medium dark:border-gray-600">담당 Agent</th>
                <th className="border-b p-2 font-medium dark:border-gray-600">
                  연결 허용 대상
                </th>
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
                        <span>{subnet.vlan_id ? `VLAN ${subnet.vlan_id}` : subnet.id}</span>
                        <input aria-label={`${subnet.id} 이름`} value={subnet.name ?? ""}
                          disabled={readOnly || !onNameChange} className={inputClass}
                          onChange={e => onNameChange?.(subnet.id, e.target.value)} placeholder="VLAN/서브넷 이름" />
                      </div>
                    </td>
                    <td className="p-2">
                      {/* CIDR 은 검증의 기준이므로 직접 고칠 수 있어야 합니다.
                          자동 수집값이 틀렸거나 대역을 좁히려는 경우가 흔합니다. */}
                      <input
                        type="text"
                        value={subnet.cidr}
                        disabled={readOnly}
                        placeholder="IP 대역 미수집 (CIDR 입력)"
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
                        value={subnet.subnet_class ?? ""}
                        disabled={readOnly}
                        onChange={(e) => onChange(subnet.id, (e.target.value || null) as SubnetClass | null)}
                        className={selectClass}
                      >
                        <option value="">미분류 — 사용자 지정 필요</option>
                        {ZONE_CLASSES.map((zone) => (
                          <option key={zone.value} value={zone.value}>
                            {zone.label}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="p-2">
                      <select
                        value={subnet.agent_id ?? ""}
                        disabled={readOnly || !onAgentChange}
                        onChange={(event) =>
                          onAgentChange?.(subnet.id, event.target.value || null)
                        }
                        className={selectClass}
                        aria-label={`${subnet.name ?? subnet.id} 담당 Agent`}
                      >
                        <option value="">담당 Agent 미지정</option>
                        {agents.map((agent) => (
                          <option key={agent.agent_id} value={agent.agent_id}>
                            {agent.label || agent.agent_id}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="p-2 align-top">
                      {/*
                        연결 허용 목록입니다.

                        ⚠️ "제한 없음" 과 "아무것도 허용 안 함" 은 다릅니다.
                           빈 목록은 제한 없음(기존 동작)이고, 하나라도 체크하면
                           체크되지 않은 상대가 배포 시 차단됩니다. 그래서
                           아무것도 체크하지 않은 상태가 곧 "해제" 입니다.
                      */}
                      {!onAllowedPeersChange ? (
                        <span className="text-[11px] text-gray-400">—</span>
                      ) : (
                        <div className="flex min-w-[9rem] flex-col gap-0.5">
                          {(subnet.allowed_peers ?? []).length === 0 ? (
                            <span className="text-[11px] text-gray-400">제한 없음</span>
                          ) : null}
                          {peers.map((peer) => {
                            const checked = (subnet.allowed_peers ?? []).includes(peer.value);
                            return (
                              <label
                                key={peer.value}
                                className="flex cursor-pointer items-center gap-1 text-[11px] leading-4"
                                title={peer.title}
                              >
                                <input
                                  type="checkbox"
                                  checked={checked}
                                  disabled={readOnly}
                                  onChange={() => {
                                    const current = subnet.allowed_peers ?? [];
                                    onAllowedPeersChange(
                                      subnet.id,
                                      checked
                                        ? current.filter((item) => item !== peer.value)
                                        : [...current, peer.value],
                                    );
                                  }}
                                  className="size-3 accent-brand-500"
                                />
                                <span className={peer.internet ? "font-medium" : ""}>
                                  {peer.label}
                                </span>
                              </label>
                            );
                          })}
                        </div>
                      )}
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
