import { apiRequest, API_BASE_URL } from "./client";
import type {
  ApiAgentList,
  ApiAgentOverviewList,
  ApiComplianceChanges,
  ApiDiscoveredDevices,
  ApiExpectedAgentRegistered,
  ApiForbiddenPairs,
  ApiPolicyViolations,
  ApiQuarantineList,
  ApiQuarantineRelease,
  ApiQuarantineState,
  ApiQuarantineStatus,
  ApiRouteTable,
  ApiTopology,
} from "./types";

/**
 * 대시보드/정책/네트워크 화면이 쓰는 조회 API 모음입니다.
 *
 * <p>기존에 더미 데이터를 쓰던 화면들을 이 모듈로 연결합니다. 각 함수는
 * 서버 경로를 은닉하므로 화면 코드에는 URL 문자열이 나타나지 않습니다.
 */

// ---------------------------------------------------------------------------
// Agent
// ---------------------------------------------------------------------------

/** 연결된 Agent 목록과 요약을 조회합니다. */
export function listAgents(): Promise<ApiAgentList> {
  return apiRequest<ApiAgentList>("/api/v1/agents");
}

/** 특정 Agent 의 최근 텔레메트리 원본을 조회합니다. */
export function getAgentTelemetry(agentId: string): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>(
    `/api/v1/agents/${encodeURIComponent(agentId)}/telemetry`,
  );
}

/** 벤더 파서별 변환 요약을 조회합니다. */
export function getAgentConfigs(): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>("/api/v1/agents/configs");
}

/**
 * 배포 예정 + 실제 관측을 합친 통합 장치 현황을 조회합니다.
 *
 * <p>대시보드/Agent 화면은 이쪽을 써야 합니다. {@link listAgents} 는 지금 붙어
 * 있는 Agent 만 주므로 "배포했는데 아직 안 붙은 장치" 가 목록에서 사라집니다.
 *
 * @param projectId 프로젝트 키 (없으면 전체)
 */
export function listAgentOverview(projectId?: string): Promise<ApiAgentOverviewList> {
  return apiRequest<ApiAgentOverviewList>("/api/v1/agents/overview", {
    params: { project_id: projectId },
  });
}

/**
 * 배포 예정 Agent 를 서버에 등록합니다. (같은 식별자는 갱신)
 *
 * <p>배포 버튼을 눌렀을 때 호출합니다. 이 호출이 있어야 프로버가 아직
 * 접속하지 않았어도 화면과 목록에 장치가 나타납니다.
 *
 * @param agentId Agent 식별자
 * @param options 프로젝트/유형/주소 등 부가 정보
 */
export function registerExpectedAgent(
  agentId: string,
  options?: {
    projectId?: string;
    deviceType?: string;
    nodeType?: string;
    expectedIp?: string;
    note?: string;
  },
): Promise<ApiExpectedAgentRegistered> {
  return apiRequest<ApiExpectedAgentRegistered>("/api/v1/agents/expected", {
    method: "POST",
    body: {
      agent_id: agentId,
      project_id: options?.projectId,
      device_type: options?.deviceType,
      node_type: options?.nodeType,
      expected_ip: options?.expectedIp,
      note: options?.note,
    },
  });
}

/** 배포 예정 항목을 삭제합니다. (연결된 세션은 유지) */
export function deleteExpectedAgent(agentId: string): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>(
    `/api/v1/agents/expected/${encodeURIComponent(agentId)}`,
    { method: "DELETE" },
  );
}

/**
 * Agent 하나의 수집 이력을 제거합니다. (유령 정리)
 *
 * <p>한 번이라도 텔레메트리를 보낸 Agent 는 저장소에 영구히 남아 목록을
 * 차지합니다. 연결 중인 Agent 는 서버가 거부합니다(409).
 *
 * @param agentId 대상 Agent 식별자
 */
export function removeAgentTelemetry(
  agentId: string,
): Promise<{ agent_id: string; removed: boolean; reason: string | null }> {
  return apiRequest(`/api/v1/agents/${encodeURIComponent(agentId)}/telemetry`, {
    method: "DELETE",
  });
}

/**
 * 오래 수신이 없는 Agent 이력을 일괄 정리합니다.
 *
 * @param olderThanHours 기준 시간 (기본 24시간)
 */
export function pruneStaleAgents(
  olderThanHours?: number,
): Promise<{ removed: number; remaining_telemetry: number; older_than_hours: number }> {
  const suffix = olderThanHours ? `?older_than_hours=${olderThanHours}` : "";
  return apiRequest(`/api/v1/agents/stale${suffix}`, { method: "DELETE" });
}

