import { useEffect, useState } from "react";
import Badge from "../ui/badge/Badge";
import Button from "../ui/button/Button";
import { Modal } from "../ui/modal";
import { useApi } from "../../hooks/useApi";
import { useApiAction } from "../../hooks/useApiAction";
import {
  checkAiProvider,
  deleteAiProvider,
  listAiProviders,
  saveAiProvider,
  setAiProviderEnabled,
  setDefaultAiProvider,
  type ApiAiProvider,
  type ApiAiProviderInput,
} from "../../lib/api/aiLogs";
import { ApiError } from "../../lib/api/client";

/**
 * AI 공급자 설정 화면입니다.
 *
 * <h2>왜 별도 화면인가</h2>
 * 로그 분석을 쓰려면 먼저 공급자를 등록해야 합니다. 분석 화면에 폼을 넣으면
 * 처음 쓰는 사람이 "분석이 왜 안 되지" 를 먼저 겪습니다. 설정을 분리해 두면
 * 로그 분석 화면이 "공급자를 먼저 등록하세요" 라고 안내할 수 있습니다.
 *
 * <h2>⚠️ API Key 를 화면에서 다루는 규칙</h2>
 * <ol>
 *   <li>수정 시 키를 <b>불러오지 않습니다.</b> 서버도 원문을 보내지 않습니다.
 *       대신 {@code has_api_key} 로 "등록돼 있음" 만 표시합니다.</li>
 *   <li>키 입력란을 <b>비워 저장하면 기존 키가 유지</b>됩니다.
 *       이 규칙을 화면에 명시하지 않으면 사용자가 "지워질까 봐" 키를 다시
 *       입력하거나, 반대로 "지워지겠지" 하고 비워 실수로 유지해 버립니다.</li>
 *   <li>저장 성공 후 <b>입력값을 즉시 비웁니다.</b> 상태에 오래 두지 않습니다.</li>
 * </ol>
 *
 * <h2>연결 확인을 저장 직후에 하는 이유</h2>
 * 저장만 하고 끝내면 운영자가 "등록했다" 고 믿고 넘어갔다가 분석 단계에서야
 * 처음 실패(401/404)를 봅니다. 그때는 원인이 설정인지 로그인지 헷갈립니다.
 */
