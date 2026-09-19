import { useCallback, useMemo, useRef, useState, type DragEvent } from "react";
import Badge from "../ui/badge/Badge";
import Button from "../ui/button/Button";
import { useApi } from "../../hooks/useApi";
import {
  formatBytes,
  getOfflineImported,
  getOfflineSchema,
  importSnapshots,
  precheckFile,
  type ApiOfflineImportResult,
  type ApiOfflineSnapshotResult,
} from "../../lib/api/offline";
import { ApiError } from "../../lib/api/client";

/**
 * 오프라인 Prober 데이터 가져오기 카드입니다.
 *
 * <h2>무엇을 하는 카드인가</h2>
 * <p>중앙 관리 서버에 연결할 수 없는 장비에서는 Prober 가 수집 결과를
 * JSON 스냅샷 파일로 남깁니다. 운영자가 그 파일들을 이 카드에
 * <b>끌어다 놓으면</b> 서버가 <b>온라인 텔레메트리와 완전히 같은 파서</b>로
 * 변환해 장치 목록에 반영합니다.
 *
 * <pre>
 *   서버 미도달 장비                  브라우저                    서버
 *   ┌──────────────┐   파일 전송   ┌──────────┐   multipart   ┌─────────────┐
 *   │ Prober       │ ────────────► │ 이 카드   │ ────────────► │ 파서 → 반영  │
 *   │ --export-once│               │ (드래그앤 │               │ (온라인과    │
 *   └──────────────┘               │  드롭)    │               │  동일 경로)  │
 *                                  └──────────┘               └─────────────┘
 * </pre>
 *
 * <h2>드래그앤드롭에서 조용히 틀리기 쉬운 4가지 (전부 방어함)</h2>
 * <ol>
 *   <li><b>dragover 에서 preventDefault 를 빼면 drop 이 아예 안 옵니다.</b>
 *       브라우저 기본 동작이 "파일 열기" 라서 여기서 막아야 합니다.
 *       게다가 dragover 는 자식 요소마다 다시 발생하므로
 *       <b>dragenter/dragleave 카운터</b>가 없으면 아이콘이 깜빡입니다.</li>
 *   <li><b>dataTransfer.files 는 drop 이후에만 채워집니다.</b> dragover 에서
 *       읽으면 빈 목록이라 "드롭했는데 아무 일도 안 일어남" 이 됩니다.</li>
 *   <li><b>폴더를 끌어다 놓을 수 있습니다.</b> 그대로 업로드하면 서버가
 *       해석 못 하는 항목이 섞입니다. 그래서 확장자/크기를 먼저 봅니다.</li>
 *   <li><b>같은 파일을 두 번 올리면 같은 결과가 두 번 반영됩니다.</b>
 *       이미 목록에 있는 파일은 제외해 중복 반영을 막습니다.</li>
 * </ol>
 *
 * <h2>파일 선택 입력을 함께 두는 이유</h2>
 * <p>드래그앤드롭은 마우스가 필요합니다. 원격 콘솔/스크립트 환경이나
 * 접근성을 위해 클릭 → 파일 선택 경로도 같은 처리로 흐르게 했습니다.
 */
