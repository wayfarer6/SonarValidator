import { useMemo, useState } from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Button from "../components/ui/button/Button";
import { useApi } from "../hooks/useApi";
import { getAllDiscoveredDevices, listAgents } from "../lib/api";
import { API_BASE_URL } from "../lib/api/client";
import type { ApiDiscoveredDevice } from "../lib/api/types";

/**
 * Agent 목록 화면입니다.
 *
 * <h2>더미 데이터에서 서버 연동으로</h2>
 * 이 화면은 이전에 TailAdmin 의 프로필 카드들을 그대로 렌더링하는 스텁이었고
 * Agent 정보가 전혀 없었습니다. 이제 두 API 를 합쳐 보여줍니다.
 *
 * <ul>
 *   <li>{@code GET /api/v1/agents} — 지금 WebSocket 으로 연결된 Agent</li>
 *   <li>{@code GET /api/v1/network/discovered} — 실제로 수신된 설정</li>
 * </ul>
 *
 * <p>두 정보를 합치는 이유: "연결은 됐는데 텔레메트리가 없는 Agent" 를
 * 구분해야 하기 때문입니다. 이 상태는 서버 푸시는 되지만 수집이 안 되는
 * 것이므로, 운영자가 바로 알아야 할 신호입니다.
 * (과거에 Tomcat WebSocket 버퍼 크기 때문에 정확히 이 증상이 발생했습니다.)
 */