export default function AiProviderSettings() {
  const providers = useApi(() => listAiProviders(), []);

  const [editing, setEditing] = useState<ApiAiProvider | null>(null);
  const [creating, setCreating] = useState(false);
  const [open, setOpen] = useState(false);

  /** 연결 확인 결과를 공급자별로 보관합니다. */
  const [checkResults, setCheckResults] = useState<
    Record<number, { ok: boolean; message: string }>
  >({});
  const [actionError, setActionError] = useState<string | null>(null);

  const saveAction = useApiAction((input: ApiAiProviderInput) =>
    saveAiProvider(input),
  );

  const openCreate = () => {
    setEditing(null);
    setCreating(true);
    setOpen(true);
  };

  const openEdit = (provider: ApiAiProvider) => {
    setEditing(provider);
    setCreating(false);
    setOpen(true);
  };

  const close = () => {
    setOpen(false);
    setEditing(null);
    setCreating(false);
    saveAction.reset();
  };

  const handleCheck = async (id: number) => {
    setActionError(null);
    try {
      const result = await checkAiProvider(id);
      setCheckResults((prev) => ({
        ...prev,
        [id]: { ok: result.ok, message: result.message },
      }));
      providers.reload();
    } catch (cause) {
      setActionError(
        cause instanceof ApiError ? cause.message : "연결 확인에 실패했습니다.",
      );
    }
  };

  const handleToggleEnabled = async (provider: ApiAiProvider) => {
    setActionError(null);
    try {
      await setAiProviderEnabled(provider.id, !provider.enabled);
      providers.reload();
    } catch (cause) {
      setActionError(
        cause instanceof ApiError ? cause.message : "상태 변경에 실패했습니다.",
      );
    }
  };

  const handleSetDefault = async (id: number) => {
    setActionError(null);
    try {
      await setDefaultAiProvider(id);
      providers.reload();
    } catch (cause) {
      setActionError(
        cause instanceof ApiError ? cause.message : "기본 지정에 실패했습니다.",
      );
    }
  };

  const handleDelete = async (provider: ApiAiProvider) => {
    // 삭제는 되돌릴 수 없고 키도 함께 사라지므로 한 번 확인합니다.
    const confirmed = window.confirm(
      `'${provider.name}' 공급자를 삭제할까요?\n저장된 API Key 도 함께 삭제됩니다.`,
    );
    if (!confirmed) return;

    setActionError(null);
    try {
      await deleteAiProvider(provider.id);
      providers.reload();
    } catch (cause) {
      setActionError(
        cause instanceof ApiError ? cause.message : "삭제에 실패했습니다.",
      );
    }
  };

  const rows = providers.data?.providers ?? [];

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 pb-4 dark:border-gray-800">
        <div className="flex items-center gap-2">
          <h3 className="text-base font-semibold text-gray-800 dark:text-white/90">
            AI 공급자 설정
          </h3>
          <Badge size="sm" color="light">
            {rows.length}건
          </Badge>
        </div>
        <div className="flex gap-2">
          <Button size="sm" variant="outline" onClick={providers.reload}>
            새로고침
          </Button>
          <Button size="sm" onClick={openCreate}>
            공급자 추가
          </Button>
        </div>
      </div>

      <p className="mb-4 text-xs text-gray-500 dark:text-gray-400">
        OpenAI 호환 API(<code className="font-mono">/chat/completions</code>)를
        지원하는 공급자를 등록하세요. OpenAI, Ollama, vLLM, LocalAI, Groq,
        OpenRouter, Azure OpenAI 등이 모두 같은 형식으로 동작합니다.
      </p>

      {providers.loading && (
        <p className="py-8 text-center text-sm text-gray-500 dark:text-gray-400">
          공급자 목록을 불러오는 중...
        </p>
      )}

      {providers.error && (
        <div className="rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
          <p className="text-sm text-gray-800 dark:text-white/90">
            {providers.offline
              ? "백엔드에 연결할 수 없습니다"
              : "목록을 불러오지 못했습니다"}
          </p>
          <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">
            {providers.error}
          </p>
        </div>
      )}

      {actionError && (
        <p className="mb-3 rounded-lg border border-error-200 bg-error-50 p-3 text-xs text-gray-700 dark:border-error-500/30 dark:bg-error-500/10 dark:text-gray-300">
          {actionError}
        </p>
      )}

      {!providers.loading && !providers.error && rows.length === 0 && (
        <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-gray-200 py-10 text-center dark:border-gray-800">
          <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
            등록된 AI 공급자가 없습니다
          </p>
          <p className="mt-1 max-w-lg text-xs text-gray-400 dark:text-gray-500">
            로그 분석을 쓰려면 공급자를 먼저 등록하세요. 로컬에서 돌리는
            Ollama 라면 <code className="font-mono">http://localhost:11434/v1</code>{" "}
            처럼 주소를 넣고 API Key 는 비워 두면 됩니다.
          </p>
          <Button className="mt-4" size="sm" onClick={openCreate}>
            공급자 추가
          </Button>
        </div>
      )}

      {rows.length > 0 && (
        <div className="space-y-3">
          {rows.map((provider) => {
            const check = checkResults[provider.id];
            const status = check
              ? check
              : provider.last_status
                ? {
                    ok: provider.last_status === "ok",
                    message: provider.last_message ?? "",
                  }
                : null;

            return (
              <div
                key={provider.id}
                className="rounded-xl border border-gray-200 p-4 dark:border-gray-700"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold text-gray-800 dark:text-white/90">
                        {provider.name}
                      </span>
                      {provider.is_default && (
                        <Badge size="sm" color="primary">
                          기본
                        </Badge>
                      )}
                      {provider.enabled ? (
                        <Badge size="sm" color="success">
                          사용 중
                        </Badge>
                      ) : (
                        <Badge size="sm" color="light">
                          사용 안 함
                        </Badge>
                      )}
                      {provider.has_api_key && (
                        <Badge size="sm" color="info">
                          Key 등록됨
                        </Badge>
                      )}
                    </div>
                    <p className="mt-1.5 break-all font-mono text-[11px] text-gray-500 dark:text-gray-400">
                      {provider.base_url}
                    </p>
                    <p className="mt-0.5 font-mono text-[11px] text-gray-500 dark:text-gray-400">
                      model: {provider.model}
                      {provider.auth_style === "azure" && " · auth: api-key 헤더"}
                    </p>
                  </div>

                  <div className="flex flex-wrap gap-1.5">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => handleCheck(provider.id)}
                    >
                      연결 확인
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => handleToggleEnabled(provider)}
                    >
                      {provider.enabled ? "끄기" : "켜기"}
                    </Button>
                    {!provider.is_default && provider.enabled && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleSetDefault(provider.id)}
                      >
                        기본 지정
                      </Button>
                    )}
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => openEdit(provider)}
                    >
                      수정
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => handleDelete(provider)}
                    >
                      삭제
                    </Button>
                  </div>
                </div>

                {/* 연결 확인 결과 — 실패 사유를 그대로 보여줍니다.
                    "실패" 만 보이면 사용자가 무엇을 고쳐야 할지 알 수 없습니다. */}
                {status && (
                  <p
                    className={`mt-3 rounded-lg p-2.5 text-[11px] ${
                      status.ok
                        ? "bg-success-50 text-gray-700 dark:bg-success-500/10 dark:text-gray-300"
                        : "bg-error-50 text-gray-700 dark:bg-error-500/10 dark:text-gray-300"
                    }`}
                  >
                    {status.ok ? "✓ " : "✗ "}
                    {status.message}
                    {provider.last_checked_at && !check && (
                      <span className="ml-2 text-gray-400">
                        ({new Date(provider.last_checked_at).toLocaleString()})
                      </span>
                    )}
                  </p>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* 생성/수정 모달 */}
      <ProviderFormModal
        isOpen={open}
        onClose={close}
        provider={editing}
        isCreating={creating}
        saving={saveAction.submitting}
        error={saveAction.error}
        onSave={async (input) => {
          const saved = await saveAction.run(input);
          if (saved) {
            close();
            providers.reload();
          }
        }}
      />
    </div>
  );
}

