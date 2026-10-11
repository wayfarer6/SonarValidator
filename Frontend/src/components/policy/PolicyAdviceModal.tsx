import { useEffect, useState } from "react";
import Badge from "../ui/badge/Badge";
import Button from "../ui/button/Button";
import { Modal } from "../ui/modal";
import { ApiError } from "../../lib/api/client";
import {
  listEnabledAiProviders,
  type ApiAiProvider,
} from "../../lib/api/aiLogs";
import {
  listPolicyAdvices,
  requestPolicyAdvice,
  type ApiPolicyAdvice,
} from "../../lib/api/policyAdvice";
import PolicyAdviceCard from "./PolicyAdviceCard";

/**
 * 위반 한 건에 대한 AI 정책 조언 모달입니다. (SONAR-43)
 *
 * <h2>흐름 — "열면 이력, 누르면 새 조언"</h2>
 * <ol>
 *   <li>모달이 열리면 <b>먼저 이력</b>({@code GET /policy/advice/{projectId}?rule_id=…})을
 *       부릅니다. AI 호출은 <b>돈과 시간</b>이 들므로, 이미 물어본 위반이면
 *       그 결과를 그대로 보여줍니다.</li>
 *   <li>운영자가 <b>"다시 물어보기"</b> 를 누르면 그때만 POST 합니다.</li>
 * </ol>
 * <p>이 순서를 지키지 않으면 카드를 누를 때마다 과금이 발생합니다.
 *
 * <h2>⚠️ 공급자를 화면에서 고르게 하는 이유</h2>
 * <p>프로젝트마다 민감도가 다릅니다. 로그 분석과 같은 원칙으로, 기본
 * 공급자를 쓰되 필요하면 고를 수 있게 합니다. 공급자가 하나도 없으면
 * <b>버튼을 누르기 전에</b> 알려 줍니다 — 눌러 놓고 실패를 보면 원인을
 * 설정에서 찾아야 하는데, 그 안내가 늦습니다.
 *
 * <h2>⚠️ 닫아도 이력은 남는다</h2>
 * <p>조언은 서버에 저장되므로 모달을 닫아도 사라지지 않습니다. 그래서
 * 모달에는 "이 결과는 저장됩니다" 를 명시해, 운영자가 <b>메모하지 않아도
 * 된다</b>는 것을 알게 합니다.
 */
