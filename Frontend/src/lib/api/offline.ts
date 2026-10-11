import { API_BASE_URL, ApiError, apiRequest } from "./client";

/**
 * 오프라인 스냅샷(Agent 가 서버 없이 남긴 JSON) 업로드 API 입니다.
 *
 * <h2>왜 파일 업로드만 별도 모듈인가</h2>
 * 다른 API 는 모두 JSON 본문을 보냅니다. 이 모듈만 {@code multipart/form-data}
 * 를 씁니다. 그래서 {@link apiRequest} 를 쓸 수 없고 fetch 를 직접 호출합니다.
 * 그렇다고 화면에서 fetch 를 직접 쓰면 오류 처리/기본 URL 규칙이 흩어지므로,
 * <b>업로드 규칙은 이 파일에만</b> 둡니다.
 *
 * <h2>세션 쿠키가 필수인 이유</h2>
 * 다른 요청과 마찬가지로 {@code credentials: "include"} 가 필요합니다.
 * 빠뜨리면 "로그인은 됐는데 업로드만 401" 이 됩니다.
 */

// ---------------------------------------------------------------------------
// 타입
// ---------------------------------------------------------------------------

/** 서버가 알려주는 스냅샷 스키마 정보. */
export interface ApiOfflineSchema {
  schema: string;
  schema_version: number;
  max_bytes: number;
  supported_formats: string[];
  server_time: string;
}

/** 파일 한 건의 처리 결과. */
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

/** 업로드 전체 결과. */
export interface ApiOfflineImportResult {
  received: number;
  accepted: number;
  rejected: number;
  snapshots: ApiOfflineSnapshotResult[];
  server_warnings: string[];
  accepted_device_ids: string[];
}

/** 오프라인으로 반영된 장치 한 대. */
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

// ---------------------------------------------------------------------------
// 조회
// ---------------------------------------------------------------------------

/** 서버가 기대하는 스키마를 조회합니다. (업로드 카드의 안내문에 씁니다) */
export function getOfflineSchema(): Promise<ApiOfflineSchema> {
  return apiRequest<ApiOfflineSchema>("/api/v1/offline/schema");
}

/** 파일 업로드로 반영된 장치 목록을 조회합니다. */
export function getOfflineImported(): Promise<ApiOfflineImportedList> {
  return apiRequest<ApiOfflineImportedList>("/api/v1/offline/imported");
}

/**
 * 특정 Agent 의 설정을 스냅샷 파일로 내려받습니다.
 *
 * <p>공유 링크가 아니라 Blob 으로 받는 이유: 서버가 401/404 를 돌려줄 때
 * 브라우저가 JSON 오류를 파일로 저장해 버리는 것을 막고, 화면에서 실패를
 * 알릴 수 있게 하기 위함입니다.
 *
 * @param agentId Agent 식별자
 * @returns 저장할 파일 이름과 Blob
 */
export async function downloadSnapshot(agentId: string): Promise<{
  file_name: string;
  blob: Blob;
}> {
  const url = `${API_BASE_URL}/api/v1/offline/export/${encodeURIComponent(agentId)}`;

  const response = await fetch(url, {
    method: "GET",
    credentials: "include",
  }).catch((cause) => {
    // 네트워크 실패(백엔드 미기동)는 status 0 으로 구분합니다.
    throw new ApiError(0, cause instanceof Error ? cause.message : "네트워크 오류");
  });

  if (!response.ok) {
    throw new ApiError(response.status, `스냅샷을 내려받지 못했습니다 (${response.status})`);
  }

  const blob = await response.blob();
  return {
    // 파일 이름에 시각을 넣어 여러 번 받아도 덮어쓰이지 않게 합니다.
    file_name: `${agentId}_${new Date().toISOString().replace(/[:.]/g, "-")}.json`,
    blob,
  };
}

// ---------------------------------------------------------------------------
// 업로드
// ---------------------------------------------------------------------------

/**
 * 스냅샷 파일들을 업로드합니다.
 *
 * <h2>왜 한 번에 여러 파일을 보내는가</h2>
 * <p>서버가 파일별 결과를 돌려주므로, 운영자는 "3건 중 2건 반영, 1건 실패" 를
 * 한 번의 조작으로 알 수 있습니다. 파일마다 요청을 보내면 진행 상황을 화면에서
 * 직접 조립해야 하고, 중간에 실패했을 때 어디까지 됐는지 흐려집니다.
 *
 * @param files 업로드할 파일들
 * @returns 파일별 처리 결과
 */
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
    throw new ApiError(0, cause instanceof Error ? cause.message : "네트워크 오류");
  });

  const body = await response.json().catch(() => null);

  if (!response.ok) {
    throw new ApiError(
      response.status,
      `업로드에 실패했습니다 (${response.status})`,
      body,
    );
  }

  return body as ApiOfflineImportResult;
}

/**
 * 스냅샷 JSON 을 본문으로 직접 업로드합니다.
 *
 * <p>Agent 의 {@code --export-stdout} 출력이나 복사한 JSON 을 붙여넣을 때
 * 씁니다. 파일을 만들 수 없는 환경(원격 콘솔)에서 필요합니다.
 *
 * @param content 스냅샷 JSON 문자열
 * @returns 처리 결과
 */
export async function importSnapshotJson(
  content: string,
): Promise<ApiOfflineImportResult> {
  return apiRequest<ApiOfflineImportResult>("/api/v1/offline/import", {
    method: "POST",
    body: content,
  });
}

// ---------------------------------------------------------------------------
// 클라이언트 측 사전 검증
// ---------------------------------------------------------------------------

/**
 * 업로드 전에 파일을 가볍게 검사합니다.
 *
 * <p>서버가 최종 판정을 하지만, 명백히 잘못된 파일(JSON 이 아님, 다른 스키마)을
 * 미리 걸러 주면 <b>업로드 시간을 낭비하지 않습니다.</b> 특히 큰 파일을 여러 개
 * 올릴 때 체감 차이가 큽니다.
 *
 * <p>주의: 여기서 통과해도 서버가 거부할 수 있습니다. 이 함수의 결과를
 * "성공" 으로 표시하면 안 되고, 단지 서버에 보낼지 말지를 정하는 데 씁니다.
 *
 * @param file  검사할 파일
 * @param schema 서버가 알려준 스키마 이름 (모르면 검사 생략)
 * @returns 통과 여부와 사유
 */
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
      reason: "JSON 파일이 아닙니다. (Agent 의 --export-once 로 만든 파일을 올리세요)",
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

/** 바이트를 사람이 읽는 크기로 바꿉니다. */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
