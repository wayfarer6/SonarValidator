import { NETWORK_ROLES } from "../../lib/policy/networkRoles";
import { useEffect, useId, useState } from "react";
import { Modal } from "../ui/modal";
import { updateProject } from "../../lib/api/projects";
import { getProjectForEditing } from "../../lib/api/discovery";
import type { ApiProject, ApiSubnet, NetworkRole, SubnetClass } from "../../lib/api/types";
import PeerSelector from "./PeerSelector";


export default function NodeConnectionModal({ projectId, agentId, expectedIp, connected, onClose }: {
  projectId: string; agentId: string; expectedIp?: string | null; connected: boolean; onClose: () => void;
}) {
  const titleId = useId();
  const [project, setProject] = useState<ApiProject | null>(null);
  const [draft, setDraft] = useState<ApiSubnet[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [dirty, setDirty] = useState(false);
  const [changedIds, setChangedIds] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  useEffect(() => {
    let active = true;
    getProjectForEditing(projectId).then(value => {
      if (!active) return;
      const owned = value.subnets.filter(item => item.agent_id === agentId);
      setProject(value); setDraft(owned); setSelectedId(owned[0]?.id ?? "");
    }).catch(cause => { if (active) setError(String(cause)); });
    return () => { active = false; };
  }, [projectId, agentId]);
  const selected = draft.find(item => item.id === selectedId);
  const allSubnets = (project?.subnets ?? []).map(item => draft.find(edit => edit.id === item.id) ?? item);
  const update = (patch: Partial<ApiSubnet>) => {
    setDraft(items => items.map(item => item.id === selectedId ? { ...item, ...patch, manually_edited: true } : item));
    setChangedIds(ids => new Set([...ids, selectedId]));
    setDirty(true); setSaved(false);
  };
  const close = () => { if (!dirty || window.confirm("저장하지 않은 변경을 취소할까요?")) onClose(); };
  const save = async () => {
    setSaving(true); setError(null);
    try {
      // Only merge this node's changes into the latest project revision.
      const latest = await getProjectForEditing(projectId);
      const edited = new Map(draft.filter(item => changedIds.has(item.id)).map(item => [item.id, item]));
      const subnets = latest.subnets.map(item => edited.get(item.id) ?? item);
      const value = await updateProject(projectId, { subnets });
      setProject(value); setDraft(value.subnets.filter(item => item.agent_id === agentId));
      setChangedIds(new Set()); setDirty(false); setSaved(true);
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setSaving(false); }
  };
  const input = "w-full rounded-lg border border-gray-300 bg-white p-3 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-900 dark:text-white";
  return <Modal isOpen onClose={close} showCloseButton={false} className="mx-4 max-w-5xl p-6 sm:p-8">
    <section role="dialog" aria-modal="true" aria-labelledby={titleId} data-testid="node-connection-modal" className="max-h-[85vh] overflow-y-auto">
      <header className="mb-6 flex items-start justify-between gap-4">
        <div><h2 id={titleId} className="text-xl font-semibold text-gray-900 dark:text-white">{agentId} 연결 관리</h2>
          <p className="mt-1 text-sm text-gray-500">{connected ? "온라인" : "오프라인"} · {projectId}</p></div>
        <button type="button" aria-label="연결 관리 닫기" onClick={close} className="rounded-lg border px-3 py-2 text-sm dark:text-white">닫기</button>
      </header>
      <dl className="mb-6 grid gap-3 rounded-xl bg-gray-50 p-4 text-sm sm:grid-cols-2 dark:bg-gray-800 dark:text-white">
        <div><dt className="text-gray-500">장비 관리 주소</dt><dd className="mt-1 font-mono">{expectedIp || "미등록"}</dd></div>
        <div><dt className="text-gray-500">Prober가 연결할 관리 서버</dt><dd className="mt-1 font-mono">{project?.management_server_ip || "미지정"}:{project?.management_server_port || "—"}</dd></div>
      </dl>
      {error && <p role="alert" className="mb-4 text-sm text-error-600">{error}</p>}
      {!project && !error && <p>서브넷을 불러오는 중...</p>}
      {project && draft.length === 0 && <p className="py-8 text-gray-500">이 장비에서 수집된 서브넷이 없습니다. Prober 수집 상태와 담당 Agent를 확인하세요.</p>}
      {selected && <div className="space-y-5">
        <label className="block text-sm font-medium dark:text-white">서브넷 선택
          <select className={`${input} mt-2`} aria-label="서브넷 선택" value={selectedId} onChange={event => setSelectedId(event.target.value)}>
            {draft.map(item => <option key={item.id} value={item.id}>{item.name || item.id} · {item.cidr || "대역 미확인"}</option>)}
          </select>
        </label>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="text-sm dark:text-white">서브넷 이름<input className={`${input} mt-2`} aria-label="서브넷 이름" value={selected.name ?? ""} onChange={event => update({ name: event.target.value })} /></label>
          <label className="text-sm dark:text-white">CIDR<input className={`${input} mt-2 font-mono`} aria-label="서브넷 CIDR" value={selected.cidr} onChange={event => update({ cidr: event.target.value })} /></label>
          <label className="text-sm dark:text-white">네트워크 역할
            <select className={`${input} mt-2`} aria-label="네트워크 역할" value={selected.network_role ?? ""} onChange={event => update({ network_role: event.target.value as NetworkRole || null })}>
              <option value="">미지정</option>{NETWORK_ROLES.map(role => <option key={role.value} value={role.value}>{role.label}</option>)}
            </select>
          </label>
          <label className="text-sm dark:text-white">CSO 보안 등급
            <select className={`${input} mt-2`} aria-label="CSO 보안 등급" value={selected.subnet_class ?? ""} onChange={event => update({ subnet_class: event.target.value as SubnetClass || null })}>
              <option value="">미분류 · 사용자 확인 필요</option><option value="Confidential">Confidential · 기밀망</option><option value="Sensitive">Sensitive · 내부 업무망</option><option value="Open">Open · 공개망</option>
            </select>
          </label>
        </div>
        <div><h3 className="mb-3 text-sm font-semibold dark:text-white">연결 허용 대상</h3>
          <PeerSelector subnet={selected} subnets={allSubnets} onChange={peers => update({ allowed_peers: peers })} />
        </div>
      </div>}
      <footer className="mt-7 flex flex-wrap items-center justify-between gap-3 border-t pt-4">
        <p className="text-xs text-gray-500">저장은 정책 초안에만 반영됩니다. 장비 적용은 검증 후 별도로 실행하세요.</p>
        <div className="flex items-center gap-3">{saved && <span role="status" className="text-sm text-success-600">저장됨</span>}
          <button type="button" onClick={save} disabled={!dirty || saving} className="rounded-lg bg-brand-500 px-5 py-2.5 text-sm text-white disabled:opacity-40">{saving ? "저장 중..." : "변경 저장"}</button>
        </div>
      </footer>
    </section>
  </Modal>;
}
