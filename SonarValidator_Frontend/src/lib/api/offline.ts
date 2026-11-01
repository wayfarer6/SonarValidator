import { API_BASE_URL, ApiError, apiRequest } from "./client";


// 타입

export interface ApiOfflineSchema {
  schema: string;
  schema_version: number;
  max_bytes: number;
  supported_formats: string[];
  server_time: string;
}

export interface ApiOfflineSnapshotResult {
  file_name: string;
  accepted: boolean;
  agent_id: string | null;
  format: string | null;
  interfaces: number;
  routes: number;
  vlans: number;
  firewall_rules: number;
  warnings: string[];
  errors: string[];
}

export interface ApiOfflineImportResult {
  received: number;
  accepted: number;
  rejected: number;
  snapshots: ApiOfflineSnapshotResult[];
  server_warnings: string[];
  accepted_device_ids: string[];
}

export interface ApiOfflineImportedDevice {
  agent_id: string;
  format: string | null;
  product: string | null;
  vendor: string | null;
  interfaces: number;
  routes: number;
  vlans: number;
  firewall_rules: number;
  bridges: string[];
  warnings: string[];
}

export interface ApiOfflineImportedList {
  imported_devices: number;
  devices: ApiOfflineImportedDevice[];
  server_time: string;
}

// 조회

export function getOfflineSchema(): Promise<ApiOfflineSchema> {
  return apiRequest<ApiOfflineSchema>("/api/v1/offline/schema");
}

export function getOfflineImported(): Promise<ApiOfflineImportedList> {
  return apiRequest<ApiOfflineImportedList>("/api/v1/offline/imported");
}

export async function downloadSnapshot(agentId: string): Promise<{
  file_name: string;
  blob: Blob;
}> {
  const path = `/api/v1/offline/export/${encodeURIComponent(agentId)}`;
  const url = `${API_BASE_URL}${path}`;

  const response = await fetch(url, {
    method: "GET",
    credentials: "include",
  }).catch((cause) => {
    // 네트워크 실패(백엔드 미기동)는 status 0 으로 구분합니다.
    throw new ApiError(0, "서버에 연결할 수 없습니다.", cause).record("GET", path);
  });

  if (!response.ok) {
    throw new ApiError(response.status, "스냅샷을 내려받지 못했습니다.", await response.text()).record("GET", path);
  }

  const blob = await response.blob().catch((cause: unknown) => {
    throw new ApiError(0, "다운로드 응답을 받지 못했습니다.", cause).record("GET", path);
  });
  return {
    // 파일 이름에 시각을 넣어 여러 번 받아도 덮어쓰이지 않게 합니다.
    file_name: `${agentId}_${new Date().toISOString().replace(/[:.]/g, "-")}.json`,
    blob,
  };
}

// 업로드

export async function importSnapshots(
  files: File[],
): Promise<ApiOfflineImportResult> {
  const form = new FormData();
  for (const file of files) {
    // 서버의 @RequestParam("files") 와 이름이 일치해야 합니다.
    form.append("files", file, file.name);
  }

  const response = await fetch(`${API_BASE_URL}/api/v1/offline/import`, {
    method: "POST",
    // multipart 는 브라우저가 boundary 를 붙여야 하므로 Content-Type 을 직접
    // 지정하면 안 됩니다. (지정하면 서버가 파싱하지 못합니다)
    body: form,
    credentials: "include",
  }).catch((cause) => {
    throw new ApiError(0, "서버에 연결할 수 없습니다.", cause).record("POST", "/api/v1/offline/import");
  });

  const text = await response.text().catch((cause: unknown) => {
    throw new ApiError(0, "응답을 확인하지 못했습니다. 실행 이력을 확인하세요.", cause).record("POST", "/api/v1/offline/import");
  });
  let body: unknown;
  try { body = JSON.parse(text); } catch { body = text; }

  if (!response.ok) {
    throw new ApiError(
      response.status,
      `업로드에 실패했습니다 (${response.status})`,
      body,
    ).record("POST", "/api/v1/offline/import");
  }

  return body as ApiOfflineImportResult;
}

export async function importSnapshotJson(
  content: string,
): Promise<ApiOfflineImportResult> {
  return apiRequest<ApiOfflineImportResult>("/api/v1/offline/import", {
    method: "POST",
    body: content,
  });
}

// 클라이언트 측 사전 검증

export function precheckFile(
  file: File,
  schema?: string,
): { ok: true } | { ok: false; reason: string } {
  if (file.size === 0) {
    return { ok: false, reason: "빈 파일입니다." };
  }

  // 확장자 검사는 관대하게 합니다. .json 이 없어도 내용이 맞으면 서버가 받습니다.
  if (!file.name.toLowerCase().endsWith(".json") && file.type !== "application/json") {
    return {
      ok: false,
      reason: "JSON 파일이 아닙니다. (Prober의 --export snapshot.json으로 만든 파일을 올리세요)",
    };
  }

  // 8MB 를 넘으면 서버가 어차피 거부하므로 먼저 알려 줍니다.
  const MAX_BYTES = 8 * 1024 * 1024;
  if (file.size > MAX_BYTES) {
    return {
      ok: false,
      reason: `파일이 너무 큽니다 (${(file.size / 1024 / 1024).toFixed(1)}MB). 최대 8MB 입니다.`,
    };
  }

  void schema; // 스키마 이름은 서버가 최종 판정하므로 여기서는 쓰지 않습니다.
  return { ok: true };
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
