import { useCallback, useMemo, useState } from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Button from "../components/ui/button/Button";
import { useApi } from "../hooks/useApi";
import {
  getAllDiscoveredDevices,
  listAgentOverview,
  listQuarantined,
  pruneStaleAgents,
  quarantineAgent,
  releaseQuarantine,
} from "../lib/api";
import { API_BASE_URL } from "../lib/api/client";
import { downloadSnapshot } from "../lib/api/offline";
import type { ApiDiscoveredDevice, ApiQuarantineState } from "../lib/api/types";

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
 * <p>⚠️ 격리는 <b>업무망 트래픽을 끊습니다.</b> 그래서 버튼은 한 번 더
 * 확인({@code window.confirm})을 받습니다. 이 장치는 관리망 경로만 남기고
 * 모든 데이터 인터페이스가 내려갑니다.
 */
export default function Agent() {
  const overview = useApi(() => listAgentOverview(), []);
  const discovered = useApi(() => getAllDiscoveredDevices(), []);
  const quarantine = useApi(() => listQuarantined(), []);

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

  /** 격리/해제 진행 중인 Agent 식별자. */
  const [quarantining, setQuarantining] = useState<string | null>(null);
  /**
   * 마지막 격리/해제 결과 메시지입니다.
   *
   * <p>조용히 성공하면 운영자는 "눌렀는데 아무 일도 안 일어났다" 로 읽습니다.
   * 특히 장치가 미연결이면 명령이 전달되지 않으므로 그 사실을 반드시 보여야 합니다.
   */
  const [quarantineMessage, setQuarantineMessage] = useState<string | null>(null);
  const [quarantineError, setQuarantineError] = useState<string | null>(null);

  /** 격리 중인 Agent 식별자 집합. (화면 표시용) */
  const quarantinedIds = useMemo(
    () => new Set(quarantine.data?.agent_ids ?? []),
    [quarantine.data],
  );

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
        // ⚠️ 방화벽은 격리 대상이 아닙니다.
        //    서버도 거부하지만, 버튼을 누를 수 있게 두면 "눌렀는데 안 됨" 이
        //    됩니다. 비활성화하고 사유를 title 로 알려야 오해가 없습니다.
        isFirewall: agent.device_type === "FIREWALL",
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
        // 설정의 product 로는 유형을 알 수 없으므로 식별자 관례로 판단합니다.
        // (서버의 DeviceTypeResolver 와 같은 관례: 이름의 끝 토큰)
        isFirewall: /firewall/i.test(agentId),
      });
    }

    return result;
  }, [overview.data, configByAgent]);

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
    isFirewall: boolean;
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

  const loading = overview.loading || discovered.loading;
  const error = overview.error ?? discovered.error;
  const offline = overview.offline || discovered.offline;

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
   * Agent 를 격리하거나 해제합니다.
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
   * @param isolate true 면 격리, false 면 해제
   */
  const handleQuarantine = async (agentId: string, isolate: boolean) => {
    if (quarantining !== null) return;

    // 격리는 업무망을 끊는 조치입니다. 되돌릴 수는 있지만 즉시 영향이 큽니다.
    if (isolate) {
      const ok = window.confirm(
        `${agentId} 를 격리합니다.\n\n` +
          "이 장치는 관리 경로를 제외한 모든 데이터 인터페이스가 내려가 " +
          "업무망 통신이 끊깁니다.\n계속하시겠습니까?",
      );
      if (!ok) return;
    }

    setQuarantining(agentId);
    setQuarantineMessage(null);
    setQuarantineError(null);

    try {
      if (isolate) {
        const result: ApiQuarantineState = await quarantineAgent(agentId);
        setQuarantineMessage(describeIsolation(agentId, result));
      } else {
        const result = await releaseQuarantine(agentId);
        setQuarantineMessage(
          // ⚠️ `result.released === false` 로만 "아니었다" 를 판정합니다.
          //    `!result.released` 로 쓰면 키가 없을 때(구버전 서버) 성공을
          //    실패로 뒤집어 말합니다. (최종 E2E 에서 실제로 재발)
          result.released === false
            ? `${agentId} 는 격리 중이 아니어서 아무것도 하지 않았습니다.`
            : `${agentId} 의 격리를 해제했습니다.`,
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

  /**
   * 격리 응답을 사람이 읽는 문장으로 바꿉니다.
   *
   * @param agentId 대상 Agent
   * @param result  서버 응답
   * @returns 한 줄 요약
   */
  function describeIsolation(agentId: string, result: ApiQuarantineState): string {
    // ⚠️ 거부를 가장 먼저 봅니다. 아래 분기(retry/delivered)는 모두
    //    "명령을 보냈다" 를 전제로 문장을 만드는데, 거부된 요청은
    //    명령을 보내지 않았습니다. 순서를 바꾸면 "격리했습니다" 가 나옵니다.
    if (result.rejected === true) {
      return `격리할 수 없습니다 — ${result.reason ?? "이 장치는 격리 대상이 아닙니다."}`;
    }

    const parts: string[] = [];
    parts.push(
      result.retry
        ? `${agentId} 는 이미 격리 중이었습니다. 명령을 다시 보냈습니다.`
        : `${agentId} 를 격리했습니다.`,
    );

    if (result.delivered === false) {
      parts.push(
        "⚠️ 장치가 연결되어 있지 않아 명령이 전달되지 않았습니다. " +
          "장치가 재접속하면 차단 정책이 자동 적용됩니다.",
      );
    } else if (result.applied === true) {
      parts.push("장치가 차단을 적용했습니다. (ack 확인)");
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
            label="격리 중"
            value={quarantinedIds.size}
            hint="운영자가 수동으로 차단"
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
                        {/*
                         * ⚠️ `telemetry-only` 는 "과거에 수신했으나 지금 세션 없음" 입니다.
                         * 이전에는 여기에 `예정에 없음`(expected=false) 배지를 함께 띄웠는데,
                         * 그 뜻은 "배포 예정에 등록하지 않음" 일 뿐이라
                         * "지금 연결이 끊겼다" 와 혼동되었습니다.
                         * 그래서 그 배지를 제거하고 상태를 `수신만` 으로 정확히 씁니다.
                         */}
                        <div className="flex flex-wrap gap-1">
                          {row.apiManaged ? (
                            /*
                             * 프로버가 아니라 REST API 로 관리되는 장치입니다.
                             * WebSocket 세션이 없으므로 `connected=false` 이지만
                             * 정상 동작 중입니다 — "무응답" 으로 보여 주면
                             * 살아 있는 방화벽을 고장으로 오해합니다.
                             */
                            <Badge size="sm" color="info">
                              API 연동
                            </Badge>
                          ) : row.connected ? (
                            <Badge size="sm" color="success">
                              연결됨
                            </Badge>
                          ) : row.state === "silent" ? (
                            <Badge size="sm" color="warning">
                              무응답
                            </Badge>
                          ) : (
                            <Badge size="sm" color="light">
                              수신만
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
                        {/*
                          격리 열입니다.

                          ⚠️ 격리된 Agent 는 연결이 끊긴 것처럼 보입니다(인터페이스가
                          내려가므로). 그래서 "연결 끊김" 만 표시하면 운영자는
                          장애로 오해합니다. 이 열이 "내가 껐다" 와 "고장났다" 를
                          구분해 줍니다.
                        */}
                        <div className="flex flex-col gap-1.5">
                          {quarantinedIds.has(row.agentId) ? (
                            <>
                              <Badge size="sm" color="error">
                                🛑 격리 중
                              </Badge>
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={quarantining !== null}
                                title="인터페이스를 다시 올리고 정상 정책을 적용합니다"
                                onClick={() => handleQuarantine(row.agentId, false)}
                              >
                                {quarantining === row.agentId ? "해제 중..." : "해제"}
                              </Button>
                            </>
                          ) : (
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={quarantining !== null || row.isFirewall}
                              title={
                                row.isFirewall
                                  ? "방화벽은 격리 대상이 아닙니다 — 트렁크에 연결된 모든 VLAN 이 함께 끊깁니다. 프로젝트 규칙으로 해당 연결만 차단하세요."
                                  : "관리 경로를 제외한 모든 데이터 인터페이스를 내립니다"
                              }
                              onClick={() => handleQuarantine(row.agentId, true)}
                            >
                              {quarantining === row.agentId ? "격리 중..." : "격리"}
                            </Button>
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