export default function PolicyAdviceModal({
  isOpen,
  onClose,
  projectId,
  ruleId,
  srcSubnet,
  dstSubnet,
  /** 위반 카드에서 보여줄 요약 (모달 헤더용) */
  violationLabel,
}: {
  isOpen: boolean;
  onClose: () => void;
  projectId: string;
  ruleId: string;
  srcSubnet?: string | null;
  dstSubnet?: string | null;
  violationLabel?: string;
}) {
  const [providers, setProviders] = useState<ApiAiProvider[]>([]);
  const [providerId, setProviderId] = useState<number | "">("");
  const [prompt, setPrompt] = useState("");

  const [history, setHistory] = useState<ApiPolicyAdvice[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);

  /** 새로 받은 조언 (이력 맨 위에 옵니다). */
  const [fresh, setFresh] = useState<ApiPolicyAdvice | null>(null);
  const [asking, setAsking] = useState(false);
  const [askError, setAskError] = useState<string | null>(null);

  // 모달이 열릴 때마다 공급자 목록과 이력을 가져옵니다.
  // (닫힌 상태로 마운트되어 있을 수 있으므로 isOpen 을 의존성에 둡니다)
  useEffect(() => {
    if (!isOpen) return;

    let cancelled = false;

    // 공급자 — 실패해도 조언 자체는 기본 공급자로 가능하므로 오류를 띄우지 않습니다.
    listEnabledAiProviders()
      .then((result) => {
        if (cancelled) return;
        setProviders(result.providers);
        // 기본 공급자를 미리 선택합니다. 없으면 첫 번째.
        const preset =
          result.providers.find((item) => item.is_default) ??
          result.providers[0];
        if (preset) setProviderId(preset.id);
      })
      .catch(() => {
        // 조용히 넘어갑니다. 조언 요청 시 서버가 사유를 알려 줍니다.
      });

    // 이력 — 먼저 보여줘 AI 재호출을 막습니다.
    setHistoryLoading(true);
    setHistoryError(null);
    setFresh(null);
    setAskError(null);

    listPolicyAdvices(projectId, { ruleId, limit: 10 })
      .then((result) => {
        if (cancelled) return;
        setHistory(result.advices);
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        setHistoryError(
          cause instanceof ApiError ? cause.message : "이력을 불러오지 못했습니다.",
        );
      })
      .finally(() => {
        if (cancelled) return;
        setHistoryLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [isOpen, projectId, ruleId]);

  /**
   * 새 조언을 요청합니다.
   *
   * <p>⚠️ 서버는 실패도 200 으로 돌려줍니다({@code succeeded=false}).
   * 그래서 여기서 다시 확인해야 합니다. HTTP 상태만 보면 실패를 성공으로
   * 표시하게 됩니다.
   */
  const ask = async () => {
    if (asking) return;
    setAsking(true);
    setAskError(null);
    try {
      const result = await requestPolicyAdvice(projectId, {
        rule_id: ruleId,
        src_subnet: srcSubnet ?? null,
        dst_subnet: dstSubnet ?? null,
        provider_id: providerId === "" ? null : providerId,
        prompt: prompt || null,
      });

      setFresh(result);
      if (!result.succeeded) {
        setAskError(result.error_message ?? "조언 요청에 실패했습니다.");
      } else {
        // 이력에도 반영합니다. 다시 부르지 않고 앞에 붙입니다.
        setHistory((previous) => [result, ...previous]);
      }
    } catch (cause) {
      setAskError(
        cause instanceof ApiError
          ? cause.message
          : cause instanceof Error
            ? cause.message
            : "조언 요청에 실패했습니다.",
      );
    } finally {
      setAsking(false);
    }
  };

  const noProviders = !historyLoading && providers.length === 0;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      className="max-h-[90vh] w-full max-w-3xl overflow-y-auto p-6"
    >
      {/* 헤더 */}
      <div className="pr-10">
        <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">
          AI 정책 조언
        </h3>
        <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
          {violationLabel ?? `규칙 ${ruleId}`}
        </p>
        <p className="mt-2 text-[11px] text-gray-400 dark:text-gray-500">
          이 위반을 어떻게 해결할지 AI 가 선택지와 트레이드오프를 제시합니다.
          조언은 서버에 저장되므로 창을 닫아도 남습니다.
        </p>
      </div>

      <div className="mt-4 space-y-4">
        {/* 공급자 미등록 — 누르기 전에 알려 줍니다 */}
        {noProviders && (
          <div className="rounded-xl border border-warning-200 bg-warning-50 p-3 dark:border-warning-500/30 dark:bg-warning-500/10">
            <p className="text-xs font-medium text-warning-700 dark:text-warning-400">
              사용 중인 AI 공급자가 없습니다
            </p>
            <p className="mt-1 text-[11px] text-warning-600 dark:text-warning-500">
              설정에서 AI 공급자를 등록하고 &lsquo;사용&rsquo;을 켜야 조언을
              받을 수 있습니다. (로그 관리 화면의 AI 공급자 설정)
            </p>
          </div>
        )}

        {/* 요청 폼 */}
        {!noProviders && (
          <div className="rounded-xl border border-gray-200 p-3 dark:border-gray-700">
            <div className="flex flex-wrap items-end gap-2">
              <label className="flex flex-col gap-1">
                <span className="text-[11px] font-medium text-gray-600 dark:text-gray-400">
                  AI 공급자
                </span>
                <select
                  value={providerId}
                  onChange={(event) =>
                    setProviderId(
                      event.target.value === "" ? "" : Number(event.target.value),
                    )
                  }
                  className="rounded-lg border border-gray-300 bg-white px-2.5 py-1.5 text-xs text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200"
                >
                  {providers.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                      {item.is_default ? " (기본)" : ""}
                    </option>
                  ))}
                </select>
              </label>

              <label className="flex min-w-[220px] flex-1 flex-col gap-1">
                <span className="text-[11px] font-medium text-gray-600 dark:text-gray-400">
                  추가 질문 (선택)
                </span>
                <input
                  type="text"
                  value={prompt}
                  onChange={(event) => setPrompt(event.target.value)}
                  placeholder="예: 군사 보안 규정 관점에서 봐줘"
                  className="rounded-lg border border-gray-300 bg-white px-2.5 py-1.5 text-xs text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200"
                />
              </label>

              <Button
                size="sm"
                disabled={asking || providerId === ""}
                onClick={ask}
                title={
                  providerId === ""
                    ? "AI 공급자를 선택하세요"
                    : "이 위반에 대한 새 조언을 요청합니다 (AI 호출 비용 발생)"
                }
              >
                {asking
                  ? "조언 생성 중..."
                  : history.length > 0
                    ? "다시 물어보기"
                    : "조언 요청"}
              </Button>
            </div>
            {history.length > 0 && (
              <p className="mt-2 text-[11px] text-gray-400 dark:text-gray-500">
                이미 받은 조언이 아래에 있습니다. 새로 물으면 AI 호출이 다시
                발생합니다.
              </p>
            )}
          </div>
        )}

        {askError && (
          <div className="rounded-xl border border-error-200 bg-error-50 p-3 dark:border-error-500/30 dark:bg-error-500/10">
            <p className="text-xs text-gray-700 dark:text-gray-300">{askError}</p>
          </div>
        )}

        {/* 새 조언 (있으면 맨 위) */}
        {fresh && (
          <div>
            <h4 className="mb-2 text-xs font-semibold text-gray-600 dark:text-gray-400">
              방금 받은 조언
            </h4>
            <PolicyAdviceCard advice={fresh} />
          </div>
        )}

        {/* 이력 */}
        <div>
          <div className="mb-2 flex items-center justify-between">
            <h4 className="text-xs font-semibold text-gray-600 dark:text-gray-400">
              이전 조언 {history.length > 0 ? `(${history.length})` : ""}
            </h4>
          </div>

          {historyLoading && (
            <p className="py-6 text-center text-xs text-gray-500 dark:text-gray-400">
              이력을 불러오는 중...
            </p>
          )}

          {historyError && (
            <p className="rounded-lg border border-error-200 bg-error-50 p-2.5 text-xs text-error-600 dark:border-error-500/30 dark:bg-error-500/10 dark:text-error-400">
              {historyError}
            </p>
          )}

          {!historyLoading && !historyError && history.length === 0 && (
            <p className="rounded-lg border border-dashed border-gray-300 p-4 text-center text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400">
              이 위반에 대한 조언이 아직 없습니다. 위 버튼으로 첫 조언을
              요청하세요.
            </p>
          )}

          {!historyLoading && history.length > 0 && (
            <div className="space-y-3">
              {history.map((item) => (
                <div key={item.advice_id}>
                  <div className="mb-1 flex flex-wrap items-center gap-2">
                    <Badge size="sm" color="light">
                      {new Date(item.created_at).toLocaleString("ko-KR")}
                    </Badge>
                    {item.requested_by && (
                      <span className="text-[11px] text-gray-400">
                        {item.requested_by}
                      </span>
                    )}
                  </div>
                  <PolicyAdviceCard advice={item} />
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}