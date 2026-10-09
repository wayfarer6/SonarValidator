import { useCallback, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Button from "../components/ui/button/Button";
import { Modal } from "../components/ui/modal";
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
import type { ApiDiscoveredDevice, ApiQuarantineState, ApiTopologyNode } from "../lib/api/types";

/**
 * Agent 목록 화면입니다.
 *
 * <h2>더미 데이터에서 서버 연동으로</h2>
 * 이 화면은 이전에 TailAdmin 의 프로필 카드들을 그대로 렌더링하는 스텁이었고
 * Agent 정보가 전혀 없었습니다. 이제 세 API 를 합쳐 보여줍니다.
 *
 * <ul>
 *   <li>{@code GET /api/v1/agents/overview} — 배포 예정 ∪ 연결 ∪ 텔레메트리</li>
 *   <li>{@code GET /api/v1/network/discovered} — 실제로 수신된 설정</li>
 * </ul>
 *
 * <h2>⚠️ 연결 목록({@code /api/v1/agents})을 1차 자료로 쓰지 않은 이유</h2>
 * <p>그 엔드포인트는 <b>지금 살아 있는 WebSocket 세션</b>만 돌려줍니다.
 * 그래서 배포 직후(첫 접속 전)와 네트워크 단절 중에는 <b>목록이 0건</b>이
 * 됩니다. 운영자는 그것을 "아무것도 배포되지 않았다" 로 읽습니다.
 * 통합 현황은 배포 예정을 알고 있으므로 그 공백을 {@code 무응답} 같은
 * 상태로 드러냅니다.
 *
 * <h2>설정 내보내기 (내려받기)</h2>
 * <p>각 Agent 의 설정을 오프라인 스냅샷 형식으로 내려받을 수 있습니다.
 * 다른 랩으로 옮기거나 백업을 남길 때 필요하고, 그 파일을 다시
 * "Import Offline Prober Data" 카드에 올리면 복원됩니다.
 * (왕복이 되어야 백업이므로 서버는 원본 텔레메트리 payload 를 그대로 싣습니다.)
 *
 * <h2>⚠️ 격리 버튼이 이 화면에 있는 이유</h2>
 * <p>격리는 판단이 아니라 <b>조치</b>이고, 조치는 사람이 누릅니다. 그래서 이 화면은
 * "무슨 일이 일어났나"(위반·경고)와 "지금 내가 뭘 할 수 있나"(격리/해제)를
 * 한 곳에 둡니다. 위반 목록을 보다가 다른 화면으로 이동해 조치하는 흐름은
 * 조치를 미루게 만듭니다.
 *
 * <p>격리는 장치가 관리하는 VLAN 서브넷 하나에 적용합니다. 프로젝트 토폴로지에서
 * 대상 CIDR을 명시적으로 선택해야 조치를 요청할 수 있습니다.
 *
 * <h2>Agent 다운로드는 프로젝트 안에서만 제공</h2>
 * Agent 는 프로젝트에 귀속되어야 관리 서버 주소와 소속이 명확하므로,
 * 다운로드 및 배포 예정 등록은 프로젝트 목록의 <b>Add Agent</b>에서만 합니다.
 */
export default function Agent() {
  const navigate = useNavigate();
  const overview = useApi(() => listAgentOverview(), []);
  const discovered = useApi(() => getAllDiscoveredDevices(), []);
  const quarantine = useApi(() => listQuarantined(), []);
  const projects = useApi(() => listProjects(), []);

  const [onlyIssues, setOnlyIssues] = useState(false);

  /**
   * 마지막으로 목록을 서버에서 다시 읽은 시각입니다.
   *
   * <p>새로고침은 요청이 즉시 끝나고 데이터가 같으면 <b>화면에 아무 변화가
   * 없어</b> "버튼이 죽었다" 로 보입니다. 그래서 갱신 시각을 남깁니다.
   */
  const [lastRefreshedAt, setLastRefreshedAt] = useState<Date | null>(null);
  /** 재요청 진행 중 여부 (버튼 스피너용). */
  const [refreshing, setRefreshing] = useState(false);
  /** 유령 정리 진행 중 여부. */
  const [pruning, setPruning] = useState(false);
  const [pruneMessage, setPruneMessage] = useState<string | null>(null);

  /** 내려받기 진행 중인 Agent 식별자. 중복 클릭을 막습니다. */
  const [downloading, setDownloading] = useState<string | null>(null);
  /** 내려받기 실패 메시지 (Agent 별). */
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const [quarantining, setQuarantining] = useState<string | null>(null);
  const [isolationRow, setIsolationRow] = useState<Row | null>(null);
  const [isolationSubnets, setIsolationSubnets] = useState<ApiTopologyNode[]>([]);
  const [selectedCidr, setSelectedCidr] = useState("");
  const [loadingIsolationSubnets, setLoadingIsolationSubnets] = useState(false);
  /**
   * 마지막 격리/해제 결과 메시지입니다.
   *
   * <p>조용히 성공하면 운영자는 "눌렀는데 아무 일도 안 일어났다" 로 읽습니다.
   * 특히 장치가 미연결이면 명령이 전달되지 않으므로 그 사실을 반드시 보여야 합니다.
   */
  const [quarantineMessage, setQuarantineMessage] = useState<string | null>(null);
  const [quarantineError, setQuarantineError] = useState<string | null>(null);

  /** 노드 전체 격리는 기존 데이터 표시용으로 유지합니다. */
  const quarantinedIds = useMemo(
    () => new Set((quarantine.data?.quarantined ?? [])
      .filter((state) => !state.scope || state.scope === "NODE")
      .map((state) => state.agent_id)),
    [quarantine.data],
  );
  const connectionQuarantinesByAgent = useMemo(() => {
    const result = new Map<string, ApiQuarantineState[]>();
    for (const state of quarantine.data?.quarantined ?? []) {
      if (state.scope !== "CONNECTION" || !state.target_cidr) continue;
      const states = result.get(state.agent_id) ?? [];
      states.push(state);
      result.set(state.agent_id, states);
    }
    return result;
  }, [quarantine.data]);

  /** Agent 식별자 → 수집 설정 */
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

  /** 화면에 표시할 행 목록을 만듭니다. */
  const rows = useMemo(() => {
    const result = (overview.data?.agents ?? []).map((agent) => {
      const config = configByAgent.get(agent.agent_id);
      return {
        agentId: agent.agent_id,
        lastSeen: agent.registered_at,
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
        // ⚠️ API 로 관리되는 장치(OPNsense 등)는 프로버가 아니므로
        //    `connected=false` 입니다. 이를 "무응답" 으로 보여 주면
        //    정상 동작 중인 방화벽을 고장으로 오해합니다.
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

  /**
   * 화면 행 하나의 모양입니다.
   *
   * <p>필터 판정 함수가 행을 받으므로 타입이 필요합니다.
   */
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
    /** REST API 로만 관리되는 장치인지(프로버 없음). */
    apiManaged: boolean;
    deviceType: string | null;
    projectId: string | null;
    projectName: string | null;
  };

  /**
   * "문제 있는 항목" 의 판정 기준입니다.
   *
   * <p>⚠️ 필터와 라벨 카운트가 <b>같은 함수</b>를 써야 합니다.
   * 예전에는 두 곳에 조건을 따로 적어서, 필터는 `warnings` 를 포함하고
   * 카운트는 빠져 있었습니다. 그래서 라벨은 4인데 실제로는 6건이 남았고
   * "체크했는데 그대로다" 로 읽혔습니다.
   *
   * <p>같은 판단을 두 곳에 적으면 <b>반드시 다시 어긍납니다.</b>
   *
   * @param row 판정할 행
   * @return 문제가 있으면 true
   */
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

  /** 필터를 적용한 실제 표시 목록입니다. */
  const visibleRows = useMemo(
    () => (onlyIssues ? rows.filter(isProblem) : rows),
    [rows, onlyIssues, isProblem],
  );

  /**
   * 필터가 걸러내는 건수입니다. {@link isProblem} 과 같은 기준을 쓴다.
   *
   * <p>라벨에 건수를 안 보여 주면, 대부분의 행이 이미 문제 조건에 해당할 때
   * "체크했는데 그대로다 = 필터가 안 된다" 로 읽힙니다.
   */
  const issueCount = useMemo(
    () => rows.filter(isProblem).length,
    [rows, isProblem],
  );

  /** 필터가 전체를 그대로 볼러오는 경우 경고합니다(필터 고장으로 오해 방지). */
  const filterMatchesAll = onlyIssues && issueCount === rows.length;

  /** 목록을 다시 읽고 갱신 시각·스피너를 관리합니다. */
  const handleRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      overview.reload();
      discovered.reload();
      quarantine.reload();
      // reload 는 비동기 resolve 를 돌려주지 않으므로 한 박자 뒤 시각을 찍습니다.
      await new Promise((resolve) => setTimeout(resolve, 400));
      setLastRefreshedAt(new Date());
    } finally {
      setRefreshing(false);
    }
  };

  /**
   * 오래된 수신 이력(유령 Agent)을 서버에서 일괄 정리합니다.
   *
   * <p>연결 중인 Agent 는 서버가 거부합니다(409).
   */
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

  /**
   * Agent 의 설정을 오프라인 스냅샷 파일로 내려받습니다.
   *
   * <p>공유 링크가 아니라 Blob 을 받아 저장하는 이유: 서버가 404/401 을
   * 돌려줄 때 브라우저가 오류 JSON 을 파일로 저장해 버리면, 운영자는
   * 그것이 오류인지 설정인지 구분할 수 없습니다. 그래서 실패를 먼저 확인합니다.
   */
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

  /**
  * 선택한 Agent 소유 서브넷 하나를 격리하거나 해당 CIDR 격리를 해제합니다.
   *
   * <h2>왜 응답의 delivered 와 applied 를 나눠 보여주는가</h2>
   * <p>{@code delivered=false} 는 "장치가 연결되어 있지 않아 명령이 못 갔다"
   * 입니다. 이때 서버는 상태를 저장하므로, 장치가 재접속하면 차단 정책이
   * 적용됩니다. 운영자가 이 차이를 모르면 "격리가 안 먹네" 하고 반복 클릭합니다.
   *
   * <p>{@code applied=null} 은 "명령은 갔는데 아직 ack 를 못 받았다" 입니다.
   * 몇 초 뒤 새로고침하면 채워집니다.
   *
   * @param agentId 대상 Agent
   * @param targetCidr 격리/해제할 CIDR
   * @param isolate true 면 격리, false 면 해제
   */
  const handleQuarantine = async (
    agentId: string,
    targetCidr: string,
    isolate: boolean,
    projectId?: string | null,
  ) => {
    if (quarantining !== null) return;

    setQuarantining(`${agentId}:${targetCidr}`);
    setQuarantineMessage(null);
    setQuarantineError(null);

    try {
      if (isolate) {
        const result: ApiQuarantineState = await quarantineAgent(agentId, {
          projectId: projectId ?? undefined,
          targetCidr,
        });
        setQuarantineMessage(describeIsolation(agentId, targetCidr, result));
      } else {
        const result = await releaseQuarantine(agentId, undefined, targetCidr);
        setQuarantineMessage(
          result.released === false
            ? `${targetCidr} 는 격리 중이 아니어서 아무것도 하지 않았습니다.`
            : `${agentId} 의 ${targetCidr} 격리를 해제했습니다.`,
        );
      }
      // 서버가 확정한 상태를 다시 받아 화면을 맞춥니다.
      quarantine.reload();
      overview.reload();
    } catch (cause) {
      setQuarantineError(
        cause instanceof Error ? cause.message : "격리 요청을 처리하지 못했습니다.",
      );
    } finally {
      setQuarantining(null);
    }
  };

  const openIsolationModal = async (row: Row) => {
    if (!row.projectId) {
      setQuarantineError(`${row.agentId}: 프로젝트에 연결된 Agent만 서브넷 격리할 수 있습니다.`);
      return;
    }
    setIsolationRow(row);
    setSelectedCidr("");
    setIsolationSubnets([]);
    setLoadingIsolationSubnets(true);
    setQuarantineError(null);
    try {
      const topology = await getTopology(row.projectId);
      const ownedSubnets = topology.nodes
        .filter((node) => node.agent_id?.toLowerCase() === row.agentId.toLowerCase())
        .filter((node) => node.cidr?.trim());
      setIsolationSubnets(ownedSubnets);
    } catch (err) {
      setQuarantineError(err instanceof Error ? err.message : "프로젝트 서브넷을 불러오지 못했습니다.");
    } finally {
      setLoadingIsolationSubnets(false);
    }
  };

  const closeIsolationModal = () => {
    if (quarantining !== null) return;
    setIsolationRow(null);
    setSelectedCidr("");
    setIsolationSubnets([]);
  };

  const submitSubnetIsolation = async () => {
    if (!isolationRow || !selectedCidr) return;
    await handleQuarantine(isolationRow.agentId, selectedCidr, true, isolationRow.projectId);
    closeIsolationModal();
  };

  const canIsolateSubnet = (row: Row) =>
    ["SWITCH", "ROUTER", "FIREWALL"].includes((row.deviceType ?? "").toUpperCase());

  /**
   * 격리 응답을 사람이 읽는 문장으로 바꿉니다.
   *
   * @param agentId 대상 Agent
   * @param result  서버 응답
   * @returns 한 줄 요약
   */
  function describeIsolation(agentId: string, targetCidr: string, result: ApiQuarantineState): string {
    // ⚠️ 거부를 가장 먼저 봅니다. 아래 분기(retry/delivered)는 모두
    //    "명령을 보냈다" 를 전제로 문장을 만드는데, 거부된 요청은
    //    명령을 보내지 않았습니다. 순서를 바꾸면 "격리했습니다" 가 나옵니다.
    if (result.rejected === true) {
      return `격리할 수 없습니다 — ${result.reason ?? "이 장치는 격리 대상이 아닙니다."}`;
    }

    const parts: string[] = [];
    parts.push(result.retry
      ? `${targetCidr} 는 이미 격리 중입니다.`
      : `${agentId} 의 ${targetCidr} 격리를 요청했습니다.`);

    if (result.delivered === false) {
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
            value={(quarantine.data?.quarantined ?? []).filter((state) => state.scope === "CONNECTION").length}
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
                  /*
                   * 라벨 숫자와 표시 건수가 같아도 필터가 고장 난 것은 아닙니다.
                   * 그런데 화면만 보면 구분이 안 되므로 사유를 적어 둡니다.
                   */
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
                        {/*
                          형식(파서 키)은 내부 이름이므로 작게 보조로 둡니다.
                          제품명과 같거나(“Arista” vs “ARISTA_vEOS” 처럼 부분 일치)
                          구분이 안 되는 경우에는 생략해 “AristaARISTA_vEOS” 처럼
                          붙어 보이지 않게 합니다.
                        */}
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
                          {quarantinedIds.has(row.agentId) ? (
                            <>
                              <Badge size="sm" color="error">
                                전체 장치 격리
                              </Badge>
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={quarantining !== null}
                                title="기존 전체 장치 격리 해제는 아래 정책 화면에서 처리합니다"
                                onClick={() => setQuarantineMessage("기존 전체 장치 격리의 해제는 장치 명령 경로를 사용하므로, 프로젝트 정책 화면에서 해제하세요.")}
                              >
                                범위 확인 필요
                              </Button>
                            </>
                          ) : null}
                          {(connectionQuarantinesByAgent.get(row.agentId) ?? []).map((state) => (
                            <div key={`${row.agentId}:${state.target_cidr}`} className="flex flex-wrap items-center gap-2">
                              <Badge size="sm" color="error">{state.target_cidr}</Badge>
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={quarantining !== null}
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
                              서브넷 격리
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
                            <span className="font-medium text-gray-700 dark:text-gray-200">
                              {row.projectName ?? "프로젝트 이름 확인 불가"}
                            </span>
                            <span className="font-mono text-[10px] text-gray-400">
                              {row.projectId}
                            </span>
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
      <Modal
        isOpen={isolationRow !== null}
        onClose={closeIsolationModal}
        className="max-w-lg p-6"
      >
        <div className="space-y-5">
          <div>
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">격리할 VLAN 서브넷 선택</h2>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
              {isolationRow?.hostname ?? isolationRow?.agentId}가 관리하는 서브넷 하나의 송수신을 차단합니다.
            </p>
          </div>
          {loadingIsolationSubnets ? (
            <p className="py-6 text-center text-sm text-gray-500">서브넷 목록을 불러오는 중...</p>
          ) : isolationSubnets.length > 0 ? (
            <fieldset className="max-h-72 space-y-2 overflow-y-auto">
              <legend className="sr-only">격리 대상 서브넷</legend>
              {isolationSubnets.map((subnet) => (
                <label key={subnet.id} className="flex cursor-pointer items-start gap-3 rounded border border-gray-200 p-3 dark:border-gray-700">
                  <input
                    type="radio"
                    name="isolation-subnet"
                    value={subnet.cidr}
                    checked={selectedCidr === subnet.cidr}
                    onChange={() => setSelectedCidr(subnet.cidr)}
                    className="mt-1"
                  />
                  <span className="min-w-0">
                    <span className="block text-sm font-medium text-gray-800 dark:text-gray-100">{subnet.label || subnet.cidr}</span>
                    <span className="block font-mono text-xs text-gray-500">{subnet.cidr}</span>
                  </span>
                </label>
              ))}
            </fieldset>
          ) : (
            <p className="rounded border border-warning-200 bg-warning-50 p-3 text-sm text-warning-800 dark:border-warning-500/30 dark:bg-warning-500/10 dark:text-warning-200">
              이 Agent에 연결된 서브넷이 없습니다. 장치 전체를 격리하는 동작으로 대체하지 않습니다.
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button size="sm" variant="outline" onClick={closeIsolationModal} disabled={quarantining !== null}>취소</Button>
            <Button
              size="sm"
              onClick={() => void submitSubnetIsolation()}
              disabled={!selectedCidr || loadingIsolationSubnets || quarantining !== null}
            >
              {quarantining ? "요청 중..." : "선택한 서브넷 격리"}
            </Button>
          </div>
        </div>
      </Modal>
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
