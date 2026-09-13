import { useState } from "react";
import { Link } from "react-router";
import { BoxIconLine } from "../../icons";
import { MOCK_AGENTS, type DeviceType } from "../../lib/mockData";

const DEVICE_STYLE: Record<DeviceType, string> = {
  Router: "bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400",
  Switch: "bg-green-50 text-green-600 dark:bg-green-500/10 dark:text-green-400",
  Firewall: "bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400",
  VM: "bg-purple-50 text-purple-600 dark:bg-purple-500/10 dark:text-purple-400",
};

const DEVICE_FILTERS: ("All" | DeviceType)[] = [
  "All",
  "Router",
  "Switch",
  "Firewall",
  "VM",
];

// Agent 리스트 카드: id, 이름, IP, 대역대, 장비 타입
export default function AgentTableCard() {
  const [filter, setFilter] = useState<"All" | DeviceType>("All");

  const agents =
    filter === "All"
      ? MOCK_AGENTS
      : MOCK_AGENTS.filter((agent) => agent.deviceType === filter);

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
              {MOCK_AGENTS.filter((a) => a.status === "online").length} online /{" "}
              {MOCK_AGENTS.length} total
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
              <th className="px-4 py-3 font-medium">IP Range</th>
              <th className="px-4 py-3 font-medium">Device</th>
              <th className="px-4 py-3 font-medium">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 text-gray-700 dark:divide-gray-800 dark:text-gray-300">
            {agents.map((agent) => (
              <tr
                key={agent.id}
                className="transition hover:bg-gray-50/70 dark:hover:bg-gray-800/40"
              >
                <td className="px-4 py-3 font-mono text-xs">{agent.id}</td>
                <td className="px-4 py-3 font-medium">{agent.name}</td>
                <td className="px-4 py-3 font-mono text-xs">{agent.ip}</td>
                <td className="px-4 py-3 font-mono text-xs">{agent.ipRange}</td>
                <td className="px-4 py-3">
                  <span
                    className={`rounded-full px-2.5 py-1 text-xs font-medium ${DEVICE_STYLE[agent.deviceType]}`}
                  >
                    {agent.deviceType}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className="flex items-center gap-1.5 text-xs">
                    <span
                      className={`size-2 rounded-full ${
                        agent.status === "online" ? "bg-green-500" : "bg-gray-400"
                      }`}
                    />
                    {agent.status}
                  </span>
                </td>
              </tr>
            ))}
            {agents.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400">
                  No agents found
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
