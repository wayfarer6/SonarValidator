import { useCallback, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Button from "../components/ui/button/Button";
import IsolationModal from "../components/agent/IsolationModal";
import { useApi } from "../hooks/useApi";
import {
  getAllDiscoveredDevices,
  getTopology,
  listAgentOverview,
  listQuarantined,
  pruneStaleAgents,
  quarantineAgent,
  releaseQuarantine,
} from "../lib/api";
import { API_BASE_URL } from "../lib/api/client";
import { downloadSnapshot } from "../lib/api/offline";
import { listProjects } from "../lib/api/projects";
import { listCredentials } from "../lib/api/opnsense";
import type { ApiDiscoveredDevice, ApiQuarantineState, ApiTopologyNode } from "../lib/api/types";

export default function Agent() {
  const navigate = useNavigate();
  const overview = useApi(() => listAgentOverview(), [], 5000);
  const discovered = useApi(() => getAllDiscoveredDevices(), [], 5000);
  const quarantine = useApi(() => listQuarantined(), [], 2000);
  const projects = useApi(() => listProjects(), []);
  const apiNodes = useApi(() => listCredentials(), []);

  const [onlyIssues, setOnlyIssues] = useState(false);

  const [lastRefreshedAt, setLastRefreshedAt] = useState<Date | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [pruning, setPruning] = useState(false);
  const [pruneMessage, setPruneMessage] = useState<string | null>(null);

  const [downloading, setDownloading] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const [quarantining, setQuarantining] = useState<string | null>(null);
  const isolationRequest = useRef(0);
  const [isolationRow, setIsolationRow] = useState<Row | null>(null);
  const [isolationSubnets, setIsolationSubnets] = useState<ApiTopologyNode[]>([]);
  const [selectedCidr, setSelectedCidr] = useState<string | null>(null);
  const [loadingIsolationSubnets, setLoadingIsolationSubnets] = useState(false);
  const [quarantineMessage, setQuarantineMessage] = useState<string | null>(null);
  const [quarantineError, setQuarantineError] = useState<string | null>(null);

  const quarantinedIds = useMemo(
    () => new Set((quarantine.data?.quarantined ?? [])
      .filter((state) => !state.scope || state.scope === "NODE")
      .map((state) => state.agent_id)),
    [quarantine.data],
  );
  const connectionQuarantinesByAgent = useMemo(() => {
    const result = new Map<string, ApiQuarantineState[]>();
    for (const state of [...(quarantine.data?.quarantined ?? []), ...(quarantine.data?.pending_releases ?? [])]) {
      if (state.scope !== "CONNECTION" || !state.target_cidr) continue;
      const states = result.get(state.agent_id) ?? [];
      states.push(state);
      result.set(state.agent_id, states);
    }
    return result;
  }, [quarantine.data]);

  const nodeQuarantines = useMemo(() => new Map(
    [...(quarantine.data?.quarantined ?? []), ...(quarantine.data?.pending_releases ?? [])]
      .filter((state) => state.scope !== "CONNECTION").map((state) => [state.agent_id, state]),
  ), [quarantine.data]);

  const configByAgent = useMemo(() => {
    const map = new Map<string, ApiDiscoveredDevice>();
    for (const device of discovered.data?.devices ?? []) {
      map.set(device.agent_id, device);
    }
    return map;
  }, [discovered.data]);

  const projectNameById = useMemo(
    () => new Map((projects.data?.projects ?? []).map((project) => [project.project_id, project.name])),
    [projects.data],
  );

  const rows = useMemo(() => {
    const result = (overview.data?.agents ?? []).map((agent) => {
      const config = configByAgent.get(agent.agent_id);
      return {
        agentId: agent.agent_id,
        lastSeen: config?.last_seen ?? null,
        hasTelemetry: agent.telemetry_seen,
        // 화면에는 제품명을 씁니다. 형식(파서 키)은 내부 이름이라 운영자에게 의미가 약합니다.
        // product 가 없으면 vendor 로, 그것도 없으면 format 으로 내려갑니다.
        product: config?.product ?? config?.vendor ?? null,
        format: config?.format ?? null,
        hostname: config?.hostname ?? null,
        interfaceCount: config?.interfaces.length ?? 0,
        vlanCount: config?.vlans.length ?? 0,
        routeCount: config?.route_count ?? 0,
        warnings: config?.warnings ?? [],
        connected: agent.connected,
        state: agent.state,
        expected: agent.expected,



        apiManaged: agent.api_managed === true,
        deviceType: agent.device_type,
        projectId: agent.project_id,
        projectName: agent.project_id ? projectNameById.get(agent.project_id) ?? null : null,
      };
    });

    // 수집은 됐지만 통합 현황에 없는 장치도 보여줍니다.
    // (프로젝트 필터 등으로 서버 목록에서 빠진 경우의 안전망)
    for (const [agentId, config] of configByAgent) {
      if (result.some((row) => row.agentId === agentId)) continue;
      result.push({
        agentId,
        lastSeen: config.last_seen ?? null,
        hasTelemetry: true,
        product: config.product ?? config.vendor ?? null,
        format: config.format,
        hostname: config.hostname,
        interfaceCount: config.interfaces.length,
        vlanCount: config.vlans.length,
        routeCount: config.route_count ?? 0,
        warnings: config.warnings ?? [],
        connected: false,
        state: "telemetry-only" as const,
        expected: false,
        // 설정만 있고 통합 현황에 없는 장치는 API 관리로 단정할 수 없습니다.
        apiManaged: false,
        // 유형 정보가 없는 설정 전용 행은 식별자 끝의 네트워크 장비 역할만 허용합니다.
        deviceType: /(?:^|[-._/])(switch|router|firewall)$/i.test(agentId)
          ? agentId.match(/(?:^|[-._/])(switch|router|firewall)$/i)?.[1]?.toUpperCase() ?? null
          : "VM",
        projectId: null,
        projectName: null,
      });
    }

    return result;
  }, [overview.data, configByAgent, projectNameById]);

  type Row = {
    agentId: string;
    lastSeen: string | null;
    hasTelemetry: boolean;
    product: string | null;
    format: string | null;
    hostname: string | null;
    interfaceCount: number;
    vlanCount: number;
    routeCount: number;
    warnings: string[];
    connected: boolean;
    state: string;
    expected: boolean;
    apiManaged: boolean;
    deviceType: string | null;
    projectId: string | null;
    projectName: string | null;
  };

  const isProblem = useCallback(
    (row: Row) =>
      // API 관리 장치는 프로버가 없어 무텔레메트리가 정상입니다.
      !row.apiManaged &&
      (!row.connected ||
        !row.hasTelemetry ||
        row.warnings.length > 0 ||
        row.expected === false),
    [],
  );

  const visibleRows = useMemo(
    () => (onlyIssues ? rows.filter(isProblem) : rows),
    [rows, onlyIssues, isProblem],
  );

  const issueCount = useMemo(
    () => rows.filter(isProblem).length,
    [rows, isProblem],
  );

  const filterMatchesAll = onlyIssues && issueCount === rows.length;

  const handleRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      overview.reload();
      discovered.reload();
      quarantine.reload();
      projects.reload();
      // reload 는 비동기 resolve 를 돌려주지 않으므로 한 박자 뒤 시각을 찍습니다.
      await new Promise((resolve) => setTimeout(resolve, 400));
      setLastRefreshedAt(new Date());
    } finally {
      setRefreshing(false);
    }
  };

  const handlePruneStale = async () => {
    if (pruning) return;
    if (!window.confirm(
      "오래 수신이 없는 Agent 이력을 정리합니다.\n연결 중인 Agent 는 제외됩니다. 계속할까요?",
    )) {
      return;
    }
    setPruning(true);
    setPruneMessage(null);
    try {
      const result = await pruneStaleAgents();
      setPruneMessage(
        result.removed > 0
          ? `${result.removed}건을 정리했습니다.`
          : "정리할 항목이 없습니다.",
      );
      await handleRefresh();
    } catch (cause) {
      setPruneMessage(
        cause instanceof Error ? `정리에 실패했습니다: ${cause.message}` : "정리에 실패했습니다.",
      );
    } finally {
      setPruning(false);
    }
  };

  const loading = overview.loading || discovered.loading || projects.loading;
  const error = overview.error ?? discovered.error ?? projects.error;
  const offline = overview.offline || discovered.offline || projects.offline;

  const handleDownload = async (agentId: string) => {
    if (downloading !== null) return;

    setDownloading(agentId);
    setDownloadError(null);

    try {
      const { file_name, blob } = await downloadSnapshot(agentId);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = file_name;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      // Blob URL 은 명시적으로 해제해야 메모리가 회수됩니다.
      URL.revokeObjectURL(url);
    } catch (cause) {
      setDownloadError(
        cause instanceof Error ? cause.message : "스냅샷을 내려받지 못했습니다.",
      );
    } finally {
      setDownloading(null);
    }
  };

  const handleQuarantine = async (
    agentId: string,
    targetCidr: string,
    isolate: boolean,
    projectId?: string | null,
  ) => {
    if (quarantining !== null) return false;

    setQuarantining(`${agentId}:${targetCidr}`);
    setQuarantineMessage(null);
    setQuarantineError(null);

    try {
      if (isolate) {
        const result: ApiQuarantineState = await quarantineAgent(agentId, {
          projectId: projectId ?? undefined,
          targetCidr,
        });
        if (result.rejected) {
          setQuarantineError(result.reason ?? "이 장치는 격리 대상이 아닙니다.");
          return false;
        }
        setQuarantineMessage(describeIsolation(agentId, targetCidr, result));
      } else {
        const result = await releaseQuarantine(agentId, undefined, targetCidr);
        setQuarantineMessage(
          result.released === false
            ? `${targetCidr || "장치"}는 격리 중이 아니어서 아무것도 하지 않았습니다.`
            : `${agentId}의 ${targetCidr || "장치 전체"} 격리 해제를 요청했습니다. 다음 정책 수신 때 차단 규칙이 제거됩니다.`,
        );
      }
      // 서버가 확정한 상태를 다시 받아 화면을 맞춥니다.
      quarantine.reload();
      overview.reload();
      return true;
    } catch (cause) {
      setQuarantineError(
        cause instanceof Error ? cause.message : "격리 요청을 처리하지 못했습니다.",
      );
      return false;
    } finally {
      setQuarantining(null);
    }
  };

  const openIsolationModal = async (row: Row) => {
    if (!row.projectId) {
      setQuarantineError(`${row.agentId}: 프로젝트에 연결된 Agent만 격리를 요청할 수 있습니다.`);
      return;
    }
    const request = ++isolationRequest.current;
    setIsolationRow(row);
    setSelectedCidr(null);
    setIsolationSubnets([]);
    setLoadingIsolationSubnets(true);
    setQuarantineError(null);
    try {
      const topology = await getTopology(row.projectId);
      if (request !== isolationRequest.current) return;
      const ownedSubnets = topology.nodes
        .filter((node) => node.agent_id?.toLowerCase() === row.agentId.toLowerCase())
        .filter((node) => node.cidr?.trim());
      setIsolationSubnets(row.deviceType?.toUpperCase() === "VM" ? [] : ownedSubnets);
    } catch (err) {
      if (request !== isolationRequest.current) return;
      setQuarantineError(err instanceof Error ? err.message : "프로젝트 서브넷을 불러오지 못했습니다.");
    } finally {
      if (request === isolationRequest.current) setLoadingIsolationSubnets(false);
    }
  };

  const closeIsolationModal = () => {
    if (quarantining !== null) return;
    ++isolationRequest.current;
    setIsolationRow(null);
    setSelectedCidr(null);
    setIsolationSubnets([]);
  };

  const submitSubnetIsolation = async () => {
    if (!isolationRow || selectedCidr === null) return;
    const accepted = await handleQuarantine(isolationRow.agentId, selectedCidr, true, isolationRow.projectId);
    if (accepted) closeIsolationModal();
  };

  const canIsolateSubnet = (row: Row) =>
    ["SWITCH", "ROUTER", "FIREWALL", "VM"].includes((row.deviceType ?? "").toUpperCase());

  function describeIsolation(agentId: string, targetCidr: string, result: ApiQuarantineState): string {



    if (result.rejected === true) {
      return `격리할 수 없습니다 — ${result.reason ?? "이 장치는 격리 대상이 아닙니다."}`;
    }

    const parts: string[] = [];
    parts.push(result.retry
      ? `${targetCidr} 는 이미 격리 중입니다.`
      : `${agentId}의 ${targetCidr || "장치 전체"} 격리를 요청했습니다.`);

    if (result.scope === "CONNECTION") {
      parts.push(result.applied === true ? "장비가 격리를 적용했습니다." : result.applied === false ? "장비 적용에 실패했습니다. 상세 로그를 확인하세요." : "정책 동기화와 장비의 적용 확인을 기다립니다.");
    } else if (result.delivered === false) {
      parts.push(
        "정책 명령은 지금 전달되지 않았습니다. Agent 가 다시 연결되면 정책 요청 때 반영됩니다.",
      );
    } else if (result.applied === true) {
      parts.push("Agent가 차단 정책을 적용했습니다. (ack 확인)");
    } else if (result.applied === false) {
      parts.push(
        `장치가 차단을 적용하지 못했습니다: ${result.applied_detail ?? "사유 미보고"}`,
      );
    } else if (result.delivered === true) {
      parts.push("명령을 전달했습니다. 장치의 적용 확인(ack)을 기다리는 중입니다.");
    }

    return parts.join(" ");
  }

  return (
    <>
      <PageMeta title="Agent List | SonarValidator" description="연결된 Agent 와 수집 상태" />
      <PageBreadcrumb pageTitle="Agent" />

      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-700 dark:bg-white/[0.03]">
          <p className="text-sm text-gray-700 dark:text-gray-300">
            프로버 없이 REST API 로 직접 연결되는 장비(예: OPNsense)는 이 목록에 나타나지
            않습니다.{" "}
            {!apiNodes.loading && (
              <span className="font-medium">
                현재 {apiNodes.data?.total ?? 0}건이 API 연결 노드로 등록되어 있습니다.
              </span>
            )}
          </p>
          <Link
            to="/api-nodes"
            className="rounded-lg border border-brand-500 bg-brand-50 px-3.5 py-2 text-sm font-medium text-brand-600 hover:bg-brand-100 dark:bg-brand-500/10 dark:text-brand-400 dark:hover:bg-brand-500/20"
          >
            API 연결 노드 보기
          </Link>
        </div>

        {/* 요약 카드 */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-4">
          <SummaryCard
            label="연결된 Agent"
            value={visibleRows.filter((row) => row.connected).length}
            hint="WebSocket 세션 기준"
            color="primary"
          />
          <SummaryCard
            label="무응답"
            value={visibleRows.filter((row) => row.state === "silent").length}
            hint="배포 예정 · 연결 없음"
            color="warning"
          />
          <SummaryCard
            label="격리된 서브넷"
            value={(quarantine.data?.quarantined ?? []).filter((state) => state.scope === "CONNECTION" && state.applied === true).length}
            hint="운영자가 VLAN 단위로 차단"
            color="error"
          />
          <SummaryCard
            label="정책 요청"
            value={overview.data?.total_policy_requests ?? 0}
            hint="누적 처리 건수"
            color="info"
          />
        </div>

        {/* 격리 결과 — 조용히 성공하면 "버튼이 안 먹는다" 로 보입니다. */}
        {quarantineMessage && (
          <div className="rounded-xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-white/[0.03]">
            <p className="text-sm text-gray-700 dark:text-gray-300">🛑 {quarantineMessage}</p>
          </div>
        )}
        {quarantineError && (
          <div className="rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
            <p className="text-sm text-gray-700 dark:text-gray-300">
              격리 요청 실패: {quarantineError}
            </p>
          </div>
        )}

        <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 pb-4 dark:border-gray-800">
            <div className="flex items-center gap-2">
              <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">Agent</h3>
              <Badge size="sm" color="light">
                {visibleRows.length}건
                {filterMatchesAll && (
                  <span
                    className="ml-1 font-normal text-gray-400"
                    title="모든 항목이 문제 조건에 해당합니다"
                  >
                    (전체가 해당)
                  </span>
                )}
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
                문제 있는 항목만 ({issueCount})
              </label>
              {lastRefreshedAt && (
                <span className="text-[11px] text-gray-400 dark:text-gray-500">
                  마지막 갱신 {lastRefreshedAt.toLocaleTimeString()}
                </span>
              )}
              <Button
                size="sm"
                variant="outline"
                onClick={handleRefresh}
                disabled={refreshing}
              >
                {refreshing ? "새로고침 중..." : "새로고침"}
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={handlePruneStale}
                disabled={pruning}
                title="오래 수신이 없는 Agent 이력을 정리합니다 (연결 중인 Agent 는 제외)"
              >
                {pruning ? "정리 중..." : "오래된 항목 정리"}
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => navigate("/project")}
                title="프로젝트를 선택한 뒤 프로젝트 안에서 Agent를 추가하고 다운로드합니다"
              >
                프로젝트에서 Agent 추가
              </Button>
            </div>
          </div>

          {pruneMessage && (
            <div className="mb-4 rounded-xl border border-gray-200 bg-gray-50 p-3 text-xs text-gray-700 dark:border-gray-700 dark:bg-white/[0.03] dark:text-gray-200">
              {pruneMessage}
            </div>
          )}

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

          {!loading && !error && visibleRows.length === 0 && (
            <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-gray-200 py-12 text-center dark:border-gray-800">
              <p className="text-base font-medium text-gray-600 dark:text-gray-400">
                {onlyIssues ? "문제가 있는 Agent 가 없습니다" : "연결된 Agent 가 없습니다"}
              </p>
              <p className="mt-1 max-w-md text-sm text-gray-400 dark:text-gray-500">
                Prober 를 실행하면 백엔드로 연결됩니다.
                (Agent 는 30초 주기로 텔레메트리를 전송합니다)
                프로젝트에서 장비를 배포하면 여기에 <b>무응답</b> 상태로 먼저
                나타납니다.
              </p>
            </div>
          )}

          {!loading && !error && visibleRows.length > 0 && (
            <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-700">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-gray-50 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
                  <tr>
                    <th className="border-b p-3 font-medium dark:border-gray-600">Agent</th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">상태</th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">제품명</th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">수집</th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">마지막 수신</th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">격리</th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">설정 내보내기</th>
                    <th className="border-b p-3 font-medium dark:border-gray-600">프로젝트</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 text-gray-600 dark:divide-gray-700 dark:text-gray-300">
                  {visibleRows.map((row) => (
                    <tr key={row.agentId} data-testid={`agent-row-${row.agentId}`}>
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
                          {row.connected || row.apiManaged ? (
                            <Badge size="sm" color="success">
                              온라인
                            </Badge>
                          ) : (
                            <Badge size="sm" color="light">
                              오프라인
                            </Badge>
                          )}
                          {/* API 관리 장치에는 텔레메트리를 요구하지 않습니다 — 범주 오류입니다. */}
                          {!row.apiManaged && !row.hasTelemetry && (
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
                      <td className="p-3 text-xs">
                        <span className="font-medium text-gray-700 dark:text-gray-200">
                          {row.product ?? "—"}
                        </span>
                        {row.format &&
                          row.format.toLowerCase() !== (row.product ?? "").toLowerCase() &&
                          !row.format.toLowerCase().includes((row.product ?? "").toLowerCase()) && (
                            <span className="ml-1 font-mono text-[10px] text-gray-400">
                              ({row.format})
                            </span>
                          )}
                      </td>
                      <td className="p-3 text-xs">
                        인터페이스 {row.interfaceCount} · VLAN {row.vlanCount} · 경로{" "}
                        {row.routeCount}
                      </td>
                      <td className="p-3 font-mono text-[11px]">
                        {row.lastSeen ? new Date(row.lastSeen).toLocaleString() : "—"}
                      </td>
                      <td className="p-3">
                        {/* 표시와 해제는 활성 격리의 실제 범위(CIDR)를 따릅니다. */}
                        <div className="flex flex-col gap-1.5">
                          {nodeQuarantines.has(row.agentId) ? (
                            <>
                              <Badge size="sm" color={nodeQuarantines.get(row.agentId)?.status === "APPLIED" ? "error" : "warning"}>
                                장치 {nodeQuarantines.get(row.agentId)?.active ? "격리" : "해제"} · {nodeQuarantines.get(row.agentId)?.status === "APPLIED" ? "적용 확인" : nodeQuarantines.get(row.agentId)?.status === "FAILED" ? "적용 실패" : "확인 대기"}
                              </Badge>
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={quarantining !== null}
                                title="이번 격리에서 변경한 포트·규칙을 복원합니다"
                                onClick={() => void handleQuarantine(row.agentId, "", false, row.projectId)}
                              >
                                장치 격리 해제
                              </Button>
                            </>
                          ) : null}
                          {(connectionQuarantinesByAgent.get(row.agentId) ?? []).map((state) => (
                            <div key={`${row.agentId}:${state.target_cidr}`} className="flex flex-wrap items-center gap-2">
                              <Badge size="sm" color={state.applied === true ? "error" : "warning"}>
                                {state.target_cidr} · {state.active ? "격리" : "해제"} {state.applied === true ? "적용 확인" : state.applied === false ? "적용 실패" : "확인 대기"}
                              </Badge>
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={quarantining !== null || (!state.active && state.status === "PENDING")}
                                onClick={() => void handleQuarantine(row.agentId, state.target_cidr!, false, row.projectId)}
                              >
                                {quarantining === `${row.agentId}:${state.target_cidr}` ? "해제 중..." : "서브넷 해제"}
                              </Button>
                            </div>
                          ))}
                          {!quarantinedIds.has(row.agentId) && canIsolateSubnet(row) && (
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={quarantining !== null || !row.projectId}
                              title={!row.projectId ? "프로젝트에 연결된 Agent만 서브넷을 선택할 수 있습니다" : "격리할 VLAN 서브넷을 선택합니다"}
                              onClick={() => void openIsolationModal(row)}
                            >
                              격리 설정
                            </Button>
                          )}
                          {!quarantinedIds.has(row.agentId) && !canIsolateSubnet(row) && (
                            <span className="text-xs text-gray-400" title="VLAN 격리는 스위치, 라우터, 방화벽에서만 지원됩니다">
                              VLAN 격리 미지원 ({row.deviceType ?? "유형 미상"})
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="p-3">
                        {/* 설정이 없으면 내보낼 것이 없으므로 비활성화하고 사유를 title 로 알립니다.
                            (비활성 버튼은 클릭 이벤트가 없어 화면으로 설명할 기회가 없습니다) */}
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={row.interfaceCount === 0 || downloading !== null}
                          title={
                            row.interfaceCount === 0
                              ? "수집된 설정이 없어 내보낼 수 없습니다"
                              : "오프라인 스냅샷 JSON 으로 내려받습니다"
                          }
                          onClick={() => handleDownload(row.agentId)}
                        >
                          {downloading === row.agentId ? "생성 중..." : "JSON"}
                        </Button>
                      </td>
                      <td className="p-3 text-xs">
                        {row.projectId ? (
                          <div className="flex flex-col">
                            <Link to={`/project/editor/${encodeURIComponent(row.projectId)}`} aria-label={`${row.projectName ?? row.projectId} 프로젝트로 이동`} className="inline-flex items-center gap-1 font-medium text-brand-600 hover:underline dark:text-brand-400">
                              {row.projectName ?? row.projectId}<span aria-hidden="true">↗</span>
                            </Link>
                            <span className="font-mono text-[10px] text-gray-400">{row.projectId}</span>
                          </div>
                        ) : (
                          <span className="text-gray-400">미지정</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* 내보내기 실패 사유 — 조용히 실패하면 "버튼이 안 먹는다" 로 보입니다. */}
          {downloadError && (
            <p className="mt-3 rounded-lg border border-error-200 bg-error-50 p-3 text-xs text-gray-700 dark:border-error-500/30 dark:bg-error-500/10 dark:text-gray-300">
              {downloadError}
            </p>
          )}
        </div>
      </div>
      <IsolationModal
        error={quarantineError}
        agent={isolationRow} subnets={isolationSubnets} selectedCidr={selectedCidr}
        loading={loadingIsolationSubnets} submitting={quarantining !== null}
        onSelect={setSelectedCidr} onClose={closeIsolationModal} onSubmit={submitSubnetIsolation}
      />
    </>
  );
}

function SummaryCard({
  label,
  value,
  hint,
  color,
}: {
  label: string;
  value: number;
  hint: string;
  color: "primary" | "success" | "info" | "warning" | "error";
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
