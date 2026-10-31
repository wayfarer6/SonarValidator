import { useCallback, useEffect, useState } from "react";
import { Modal } from "../ui/modal";
import Badge from "../ui/badge/Badge";
import Button from "../ui/button/Button";
import Input from "../form/input/InputField";
import Label from "../form/Label";
import { AlertIcon, CheckCircleIcon, InfoIcon } from "../../icons";
import { ApiError } from "../../lib/api/client";
import { listProjects } from "../../lib/api/projects";
import {
  deleteCredential,
  getCredential,
  saveCredential,
  statusColor,
  statusLabel,
  verifyCredential,
  type OPNsenseCredential,
} from "../../lib/api/opnsense";

interface OPNsenseConfigModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialNodeId?: string;
  deviceLabel?: string | null;
  onSaved?: () => void;
}

export default function OPNsenseConfigModal({
  isOpen,
  onClose,
  initialNodeId,
  deviceLabel,
  onSaved,
}: OPNsenseConfigModalProps) {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [nodeId, setNodeId] = useState("");
  const [nodeIdError, setNodeIdError] = useState<string | null>(null);
  const [loadedFor, setLoadedFor] = useState<string | null>(null);

  const [existing, setExisting] = useState<OPNsenseCredential | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [baseUrl, setBaseUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [apiSecret, setApiSecret] = useState("");
  const [allowInsecureTls, setAllowInsecureTls] = useState(false);
  const [apiKeyError, setApiKeyError] = useState<string | null>(null);
  const [apiSecretError, setApiSecretError] = useState<string | null>(null);

  const [projectKey, setProjectKey] = useState("");
  const [projects, setProjects] = useState<{ project_id: string; name: string }[]>([]);
  const [projectsError, setProjectsError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    listProjects()
      .then((result) => {
        if (cancelled) return;
        setProjects(
          (result.projects ?? []).map((project) => ({
            project_id: project.project_id,
            name: project.name,
          })),
        );
        setProjectsError(null);
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        setProjects([]);
        setProjectsError(
          cause instanceof Error ? cause.message : "프로젝트 목록을 불러오지 못했습니다.",
        );
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen]);

  const loadExisting = useCallback(async (target: string) => {
    const id = target.trim();
    if (id === "") return;
    setLoading(true);
    setError(null);
    try {
      const credential = await getCredential(id);
      setExisting(credential);
      setDisplayName(credential.display_name ?? "");
      setBaseUrl(credential.base_url ?? "");
      setAllowInsecureTls(credential.allow_insecure_tls);
      setProjectKey(credential.project_id ?? "");
    } catch (cause) {
      setExisting(null);
      if (!(cause instanceof ApiError && cause.isNotFound)) {
        setError(cause instanceof Error ? cause.message : "설정을 불러오지 못했습니다.");
      }
    } finally {
      setLoadedFor(id);
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!isOpen) return;
    const initial = initialNodeId?.trim() ?? "";
    setNodeId(initial);
    setNodeIdError(null);
    setLoadedFor(null);
    setExisting(null);
    setLoading(false);
    setError(null);
    setSuccess(null);
    setDisplayName(deviceLabel ?? "");
    setBaseUrl("");
    setApiKey("");
    setApiSecret("");
    setAllowInsecureTls(false);
    // 프로젝트는 항상 "미지정" 으로 시작하고, 편집이면 바로 아래
    // loadExisting 이 저장된 값을 다시 채웁니다.
    setProjectKey("");
    setApiKeyError(null);
    setApiSecretError(null);
    if (initial !== "") {
      void loadExisting(initial);
    }
  }, [isOpen, initialNodeId, deviceLabel, loadExisting]);

  const handleNodeIdBlur = useCallback(() => {
    const id = nodeId.trim();
    if (id === "" || id === loadedFor) return;
    void loadExisting(id);
  }, [nodeId, loadedFor, loadExisting]);

  const handleSave = useCallback(async () => {



    const target = nodeId.trim();
    let invalid = false;
    if (target === "") {
      setNodeIdError(
        "장치 이름을 입력하세요. OPNsense 에서 이 장비를 식별할 이름입니다. (예: OPNsense-Firewall)",
      );
      invalid = true;
    }
    if (!baseUrl.trim()) {
      setError("OPNsense 주소를 입력하세요. (예: https://10.99.143.2)");
      invalid = true;
    }
    // 신규 등록(existing 없음)에는 Key/Secret 이 반드시 필요합니다.
    // 기존 설정을 수정할 때는 비워 두면 서버가 기존 값을 유지합니다.
    const needsKey = !existing?.has_api_key && !apiKey.trim();
    const needsSecret = !existing?.has_secret && !apiSecret.trim();
    setApiKeyError(
      needsKey ? "API Key 를 입력하세요. (System > Access > Users > API keys)" : null,
    );
    setApiSecretError(needsSecret ? "API Secret 을 입력하세요." : null);
    if (needsKey || needsSecret) invalid = true;
    if (invalid) return;

    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const saved = await saveCredential(target, {
        display_name: displayName.trim() || undefined,
        base_url: baseUrl.trim(),
        // 빈 값이면 서버가 기존 값을 유지합니다.
        api_key: apiKey.trim() || undefined,
        api_secret: apiSecret.trim() || undefined,
        allow_insecure_tls: allowInsecureTls,
        verify_now: true,
        // ⚠️ 항상 보냅니다. 빈 문자열은 "지정 해제" 라는 뜻이므로,
        //    생략(유지)과 구분해야 프로젝트를 바꿀 수도 지울 수도 있습니다.
        project_key: projectKey.trim(),
      });
      setExisting(saved);
      setLoadedFor(target);
      setApiKey("");
      setApiSecret("");
      setApiKeyError(null);
      setApiSecretError(null);
      // ⚠️ "저장" 과 "연결" 은 다른 사건입니다.
      //    한 문장에 뭉치면 "저장했는지" 를 알 수 없습니다.
      setSuccess(
        saved.status === "OK"
          ? `저장하고 연결을 확인했습니다.${saved.detected_version ? ` (버전 ${saved.detected_version})` : ""}`
          : "설정은 저장했습니다. 다만 연결 확인에 실패했습니다 — 아래 오류를 참고하세요.",
      );
      onSaved?.();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  }, [nodeId, displayName, baseUrl, apiKey, apiSecret, allowInsecureTls, projectKey, onSaved, existing]);

  const handleVerify = useCallback(async () => {
    setVerifying(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await verifyCredential(nodeId.trim());
      setExisting(result);
      setSuccess(null);
      onSaved?.();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "연결 확인에 실패했습니다.");
    } finally {
      setVerifying(false);
    }
  }, [nodeId, onSaved]);

  const handleDelete = useCallback(async () => {
    if (!window.confirm("OPNsense 접속 정보를 삭제하시겠습니까?")) return;
    setSaving(true);
    setError(null);
    try {
      await deleteCredential(nodeId.trim());
      setExisting(null);
      setLoadedFor(null);
      setApiKey("");
      setApiSecret("");
      setSuccess("삭제했습니다.");
      onSaved?.();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "삭제에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  }, [nodeId, onSaved]);

  return (
    <Modal isOpen={isOpen} onClose={onClose} className="max-w-[720px] m-4">
      <div className="no-scrollbar relative w-full max-w-[720px] overflow-y-auto rounded-3xl bg-white p-4 dark:bg-gray-900 lg:p-9">
        {/* 헤더 */}
        <div className="px-2 pr-14">
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <h4 className="text-2xl font-semibold text-gray-800 dark:text-white/90">
              OPNsense 연동 설정
            </h4>
            {existing && (
              <Badge size="sm" color={statusColor(existing.status)}>
                {statusLabel(existing.status)}
              </Badge>
            )}
          </div>
          <p className="mb-6 text-sm text-gray-500 dark:text-gray-400">
            대상 장치:{" "}
            {nodeId.trim() ? (
              <span className="font-mono">{nodeId.trim()}</span>
            ) : (
              <span className="text-warning-600 dark:text-orange-300">
                아직 지정되지 않음 — 아래에서 장치 이름을 입력하세요
              </span>
            )}
            {deviceLabel ? ` (${deviceLabel})` : ""}
          </p>
        </div>

        {loading ? (
          <div className="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
            설정을 불러오는 중...
          </div>
        ) : (
          <div className="custom-scrollbar max-h-[520px] space-y-5 overflow-y-auto px-2 pb-3">
            {/* 안내: OPNsense 인증 형식 */}
            <div className="rounded-lg bg-blue-light-50 px-3 py-2.5 text-xs text-blue-light-700 dark:bg-blue-light-500/10 dark:text-blue-light-300">
              <p className="font-medium">OPNsense API 사용 준비</p>
              <ol className="mt-1 list-inside list-decimal space-y-0.5">
                <li>
                  OPNsense 에서 <span className="font-mono">System &gt; Access &gt; Users</span> 로
                  전용 사용자를 만들고 필요한 권한을 부여하세요.
                </li>
                <li>
                  같은 화면의 <span className="font-mono">API keys</span> 에서 Key 와 Secret 을
                  발급하세요.
                </li>
                <li>
                  인증은 <span className="font-mono">Basic base64(key:secret)</span> 형식입니다.
                  (Key 가 사용자 이름 자리)
                </li>
              </ol>
            </div>

            <div>
              <Label>
                장치 이름 (node_id) <span className="text-error-500">*</span>
              </Label>
              <Input
                type="text"
                placeholder="예: OPNsense-Firewall"
                value={nodeId}
                error={Boolean(nodeIdError)}
                onChange={(e) => {
                  setNodeId(e.target.value);
                  setLoadedFor(null);
                  if (nodeIdError) setNodeIdError(null);
                }}
                onBlur={handleNodeIdBlur}
              />
              {nodeIdError ? (
                <p className="mt-1 text-[11px] text-error-500">{nodeIdError}</p>
              ) : (
                <p className="mt-1 text-[11px] text-gray-400">
                  서버가 이 설정을 식별하는 이름입니다. 배포 예정/Agent 목록에 등록한
                  이름과 같게 두세요. 입력 후 다른 곳을 누르면 저장된 설정을 불러옵니다.
                </p>
              )}
            </div>

            {/* 표시 이름 */}
            <div>
              <Label>표시 이름</Label>
              <Input
                type="text"
                placeholder="예: FW-DMZ-01"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
              />
              <p className="mt-1 text-[11px] text-gray-400">
                목록에서 구분하기 위한 이름입니다. 비워두면 장치 이름을 씁니다.
              </p>
            </div>

            <div>
              <Label>프로젝트</Label>
              <select
                value={projectKey}
                onChange={(e) => setProjectKey(e.target.value)}
                className="h-11 w-full appearance-none rounded-lg border border-gray-300 bg-transparent px-4 py-2.5 text-sm text-gray-800 shadow-theme-xs focus:border-brand-300 focus:outline-hidden focus:ring-3 focus:ring-brand-500/10 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90 dark:focus:border-brand-800"
              >
                <option value="">미지정 (어느 프로젝트에도 속하지 않음)</option>
                {projectKey !== "" &&
                  !projects.some((project) => project.project_id === projectKey) && (
                    <option value={projectKey}>{projectKey} (목록에 없음)</option>
                  )}
                {projects.map((project) => (
                  <option key={project.project_id} value={project.project_id}>
                    {project.name} ({project.project_id})
                  </option>
                ))}
              </select>
              {projectsError ? (
                <p className="mt-1 text-[11px] text-error-500">
                  프로젝트 목록을 불러오지 못했습니다: {projectsError}
                </p>
              ) : (
                <p className="mt-1 text-[11px] text-gray-400">
                  목록에서 이 장치의 소속을 표시합니다. 선택을 지우려면 “미지정” 을 고르세요.
                </p>
              )}
            </div>

            {/* 주소 */}
            <div>
              <Label>
                OPNsense 주소 <span className="text-error-500">*</span>
              </Label>
              <Input
                type="text"
                placeholder="https://10.99.143.2"
                value={baseUrl}
                onChange={(e) => setBaseUrl(e.target.value)}
              />
              <p className="mt-1 text-[11px] text-gray-400">
                포트가 기본값(443)이 아니면 함께 입력하세요. API 경로(예:
                /api/firewall)는 넣지 마세요 — 자동으로 붙습니다.
              </p>
            </div>

            {/* API Key */}
            <div>
              <Label>
                API Key{" "}
                {!existing?.has_api_key && <span className="text-error-500">*</span>}
              </Label>
              <Input
                type="text"
                placeholder={
                  existing?.has_api_key
                    ? `저장됨 (${existing.api_key_masked}) — 변경하려면 입력`
                    : "OPNsense API Key"
                }
                value={apiKey}
                onChange={(e) => {
                  setApiKey(e.target.value);
                  if (apiKeyError) setApiKeyError(null);
                }}
              />
              {apiKeyError ? (
                <p className="mt-1 text-[11px] text-error-500">{apiKeyError}</p>
              ) : (
                <p className="mt-1 text-[11px] text-gray-400">
                  새로 등록할 때는 필수입니다. (기존 설정 수정 시에는 비워두면 유지)
                </p>
              )}
            </div>

            {/* API Secret */}
            <div>
              <Label>
                API Secret{" "}
                {!existing?.has_secret && <span className="text-error-500">*</span>}
              </Label>
              <Input
                type="password"
                placeholder={
                  existing?.has_secret
                    ? "저장됨 — 변경하려면 입력"
                    : "OPNsense API Secret"
                }
                value={apiSecret}
                onChange={(e) => {
                  setApiSecret(e.target.value);
                  if (apiSecretError) setApiSecretError(null);
                }}
              />
              {apiSecretError ? (
                <p className="mt-1 text-[11px] text-error-500">{apiSecretError}</p>
              ) : (
                <p className="mt-1 text-[11px] text-gray-400">
                  Secret 은 암호화되어 저장되며, 저장 후에는 다시 표시되지 않습니다.
                  비워두면 기존 값이 유지됩니다.
                </p>
              )}
            </div>

            {/* 자체 서명 인증서 */}
            <label className="flex cursor-pointer items-start gap-2 rounded-lg border border-gray-200 p-3 dark:border-gray-700">
              <input
                type="checkbox"
                checked={allowInsecureTls}
                onChange={(e) => setAllowInsecureTls(e.target.checked)}
                className="mt-0.5 size-4 rounded border-gray-300"
              />
              <span className="text-xs">
                <span className="font-medium text-gray-800 dark:text-gray-200">
                  자체 서명 인증서 허용
                </span>
                <span className="mt-0.5 block text-gray-500 dark:text-gray-400">
                  랩 장비가 자체 서명 인증서를 쓸 때만 켜세요. 켜면 TLS 검증을
                  건너뛰므로 운영 환경에서는 사용하지 마세요.
                </span>
              </span>
            </label>

            {/* 상태 정보 */}
            {existing && (
              <div className="rounded-lg border border-gray-200 p-3 text-xs dark:border-gray-700">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium text-gray-700 dark:text-gray-200">
                    마지막 확인
                  </span>
                  <span className="font-mono text-gray-500 dark:text-gray-400">
                    {existing.last_checked_at
                      ? new Date(existing.last_checked_at).toLocaleString()
                      : "아직 확인하지 않음"}
                  </span>
                </div>
                {existing.detected_version && (
                  <div className="mt-1 flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium text-gray-700 dark:text-gray-200">감지 버전</span>
                    <span className="font-mono text-gray-500 dark:text-gray-400">
                      {existing.detected_version}
                    </span>
                  </div>
                )}
                {existing.last_error && (
                  <div className="mt-2 flex items-start gap-1.5 rounded bg-error-50 px-2 py-1.5 text-[11px] text-error-700 dark:bg-error-500/15 dark:text-error-300">
                    <AlertIcon className="mt-0.5 size-3 shrink-0" />
                    <span>{existing.last_error}</span>
                  </div>
                )}
              </div>
            )}

            {/* 오류 / 성공 */}
            {error && (
              <div className="flex items-start gap-1.5 rounded-lg bg-error-50 px-3 py-2 text-xs text-error-700 dark:bg-error-500/15 dark:text-error-300">
                <AlertIcon className="mt-0.5 size-3.5 shrink-0" />
                <span>{error}</span>
              </div>
            )}
            {success && (
              <div className="flex items-start gap-1.5 rounded-lg bg-success-50 px-3 py-2 text-xs text-success-700 dark:bg-success-500/15 dark:text-success-500">
                <CheckCircleIcon className="mt-0.5 size-3.5 shrink-0" />
                <span>{success}</span>
              </div>
            )}

            {/* 미검증 안내 */}
            {!existing && (
              <div className="flex items-start gap-1.5 rounded-lg bg-warning-50 px-3 py-2 text-[11px] text-warning-700 dark:bg-warning-500/15 dark:text-orange-300">
                <InfoIcon className="mt-0.5 size-3.5 shrink-0" />
                <span>
                  저장하면 서버가 즉시 연결을 확인합니다. 실패하면 사유(인증 오류 /
                  권한 없음 / 연결 거부 등)가 표시됩니다.
                </span>
              </div>
            )}
          </div>
        )}

        {/* 하단 버튼 */}
        <div className="mt-6 flex flex-wrap items-center gap-2 border-t border-gray-100 px-2 pt-4 dark:border-gray-800">
          {existing && (
            <>
              <Button
                size="sm"
                variant="outline"
                onClick={handleVerify}
                disabled={verifying || saving}
              >
                {verifying ? "확인 중..." : "연결 테스트"}
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={handleDelete}
                disabled={saving}
                className="text-error-500"
              >
                삭제
              </Button>
            </>
          )}
          <div className="ml-auto flex items-center gap-2">
            <Button size="sm" variant="outline" onClick={onClose}>
              닫기
            </Button>
            <Button size="sm" onClick={handleSave} disabled={saving || loading}>
              {saving ? "저장 중..." : "저장"}
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}
