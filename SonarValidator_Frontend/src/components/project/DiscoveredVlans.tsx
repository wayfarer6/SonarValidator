import { useApi } from "../../hooks/useApi";
import { getProjectDevices } from "../../lib/api/discovery";
import { observedVlans } from "../../lib/discovery";

export default function DiscoveredVlans({projectId}: {projectId?: string | null}) {
  const {data, loading, error, reload} = useApi(() => getProjectDevices(projectId), [projectId]);
  const rows = observedVlans(data ?? []);
  return <section className="my-5 rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-900" aria-label="수집된 VLAN">
    <div className="mb-3 flex items-center justify-between">
      <h3 className="font-semibold text-gray-800 dark:text-white">수집된 VLAN · {new Set(rows.map(r => r.vlan)).size}개 VLAN / {rows.length}개 장비 항목</h3>
      <button type="button" onClick={reload} disabled={loading} className="rounded-lg border px-3 py-1 text-sm dark:text-gray-200">VLAN 새로고침</button>
    </div>
    <p className="mb-3 text-sm text-gray-500">스위치의 access/trunk 포트와 라우터의 VLAN 인터페이스 수집 결과입니다. IP 대역은 주소가 수집된 장비에 표시됩니다. VLAN 번호만으로 IP 대역이나 장비 간 연결을 추정하지 않습니다.</p>
    {loading && <p>VLAN 수집 결과를 불러오는 중...</p>}
    {error && <p role="alert" className="text-error-600">{error}</p>}
    {!loading && !error && rows.length === 0 && <p className="text-sm text-gray-500">등록된 장비에서 아직 VLAN 정보가 수집되지 않았습니다.</p>}
    {rows.length > 0 && <div className="overflow-x-auto"><table className="w-full text-left text-sm text-gray-700 dark:text-gray-200">
      <thead><tr>{["장비", "VLAN", "Access / VLAN 인터페이스", "Trunk 포트", "이 장비에서 수집한 IP 대역"].map(h=><th key={h} className="border-b p-2">{h}</th>)}</tr></thead>
      <tbody>{rows.map(r=><tr key={`${r.agent}:${r.vlan}`}>
        <td className="border-b p-2">{r.agent}</td><td className="border-b p-2">{r.vlan}</td>
        <td className="border-b p-2">{r.access.join(", ") || "—"}</td><td className="border-b p-2">{r.trunk.join(", ") || "—"}</td>
        <td className="border-b p-2">{r.cidrs.join(", ") || "IP 대역 미수집 (L2 VLAN)"}</td>
      </tr>)}</tbody>
    </table></div>}
  </section>;
}
