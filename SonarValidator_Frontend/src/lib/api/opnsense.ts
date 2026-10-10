import { apiRequest } from "./client";

/**
 * OPNsense 연동 API 모듈입니다.
 *
 * <h2>설계 의도</h2>
 * 프로버를 올릴 수 없는 장비라 서버가 <b>REST API 로 직접 접속</b>합니다.
 * 화면에는 자격증명 원문을 내려보내지 않고, 저장 여부({@code has_secret})와
 * 마스킹된 Key 만 보여줍니다.
 *
 * <h2>⚠️ 화면 조작은 장치 이름으로 합니다</h2>
 * 서버는 사람이 읽는 {@code agent_id} 와 정본 번호 {@code node_id} 를 모두
 * 받습니다. 편집 대상을 고를 때는 {@link credentialNodeId} 를 쓰세요 —
 * Agent 가 없는 장비는 {@code agent_id} 가 null 이라 번호로만 지정할 수 있습니다.
 */

/** 연결 확인 상태입니다. */
export type OPNsenseStatus = "UNVERIFIED" | "OK" | "FAILED";

/** 등록된 OPNsense 접속 정보입니다. 시크릿 원문은 서버가 내려보내지 않습니다. */
export interface OPNsenseCredential {
  /**
   * 노드의 정본 번호입니다. 서버가 저장/조회의 실제 키로 씁니다.
   *
   * <p>REST 전용 장비(OPNsense)는 Agent 가 없어 {@code agent_id} 가 비어 있을 수
   * 있습니다. 그래서 편집/삭제 시에는 이 번호를 쓸 수 있어야 합니다.
   */
  node_id: number;
  /**
   * Agent 식별자(자연키)입니다. 서버가 legacy 별칭으로도 받아 줍니다.
   *
   * <p>REST 전용 장비는 Agent 가 없으므로 {@code null} 입니다.
   */
  agent_id: string | null;
  display_name: string | null;
  base_url: string;
  /** 마스킹된 API Key (예: `abcd****`). */
  api_key_masked: string;
  /** API Key 가 저장되어 있는지 여부. */
  has_api_key: boolean;
  /** Secret 이 저장되어 있는지 여부. 값 자체는 오지 않습니다. */
  has_secret: boolean;
  allow_insecure_tls: boolean;
  /**
   * 이 장치가 속한 프로젝트 키입니다. 미지정이면 null 입니다.
   *
   * <p>REST 전용 장치는 `expected_agent` 에 없어 다른 곳에서 소속을 알 수
   * 없습니다. 그래서 접속 정보가 직접 들고 있습니다.
   */
  project_id: string | null;
  /** 프로젝트 표시 이름. 프로젝트가 지워지면 키가 대신 들어옵니다. */
  project_name: string | null;
  status: OPNsenseStatus;
  last_checked_at: string | null;
  last_error: string | null;
  detected_version: string | null;
  created_at: string | null;
  updated_at: string | null;
}

/** 설정 저장 요청입니다. */
export interface OPNsenseCredentialInput {
  display_name?: string;
  base_url: string;
  /** 비우면 기존 값이 유지됩니다. */
  api_key?: string;
  /** 비우면 기존 값이 유지됩니다. */
  api_secret?: string;
  allow_insecure_tls?: boolean;
  verify_now?: boolean;
  /**
   * 이 장치가 속한 프로젝트 키입니다.
   *
   * <p>규칙: <b>필드를 생략</b>하면 서버가 기존 값을 유지하고, <b>빈 문자열</b>을
   * 보내면 지정이 해제됩니다. 프로젝트를 바꾸는 것뿐 아니라 <b>지우는 것</b>도
   * 가능해야 하므로 두 경우를 구분합니다.
   */
  project_key?: string;
}

/** 후보 Agent (설정이 필요한 방화벽). */
export interface OPNsenseCandidate {
  agent_id: string;
  hostname: string | null;
  product: string | null;
  vendor: string | null;
  format: string | null;
  has_credential: boolean;
  /** 수집된 설정에서 OPNsense 로 식별되었으면 true. */
  detected?: boolean;
}

/** 등록된 접속 정보 목록을 조회합니다. */
export function listCredentials(): Promise<{
  total: number;
  credentials: OPNsenseCredential[];
}> {
  return apiRequest("/api/v1/opnsense/credentials");
}

