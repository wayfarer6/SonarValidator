import { apiRequest } from "./client";

/**
 * OPNsense 연동 API 모듈입니다.
 *
 * <h2>설계 의도</h2>
 * OPNsense 는 아직 실장비로 검증할 수 없으므로, 이 모듈은
 * <b>원문을 볼 수 있는 경로</b>를 함께 제공합니다(`probe`).
 * 응답 구조를 추측해 화면을 만들면 조용히 빈 값이 나오고, 그 원인을
 * 찾기 어려워집니다.
 */

/** 연결 확인 상태입니다. */
export type OPNsenseStatus = "UNVERIFIED" | "OK" | "FAILED";

/** 등록된 OPNsense 접속 정보입니다. 시크릿 원문은 서버가 내려보내지 않습니다. */
export interface OPNsenseCredential {
  agent_id: string;
  display_name: string | null;
  base_url: string;
  /** 마스킹된 API Key (예: `abcd****`). */
  api_key_masked: string;
  /** API Key 가 저장되어 있는지 여부. */
  has_api_key: boolean;
  /** Secret 이 저장되어 있는지 여부. 값 자체는 오지 않습니다. */
  has_secret: boolean;
  allow_insecure_tls: boolean;
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

/** 원문 조회(probe) 결과입니다. */
export interface OPNsenseProbeResult {
  agent_id: string;
  target: string;
  base_url: string;
  ok: boolean;
  status_code: number;
  error: string | null;
  summary: Record<string, unknown>;
  raw_preview: string | null;
}

/** 등록된 접속 정보 목록을 조회합니다. */
export function listCredentials(): Promise<{
  total: number;
  credentials: OPNsenseCredential[];
}> {
  return apiRequest("/api/v1/opnsense/credentials");
}

/**
 * Agent 의 접속 정보를 조회합니다.
 *
 * @param agentId Agent 식별자
 */
export function getCredential(agentId: string): Promise<OPNsenseCredential> {
  return apiRequest(`/api/v1/opnsense/credentials/${encodeURIComponent(agentId)}`);
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
  return apiRequest(`/api/v1/opnsense/credentials/${encodeURIComponent(agentId)}`, {
    method: "PUT",
    body: input,
  });
}

/** 접속 정보를 삭제합니다. */
export function deleteCredential(agentId: string): Promise<{ deleted: boolean }> {
  return apiRequest(`/api/v1/opnsense/credentials/${encodeURIComponent(agentId)}`, {
    method: "DELETE",
  });
}

/**
 * 연결을 확인합니다. (저장 후 "연결 테스트")
 *
 * @param agentId Agent 식별자
 */
export function verifyCredential(agentId: string): Promise<OPNsenseCredential> {
  return apiRequest(`/api/v1/opnsense/credentials/${encodeURIComponent(agentId)}/verify`, {
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

/**
 * OPNsense 응답 원문을 조회합니다. (진단 전용)
 *
 * <p>실장비가 붙었을 때 응답 구조를 확인하는 용도입니다.
 *
 * @param agentId Agent 식별자
 * @param target  interfaces | rules | nat | aliases | firmware
 */
export function probeCredential(
  agentId: string,
  target: "interfaces" | "rules" | "nat" | "aliases" | "firmware" = "firmware",
): Promise<OPNsenseProbeResult> {
  return apiRequest(
    `/api/v1/opnsense/credentials/${encodeURIComponent(agentId)}/probe`,
    { method: "POST", params: { target } },
  );
}

/** 설정이 필요할 수 있는 방화벽 후보 목록을 조회합니다. */
export function listCandidates(): Promise<{
  total: number;
  candidates: OPNsenseCandidate[];
}> {
  return apiRequest("/api/v1/opnsense/candidates");
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
