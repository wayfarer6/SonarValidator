import { useState } from "react";
import Badge from "../ui/badge/Badge";
import Button from "../ui/button/Button";
import OPNsenseConfigModal from "./OPNsenseConfigModal";
import { useApi } from "../../hooks/useApi";
import { AlertIcon, InfoIcon } from "../../icons";
import {
  credentialLabel,
  credentialNodeId,
  deleteCredential,
  listCandidates,
  listCredentials,
  statusColor,
  statusLabel,
  verifyAllCredentials,
  verifyCredential,
  type OPNsenseCandidate,
  type OPNsenseCredential,
} from "../../lib/api/opnsense";

interface ApiConnectedNodesPanelProps {
  /** 요약 카드(전체 / 연결됨 / 실패 / 미확인 + 전체 연결 확인)를 표시합니다. */
  showSummary?: boolean;
  /** 아직 설정이 없는 OPNsense 후보까지 표시합니다. */
  showCandidates?: boolean;
  /** 섹션 제목. */
  title?: string;
  /** 섹션 설명. */
  description?: string;
}

/**
 * REST API 로만 연결되는 장비(OPNsense 등)의 목록입니다.
 *
 * <h2>⚠️ 왜 Agent 목록과 따로 보여야 하는가</h2>
 * <p>Agent 목록({@code /agent})은 <b>프로버가 설치된 장비</b>를 다룹니다.
 * OPNsense 는 프로버를 올릴 수 없고 서버가 REST API 로 직접 접속하므로
 * <b>Agent 목록에 나타나지 않습니다.</b> 그래서 "방화벽을 등록했는데 목록에
 * 안 보인다" 는 혼란이 생깁니다. 이 패널이 그 장치들을 한 곳에 모읍니다.
 *
 * <h2>편집이 가능해야 하는 이유</h2>
 * 주소가 바뀌거나 API Key 를 재발급하면 <b>다시 입력</b>해야 합니다.
 * 등록만 되고 수정이 안 되면 사용자는 삭제 후 재등록을 하게 되고, 그 과정에서
 * 연결 확인 이력과 감지 버전이 사라집니다.
 *
 * <h2>Project 화면에서도 쓰는 이유</h2>
 * API 연결 장치는 Agent 처럼 프로젝트에 귀속되지 않습니다(설정 테이블에
 * 프로젝트 컬럼이 없습니다). 그래서 프로젝트별로 나누어 보여줄 수 없고,
 * 프로젝트 화면에서는 <b>공통 장치</b>로 한 번만 보여줍니다.
 */
