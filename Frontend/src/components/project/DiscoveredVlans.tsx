import { useCallback, useEffect, useMemo, useState } from "react";
import { useApi } from "../../hooks/useApi";
import { getProjectDevices, listProjectVlanNames, saveProjectVlanName } from "../../lib/api/discovery";
import { observedVlans } from "../../lib/discovery";

/** 행 하나의 이름 저장 상태입니다. */
type SaveState = "idle" | "saving" | "saved" | "error";

/**
 * 장비에서 <b>수집한 VLAN</b> 목록과 이름 편집을 보여 줍니다.
 *
 * <h2>⚠️ 왜 이름 편집이 여기 있어야 하는가</h2>
 * <p>서브넷 편집 화면은 {@code cidr} 이 있는 행만 다룹니다. 그런데 Open vSwitch
 * 의 VLAN 은 주소가 없는 <b>L2 전용</b>이 대부분이라(실측: Switch-1
 * VLAN111/112 는 access 포트와 uplink trunk 만 있음) 그 화면에는 아예 나타나지
 * 않습니다. 그래서 이름을 붙일 방법이 없었습니다.
 *
 * <p>이 표는 수집 결과(장비 텔레메트리)를 그대로 보여 주므로 L2 VLAN 도
 * 들어옵니다. 여기서 이름을 붙이면 {@code /api/v1/projects/{id}/vlans} 에
 * 저장되고, 이름은 <b>등급·대역·집행 규칙과 별개</b>라 정책을 건드리지 않습니다.
 */
