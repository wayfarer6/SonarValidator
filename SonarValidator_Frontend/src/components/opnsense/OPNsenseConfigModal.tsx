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
  /** 모달 열림 여부. */
  isOpen: boolean;
  /** 닫기 콜백. */
  onClose: () => void;
  /**
   * 장치 이름(node_id)의 초기값입니다.
   *
   * <p>⚠️ 비워 두면 사용자가 모달에서 직접 입력합니다. 이 값은 서버가
   * OPNsense 설정을 식별하는 <b>키</b>이므로, 표시 이름이나 관리 서버 IP 로
   * 대신 채우면 안 됩니다. (그렇게 하면 실제 장치 이름과 어긋나 기존 설정을
   * 불러오지 못합니다)
   */
  initialNodeId?: string;
  /** 표시용 장치 이름 (제목에 사용). */
  deviceLabel?: string | null;
  /** 저장/삭제 성공 후 호출. (부모 목록 갱신용) */
  onSaved?: () => void;
}

/**
 * OPNsense 접속 정보 입력 모달입니다.
 *
 * <h2>기존 모달 컴포넌트를 재사용한 방식</h2>
 * `components/ui/modal` 의 `Modal` 을 그대로 씁니다. 이 컴포넌트가 이미
 * ESC 닫기, 배경 클릭 닫기, 스크롤 잠금을 처리하므로 여기서 다시 구현하지
 * 않습니다. `Project.tsx` 의 생성 모달과 같은 클래스 조합을 씁니다.
 *
 * <h2>시크릿 입력란을 항상 비워 두는 이유</h2>
 * 서버는 저장된 Secret 을 <b>절대 평문으로 내려보내지 않습니다.</b> 대신
 * `has_secret` 플래그만 줍니다. 그래서 입력란은 비어 있고, placeholder 에
 * "저장됨(변경하려면 입력)" 이라고 안내합니다.
 *
 * <p>값을 하드코딩해 채워 두면 화면 캡처나 DOM 검사로 비밀이 노출됩니다.
 * 또 서버로 다시 보내는 과정에서 평문이 여러 곳에 남습니다.
 *
 * <h2>OPNsense 인증 형식 안내를 화면에 넣은 이유</h2>
 * OPNsense 는 API Key 를 <b>사용자 이름 자리</b>에 넣어 Basic 인증합니다.
 * 이걸 모르고 반대로 넣으면 "Authentication Failed" 만 나와서 원인을 찾기
 * 어렵습니다. 그래서 발급 경로와 함께 화면에 적어 둡니다.
 */
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

  /**
   * 서버가 설정을 식별하는 키입니다. (node_id)
   *
   * <p>⚠️ 이 입력란이 없던 동안에는 값이 항상 빈 문자열이었고, 화면은
   * 모든 요청을 `/credentials/`(경로 변수 없음) 로 보냈습니다. 서버는 이를
   * <b>"No static resource api/v1/opnsense/credentials."</b> 라는 404 로
   * 돌려주기 때문에, 운영자에게는 "엔드포인트가 잘못됐다" 처럼 보였습니다.
   */
  const [nodeId, setNodeId] = useState("");
  const [nodeIdError, setNodeIdError] = useState<string | null>(null);
  /** 마지막으로 서버에서 조회를 끝낸 node_id. 중복 조회를 막는 표식입니다. */
  const [loadedFor, setLoadedFor] = useState<string | null>(null);

  const [existing, setExisting] = useState<OPNsenseCredential | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [baseUrl, setBaseUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [apiSecret, setApiSecret] = useState("");
  const [allowInsecureTls, setAllowInsecureTls] = useState(false);
  const [apiKeyError, setApiKeyError] = useState<string | null>(null);
  const [apiSecretError, setApiSecretError] = useState<string | null>(null);

  /**
   * 이 장치가 속한 프로젝트 키입니다. 빈 문자열이면 "미지정" 입니다.
   *
   * <p>REST 전용 장치는 프로버가 없어 `expected_agent` 에 등록되지 않으므로,
   * 소속을 다른 곳에서 알 수 없습니다. 그래서 접속 정보에 직접 남깁니다.
   */
  const [projectKey, setProjectKey] = useState("");
  /** 선택지로 쓸 프로젝트 목록입니다. */
  const [projects, setProjects] = useState<{ project_id: string; name: string }[]>([]);
  const [projectsError, setProjectsError] = useState<string | null>(null);

  /**
   * 선택지로 쓸 프로젝트 목록을 한 번 읽습니다.
   *
   * <p>실패해도 모달 자체는 쓸 수 있어야 합니다 — 프로젝트 지정은 부가
   * 정보이고, 그것 때문에 자격증명 등록을 막으면 손해가 큽니다.
   */
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

  /**
   * 장치 이름으로 기존 설정을 불러옵니다.
   *
   * <p>404 는 "아직 설정 없음" 이므로 오류가 아닙니다. 다만 그 외의 오류
   * (권한/네트워크)는 감추지 않고 그대로 보여줍니다 — 감추면 "새로 등록"
   * 으로 오인해 기존 설정을 덮어쓸 수 있습니다.
   *
   * @param target 장치 이름
   */
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

  /**
   * 모달을 열 때마다 상태를 초기화합니다.
   *
   * <p>⚠️ 장치 이름이 <b>주어진 경우(편집 진입)에는 즉시 서버 조회</b>를
   * 시작합니다. 예전에는 이름을 직접 입력하고 포커스를 옮겨야만 조회했기
   * 때문에, 편집으로 들어오면 주소가 빈 채로 열려 저장 버튼이 거부됐습니다.
   * (값이 비어 있는데 오류도 없어 "주소가 사라졌다" 로 보였습니다)
   */
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

  /** 이름 입력을 마치면(=포커스 해제) 기존 설정을 조회합니다. */
  const handleNodeIdBlur = useCallback(() => {
    const id = nodeId.trim();
    if (id === "" || id === loadedFor) return;
    void loadExisting(id);
  }, [nodeId, loadedFor, loadExisting]);

  /** 저장합니다. */
  const handleSave = useCallback(async () => {
    // ⚠️ 장치 이름·주소·Key/Secret 을 "같은 무게" 로 검증합니다.
    //    이름을 빼면 요청 경로에 경로 변수가 사라져(=/credentials/)
    //    서버가 "No static resource ..." 라는 404 를 돌려줍니다.
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

  /** 연결을 다시 확인합니다. */
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

  /** 설정을 삭제합니다. */
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

            {/*
              장치 이름(node_id) — 서버가 이 설정을 찾는 키입니다.
              ⚠️ 이 입력란이 없던 동안 요청 경로의 변수가 비어
                 `/credentials/` 로 나갔고, 서버가 "No static resource
                 api/v1/opnsense/credentials." 라는 404 를 돌려줬습니다.
            */}
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

            {/*
              프로젝트 소속 — REST 전용 장치는 프로버가 없어 `expected_agent`
              에 등록되지 않으므로, 소속을 알 근거가 여기에밖에 없습니다.
              목록에서 "어느 프로젝트 방화벽인가" 를 볼 수 있게 합니다.
            */}
            <div>
              <Label>프로젝트</Label>
              <select
                value={projectKey}
                onChange={(e) => setProjectKey(e.target.value)}
                className="h-11 w-full appearance-none rounded-lg border border-gray-300 bg-transparent px-4 py-2.5 text-sm text-gray-800 shadow-theme-xs focus:border-brand-300 focus:outline-hidden focus:ring-3 focus:ring-brand-500/10 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90 dark:focus:border-brand-800"
              >
                <option value="">미지정 (어느 프로젝트에도 속하지 않음)</option>
                {/* 저장된 값이 목록에 없을 수도 있습니다(프로젝트 삭제 등).
                    그때 선택지가 사라지면 화면은 미지정으로 보이는데 서버에는
                    값이 남아 있어, 저장 시 조용히 지워집니다. 그래서 항목을
                    하나 더 넣어 현재 값을 그대로 보여줍니다. */}
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
