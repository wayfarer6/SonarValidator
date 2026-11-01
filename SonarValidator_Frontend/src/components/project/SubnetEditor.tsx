import { useState } from "react";
import { Modal } from "../ui/modal";
import PeerSelector from "./PeerSelector";
import { NETWORK_ROLES } from "../../lib/policy/networkRoles";
import Badge from "../ui/badge/Badge";
import { TrashBinIcon, PlusIcon } from "../../icons";
import type { SubnetClass, ApiSubnet, NetworkRole } from "../../lib/api/types";
import { ZONE_CLASSES, zoneInfo } from "../../lib/policy/zones";

interface AgentOption {
  agent_id: string;
  label?: string | null;
}

interface SubnetEditorProps {
  subnets: ApiSubnet[];
  onChange: (subnetId: string, subnetClass: SubnetClass | null) => void;
  onNameChange?: (subnetId: string, name: string) => void;
  onCidrChange?: (subnetId: string, cidr: string) => void;
  onAgentChange?: (subnetId: string, agentId: string | null) => void;
  onAllowedPeersChange?: (subnetId: string, peers: string[]) => void;
  agents?: AgentOption[];
  onRoleChange?: (subnetId: string, role: NetworkRole | null) => void;
  onRemove: (subnetId: string) => void;
  onAdd: () => void;
  violatingSubnetIds?: Set<string>;
  readOnly?: boolean;
}

export default function SubnetEditor({
  subnets,
  onChange,
  onNameChange,
  onCidrChange,
  onAgentChange,
  onAllowedPeersChange,
  agents = [],
  onRoleChange,
  onRemove,
  onAdd,
  violatingSubnetIds,
  readOnly = false,
}: SubnetEditorProps) {
  const selectClass =
    "w-full rounded-lg border border-gray-300 bg-transparent px-2 py-1.5 text-xs text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white";
  const inputClass =
    "w-full rounded-lg border border-gray-300 bg-transparent px-2 py-1.5 font-mono text-xs text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white";

  const [connectionId, setConnectionId] = useState<string | null>(null);
  const [connectionDraft, setConnectionDraft] = useState<string[]>([]);
  const connectionSubnet = subnets.find(item => item.id === connectionId);

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
        <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-700">
          <table className="w-full min-w-[1000px] text-left text-sm">
            <thead className="bg-gray-50 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
              <tr>
                <th className="border-b p-2 font-medium dark:border-gray-600">서브넷</th>
                <th className="border-b p-2 font-medium dark:border-gray-600">CIDR</th>
                <th className="border-b p-2 font-medium dark:border-gray-600">CSO 보안 등급</th>
                <th className="border-b p-2 font-medium dark:border-gray-600">네트워크 역할</th>
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
                        aria-label={`${subnet.name || subnet.id} CSO 보안 등급`}
                        value={subnet.subnet_class ?? ""}
                        disabled={readOnly}
                        onChange={(e) => onChange(subnet.id, (e.target.value || null) as SubnetClass | null)}
                        className={`${selectClass} min-w-[140px]`}
                      >
                        <option value="">미분류 — 사용자 지정 필요</option>
                        {ZONE_CLASSES.map((zone) => (
                          <option key={zone.value} value={zone.value}>
                            {zone.label}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="min-w-[170px] p-2">
                      <select aria-label={`${subnet.name || subnet.id} 네트워크 역할`} value={subnet.network_role ?? ""}
                        disabled={readOnly || !onRoleChange} className={`${selectClass} min-w-[140px]`}
                        onChange={event => onRoleChange?.(subnet.id, event.target.value as NetworkRole || null)}>
                        <option value="">미지정</option>{NETWORK_ROLES.map(role => <option key={role.value} value={role.value}>{role.label}</option>)}
                      </select>
                    </td>
                    <td className="p-2">
                      <select
                        value={subnet.agent_id ?? ""}
                        disabled={readOnly || !onAgentChange}
                        onChange={(event) =>
                          onAgentChange?.(subnet.id, event.target.value || null)
                        }
                        className={`${selectClass} min-w-[140px]`}
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
                      <button type="button" disabled={!onAllowedPeersChange} className="whitespace-nowrap rounded-lg border border-brand-300 px-3 py-2 text-brand-600 disabled:opacity-40"
                        aria-label={`${subnet.name || subnet.id} 서브넷 연결 관리`}
                        onClick={() => { setConnectionId(subnet.id); setConnectionDraft(subnet.allowed_peers ?? []); }}>
                        {(subnet.allowed_peers ?? []).length ? `${subnet.allowed_peers?.length}개 대상 · 연결 관리` : "제한 없음 · 연결 관리"}
                      </button>
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
      {connectionSubnet && <Modal ariaLabel="서브넷 연결 관리" isOpen onClose={() => setConnectionId(null)} showCloseButton={false} className="mx-4 max-w-2xl p-7">
        <section className="max-h-[80vh] overflow-y-auto">
          <h3 className="mb-1 text-lg font-semibold dark:text-white">{connectionSubnet.name || connectionSubnet.id}</h3>
          <p className="mb-5 font-mono text-sm text-gray-500">{connectionSubnet.cidr}</p>
          <PeerSelector subnet={{ ...connectionSubnet, allowed_peers: connectionDraft }} subnets={subnets} onChange={setConnectionDraft} disabled={readOnly} />
          <footer className="mt-6 flex justify-end gap-3">
            <button type="button" onClick={() => setConnectionId(null)} className="rounded-lg border px-4 py-2 text-sm dark:text-white">취소</button>
            {!readOnly && <button type="button" onClick={() => { onAllowedPeersChange?.(connectionSubnet.id, connectionDraft); setConnectionId(null); }} className="rounded-lg bg-brand-500 px-4 py-2 text-sm text-white">프로젝트 초안에 반영</button>}
          </footer>
        </section>
      </Modal>}
    </div>
  );
}