export default function DiscoveredVlans({projectId}: {projectId?: string | null}) {
  const {data, loading, error, reload} = useApi(() => getProjectDevices(projectId), [projectId]);
  const names = useApi(() => (projectId ? listProjectVlanNames(projectId) : Promise.resolve([])), [projectId]);
  const rows = observedVlans(data ?? []);

  // 편집 중인 입력값과 저장 상태를 행(`agent|vlan`)별로 둡니다.
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [states, setStates] = useState<Record<string, SaveState>>({});
  const [message, setMessage] = useState<string | null>(null);

  // 서버가 준 저장 이름을 (agent|vlan) → name 으로 폅니다.
  const savedNames = useMemo(() => {
    const map = new Map<string, string>();
    for (const entry of names.data ?? []) {
      if (entry.name) map.set(`${entry.agent_id.toLowerCase()}|${entry.vlan_id}`, entry.name);
    }
    return map;
  }, [names.data]);

  const rowKey = useCallback((agent: string, vlan: number) => `${agent.toLowerCase()}|${vlan}`, []);

  // 서버 응답이 바뀌면 입력값을 저장된 이름(없으면 장비 이름)으로 맞춥니다.
  // ⚠️ 편집 중인 값을 덮어쓰지 않도록 "아직 건드리지 않은 행"만 채웁니다.
  const rowKeys = rows.map(r => rowKey(r.agent, r.vlan)).join(",");
  useEffect(() => {
    setDrafts(previous => {
      const next = {...previous};
      for (const row of rows) {
        const key = rowKey(row.agent, row.vlan);
        if (next[key] === undefined) {
          next[key] = savedNames.get(key) ?? row.name ?? `VLAN${row.vlan}`;
        }
      }
      return next;
    });
    // rows/savedNames 는 매 렌더 새로 만들어지므로 키 목록으로만 의존성을 잡습니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [savedNames, rowKeys]);

  const save = async (agent: string, vlan: number) => {
    if (!projectId) {
      setMessage("프로젝트를 먼저 저장한 뒤 이름을 붙일 수 있습니다.");
      return;
    }
    const key = rowKey(agent, vlan);
    const value = (drafts[key] ?? "").trim();
    setStates(previous => ({...previous, [key]: "saving"}));
    setMessage(null);
    try {
      await saveProjectVlanName(projectId, agent, vlan, value);
      setStates(previous => ({...previous, [key]: "saved"}));
      setMessage(`저장했습니다: ${agent} VLAN${vlan} → ${value || "미지정"}`);
      names.reload();
    } catch (saveError) {
      setStates(previous => ({...previous, [key]: "error"}));
      setMessage(`저장에 실패했습니다: ${(saveError as Error).message}`);
    }
  };

  return <section className="my-5 rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-900" aria-label="수집된 VLAN">
    <div className="mb-3 flex items-center justify-between">
      <h3 className="font-semibold text-gray-800 dark:text-white">수집된 VLAN · {new Set(rows.map(r => r.vlan)).size}개 VLAN / {rows.length}개 장비 항목</h3>
      <button type="button" onClick={() => { reload(); names.reload(); }} disabled={loading} className="rounded-lg border px-3 py-1 text-sm dark:text-gray-200">VLAN 새로고침</button>
    </div>
    <p className="mb-3 text-sm text-gray-500">스위치의 access/trunk 포트와 라우터의 VLAN 인터페이스 수집 결과입니다. 주소가 없는 L2 전용 VLAN 도 표시하며, IP 대역은 <b>그 장비에서 실제로 수집된 주소만</b> 씁니다. VLAN 번호만으로 대역이나 장비 간 연결을 추정하지 않습니다.</p>
    <p className="mb-3 text-sm text-gray-500">이름은 <b>표시용</b>이며 등급·대역·집행 규칙을 바꾸지 않습니다. 비우고 저장하면 미지정으로 돌아갑니다.</p>
    {message && <p role="status" className="mb-3 text-sm text-brand-600 dark:text-brand-400">{message}</p>}
    {loading && <p>VLAN 수집 결과를 불러오는 중...</p>}
    {error && <p role="alert" className="text-error-600">{error}</p>}
    {names.error && <p role="alert" className="text-error-600">저장된 VLAN 이름을 불러오지 못했습니다: {names.error}</p>}
    {!loading && !error && rows.length === 0 && <p className="text-sm text-gray-500">수집된 VLAN 이 없습니다. 스위치의 access/trunk 포트가 수집되면 여기에 나타납니다.</p>}
    {rows.length > 0 && <div className="overflow-x-auto"><table className="w-full text-left text-sm text-gray-700 dark:text-gray-200">
      <thead><tr>{["장비", "VLAN", "이름", "Access / VLAN 인터페이스", "Trunk 포트", "이 장비에서 수집한 IP 대역"].map(h=><th key={h} className="border-b p-2">{h}</th>)}</tr></thead>
      <tbody>{rows.map(r=>{
        const key = rowKey(r.agent, r.vlan);
        const state = states[key] ?? "idle";
        const stored = savedNames.get(key);
        return <tr key={`${r.agent}:${r.vlan}`}>
          <td className="border-b p-2">{r.agent}</td>
          <td className="border-b p-2">{r.vlan}</td>
          <td className="border-b p-2">
            <div className="flex items-center gap-2">
              <input
                aria-label={`${r.agent} VLAN${r.vlan} 이름`}
                value={drafts[key] ?? ""}
                onChange={event => setDrafts(previous => ({...previous, [key]: event.target.value}))}
                onKeyDown={event => { if (event.key === "Enter") void save(r.agent, r.vlan); }}
                placeholder={`VLAN${r.vlan}`}
                className="w-48 rounded-lg border border-gray-300 bg-transparent px-2.5 py-1.5 text-sm dark:border-gray-700 dark:text-white"
              />
              <button
                type="button"
                onClick={() => void save(r.agent, r.vlan)}
                disabled={state === "saving"}
                className="rounded-lg border px-2.5 py-1.5 text-xs dark:text-gray-200 disabled:opacity-50"
              >
                {state === "saving" ? "저장 중" : "저장"}
              </button>
              {stored && state !== "error" && <span className="text-xs text-gray-400">저장됨</span>}
              {state === "error" && <span className="text-xs text-error-600">실패</span>}
            </div>
          </td>
          <td className="border-b p-2">{r.access.join(", ") || "—"}</td><td className="border-b p-2">{r.trunk.join(", ") || "—"}</td>
          <td className="border-b p-2">{r.cidrs.join(", ") || "IP 대역 미수집 (L2 VLAN)"}</td>
        </tr>;
      })}</tbody>
    </table></div>}
  </section>;
}
