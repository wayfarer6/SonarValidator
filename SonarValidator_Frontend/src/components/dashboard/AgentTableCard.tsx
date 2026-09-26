import { useMemo, useState } from "react";
import { Link } from "react-router";
import { BoxIconLine } from "../../icons";
import { useApi } from "../../hooks/useApi";
import { getAllDiscoveredDevices, listAgentOverview } from "../../lib/api";
import {
  AGENT_STATE_LABEL,
  mergeOverviewWithDevices,
  normalizeDeviceType,
} from "../../lib/agentView";

const DEVICE_STYLE: Record<string, string> = {
  Router: "bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400",
  Switch: "bg-green-50 text-green-600 dark:bg-green-500/10 dark:text-green-400",
  Firewall: "bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400",
  VM: "bg-purple-50 text-purple-600 dark:bg-purple-500/10 dark:text-purple-400",
};

const DEVICE_FILTERS: ("All" | "Router" | "Switch" | "Firewall" | "VM")[] = [
  "All",
  "Router",
  "Switch",
  "Firewall",
  "VM",
];

// Agent 리스트 카드: id, 이름, IP, 장비 타입, 연결 상태
export default function AgentTableCard() {
  const [filter, setFilter] = useState<"All" | "Router" | "Switch" | "Firewall" | "VM">("All");

  // 통합 현황이 1순위, 실패하면 연결 목록으로 폴백합니다.
  // (구버전 서버에는 /overview 가 없을 수 있습니다)
  const overview = useApi(() => listAgentOverview(), []);
  const devices = useApi(() => getAllDiscoveredDevices(), []);

  const rows = useMemo(() => {
    const agentRows = overview.data?.agents ?? [];
    if (agentRows.length > 0) {
      return mergeOverviewWithDevices(agentRows, devices.data?.devices ?? []);
    }
    return [];
  }, [overview.data, devices.data]);

  // ⚠️ 필터 판정에서 <b>정규화된 값</b>을 비교합니다.
  //
  //   이전에는 `row.deviceType === filter` 였고, 서버가 대문자 코드(ROUTER)를
  //   주는데 탭은 Title Case(Router)라 **Router 탭이 항상 빈 표**였습니다.
  //   All 탭만 보였기 때문에 "다른 탭에 장비가 없는 건가" 로 오해하기 쉽습니다.
  //
  //   분류되지 않는 유형("—" 또는 새 장비 유형)은 **모든 탭에 남깁니다.**
  //   그래야 "필터 때문에 사라진 장비" 가 생기지 않습니다 — 사라진 장비는
  //   운영자는 없는 장비로 읽습니다.
  const filtered = useMemo(() => {
    if (filter === "All") return rows;
    return rows.filter((row) => {
      const type = normalizeDeviceType(row.deviceType);
      if (type === "—") return true;
      // 목록에 없는 유형(예: 앞으로 추가될 "LoadBalancer")은 어떤 탭에서도
      // 볼 수 없게 되므로, All 이 아닌 탭에서도 함께 보여줍니다.
      if (!DEVICE_FILTERS.includes(type as (typeof DEVICE_FILTERS)[number])) return true;
      return type === filter;
    });
  }, [rows, filter]);

  const onlineCount = rows.filter((row) => row.connected).length;
  const silentCount = rows.filter((row) => row.state === "silent").length;

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] md:p-6">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800">
            <BoxIconLine className="size-5 text-gray-700 dark:text-white/90" />
          </div>
          <div>
            <h4 className="text-base font-semibold text-gray-800 dark:text-white/90">
              Agents
            </h4>
            <p className="text-xs text-gray-500 dark:text-gray-400">
              {onlineCount} online / {rows.length} total
              {silentCount > 0 && (
                <span className="ml-2 text-amber-600 dark:text-amber-400">
                  무응답 {silentCount}
                </span>
              )}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {/* 장비 타입 필터 */}
          <div className="flex gap-1 rounded-lg bg-gray-100 p-1 dark:bg-gray-800">
            {DEVICE_FILTERS.map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => setFilter(type)}
                className={`rounded-md px-2.5 py-1 text-xs font-medium transition ${
                  filter === type
                    ? "bg-white text-gray-800 shadow-sm dark:bg-gray-700 dark:text-white"
                    : "text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
                }`}
              >
                {type}
              </button>
            ))}
          </div>
          <Link
            to="/agent"
            className="text-sm font-medium text-brand-500 hover:text-brand-600"
          >
            View All
          </Link>
        </div>
      </div>

      <div className="overflow-x-auto rounded-xl border border-gray-100 dark:border-gray-800">
        <table className="w-full min-w-[640px] text-left text-sm">
          <thead className="border-b border-gray-100 bg-gray-50 text-xs text-gray-500 dark:border-gray-800 dark:bg-gray-900/50 dark:text-gray-400">
            <tr>
              <th className="px-4 py-3 font-medium">Agent ID</th>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">IP</th>
              <th className="px-4 py-3 font-medium">Device</th>
              <th className="px-4 py-3 font-medium">Telemetry</th>
              <th className="px-4 py-3 font-medium">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 text-gray-700 dark:divide-gray-800 dark:text-gray-300">
            {filtered.map((agent) => (
              <tr
                key={agent.agentId}
                className="transition hover:bg-gray-50/70 dark:hover:bg-gray-800/40"
              >
                <td className="px-4 py-3 font-mono text-xs">{agent.agentId}</td>
                <td className="px-4 py-3 font-medium">{agent.hostname}</td>
                <td className="px-4 py-3 font-mono text-xs">{agent.primaryIp}</td>
                <td className="px-4 py-3">
                  {/*
                    ⚠️ {agent.deviceType} 을 그대로 그리지 않습니다.
                    서버 코드(ROUTER)가 노출되면 화면과 탭 이름이 달라 보이고,
                    스타일 표(키가 "Router")도 못 찾아 회색으로 떨어집니다.
                    정규화한 값을 표시와 색상에 함께 씁니다.
                  */}
                  <span
                    className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                      DEVICE_STYLE[normalizeDeviceType(agent.deviceType)] ??
                      "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300"
                    }`}
                  >
                    {normalizeDeviceType(agent.deviceType)}
                  </span>
                </td>
                <td className="px-4 py-3 text-xs text-gray-500 dark:text-gray-400">
                  {agent.hasTelemetry ? "수집됨" : "대기"}
                </td>
                <td className="px-4 py-3">
                  <span className="flex items-center gap-1.5 text-xs">
                    <span
                      className={`size-2 rounded-full ${
                        agent.connected
                          ? "bg-green-500"
                          : agent.state === "silent"
                            ? "bg-amber-500"
                            : "bg-gray-400"
                      }`}
                    />
                    {/* 서버 상태값이 있으면 그것을 우선합니다. */}
                    {agent.state
                      ? AGENT_STATE_LABEL[agent.state]
                      : agent.connected
                        ? "online"
                        : "offline"}
                  </span>
                </td>
              </tr>
            ))}
            {overview.loading && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400">
                  불러오는 중...
                </td>
              </tr>
            )}
            {!overview.loading && filtered.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400">
                  {overview.error ?? "No agents found"}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