// ---------------------------------------------------------------------------
// Agent 설치 번들 (설정이 미리 채워진 다운로드)
// ---------------------------------------------------------------------------

/**
 * Agent 설치 번들 정보를 미리 조회합니다. (다운로드 전 확인)
 *
 * <p>서버가 어떤 주소/유형으로 설정을 채우는지, 스테이징된 자산이 무엇인지
 * 확인할 수 있습니다.
 *
 * @param agentId  Agent 이름
 * @param nodeType 장치 유형 (Router/Switch/VM/Firewall)
 */
export function getAgentBundleInfo(
  agentId: string,
  nodeType?: string,
): Promise<{
  agent_id: string;
  node_type: string;
  server_ip: string;
  server_port: number;
  file_name: string;
  staged_assets: Record<string, boolean>;
}> {
  return apiRequest("/api/v1/agents/bundle/info", {
    params: { agent_id: agentId, node_type: nodeType },
  });
}

/**
 * Agent 설치 번들(ZIP)을 내려받습니다.
 *
 * <h2>⚠️ 공유 링크가 아니라 Blob 을 받는 이유</h2>
 * <p>{@code <a href>} 로 열면 브라우저가 새 탭에서 다운로드합니다. 그러면
 * <b>실패를 알 수 없습니다</b> — 401/500 이면 오류 JSON 이 파일로 저장되고,
 * 운영자는 그것이 설치 번들인지 오류인지 구분하지 못합니다.
 * 먼저 응답을 확인하고 실패를 드러냅니다.
 *
 * @param agentId       Agent 이름
 * @param options       장치 유형 / 서버 주소 / 데이터 경로
 * @returns 파일 이름과 Blob
 */
export async function downloadAgentBundle(
  agentId: string,
  options?: { nodeType?: string; serverIp?: string; dataDirectory?: string },
): Promise<{ fileName: string; blob: Blob }> {
  const params = new URLSearchParams();
  if (options?.nodeType) params.set("node_type", options.nodeType);
  if (options?.serverIp) params.set("server_ip", options.serverIp);
  if (options?.dataDirectory) params.set("data_directory", options.dataDirectory);

  const query = params.toString();
  const path = `/api/v1/agents/bundle/${encodeURIComponent(agentId)}${query ? `?${query}` : ""}`;

  // apiRequest 는 JSON 을 기대하므로 ZIP 에는 쓸 수 없습니다. 직접 fetch 합니다.
  const response = await fetch(`${API_BASE_URL}${path}`, { credentials: "include" });
  if (!response.ok) {
    // 서버가 보낸 사유를 최대한 읽어 오류 메시지에 넣습니다.
    let detail = `${response.status}`;
    try {
      const text = await response.text();
      if (text) detail += ` — ${text.slice(0, 200)}`;
    } catch {
      // 본문을 못 읽어도 상태 코드는 남깁니다.
    }
    throw new Error(`설치 번들을 만들지 못했습니다 (${detail})`);
  }

  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  const fileName = match ? decodeURIComponent(match[1]) : `sonar-agent-${agentId}.zip`;

  return { fileName, blob: await response.blob() };
}

// ---------------------------------------------------------------------------
// 정책
// ---------------------------------------------------------------------------

/**
 * 프로젝트의 정책 위반 현황을 조회합니다.
 *
 * <p>심각도 집계와 규칙별 묶음이 함께 오므로, 화면에서 다시 집계할 필요가
 * 없습니다.
 */
export function getPolicyViolations(projectId: string): Promise<ApiPolicyViolations> {
  return apiRequest<ApiPolicyViolations>(
    `/api/v1/policy/violations/${encodeURIComponent(projectId)}`,
  );
}

/** 등급을 건너뛰는 서브넷 쌍 목록을 조회합니다. */
export function getPolicyForbiddenPairs(projectId: string): Promise<ApiForbiddenPairs> {
  return apiRequest<ApiForbiddenPairs>(
    `/api/v1/policy/forbidden-pairs/${encodeURIComponent(projectId)}`,
  );
}

/**
 * 검증을 통과한 정책을 장치로 푸시합니다.
 *
 * @param projectId 프로젝트 키
 * @param force 위반이 있어도 강제 전송할지 여부
 */
export function pushPolicy(
  projectId: string,
  force = false,
): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>(
    `/api/v1/policy/push/${encodeURIComponent(projectId)}`,
    { method: "POST", params: { force } },
  );
}

