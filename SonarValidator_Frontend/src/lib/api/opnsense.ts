import { apiRequest } from "./client";


export type OPNsenseStatus = "UNVERIFIED" | "OK" | "FAILED";

export interface OPNsenseCredential {
  node_id: number;
  agent_id: string | null;
  display_name: string | null;
  base_url: string;
  api_key_masked: string;
  has_api_key: boolean;
  has_secret: boolean;
  allow_insecure_tls: boolean;
  project_id: string | null;
  project_name: string | null;
  status: OPNsenseStatus;
  last_checked_at: string | null;
  last_error: string | null;
  detected_version: string | null;
  created_at: string | null;
  updated_at: string | null;
}

export interface OPNsenseCredentialInput {
  display_name?: string;
  base_url: string;
  api_key?: string;
  api_secret?: string;
  allow_insecure_tls?: boolean;
  verify_now?: boolean;
  project_key?: string;
}

export interface OPNsenseCandidate {
  agent_id: string;
  hostname: string | null;
  product: string | null;
  vendor: string | null;
  format: string | null;
  has_credential: boolean;
  detected?: boolean;
}

export function listCredentials(): Promise<{
  total: number;
  credentials: OPNsenseCredential[];
}> {
  return apiRequest("/api/v1/opnsense/credentials");
}

function requireNodeId(agentId: string): string {
  const nodeId = (agentId ?? "").trim();
  if (nodeId === "") {
    throw new Error(
      "장치 이름(node_id)이 비어 있습니다. OPNsense 설정 화면에서 장치 이름을 먼저 입력하세요.",
    );
  }
  return encodeURIComponent(nodeId);
}

export function getCredential(agentId: string): Promise<OPNsenseCredential> {
  return apiRequest(`/api/v1/opnsense/credentials/${requireNodeId(agentId)}`);
}

export function saveCredential(
  agentId: string,
  input: OPNsenseCredentialInput,
): Promise<OPNsenseCredential> {
  return apiRequest(`/api/v1/opnsense/credentials/${requireNodeId(agentId)}`, {
    method: "PUT",
    body: input,
  });
}

export function deleteCredential(agentId: string): Promise<{ deleted: boolean }> {
  return apiRequest(`/api/v1/opnsense/credentials/${requireNodeId(agentId)}`, {
    method: "DELETE",
  });
}

export function verifyCredential(agentId: string): Promise<OPNsenseCredential> {
  return apiRequest(`/api/v1/opnsense/credentials/${requireNodeId(agentId)}/verify`, {
    method: "POST",
  });
}

export function verifyAllCredentials(): Promise<{
  total: number;
  results: OPNsenseCredential[];
}> {
  return apiRequest("/api/v1/opnsense/verify-all", { method: "POST" });
}

export function listCandidates(): Promise<{
  total: number;
  candidates: OPNsenseCandidate[];
}> {
  return apiRequest("/api/v1/opnsense/candidates");
}

export function credentialNodeId(credential: OPNsenseCredential): string {
  const agentId = credential.agent_id?.trim();
  return agentId ? agentId : String(credential.node_id);
}

export function credentialLabel(credential: OPNsenseCredential): string {
  const display = credential.display_name?.trim();
  if (display) return display;
  const agentId = credential.agent_id?.trim();
  return agentId ? agentId : `node-${credential.node_id}`;
}

export function statusColor(status: OPNsenseStatus): "success" | "error" | "warning" {
  if (status === "OK") return "success";
  if (status === "FAILED") return "error";
  return "warning";
}

export function statusLabel(status: OPNsenseStatus): string {
  if (status === "OK") return "연결됨";
  if (status === "FAILED") return "실패";
  return "미확인";
}