export default function Agent() {
  const agents = useApi(() => listAgents(), []);
  const discovered = useApi(() => getAllDiscoveredDevices(), []);

  const [onlyIssues, setOnlyIssues] = useState(false);

  /** Agent 식별자 → 수집 설정 */
  const configByAgent = useMemo(() => {
    const map = new Map<string, ApiDiscoveredDevice>();
    for (const device of discovered.data?.devices ?? []) {
      map.set(device.agent_id, device);
    }
    return map;
  }, [discovered.data]);

  /** 화면에 표시할 행 목록을 만듭니다. */
  const rows = useMemo(() => {
    const connected = agents.data?.agents ?? [];
    const result = connected.map((agent) => {
      const config = configByAgent.get(agent.agent_id);
      return {
        agentId: agent.agent_id,
        lastSeen: agent.last_seen,
        hasTelemetry: agent.has_telemetry,
        format: config?.format ?? null,
        hostname: config?.hostname ?? null,
        interfaceCount: config?.interfaces.length ?? 0,
        vlanCount: config?.vlans.length ?? 0,
        routeCount: config?.route_count ?? 0,
        warnings: config?.warnings ?? [],
        connected: true,
      };
    });

    // 수집은 됐지만 현재 연결 목록에 없는 Agent 도 보여줍니다.
    // (연결이 끊겼지만 마지막 설정은 남아 있는 경우)
    for (const [agentId, config] of configByAgent) {
      if (result.some((row) => row.agentId === agentId)) continue;
      result.push({
        agentId,
        lastSeen: config.last_seen ?? null,
        hasTelemetry: true,
        format: config.format,
        hostname: config.hostname,
        interfaceCount: config.interfaces.length,
        vlanCount: config.vlans.length,
        routeCount: config.route_count ?? 0,
        warnings: config.warnings ?? [],
        connected: false,
      });
    }

    return onlyIssues
      ? result.filter((row) => !row.hasTelemetry || row.warnings.length > 0 || !row.connected)
      : result;
  }, [agents.data, configByAgent, onlyIssues]);

  const loading = agents.loading || discovered.loading;
  const error = agents.error ?? discovered.error;
  const offline = agents.offline || discovered.offline;

  return (
    <>
      <PageMeta title="Agent List | SonarValidator" description="연결된 Agent 와 수집 상태" />
      <PageBreadcrumb pageTitle="Agent" />

      <div className="space-y-6">
        {/* 요약 카드 */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <SummaryCard
            label="연결된 Agent"
            value={agents.data?.connected ?? 0}
            hint="WebSocket 세션 기준"
            color="primary"
          />
          <SummaryCard
            label="수집 완료"
            value={discovered.data?.parsed_devices ?? 0}
            hint="설정을 해석한 장치"
            color="success"
          />
          <SummaryCard
            label="정책 요청"
            value={agents.data?.total_policy_requests ?? 0}
            hint="누적 처리 건수"
            color="info"
          />
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 pb-4 dark:border-gray-800">
            <div className="flex items-center gap-2">
              <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">Agent</h3>
              <Badge size="sm" color="light">
                {rows.length}건
              </Badge>
            </div>
            <div className="flex items-center gap-3">
              <label className="flex cursor-pointer items-center gap-1.5 text-xs text-gray-600 dark:text-gray-300">
                <input
                  type="checkbox"
                  checked={onlyIssues}
                  onChange={(e) => setOnlyIssues(e.target.checked)}
                  className="size-3.5 rounded border-gray-300"
                />
                문제 있는 항목만
              </label>
              <Button
                size="sm"
                variant="outline"
                onClick={() => {
                  agents.reload();
                  discovered.reload();
                }}
              >
                새로고침
              </Button>
            </div>
          </div>

          {loading && (
            <div className="py-12 text-center text-sm text-gray-500 dark:text-gray-400">
              Agent 목록을 불러오는 중...
            </div>
          )}

          {error && (
            <div className="rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
              <p className="text-sm font-medium text-gray-800 dark:text-white/90">
                {offline ? "백엔드에 연결할 수 없습니다" : "Agent 목록을 불러오지 못했습니다"}
              </p>
              <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">{error}</p>
              <p className="mt-2 rounded bg-white/60 p-2 font-mono text-[11px] text-gray-700 dark:bg-black/20 dark:text-gray-200">
                API: {API_BASE_URL}
              </p>
            </div>
          )}

          {!loading && !error && rows.length === 0 && (
            <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-gray-200 py-12 text-center dark:border-gray-800">
              <p className="text-base font-medium text-gray-600 dark:text-gray-400">
                {onlyIssues ? "문제가 있는 Agent 가 없습니다" : "연결된 Agent 가 없습니다"}
              </p>
              <p className="mt-1 max-w-md text-sm text-gray-400 dark:text-gray-500">
                Prober 를 실행하면 백엔드로 연결됩니다.
                (Agent 는 30초 주기로 텔레메트리를 전송합니다)
              </p>
            </div>
          )}

          {!loading && !error && rows.length > 0 && (
            <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-700">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-gray-50 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
                  <tr>
                    <th className="border-b p-3 font-medium dark:border-gray-600">Agent</th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">상태</th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">형식</th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">수집</th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">마지막 수신</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 text-gray-600 dark:divide-gray-700 dark:text-gray-300">
                  {rows.map((row) => (
                    <tr key={row.agentId}>
                      <td className="p-3">
                        <div className="flex flex-col">
                          <span className="font-medium text-gray-800 dark:text-white/90">
                            {row.hostname ?? row.agentId}
                          </span>
                          <span className="font-mono text-[11px] text-gray-400">
                            {row.agentId}
                          </span>
                        </div>
                      </td>
                      <td className="p-3">
                        <div className="flex flex-wrap gap-1">
                          {row.connected ? (
                            <Badge size="sm" color="success">
                              연결됨
                            </Badge>
                          ) : (
                            <Badge size="sm" color="light">
                              연결 끊김
                            </Badge>
                          )}
                          {!row.hasTelemetry && (
                            <Badge size="sm" color="warning">
                              텔레메트리 없음
                            </Badge>
                          )}
                          {row.warnings.length > 0 && (
                            <Badge size="sm" color="error">
                              경고 {row.warnings.length}
                            </Badge>
                          )}
                        </div>
                      </td>
                      <td className="p-3 font-mono text-xs">{row.format ?? "—"}</td>
                      <td className="p-3 text-xs">
                        인터페이스 {row.interfaceCount} · VLAN {row.vlanCount} · 경로{" "}
                        {row.routeCount}
                      </td>
                      <td className="p-3 font-mono text-[11px]">
                        {row.lastSeen ? new Date(row.lastSeen).toLocaleString() : "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

/** 요약 카드 한 장을 렌더링합니다. */
function SummaryCard({
  label,
  value,
  hint,
  color,
}: {
  label: string;
  value: number;
  hint: string;
  color: "primary" | "success" | "info";
}) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] md:p-6">
      <span className="text-sm text-gray-500 dark:text-gray-400">{label}</span>
      <div className="mt-2 flex items-end gap-2">
        <h4 className="text-title-sm font-bold text-gray-800 dark:text-white/90">{value}</h4>
        <Badge size="sm" color={color}>
          {hint}
        </Badge>
      </div>
    </div>
  );
}
