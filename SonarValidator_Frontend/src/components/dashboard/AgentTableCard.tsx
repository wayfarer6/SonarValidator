import { useMemo, useState } from "react";
import { Link } from "react-router";
import { BoxIconLine } from "../../icons";
import { useApi } from "../../hooks/useApi";
import { getAllDiscoveredDevices, listAgents } from "../../lib/api";
import { buildAgentViews } from "../../lib/agentView";

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

  const agents = useApi(() => listAgents(), []);
  const devices = useApi(() => getAllDiscoveredDevices(), []);

  const rows = useMemo(
    () => buildAgentViews(agents.data?.agents ?? [], devices.data?.devices ?? []),
    [agents.data, devices.data],
  );

  const filtered =
    filter === "All" ? rows : rows.filter((row) => row.deviceType === filter);

  const onlineCount = rows.filter((row) => row.connected).length;

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
              {onlineCount} online /{" "}
              {agents.data?.connected ?? rows.length} total
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
                  <span
                    className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                      DEVICE_STYLE[agent.deviceType] ??
                      "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300"
                    }`}
                  >
                    {agent.deviceType}
                  </span>
                </td>
                <td className="px-4 py-3 text-xs text-gray-500 dark:text-gray-400">
                  {agent.hasTelemetry ? "수집됨" : "대기"}
                </td>
                <td className="px-4 py-3">
                  <span className="flex items-center gap-1.5 text-xs">
                    <span
                      className={`size-2 rounded-full ${
                        agent.connected ? "bg-green-500" : "bg-gray-400"
                      }`}
                    />
                    {agent.connected ? "online" : "offline"}
                  </span>
                </td>
              </tr>
            ))}
            {agents.loading && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400">
                  불러오는 중...
                </td>
              </tr>
            )}
            {!agents.loading && filtered.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400">
                  {agents.error ?? "No agents found"}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