/**
 * 경로 변수에 들어갈 장치 이름(node_id)을 검증하고 URL 인코딩해 돌려줍니다.
 *
 * <h2>⚠️ 왜 여기서 막는가</h2>
 * 빈 값을 그대로 경로에 넣으면 `/api/v1/opnsense/credentials/` 가 되어
 * <b>경로 변수가 없는</b> 요청이 됩니다. 그러면 컨트롤러의
 * `/credentials/{nodeId}` 와도, 목록용 `/credentials` 와도 매칭되지 않아
 * 서버는 <b>"No static resource api/v1/opnsense/credentials."</b> 라는
 * 404 를 돌려줍니다. 이 메시지는 "엔드포인트가 틀렸다" 처럼 보이지만
 * 실제 원인은 <b>장치 이름이 비어 있는 것</b>이라 추적이 오래 걸립니다.
 * 그래서 요청을 보내기 전에 여기서 명확한 오류로 바꿉니다.
 *
 * @param agentId 장치 이름(node_id)
 * @returns URL 인코딩된 장치 이름
 */
function requireNodeId(agentId: string): string {
  const nodeId = (agentId ?? "").trim();
  if (nodeId === "") {
    throw new Error(
      "장치 이름(node_id)이 비어 있습니다. OPNsense 설정 화면에서 장치 이름을 먼저 입력하세요.",
    );
  }
  return encodeURIComponent(nodeId);
}

/**
 * Agent 의 접속 정보를 조회합니다.
 *
 * @param agentId Agent 식별자
 */
export function getCredential(agentId: string): Promise<OPNsenseCredential> {
  return apiRequest(`/api/v1/opnsense/credentials/${requireNodeId(agentId)}`);
}

/**
 * 접속 정보를 저장합니다. (없으면 생성)
 *
 * <p>`api_key` / `api_secret` 을 비우면 서버가 기존 값을 유지합니다.
 * 운영자가 매번 평문 시크릿을 다시 입력하지 않도록 하기 위함입니다.
 *
 * @param agentId Agent 식별자
 * @param input   설정 값
 */
export function saveCredential(
  agentId: string,
  input: OPNsenseCredentialInput,
): Promise<OPNsenseCredential> {
  return apiRequest(`/api/v1/opnsense/credentials/${requireNodeId(agentId)}`, {
    method: "PUT",
    body: input,
  });
}

/** 접속 정보를 삭제합니다. */
export function deleteCredential(agentId: string): Promise<{ deleted: boolean }> {
  return apiRequest(`/api/v1/opnsense/credentials/${requireNodeId(agentId)}`, {
    method: "DELETE",
  });
}

/**
 * 연결을 확인합니다. (저장 후 "연결 테스트")
 *
 * @param agentId Agent 식별자
 */
export function verifyCredential(agentId: string): Promise<OPNsenseCredential> {
  return apiRequest(`/api/v1/opnsense/credentials/${requireNodeId(agentId)}/verify`, {
    method: "POST",
  });
}

/** 등록된 모든 설정의 연결을 확인합니다. */
export function verifyAllCredentials(): Promise<{
  total: number;
  results: OPNsenseCredential[];
}> {
  return apiRequest("/api/v1/opnsense/verify-all", { method: "POST" });
}

/** 설정이 필요할 수 있는 방화벽 후보 목록을 조회합니다. */
export function listCandidates(): Promise<{
  total: number;
  candidates: OPNsenseCandidate[];
}> {
  return apiRequest("/api/v1/opnsense/candidates");
}

/**
 * 목록/편집에서 이 설정을 가리키는 키를 돌려줍니다.
 *
 * <p>사람이 읽을 수 있는 {@code agent_id} 를 우선하고, 없으면(REST 전용 장비)
 * 정본 번호 {@code node_id} 를 씁니다. 서버는 두 표기를 모두 받아 줍니다.
 *
 * @param credential 접속 정보
 * @returns 편집/삭제 요청에 쓸 식별자
 */
export function credentialNodeId(credential: OPNsenseCredential): string {
  const agentId = credential.agent_id?.trim();
  return agentId ? agentId : String(credential.node_id);
}

/**
 * 목록에 보여줄 이름을 돌려줍니다.
 *
 * <p>{@code display_name} 은 서버가 비어 있으면 {@code agent_id} 로 채우므로
 * 대개 값이 있습니다. 그마저 없으면 노드 번호로 대체합니다.
 *
 * @param credential 접속 정보
 * @returns 표시 이름
 */
export function credentialLabel(credential: OPNsenseCredential): string {
  const display = credential.display_name?.trim();
  if (display) return display;
  const agentId = credential.agent_id?.trim();
  return agentId ? agentId : `node-${credential.node_id}`;
}

/** 상태에 맞는 배지 색을 돌려줍니다. */
export function statusColor(status: OPNsenseStatus): "success" | "error" | "warning" {
  if (status === "OK") return "success";
  if (status === "FAILED") return "error";
  return "warning";
}

/** 상태를 사람이 읽는 문구로 바꿉니다. */
export function statusLabel(status: OPNsenseStatus): string {
  if (status === "OK") return "연결됨";
  if (status === "FAILED") return "실패";
  return "미확인";
}
