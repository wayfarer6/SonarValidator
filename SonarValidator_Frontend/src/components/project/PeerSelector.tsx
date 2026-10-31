import type { ApiSubnet } from "../../lib/api/types";

export default function PeerSelector({ subnet, subnets, onChange, disabled = false }: {
  subnet: ApiSubnet;
  subnets: ApiSubnet[];
  onChange: (peers: string[]) => void;
  disabled?: boolean;
}) {
  const peers = subnet.allowed_peers ?? [];
  const choices = [
    ...subnets.filter(item => item.id !== subnet.id).map(item => ({
      value: item.id,
      label: `${item.name || item.agent_id || "서브넷"} · ${item.cidr || "대역 미확인"}`,
    })),
    { value: "internet", label: "인터넷 · 기본 경로" },
  ];
  const remove = (value: string) => {
    if (peers.length === 1 && !window.confirm("마지막 대상을 삭제하면 연결 제한이 해제됩니다. 계속할까요?")) return;
    onChange(peers.filter(item => item !== value));
  };
  return <div className="space-y-3" data-testid="peer-selector">
    <p className="text-sm font-medium text-gray-800 dark:text-white">
      {peers.length ? `선택한 ${peers.length}개 대상만 허용` : "제한 없음"}
    </p>
    <select aria-label="허용 대상 추가" value="" disabled={disabled}
      className="w-full rounded-lg border border-gray-300 bg-white p-3 text-sm dark:border-gray-700 dark:bg-gray-900 dark:text-white"
      onChange={event => { if (event.target.value) onChange([...peers, event.target.value]); }}>
      <option value="">연결할 서브넷 또는 인터넷 선택</option>
      {choices.filter(item => !peers.includes(item.value)).map(item =>
        <option key={item.value} value={item.value}>{item.label}</option>)}
    </select>
    {peers.length > 0 && <ul className="space-y-2">
      {peers.map(value => <li key={value} className="flex items-center justify-between gap-3 rounded-lg bg-gray-50 p-3 text-sm dark:bg-gray-800 dark:text-white">
        <span>{choices.find(item => item.value === value)?.label ?? value}</span>
        {!disabled && <button type="button" onClick={() => remove(value)} aria-label={`${value} 허용 대상 제거`} className="shrink-0 text-error-600">제거</button>}
      </li>)}
    </ul>}
    <p className="text-xs leading-relaxed text-gray-500 dark:text-gray-400">
      대상을 추가하면 목록 밖의 연결을 제한합니다. 모두 해제하면 제한 없음으로 돌아갑니다. CSO 등급 규칙은 별도로 유지됩니다.
    </p>
  </div>;
}