/** 공급자 생성/수정 폼입니다. */
function ProviderFormModal({
  isOpen,
  onClose,
  provider,
  isCreating,
  saving,
  error,
  onSave,
}: {
  isOpen: boolean;
  onClose: () => void;
  provider: ApiAiProvider | null;
  isCreating: boolean;
  saving: boolean;
  error: string | null;
  onSave: (input: ApiAiProviderInput) => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [baseUrl, setBaseUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [model, setModel] = useState("");
  const [authStyle, setAuthStyle] = useState("bearer");
  const [systemPrompt, setSystemPrompt] = useState("");
  const [timeoutSeconds, setTimeoutSeconds] = useState("120");
  const [maxTokens, setMaxTokens] = useState("");
  const [temperature, setTemperature] = useState("0.2");
  const [allowInsecureTls, setAllowInsecureTls] = useState(false);
  const [isDefault, setIsDefault] = useState(false);
  const [enabled, setEnabled] = useState(true);
  const [validationError, setValidationError] = useState<string | null>(null);

  // 모달이 열릴 때 폼을 채웁니다.
  // ⚠️ apiKey 는 채우지 않습니다. 서버가 원문을 보내지 않고,
  //    비워 두면 저장 시 서버가 기존 값을 유지합니다.
  useEffect(() => {
    if (!isOpen) return;

    if (provider) {
      setName(provider.name);
      setBaseUrl(provider.base_url);
      setModel(provider.model);
      setAuthStyle(provider.auth_style ?? "bearer");
      setSystemPrompt(provider.system_prompt ?? "");
      setTimeoutSeconds(String(provider.timeout_seconds ?? 120));
      setMaxTokens(provider.max_tokens == null ? "" : String(provider.max_tokens));
      setTemperature(String(provider.temperature ?? 0.2));
      setAllowInsecureTls(provider.allow_insecure_tls);
      setIsDefault(provider.is_default);
      setEnabled(provider.enabled);
    } else {
      setName("");
      setBaseUrl("https://api.openai.com/v1");
      setModel("");
      setAuthStyle("bearer");
      setSystemPrompt("");
      setTimeoutSeconds("120");
      setMaxTokens("");
      setTemperature("0.2");
      setAllowInsecureTls(false);
      setIsDefault(true);
      setEnabled(true);
    }

    // 키 입력은 항상 비우고 시작합니다.
    setApiKey("");
    setValidationError(null);
  }, [isOpen, provider]);

  const handleSubmit = async () => {
    setValidationError(null);

    if (!name.trim()) {
      setValidationError("공급자 이름을 입력하세요.");
      return;
    }
    if (!model.trim()) {
      setValidationError("모델 이름을 입력하세요. (예: gpt-4o-mini, llama3.1:8b)");
      return;
    }
    if (!baseUrl.trim()) {
      setValidationError("Base URL 을 입력하세요.");
      return;
    }

    const parsedTimeout = Number(timeoutSeconds);
    const parsedMaxTokens = maxTokens.trim() === "" ? undefined : Number(maxTokens);
    const parsedTemperature = Number(temperature);

    if (!Number.isFinite(parsedTimeout) || parsedTimeout <= 0) {
      setValidationError("타임아웃은 1 이상의 숫자여야 합니다.");
      return;
    }
    if (parsedMaxTokens !== undefined && (!Number.isFinite(parsedMaxTokens) || parsedMaxTokens <= 0)) {
      setValidationError("최대 토큰은 비우거나 1 이상의 숫자여야 합니다.");
      return;
    }
    if (!Number.isFinite(parsedTemperature)) {
      setValidationError("temperature 는 숫자여야 합니다. (권장 0.0 ~ 0.3)");
      return;
    }

    await onSave({
      id: provider?.id,
      name: name.trim(),
      base_url: baseUrl.trim(),
      // 빈 문자열이면 서버가 기존 키를 유지합니다.
      api_key: apiKey.trim(),
      model: model.trim(),
      auth_style: authStyle,
      system_prompt: systemPrompt.trim(),
      timeout_seconds: parsedTimeout,
      max_tokens: parsedMaxTokens,
      temperature: parsedTemperature,
      allow_insecure_tls: allowInsecureTls,
      is_default: isDefault,
      enabled,
    });
  };

  const shownError = validationError ?? error;

  return (
    <Modal isOpen={isOpen} onClose={onClose} className="max-w-[720px] m-4">
      <div className="no-scrollbar relative w-full max-w-[720px] overflow-y-auto rounded-3xl bg-white p-6 dark:bg-gray-900 lg:p-8">
        <h4 className="mb-1 text-xl font-semibold text-gray-800 dark:text-white/90">
          {isCreating ? "AI 공급자 추가" : "AI 공급자 수정"}
        </h4>
        <p className="mb-5 text-xs text-gray-500 dark:text-gray-400">
          OpenAI 호환 API 규격이면 어떤 공급자든 사용할 수 있습니다.
        </p>

        {/* 빠른 선택 — 자주 쓰는 조합을 한 번에 채웁니다.
            주소 형식을 틀려 404 가 나는 실수를 크게 줄여 줍니다. */}
        <div className="mb-4 flex flex-wrap gap-1.5">
          {[
            { label: "OpenAI", url: "https://api.openai.com/v1", model: "gpt-4o-mini" },
            { label: "Ollama(로컬)", url: "http://localhost:11434/v1", model: "llama3.1:8b" },
            { label: "vLLM(로컬)", url: "http://localhost:8000/v1", model: "Qwen2.5-14B-Instruct" },
            { label: "OpenRouter", url: "https://openrouter.ai/api/v1", model: "" },
          ].map((preset) => (
            <button
              key={preset.label}
              type="button"
              onClick={() => {
                setBaseUrl(preset.url);
                if (preset.model) setModel(preset.model);
                if (preset.label === "Ollama(로컬)" || preset.label === "vLLM(로컬)") {
                  // 로컬 모델은 키가 없고 타임아웃이 길어야 합니다.
                  setAuthStyle("bearer");
                  setTimeoutSeconds("300");
                }
              }}
              className="rounded-lg border border-gray-300 bg-white px-2.5 py-1 text-[11px] font-medium text-gray-700 transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-white/[0.03]"
            >
              {preset.label}
            </button>
          ))}
        </div>

        <div className="space-y-4">
          <Field label="공급자 이름" required>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="예: 사내 vLLM"
              className={inputClass}
            />
          </Field>

          <Field label="Base URL" required hint="끝에 /v1 을 포함하세요. 끝 슬래시는 없어도 됩니다.">
            <input
              type="text"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              placeholder="https://api.openai.com/v1"
              className={`${inputClass} font-mono`}
            />
          </Field>

          <Field label="모델" required hint="공급자가 인식하는 정확한 이름이어야 합니다.">
            <input
              type="text"
              value={model}
              onChange={(e) => setModel(e.target.value)}
              placeholder="gpt-4o-mini"
              className={`${inputClass} font-mono`}
            />
          </Field>

          <Field
            label="API Key"
            hint={
              provider?.has_api_key
                ? "이미 등록되어 있습니다. 비워 두면 기존 키가 유지됩니다."
                : "로컬 모델(Ollama 등)은 비워 두세요."
            }
          >
            <input
              type="password"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              placeholder={provider?.has_api_key ? "•••••••• (변경하지 않으려면 비워 두세요)" : "sk-..."}
              autoComplete="new-password"
              className={`${inputClass} font-mono`}
            />
          </Field>

          <Field label="인증 방식" hint="Azure OpenAI 는 api-key 헤더를 씁니다.">
            <select
              value={authStyle}
              onChange={(e) => setAuthStyle(e.target.value)}
              className={inputClass}
            >
              <option value="bearer">Authorization: Bearer (대부분)</option>
              <option value="azure">api-key 헤더 (Azure OpenAI)</option>
            </select>
          </Field>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Field label="타임아웃(초)" hint="로컬 모델은 300 권장">
              <input
                type="number"
                value={timeoutSeconds}
                onChange={(e) => setTimeoutSeconds(e.target.value)}
                className={inputClass}
              />
            </Field>
            <Field label="최대 토큰" hint="비우면 공급자 기본값">
              <input
                type="number"
                value={maxTokens}
                onChange={(e) => setMaxTokens(e.target.value)}
                placeholder="2048"
                className={inputClass}
              />
            </Field>
            <Field label="temperature" hint="낮을수록 사실 기반">
              <input
                type="number"
                step="0.1"
                value={temperature}
                onChange={(e) => setTemperature(e.target.value)}
                className={inputClass}
              />
            </Field>
          </div>

          <Field label="추가 시스템 프롬프트" hint="조직 규칙이나 분석 관점을 넣습니다. (선택)">
            <textarea
              value={systemPrompt}
              onChange={(e) => setSystemPrompt(e.target.value)}
              rows={3}
              placeholder="예: 우리 조직은 금융망 분리를 최우선으로 봅니다."
              className={`${inputClass} resize-y`}
            />
          </Field>

          <div className="flex flex-wrap gap-5 border-t border-gray-100 pt-4 dark:border-gray-800">
            <label className="flex cursor-pointer items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => setEnabled(e.target.checked)}
                className="size-4 rounded border-gray-300"
              />
              사용
            </label>
            <label className="flex cursor-pointer items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
              <input
                type="checkbox"
                checked={isDefault}
                onChange={(e) => setIsDefault(e.target.checked)}
                className="size-4 rounded border-gray-300"
              />
              기본 공급자
            </label>
            <label className="flex cursor-pointer items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
              <input
                type="checkbox"
                checked={allowInsecureTls}
                onChange={(e) => setAllowInsecureTls(e.target.checked)}
                className="size-4 rounded border-gray-300"
              />
              TLS 검증 건너뛰기
              <span className="text-[10px] text-warning-600 dark:text-orange-400">
                (자체 서명 인증서 전용)
              </span>
            </label>
          </div>
        </div>

        {shownError && (
          <p className="mt-4 rounded-lg border border-error-200 bg-error-50 p-3 text-xs text-gray-700 dark:border-error-500/30 dark:bg-error-500/10 dark:text-gray-300">
            {shownError}
          </p>
        )}

        <div className="mt-6 flex justify-end gap-2 border-t border-gray-100 pt-4 dark:border-gray-800">
          <Button size="sm" variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button size="sm" onClick={handleSubmit} disabled={saving}>
            {saving ? "저장 중..." : "저장"}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

/** 라벨 + 입력 묶음입니다. */
function Field({
  label,
  required,
  hint,
  children,
}: {
  label: string;
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
        {label}
        {required && <span className="ml-0.5 text-error-500">*</span>}
      </label>
      {children}
      {hint && (
        <p className="mt-1 text-[11px] text-gray-400 dark:text-gray-500">{hint}</p>
      )}
    </div>
  );
}

const inputClass =
  "w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white";
