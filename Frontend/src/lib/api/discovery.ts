import { getAllDiscoveredDevices, getDiscoveredDevices, listAgentOverview } from "./index";
import { apiRequest } from "./client";
import type { ApiDiscoveredDevice, ApiProject } from "./types";

/**
 * 프로젝트 화면이 쓰는 <b>수집 장치 목록</b>입니다.
 *
 * <h2>⚠️ 예전에는 등록 예정(ExpectedAgent)으로만 걸렀다</h2>
 * <p>이전 구현은 {@code overview.agents} 에서 {@code expected && project_id === projectId}
 * 인 Agent 만 남기고 수집 결과를 그 집합과 교집합했습니다. 그런데 스위치는
 * <b>프로젝트에 배정되기 전에도</b> OVS {@code tag}/{@code trunks} 를 수집해
 * 보고합니다. 그래서 장비를 프로젝트에 등록하지 않은 상태에서는 프론트엔드의
 * "수집된 VLAN" 표가 <b>항상 0건</b>으로 보였습니다.
 *
 * <p>실측(dev 스택): 스위치 5대가 VLAN 10 / 111·112 / 121·122 / 131·132·133 / 141
 * 을 보고했고 백엔드 {@code /api/v1/network/discovered} 에도 들어 있었지만,
 * 등록 예정 Agent 가 Gateway-Router 1대뿐이라 목록이 1대로 줄어 표가 비었습니다.
 *
 * <h2>지금의 규칙</h2>
 * <p>백엔드 {@code /api/v1/network/discovered/{projectId}} 는 이미 장치를
 * 돌려주고(연결된 장치는 수집 전이라도 포함) 프로젝트 CIDR 로 등급까지 붙입니다.
 * 그래서 <b>수집 결과를 그대로 두고</b>, 등록 예정이지만 아직 보고가 없는 장치만
 * 자리표시자로 더합니다. 그러면 "수집된 VLAN" 이 사라지지 않으면서도 배포 예정
 * 장비가 화면에 미리 나타납니다.
 *
 * @param projectId 프로젝트 키 (없으면 전체)
 * @returns 장치 목록 (수집 결과 + 미보고 등록 예정)
 */
export async function getProjectDevices(projectId?: string | null): Promise<ApiDiscoveredDevice[]> {
  if (!projectId) return (await getAllDiscoveredDevices()).devices;
  const [discovered, overview] = await Promise.all([
    getDiscoveredDevices(projectId), listAgentOverview(projectId),
  ]);

  const byId = new Map<string, ApiDiscoveredDevice>(
    discovered.devices.map(device => [device.agent_id, device]),
  );
  for (const agent of overview.agents ?? []) {
    if (agent.project_id !== projectId) continue;
    if (byId.has(agent.agent_id)) continue;
    // 아직 정책/텔레메트리를 보내지 않은 등록 예정 장치입니다.
    // 목록에서 빠지면 운영자가 "등록했는데 안 보인다" 로 오해합니다.
    byId.set(agent.agent_id, {
      agent_id: agent.agent_id,
      hostname: agent.agent_id,
      format: null,
      vendor: null,
      product: null,
      device_type: agent.device_type ?? null,
      last_seen: null,
      discovered: false,
      interfaces: [],
      vlans: [],
    });
  }
  return [...byId.values()];
}

export function getProjectForEditing(projectId: string): Promise<ApiProject> {
  return apiRequest<ApiProject>(`/api/v1/projects/${encodeURIComponent(projectId)}/editing`);
}

/** 프로젝트에 저장된 VLAN 이름 한 건입니다. */
export interface ApiVlanName {
  agent_id: string;
  vlan_id: number;
  name: string | null;
}

/**
 * 프로젝트에 저장된 <b>VLAN 이름</b>을 조회합니다.
 *
 * <p>장비는 {@code tag: 131} 같은 숫자만 주므로, 수집된 VLAN 의 업무 이름은
 * 운영자가 붙여 저장합니다. 이 값은 등급·대역·집행 규칙과 별개입니다.
 *
 * @param projectId 프로젝트 키
 * @returns 저장된 VLAN 이름 목록
 */
export async function listProjectVlanNames(projectId: string): Promise<ApiVlanName[]> {
  const response = await apiRequest<{ vlans?: ApiVlanName[] }>(
    `/api/v1/projects/${encodeURIComponent(projectId)}/vlans`,
  );
  return response.vlans ?? [];
}

/**
 * 수집된 VLAN 의 이름을 저장합니다.
 *
 * <p>{@code name} 을 비우면 <b>미지정</b>으로 되돌립니다. 행을 빈 문자열로
 * 남기면 화면이 빈 칸을 이름으로 표시하므로 서버가 삭제로 처리합니다.
 *
 * @param projectId 프로젝트 키
 * @param agentId   VLAN 을 보고한 Agent
 * @param vlanId    VLAN 번호
 * @param name      이름 (비우면 미지정)
 * @returns 저장 결과
 */
export function saveProjectVlanName(
  projectId: string,
  agentId: string,
  vlanId: number,
  name: string,
): Promise<{ saved: boolean; name: string | null }> {
  return apiRequest<{ saved: boolean; name: string | null }>(
    `/api/v1/projects/${encodeURIComponent(projectId)}/vlans`,
    {
      method: "PUT",
      body: { agent_id: agentId, vlan_id: vlanId, name },
    },
  );
}