export default function OfflineImportCard() {
  // 드래그 중인지. dragenter/dragleave 횟수를 세어 자식 이동 시 깜빡임을 막습니다.
  const dragDepth = useRef(0);
  const [isDragOver, setIsDragOver] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);

  /** 아직 업로드하지 않은 선택 파일들. */
  const [pending, setPending] = useState<File[]>([]);

  /** 사전 검사에서 걸러진 파일들 (이름 → 사유). 업로드하지 않습니다. */
  const [skipped, setSkipped] = useState<{ name: string; reason: string }[]>([]);

  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState<ApiOfflineImportResult | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const schema = useApi(() => getOfflineSchema(), []);
  const imported = useApi(() => getOfflineImported(), []);

  const maxBytes = schema.data?.max_bytes ?? 8 * 1024 * 1024;

  /**
   * 선택/드롭된 파일을 목록에 추가합니다.
   *
   * <p>사전 검사로 명백히 잘못된 파일은 <b>여기서</b> 걸러 사유를 보여줍니다.
   * 서버까지 보내고 거부당하면 왕복 시간을 낭비하고, 사용자는 왜 실패했는지
   * 알기 어렵습니다.
   */
  const addFiles = useCallback(
    (files: FileList | null) => {
      if (!files || files.length === 0) return;

      const accepted: File[] = [];
      const rejected: { name: string; reason: string }[] = [];

      for (const file of Array.from(files)) {
        const check = precheckFile(file, schema.data?.schema);
        if (check.ok) {
          accepted.push(file);
        } else {
          rejected.push({ name: file.name, reason: check.reason });
        }
      }

      setSkipped(rejected);

      if (accepted.length === 0) return;

      setPending((previous) => {
        // 같은 이름 + 같은 크기면 이미 고른 파일로 봅니다.
        // (중복 업로드는 같은 설정을 두 번 반영하게 만듭니다)
        const seen = new Set(previous.map((file) => `${file.name}:${file.size}`));
        const merged = [...previous];
        for (const file of accepted) {
          const key = `${file.name}:${file.size}`;
          if (!seen.has(key)) {
            seen.add(key);
            merged.push(file);
          }
        }
        return merged;
      });
    },
    [schema.data?.schema],
  );

  // ---------------------------------------------------------------------------
  // 드래그 이벤트 (1번 함정 방어)
  // ---------------------------------------------------------------------------

  const handleDragEnter = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    // 자식 요소로 들어갈 때도 dragenter 가 오므로 깊이를 세어야
    // 테두리 강조가 깜빡이지 않습니다.
    dragDepth.current += 1;
    setIsDragOver(true);
  };

  const handleDragOver = (event: DragEvent<HTMLDivElement>) => {
    // 이 한 줄이 없으면 drop 이벤트가 발생하지 않습니다.
    // (브라우저가 파일을 "열어 버리는" 기본 동작을 막는다)
    event.preventDefault();
    // 복사 의도임을 브라우저에 알려 커서를 + 로 바꿉니다.
    event.dataTransfer.dropEffect = "copy";
  };

  const handleDragLeave = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    dragDepth.current = Math.max(0, dragDepth.current - 1);
    if (dragDepth.current === 0) {
      setIsDragOver(false);
    }
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    dragDepth.current = 0;
    setIsDragOver(false);
    // dataTransfer.files 는 여기서만 유효합니다. (2번 함정)
    addFiles(event.dataTransfer.files);
  };

  // ---------------------------------------------------------------------------
  // 업로드
  // ---------------------------------------------------------------------------

  const handleUpload = async () => {
    if (pending.length === 0 || uploading) return;

    setUploading(true);
    setUploadError(null);
    setResult(null);

    try {
      const response = await importSnapshots(pending);
      setResult(response);
      // 성공/실패와 무관하게 선택 목록은 비웁니다.
      // 남겨 두면 같은 파일을 또 올려 중복 반영을 만들게 됩니다.
      setPending([]);
      // 반영된 장치 목록을 갱신해 아래 표에 바로 나타나게 합니다.
      imported.reload();
    } catch (cause) {
      setUploadError(
        cause instanceof ApiError
          ? cause.message
          : cause instanceof Error
            ? cause.message
            : "업로드에 실패했습니다.",
      );
    } finally {
      setUploading(false);
    }
  };

  const removePending = (index: number) => {
    setPending((previous) => previous.filter((_, i) => i !== index));
  };

  /** 선택된 파일들의 총 크기. */
  const totalBytes = useMemo(
    () => pending.reduce((sum, file) => sum + file.size, 0),
    [pending],
  );

  /** 성공/실패 건수 요약 문구. */
  const summary = useMemo(() => {
    if (!result) return null;
    return `${result.received}건 중 ${result.accepted}건 반영, ${result.rejected}건 실패`;
  }, [result]);

  return (
    <div className="rounded-xl border border-gray-200 bg-gray-50/50 p-5 dark:border-gray-800 dark:bg-transparent">
      <div className="mb-4 flex items-center justify-between">
        <div className="rounded-lg border border-gray-200 bg-white px-3 py-2.5 text-center text-sm font-medium text-gray-800 shadow-sm dark:border-gray-700 dark:bg-gray-800 dark:text-white/90">
          Import Offline Prober Data
        </div>
        <Button
          size="sm"
          variant="outline"
          onClick={() => {
            schema.reload();
            imported.reload();
          }}
        >
          새로고침
        </Button>
      </div>

      <p className="mb-4 text-xs text-gray-500 dark:text-gray-400">
        관리 서버에 연결할 수 없는 장비는 Prober 가 설정을 JSON 파일로 남깁니다.
        그 파일을 아래 영역에 끌어다 놓으면 장치 목록에 반영됩니다.
      </p>

      {/* 드롭 영역 */}
      <div
        onDragEnter={handleDragEnter}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => fileInputRef.current?.click()}
        role="button"
        tabIndex={0}
        onKeyDown={(event) => {
          // 키보드로도 열 수 있어야 접근성 요구를 만족합니다.
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            fileInputRef.current?.click();
          }
        }}
        className={`flex min-h-[150px] cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed p-4 text-sm transition ${
          isDragOver
            ? "border-brand-500 bg-brand-50/60 text-brand-600 dark:bg-brand-500/10 dark:text-brand-400"
            : "border-gray-300 bg-white text-gray-500 hover:border-brand-500 hover:text-brand-600 dark:border-gray-700 dark:bg-gray-800/50 dark:text-gray-400 dark:hover:border-brand-500 dark:hover:text-brand-400"
        }`}
      >
        <span className="text-2xl leading-none">{isDragOver ? "⬇" : "📄"}</span>
        <span className="font-medium">
          {isDragOver ? "여기에 놓으세요" : "Drop Here"}
        </span>
        <span className="text-xs text-gray-400 dark:text-gray-500">
          파일을 끌어다 놓거나 클릭해서 선택하세요 (.json, 최대{" "}
          {formatBytes(maxBytes)})
        </span>
      </div>

      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept=".json,application/json"
        hidden
        onChange={(event) => {
          addFiles(event.target.files);
          // 같은 파일을 다시 고를 수 있도록 비웁니다.
          // (비우지 않으면 change 이벤트가 발생하지 않습니다)
          event.target.value = "";
        }}
      />

      {/* 사전 검사에서 걸러진 파일 */}
      {skipped.length > 0 && (
        <div className="mt-3 rounded-lg border border-warning-200 bg-warning-50 p-3 dark:border-warning-500/30 dark:bg-warning-500/10">
          <p className="text-xs font-medium text-gray-800 dark:text-white/90">
            {skipped.length}건은 업로드하지 않았습니다
          </p>
          <ul className="mt-1.5 space-y-1">
            {skipped.map((item) => (
              <li key={item.name} className="text-[11px] text-gray-600 dark:text-gray-300">
                <span className="font-mono">{item.name}</span> — {item.reason}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 선택된 파일 목록 */}
      {pending.length > 0 && (
        <div className="mt-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-medium text-gray-700 dark:text-gray-300">
              업로드 대기 {pending.length}건 ({formatBytes(totalBytes)})
            </span>
            <button
              type="button"
              onClick={() => setPending([])}
              className="text-[11px] text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
            >
              모두 지우기
            </button>
          </div>

          <ul className="space-y-1">
            {pending.map((file, index) => (
              <li
                key={`${file.name}-${file.size}-${index}`}
                className="flex items-center justify-between rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-xs dark:border-gray-700 dark:bg-gray-800"
              >
                <span className="truncate font-mono text-gray-700 dark:text-gray-300">
                  {file.name}
                </span>
                <div className="ml-2 flex shrink-0 items-center gap-2">
                  <span className="text-[10px] text-gray-400">
                    {formatBytes(file.size)}
                  </span>
                  <button
                    type="button"
                    onClick={() => removePending(index)}
                    className="text-gray-400 hover:text-error-500"
                    title="목록에서 제거"
                  >
                    ✕
                  </button>
                </div>
              </li>
            ))}
          </ul>

          <Button
            className="mt-3 w-full"
            size="sm"
            onClick={handleUpload}
            disabled={uploading}
          >
            {uploading ? "업로드 중..." : `${pending.length}건 업로드`}
          </Button>
        </div>
      )}

      {/* 업로드 오류 */}
      {uploadError && (
        <div className="mt-3 rounded-lg border border-error-200 bg-error-50 p-3 dark:border-error-500/30 dark:bg-error-500/10">
          <p className="text-xs font-medium text-gray-800 dark:text-white/90">
            업로드에 실패했습니다
          </p>
          <p className="mt-1 text-[11px] text-gray-600 dark:text-gray-300">{uploadError}</p>
        </div>
      )}

      {/* 업로드 결과 */}
      {result && (
        <div className="mt-3 space-y-2">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="text-xs font-medium text-gray-700 dark:text-gray-300">
              {summary}
            </span>
            {result.accepted > 0 && (
              <Badge size="sm" color="success">
                반영 {result.accepted}
              </Badge>
            )}
            {result.rejected > 0 && (
              <Badge size="sm" color="error">
                실패 {result.rejected}
              </Badge>
            )}
          </div>

          {result.server_warnings.map((warning) => (
            <p
              key={warning}
              className="rounded-lg bg-warning-50 p-2 text-[11px] text-gray-700 dark:bg-warning-500/10 dark:text-gray-300"
            >
              {warning}
            </p>
          ))}

          <ul className="space-y-1.5">
            {result.snapshots.map((snapshot) => (
              <SnapshotResultRow
                key={`${snapshot.file_name}-${snapshot.agent_id ?? "?"}`}
                snapshot={snapshot}
              />
            ))}
          </ul>
        </div>
      )}

      {/* 반영된 장치 목록 */}
      <div className="mt-4 border-t border-gray-200 pt-3 dark:border-gray-800">
        <div className="mb-2 flex items-center gap-2">
          <span className="text-xs font-medium text-gray-700 dark:text-gray-300">
            파일로 반영된 장치
          </span>
          <Badge size="sm" color="light">
            {imported.data?.imported_devices ?? 0}
          </Badge>
        </div>

        {imported.loading && (
          <p className="text-[11px] text-gray-400">불러오는 중...</p>
        )}

        {!imported.loading && (imported.data?.devices.length ?? 0) === 0 && (
          <p className="text-[11px] text-gray-400 dark:text-gray-500">
            아직 파일로 반영된 장치가 없습니다.
          </p>
        )}

        {!imported.loading && (imported.data?.devices.length ?? 0) > 0 && (
          <ul className="space-y-1">
            {imported.data?.devices.map((device) => (
              <li
                key={device.agent_id}
                className="flex items-center justify-between rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs dark:border-gray-700 dark:bg-gray-800"
              >
                <div className="flex min-w-0 flex-col">
                  <span className="truncate font-medium text-gray-800 dark:text-white/90">
                    {device.agent_id}
                  </span>
                  <span className="truncate text-[10px] text-gray-400">
                    {device.format ?? "—"} · 인터페이스 {device.interfaces} · 경로{" "}
                    {device.routes} · VLAN {device.vlans}
                  </span>
                </div>
                <Badge size="sm" color="info">
                  파일
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

/**
 * 파일 한 건의 처리 결과를 렌더링합니다.
 *
 * <p>실패한 파일은 <b>사유를 그대로</b> 보여줍니다. "실패" 만 보이면 운영자가
 * 다음에 무엇을 해야 할지 알 수 없습니다. (예: "schema 불일치" 면 다른 파일을
 * 올려야 하고, "빈 payload" 면 장비에서 다시 뽑아야 합니다)
 */
function SnapshotResultRow({ snapshot }: { snapshot: ApiOfflineSnapshotResult }) {
  return (
    <li
      className={`rounded-lg border px-3 py-2 text-xs ${
        snapshot.accepted
          ? "border-success-200 bg-success-50/60 dark:border-success-500/30 dark:bg-success-500/10"
          : "border-error-200 bg-error-50/60 dark:border-error-500/30 dark:bg-error-500/10"
      }`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="truncate font-mono text-gray-700 dark:text-gray-300">
          {snapshot.file_name}
        </span>
        <Badge size="sm" color={snapshot.accepted ? "success" : "error"}>
          {snapshot.accepted ? "반영됨" : "실패"}
        </Badge>
      </div>

      {snapshot.accepted && (
        <p className="mt-1 text-[10px] text-gray-500 dark:text-gray-400">
          {snapshot.agent_id} · {snapshot.format ?? "—"} · 인터페이스{" "}
          {snapshot.interfaces} · 경로 {snapshot.routes} · 규칙{" "}
          {snapshot.firewall_rules}
        </p>
      )}

      {snapshot.errors.length > 0 && (
        <ul className="mt-1 space-y-0.5">
          {snapshot.errors.map((error) => (
            <li key={error} className="text-[10px] text-error-600 dark:text-error-400">
              • {error}
            </li>
          ))}
        </ul>
      )}

      {snapshot.warnings.length > 0 && (
        <ul className="mt-1 space-y-0.5">
          {snapshot.warnings.map((warning) => (
            <li
              key={warning}
              className="text-[10px] text-warning-600 dark:text-orange-400"
            >
              ⚠ {warning}
            </li>
          ))}
        </ul>
      )}
    </li>
  );
}