export default function ApiConnectedNodesPanel({
  showSummary = false,
  showCandidates = false,
  title = "API 연결 장치",
  description = "프로버 없이 REST API 로 직접 연결되는 장비입니다. Agent 목록에는 나타나지 않습니다.",
}: ApiConnectedNodesPanelProps) {
  const credentials = useApi(() => listCredentials(), []);
  const candidates = useApi(
    () =>
      showCandidates
        ? listCandidates()
        : Promise.resolve({ total: 0, candidates: [] as OPNsenseCandidate[] }),
    [showCandidates],
  );

  /** 편집 중인 장치의 식별자입니다. null 이면 편집 모달이 닫혀 있습니다. */
  const [editingKey, setEditingKey] = useState<string | null>(null);
  /** 마지막으로 읽은 편집 대상의 표시 이름 (모달 제목용). */
  const [editingLabel, setEditingLabel] = useState<string | null>(null);
  /** 새 연결 등록 모달이 열려 있는지 여부. */
  const [adding, setAdding] = useState(false);

  /** 요청 진행 중인 장치의 식별자입니다. 같은 행의 중복 클릭을 막습니다. */
  const [busyKey, setBusyKey] = useState<string | null>(null);
  /** 행 작업 결과 메시지입니다. */
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const rows = credentials.data?.credentials ?? [];
  const connectedCount = rows.filter((row) => row.status === "OK").length;
  const failedCount = rows.filter((row) => row.status === "FAILED").length;
  const unverifiedCount = rows.filter((row) => row.status === "UNVERIFIED").length;

  /** 목록과 후보를 다시 읽습니다. */
  const refresh = () => {
    credentials.reload();
    if (showCandidates) candidates.reload();
  };

  /** 연결을 다시 확인합니다. */
  const handleVerify = async (credential: OPNsenseCredential) => {
    const key = credentialNodeId(credential);
    setBusyKey(key);
    setNotice(null);
    setError(null);
    try {
      const result = await verifyCredential(key);
      setNotice(
        result.status === "OK"
          ? `${credentialLabel(result)} 연결을 확인했습니다.${result.detected_version ? ` (버전 ${result.detected_version})` : ""}`
          : `${credentialLabel(result)} 연결에 실패했습니다 — 아래 오류를 참고하세요.`,
      );
      refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "연결 확인에 실패했습니다.");
    } finally {
      setBusyKey(null);
    }
  };

  /** 등록된 모든 장치의 연결을 한 번에 확인합니다. */
  const handleVerifyAll = async () => {
    setBusyKey("*");
    setNotice(null);
    setError(null);
    try {
      const result = await verifyAllCredentials();
      const ok = result.results.filter((row) => row.status === "OK").length;
      setNotice(`${result.total}건 중 ${ok}건 연결을 확인했습니다.`);
      refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "전체 연결 확인에 실패했습니다.");
    } finally {
      setBusyKey(null);
    }
  };

  /** 설정을 삭제합니다. */
  const handleDelete = async (credential: OPNsenseCredential) => {
    const key = credentialNodeId(credential);
    if (
      !window.confirm(
        `${credentialLabel(credential)} 연결 설정을 삭제할까요?\n장치 자체는 그대로이고, 서버가 이 장치로 접속하는 정보만 지웁니다.`,
      )
    ) {
      return;
    }
    setBusyKey(key);
    setNotice(null);
    setError(null);
    try {
      await deleteCredential(key);
      setNotice(`${credentialLabel(credential)} 설정을 삭제했습니다.`);
      refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "삭제에 실패했습니다.");
    } finally {
      setBusyKey(null);
    }
  };

  /** 편집 모달을 엽니다. */
  const openEditor = (credential: OPNsenseCredential) => {
    setEditingKey(credentialNodeId(credential));
    setEditingLabel(credentialLabel(credential));
    setNotice(null);
    setError(null);
  };

  /** 후보 장치를 새 연결로 등록합니다. */
  const openCandidate = (candidate: OPNsenseCandidate) => {
    setEditingKey(candidate.agent_id);
    setEditingLabel(candidate.hostname ?? candidate.agent_id);
    setNotice(null);
    setError(null);
  };

  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3 border-b border-gray-100 pb-4 dark:border-gray-800">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">{title}</h3>
            {!credentials.loading && (
              <Badge size="sm" color="light">
                {rows.length}건
              </Badge>
            )}
          </div>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{description}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" variant="outline" onClick={refresh} disabled={credentials.loading}>
            새로고침
          </Button>
          {showSummary && rows.length > 0 && (
            <Button
              size="sm"
              variant="outline"
              onClick={() => void handleVerifyAll()}
              disabled={busyKey !== null}
            >
              {busyKey === "*" ? "확인 중..." : "전체 연결 확인"}
            </Button>
          )}
          <Button
            size="sm"
            onClick={() => {
              setAdding(true);
              setNotice(null);
              setError(null);
            }}
          >
            새 연결 추가
          </Button>
        </div>
      </div>

      {/* 요약 — "몇 개가 실제로 살아 있는가" 를 먼저 보여줍니다. */}
      {showSummary && !credentials.loading && (
        <div className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <SummaryTile label="등록됨" value={rows.length} tone="neutral" />
          <SummaryTile label="연결됨" value={connectedCount} tone="success" />
          <SummaryTile label="연결 실패" value={failedCount} tone="error" />
          <SummaryTile label="미확인" value={unverifiedCount} tone="warning" />
        </div>
      )}

      {credentials.loading && (
        <p className="text-sm text-gray-500 dark:text-gray-400">API 연결 장치를 불러오는 중...</p>
      )}

      {credentials.offline && (
        <div className="flex items-start gap-2 rounded-lg bg-warning-50 px-3 py-2.5 text-xs text-warning-700 dark:bg-warning-500/15 dark:text-orange-300">
          <InfoIcon className="mt-0.5 size-3.5 shrink-0" />
          <span>
            서버에 연결하지 못했습니다. 백엔드가 실행 중인지 확인하세요.
            ({credentials.error})
          </span>
        </div>
      )}

      {!credentials.loading && !credentials.offline && credentials.error && (
        <p className="text-sm text-error-600 dark:text-error-400">
          API 연결 장치를 불러오지 못했습니다: {credentials.error}
        </p>
      )}

      {(notice || error) && (
        <div
          className={`mb-4 flex items-start gap-2 rounded-lg px-3 py-2.5 text-xs ${
            error
              ? "bg-error-50 text-error-700 dark:bg-error-500/15 dark:text-error-300"
              : "bg-success-50 text-success-700 dark:bg-success-500/15 dark:text-success-500"
          }`}
        >
          {error && <AlertIcon className="mt-0.5 size-3.5 shrink-0" />}
          <span>{error ?? notice}</span>
        </div>
      )}

      {!credentials.loading && !credentials.error && rows.length === 0 && (
        <p className="rounded-lg border border-dashed border-gray-300 px-4 py-6 text-center text-sm text-gray-500 dark:border-gray-700 dark:text-gray-400">
          등록된 API 연결 장치가 없습니다. <br className="hidden sm:block" />
          OPNsense 처럼 프로버를 올릴 수 없는 장비는 “새 연결 추가”로 주소와 API Key 를
          등록하세요.
        </p>
      )}

      {rows.length > 0 && (
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-xs uppercase tracking-wide text-gray-400 dark:border-gray-800">
                <th className="py-2 pr-4 font-medium">이름</th>
                <th className="py-2 pr-4 font-medium">프로젝트</th>
                <th className="py-2 pr-4 font-medium">식별자</th>
                <th className="py-2 pr-4 font-medium">주소</th>
                <th className="py-2 pr-4 font-medium">상태</th>
                <th className="py-2 pr-4 font-medium">감지 버전</th>
                <th className="py-2 pr-4 font-medium">마지막 확인</th>
                <th className="py-2 font-medium">작업</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {rows.map((row) => {
                const key = credentialNodeId(row);
                const busy = busyKey === key;
                return (
                  <tr key={`${row.node_id}`} className="align-top">
                    <td className="py-3 pr-4">
                      <p className="font-medium text-gray-800 dark:text-gray-200">
                        {credentialLabel(row)}
                      </p>
                      {row.allow_insecure_tls && (
                        <p className="mt-0.5 text-[11px] text-warning-600 dark:text-orange-300">
                          자체 서명 인증서 허용
                        </p>
                      )}
                      {row.last_error && (
                        <p className="mt-1 max-w-[280px] text-[11px] text-error-600 dark:text-error-400">
                          {row.last_error}
                        </p>
                      )}
                    </td>
                    <td className="py-3 pr-4">
                      {row.project_id ? (
                        <>
                          <p className="text-xs font-medium text-gray-700 dark:text-gray-200">
                            {row.project_name ?? row.project_id}
                          </p>
                          <p className="font-mono text-[11px] text-gray-400">
                            {row.project_id}
                          </p>
                        </>
                      ) : (
                        <span className="text-xs text-gray-400">미지정</span>
                      )}
                    </td>
                    <td className="py-3 pr-4">
                      <p className="font-mono text-xs text-gray-600 dark:text-gray-300">
                        {row.agent_id ?? "—"}
                      </p>
                      <p className="text-[11px] text-gray-400">node_id {row.node_id}</p>
                    </td>
                    <td className="py-3 pr-4">
                      <span className="font-mono text-xs text-gray-600 dark:text-gray-300">
                        {row.base_url}
                      </span>
                      <p className="mt-0.5 text-[11px] text-gray-400">
                        {row.has_api_key && row.has_secret ? "Key·Secret 저장됨" : "Key·Secret 미저장"}
                      </p>
                    </td>
                    <td className="py-3 pr-4">
                      <Badge size="sm" color={statusColor(row.status)}>
                        {statusLabel(row.status)}
                      </Badge>
                    </td>
                    <td className="py-3 pr-4 text-xs text-gray-600 dark:text-gray-300">
                      {row.detected_version ?? "—"}
                    </td>
                    <td className="py-3 pr-4 text-xs text-gray-500 dark:text-gray-400">
                      {row.last_checked_at
                        ? new Date(row.last_checked_at).toLocaleString()
                        : "아직 확인하지 않음"}
                    </td>
                    <td className="py-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <button
                          type="button"
                          onClick={() => openEditor(row)}
                          disabled={busy}
                          className="rounded-lg border border-brand-300 px-3 py-1.5 text-xs font-medium text-brand-600 hover:bg-brand-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-brand-500/40 dark:text-brand-400 dark:hover:bg-brand-500/10"
                          title="주소·API Key·Secret 을 다시 입력합니다"
                        >
                          편집
                        </button>
                        <button
                          type="button"
                          onClick={() => void handleVerify(row)}
                          disabled={busy}
                          className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-white/[0.03]"
                        >
                          {busy ? "확인 중..." : "연결 테스트"}
                        </button>
                        <button
                          type="button"
                          onClick={() => void handleDelete(row)}
                          disabled={busy}
                          className="rounded-lg border border-error-300 px-3 py-1.5 text-xs font-medium text-error-600 hover:bg-error-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-error-500/40 dark:text-error-400 dark:hover:bg-error-500/10"
                        >
                          삭제
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* 미등록 후보 — 수집된 설정에서 OPNsense 로 보이는데 아직 연결이 없는 장치 */}
      {showCandidates && (
        <CandidateSection
          candidates={candidates.data?.candidates ?? []}
          loading={candidates.loading}
          onRegister={openCandidate}
        />
      )}

      {/* 편집/등록 모달 — 같은 컴포넌트를 재사용합니다. */}
      <OPNsenseConfigModal
        isOpen={adding || editingKey !== null}
        onClose={() => {
          setAdding(false);
          setEditingKey(null);
          setEditingLabel(null);
        }}
        initialNodeId={adding ? undefined : (editingKey ?? undefined)}
        deviceLabel={editingLabel}
        onSaved={() => {
          // ⚠️ 여기서 "저장했습니다." 같은 문구를 띄우지 않습니다.
          //    이 콜백은 저장뿐 아니라 모달의 <b>연결 테스트</b> 뒤에도
          //    호출됩니다. 저장하지 않았는데 "저장했습니다." 가 뜨면
          //    "설정이 바뀌었나?" 로 오해합니다. 결과 안내는 모달이 책임지고,
          //    이 패널은 목록만 최신으로 맞춥니다.
          refresh();
        }}
      />
    </section>
  );
}

/** 요약 타일입니다. (숫자 하나 + 라벨) */
function SummaryTile({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone: "neutral" | "success" | "error" | "warning";
}) {
  const valueClass =
    tone === "success"
      ? "text-success-600 dark:text-success-500"
      : tone === "error"
        ? "text-error-600 dark:text-error-400"
        : tone === "warning"
          ? "text-warning-600 dark:text-orange-300"
          : "text-gray-800 dark:text-white/90";
  return (
    <div className="rounded-xl border border-gray-200 p-3 dark:border-gray-800">
      <p className="text-xs text-gray-500 dark:text-gray-400">{label}</p>
      <p className={`mt-1 text-xl font-semibold ${valueClass}`}>{value}</p>
    </div>
  );
}

/** OPNsense 로 보이지만 아직 연결이 등록되지 않은 장치 목록입니다. */
function CandidateSection({
  candidates,
  loading,
  onRegister,
}: {
  candidates: OPNsenseCandidate[];
  loading: boolean;
  onRegister: (candidate: OPNsenseCandidate) => void;
}) {
  const pending = candidates.filter((candidate) => !candidate.has_credential);

  if (loading) {
    return (
      <p className="mt-5 border-t border-gray-100 pt-4 text-xs text-gray-500 dark:border-gray-800 dark:text-gray-400">
        OPNsense 후보를 확인하는 중...
      </p>
    );
  }
  if (pending.length === 0) return null;

  return (
    <div className="mt-5 border-t border-gray-100 pt-4 dark:border-gray-800">
      <div className="mb-2 flex items-center gap-2">
        <h4 className="text-sm font-semibold text-gray-800 dark:text-white/90">
          연결이 필요한 OPNsense 후보
        </h4>
        <Badge size="sm" color="warning">
          {pending.length}건
        </Badge>
      </div>
      <p className="mb-3 text-xs text-gray-500 dark:text-gray-400">
        수집된 설정에서 OPNsense 로 보이지만 서버에 접속 정보가 없습니다. 주소와 API Key 를
        등록하면 연결 확인을 할 수 있습니다.
      </p>
      <ul className="divide-y divide-gray-100 dark:divide-gray-800">
        {pending.map((candidate) => (
          <li key={candidate.agent_id} className="flex flex-wrap items-center justify-between gap-3 py-2">
            <div className="min-w-0">
              <p className="truncate font-mono text-sm text-gray-800 dark:text-gray-200">
                {candidate.agent_id}
              </p>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                {[candidate.hostname, candidate.product, candidate.vendor]
                  .filter((value) => value && value.trim() !== "")
                  .join(" · ") || "수집된 정보 없음"}
              </p>
            </div>
            <button
              type="button"
              onClick={() => onRegister(candidate)}
              className="rounded-lg border border-brand-300 px-3 py-1.5 text-xs font-medium text-brand-600 hover:bg-brand-50 dark:border-brand-500/40 dark:text-brand-400 dark:hover:bg-brand-500/10"
            >
              연결 등록
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