// ---------------------------------------------------------------------------
// 네트워크 / 토폴로지
// ---------------------------------------------------------------------------

/** 프로젝트 관점의 토폴로지(노드/간선)를 조회합니다. */
export function getTopology(projectId: string): Promise<ApiTopology> {
  return apiRequest<ApiTopology>(`/api/v1/network/topology/${encodeURIComponent(projectId)}`);
}

/** 프로젝트 서브넷 등급과 결합된 수집 장치 목록을 조회합니다. */
export function getDiscoveredDevices(projectId: string): Promise<ApiDiscoveredDevices> {
  return apiRequest<ApiDiscoveredDevices>(
    `/api/v1/network/discovered/${encodeURIComponent(projectId)}`,
  );
}

/** 전체 수집 장치 현황을 조회합니다. (프로젝트 무관) */
export function getAllDiscoveredDevices(): Promise<ApiDiscoveredDevices> {
  return apiRequest<ApiDiscoveredDevices>("/api/v1/network/discovered");
}

/** 전체 장치의 라우팅 테이블을 조회합니다. */
export function listRouteTables(protocol?: string): Promise<{
  protocol_filter: string | null;
  device_count: number;
  devices: ApiRouteTable[];
}> {
  return apiRequest("/api/v1/routes", { params: { protocol } });
}

/** 특정 장치의 라우팅 테이블을 조회합니다. */
export function getRouteTable(agentId: string, protocol?: string): Promise<ApiRouteTable> {
  return apiRequest<ApiRouteTable>(`/api/v1/routes/${encodeURIComponent(agentId)}`, {
    params: { protocol },
  });
}

// ---------------------------------------------------------------------------
// 변경 이력 (Compliance)
// ---------------------------------------------------------------------------

/** 변경 이력을 조회합니다. 둘 다 주면 장치 축이 우선입니다. */
export function listComplianceChanges(options?: {
  projectId?: string;
  agentId?: string;
}): Promise<ApiComplianceChanges> {
  return apiRequest<ApiComplianceChanges>("/api/v1/compliance/changes", {
    params: { project_id: options?.projectId, agent_id: options?.agentId },
  });
}

// ---------------------------------------------------------------------------
// 격리 (Quarantine)
// ---------------------------------------------------------------------------

/**
 * Agent 를 격리합니다.
 *
 * <h2>응답의 두 값을 모두 확인해야 합니다</h2>
 * <ul>
 *   <li>{@code delivered} — 명령이 장치에 도달했나
 *         ({@code false} 면 장치 미연결. 재접속 시 차단 정책이 적용됩니다)</li>
 *   <li>{@code applied} — 장치가 실제로 인터페이스를 내렸나
 *         ({@code null} 이면 아직 ack 를 못 받음)</li>
 * </ul>
 *
 * @param agentId 격리할 Agent 식별자
 * @param options 프로젝트 키 / 사유
 */
export function quarantineAgent(
  agentId: string,
  options?: { projectId?: string; reason?: string; requestedBy?: string },
): Promise<ApiQuarantineState> {
  return apiRequest<ApiQuarantineState>(
    `/api/v1/quarantine/${encodeURIComponent(agentId)}`,
    {
      method: "POST",
      body: {
        project_id: options?.projectId,
        reason: options?.reason,
        requested_by: options?.requestedBy,
      },
    },
  );
}

/** Agent 의 격리를 해제합니다. (격리 중이 아니면 {@code released:false}) */
export function releaseQuarantine(
  agentId: string,
  releasedBy?: string,
): Promise<ApiQuarantineRelease> {
  return apiRequest<ApiQuarantineRelease>(
    `/api/v1/quarantine/${encodeURIComponent(agentId)}`,
    { method: "DELETE", params: { released_by: releasedBy } },
  );
}

/** 현재 격리 중인 Agent 목록을 조회합니다. (토폴로지 빨간색 표시용) */
export function listQuarantined(projectId?: string): Promise<ApiQuarantineList> {
  return apiRequest<ApiQuarantineList>("/api/v1/quarantine", {
    params: { project_id: projectId },
  });
}

/** 한 Agent 의 격리 여부와 이력을 조회합니다. */
export function getQuarantineStatus(agentId: string): Promise<ApiQuarantineStatus> {
  return apiRequest<ApiQuarantineStatus>(
    `/api/v1/quarantine/${encodeURIComponent(agentId)}`,
  );
}
