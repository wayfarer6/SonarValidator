import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import Badge from "../ui/badge/Badge";
import Branch_Divider from "../common/Branch_Divider";
import OPNsenseConfigModal from "../opnsense/OPNsenseConfigModal";
import { downloadAgent, registerExpectedAgent } from "../../lib/api";
import { getProject } from "../../lib/api/projects";
import { useApi } from "../../hooks/useApi";


export interface AgentDeviceType {
  label: string;
  image: string;
  imageAlt: string;
  nodeType: "Router" | "Switch" | "VM" | "Firewall";
  note?: string;
  opensCredentialModal?: boolean;
}

export const AGENT_DEVICE_TYPES: AgentDeviceType[] = [
  {
    label: "Cisco Router",
    image: "https://companieslogo.com/img/orig/CSCO-187e9f61.png?t=1728111511",
    imageAlt: "Cisco Router",
    nodeType: "Router",
  },
  {
    label: "Arista Switch",
    image: "https://companieslogo.com/img/orig/ANET_BIG-150f82cc.png?t=1720244490",
    imageAlt: "Arista Switch",
    nodeType: "Switch",
  },
  {
    label: "OPNsense Firewall",
    image: "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/png/opnsense.png",
    imageAlt: "OPNsense Firewall",
    nodeType: "Firewall",
    note: "API 설정 필요",
    opensCredentialModal: true,
  },
  {
    label: "Linux VM",
    image: "https://img.icons8.com/color/150/linux.png",
    imageAlt: "Linux VM",
    nodeType: "VM",
  },
  {
    label: "Open vSwitch(for poc)",
    image:
      "https://images.seeklogo.com/logo-png/27/1/open-vswitch-logo-png_seeklogo-271617.png",
    imageAlt: "OpenvSwitch",
    nodeType: "Switch",
  },
  {
    label: "Alpine Based Firewall (for poc)",
    image: "https://cdn-icons-png.flaticon.com/512/6071/6071236.png",
    imageAlt: "Alpine Based Firewall",
    nodeType: "Firewall",
  },
  {
    label: "FRRouting (for poc)",
    image: "https://docs.frrouting.org/en/stable-8.5/_static/frr-icon.svg",
    imageAlt: "FRRouting",
    nodeType: "Router",
  },
];

export interface AgentDeployCardProps {
  projectId?: string;
  onClose: () => void;
  onCredentialSaved?: () => void;
  onImportOffline?: () => void;
}

function DeviceCard({
  device,
  selected,
  savedCount,
  onSelect,
}: {
  device: AgentDeviceType;
  selected: boolean;
  savedCount: number;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={`선택: ${device.label}`}
      title={
        device.opensCredentialModal
          ? "클릭하면 API Key / Secret 을 등록하는 화면이 열립니다"
          : "클릭하면 이 장비용 프로버 설정이 아래에 표시됩니다"
      }
      className={`group relative flex h-[170px] w-[150px] flex-col items-center justify-center rounded-xl border bg-white p-4 shadow-theme-xs transition-all dark:bg-gray-800 ${
        selected
          ? "border-brand-500 ring-2 ring-brand-500/30 dark:border-brand-500"
          : "border-gray-200 hover:border-brand-500 dark:border-gray-700 dark:hover:border-brand-500"
      }`}
    >
      {savedCount > 0 && (
        <span className="absolute right-2 top-2">
          <Badge size="sm" color="success">
            {savedCount}
          </Badge>
        </span>
      )}
      <div className="mb-2 flex h-[100px] w-[100px] items-center justify-center">
        <img
          src={device.image}
          alt={device.imageAlt}
          className="max-h-full max-w-full object-contain"
        />
      </div>
      <span className="text-center text-xs font-semibold text-gray-800 dark:text-white/90">
        {device.label}
      </span>
      {device.note && (
        <span className="mt-1 text-[10px] text-brand-500">{device.note}</span>
      )}
    </button>
  );
}

export default function AgentDeployCard({
  projectId,
  onClose,
  onCredentialSaved,
  onImportOffline,
}: AgentDeployCardProps) {
  const projectSettings = useApi(
    () => (projectId ? getProject(projectId) : Promise.resolve(null)),
    [projectId],
  );
  const [expectedDeviceIp, setExpectedDeviceIp] = useState("");
  const managementServerIPAddr = projectSettings.data?.management_server_ip?.trim() ?? "";
  const managementServerPort = projectSettings.data?.management_server_port?.toString() ?? "";
  const serverReady = !!managementServerIPAddr && !!managementServerPort && !projectSettings.loading && !projectSettings.error;

  // 선택한 장비 (라벨로 보관해 장비 목록이 바뀌어도 깨지지 않게 합니다)
  const [selectedLabel, setSelectedLabel] = useState<string | null>(null);

  // 서버에 등록할 Agent 식별자. 프로버 `default.conf` 의 이름과 일치해야 합니다.
  const [agentName, setAgentName] = useState("");
  // 등록 상태: idle → saving → done/error
  const [registerState, setRegisterState] = useState<
    "idle" | "saving" | "done" | "error"
  >("idle");
  const [registerError, setRegisterError] = useState<string | null>(null);
  // 이미 등록한 식별자 목록. 중복 클릭과 같은 이름 재등록을 눈에 보이게 합니다.
  const [registered, setRegistered] = useState<string[]>([]);

  // Agent 다운로드 상태: idle → downloading → done/error
  const [bundleState, setBundleState] = useState<
    "idle" | "downloading" | "done" | "error"
  >("idle");
  const [bundleError, setBundleError] = useState<string | null>(null);
  const [showConfig, setShowConfig] = useState(false);

  // OPNsense 자격증명 모달
  const [opnsenseOpen, setOpnsenseOpen] = useState(false);
  const [opnsenseSavedCount, setOpnsenseSavedCount] = useState(0);

  const hasProject = (projectId ?? "").trim() !== "";

  const selected = useMemo(
    () => AGENT_DEVICE_TYPES.find((device) => device.label === selectedLabel) ?? null,
    [selectedLabel],
  );

  const configPreview = useMemo(() => {
    const serverIp = managementServerIPAddr || "<프로젝트에서 설정>";
    const serverPort = managementServerPort || "<프로젝트에서 설정>";
    return [

      `SERVER_IP=${serverIp};`,
      `SERVER_PORT=${serverPort};`,
      `NODE_TYPE=${selected?.nodeType ?? "VM"};`,
      `AGENT_NAME=${agentName.trim() || "<노드 이름>"};`,
    ].join("\n");
  }, [managementServerIPAddr, managementServerPort, selected, agentName]);

  const handleDownloadBundle = async () => {
    const agentId = agentName.trim();
    if (agentId === "" || !projectId || !serverReady || bundleState === "downloading") {
      return;
    }

    setBundleState("downloading");
    setBundleError(null);
    try {
      const { fileName, blob } = await downloadAgent(agentId, {
        nodeType: selected?.nodeType,
        projectId,
      });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = fileName;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      // Blob URL 은 명시적으로 해제해야 메모리가 회수됩니다.
      URL.revokeObjectURL(url);
      setBundleState("done");
    } catch (cause) {
      setBundleState("error");
      setBundleError(
        cause instanceof Error ? cause.message : "Agent 다운로드를 완료하지 못했습니다.",
      );
    }
  };

  const handleSelect = (device: AgentDeviceType) => {
    if (device.opensCredentialModal) {
      setOpnsenseOpen(true);
      return;
    }
    setSelectedLabel(device.label);
    setAgentName("");
    setExpectedDeviceIp("");
    setBundleState("idle");
    setBundleError(null);
    setRegisterState("idle");
    setRegisterError(null);
  };

  const handleRegister = async () => {
    const agentId = agentName.trim();
    if (agentId === "" || registerState === "saving") {
      return;
    }

    setRegisterState("saving");
    setRegisterError(null);
    try {
      await registerExpectedAgent(agentId, {
        projectId,
        deviceType: selected?.nodeType,
        nodeType: selected?.nodeType,
        expectedIp: expectedDeviceIp.trim() || undefined,
        note: selected?.label,
      });
      setRegistered((current) =>
        current.includes(agentId) ? current : [...current, agentId],
      );
      setRegisterState("done");
    } catch (cause) {
      setRegisterState("error");
      setRegisterError(
        cause instanceof Error ? cause.message : "서버에 등록하지 못했습니다.",
      );
    }
  };

  return (
    <div data-testid="agent-deploy-card" className="mt-4 rounded-2xl border border-gray-200 bg-gray-50 p-5 dark:border-gray-700 dark:bg-gray-800/50 lg:p-6">
      {/* 헤더 */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-gray-200 pb-3 dark:border-gray-700">
        <div className="flex flex-wrap items-center gap-2">
          <h4 className="font-semibold text-gray-800 dark:text-white/90">
            Agent 다운로드
          </h4>
          <Badge size="sm" color="light">
            {projectId}
          </Badge>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
        >
          닫기 ✕
        </button>
      </div>

      <p className="mb-4 text-sm text-gray-600 dark:text-gray-300">
        네트워크 장비 유형을 선택하세요
      </p>

      <div data-testid="deployment-server" className="mb-5 flex flex-wrap items-center justify-between gap-3 border-b border-gray-200 pb-4 text-sm dark:border-gray-700">
        <div>
          <p className="text-xs text-gray-500">프로젝트 관리 서버</p>
          <p className="mt-1 font-mono text-gray-800 dark:text-gray-200">
            {projectSettings.loading ? "불러오는 중..." : serverReady ? `${managementServerIPAddr}:${managementServerPort}` : "프로젝트 편집에서 주소와 포트를 설정하세요"}
          </p>
          {projectSettings.error && <p role="alert" className="mt-1 text-xs text-error-600">{projectSettings.error}</p>}
        </div>
        {projectId && <Link to={`/project/editor/${encodeURIComponent(projectId)}#management-server`} className="text-xs font-medium text-brand-600 hover:underline dark:text-brand-400">프로젝트 설정</Link>}
      </div>

      {/* 장비 카드 그리드 */}
      <div className="flex flex-wrap items-center gap-4">
        {AGENT_DEVICE_TYPES.map((device) => (
          <DeviceCard
            key={device.label}
            device={device}
            selected={selectedLabel === device.label}
            savedCount={device.opensCredentialModal ? opnsenseSavedCount : 0}
            onSelect={() => handleSelect(device)}
          />
        ))}
      </div>

      {/* 선택한 장비의 설정 미리보기 */}
      {selected && (
        <div className="mt-5 rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800/60">
          <div className="mb-3 flex flex-wrap items-center gap-2">
            <h5 className="text-sm font-semibold text-gray-800 dark:text-white/90">
              {selected.label} 설정
            </h5>
            <Badge size="sm" color="info">
              NODE_TYPE={selected.nodeType}
            </Badge>
          </div>

          {/* Agent 이름 + 서버 등록. 이 두 가지가 있어야 목록에 나타납니다.
              ⚠️ 프로젝트를 고르지 않은 화면(Agent 목록)에서는 등록 UI 를 감춥니다.
                 프로젝트 없이 등록하면 목록의 프로젝트 필터가 어긋나기 때문입니다. */}
          <div className="mb-4 rounded-lg border border-brand-200 bg-brand-50/60 p-3 dark:border-brand-500/30 dark:bg-brand-500/10">
            <label className="mb-1.5 block text-xs font-semibold text-gray-700 dark:text-gray-200">
              Agent 이름{hasProject && " (서버 등록 + default.conf 에 함께 들어갑니다)"}
            </label>
            <div className="flex flex-wrap items-center gap-2">
              <input
                type="text"
                value={agentName}
                onChange={(e) => {
                  setAgentName(e.target.value);
                  setRegisterState("idle");
                }}
                aria-label="Agent 이름"
                placeholder="예: VDI-1-agent"
                className="min-w-[200px] flex-1 rounded-lg border border-gray-300 bg-white px-3 py-2 font-mono text-xs text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:bg-gray-800 dark:text-white"
              />
            <label className="mt-3 block text-xs text-gray-600 dark:text-gray-300">장비 관리 IP (선택)
              <input aria-label="장비 관리 IP" value={expectedDeviceIp} onChange={event => setExpectedDeviceIp(event.target.value)}
                placeholder="예: 192.168.148.181" className="mt-1 w-full rounded-lg border border-gray-300 bg-transparent px-3 py-2 text-sm dark:border-gray-600" />
            </label>
              {hasProject && (
                <button
                  type="button"
                  onClick={handleRegister}
                  disabled={agentName.trim() === "" || registerState === "saving"}
                  title={
                    agentName.trim() === ""
                      ? "Agent 이름을 먼저 입력하세요"
                      : "배포 예정으로 서버에 등록합니다"
                  }
                  className="rounded-lg bg-brand-500 px-3 py-2 text-xs font-semibold text-white transition hover:bg-brand-600 disabled:cursor-not-allowed disabled:bg-gray-300 dark:disabled:bg-gray-700"
                >
                  {registerState === "saving"
                    ? "등록 중..."
                    : registered.includes(agentName.trim())
                      ? "다시 등록"
                      : "서버에 등록"}
                </button>
              )}
            </div>

            {
}
            {!hasProject && (
              <p className="mt-2 text-[11px] text-gray-500 dark:text-gray-400">
                배포 예정 등록은 프로젝트가 있어야 합니다. 프로젝트 목록에서{" "}
                <b>Add Agent</b> 로 들어오면 그 프로젝트에 장비가 등록됩니다.
                먼저 소속 프로젝트를 선택하세요.
              </p>
            )}

            <p className="mt-2 text-[11px] text-gray-500">새 Agent는 먼저 등록하세요. 재배포할 때는 기존 이름과 장비 유형을 사용하세요.</p>
            {registerState === "done" && (
              <p className="mt-2 text-[11px] text-success-600 dark:text-success-400">
                ✓ <span className="font-mono">{agentName.trim()}</span> 을(를) 배포
                예정으로 등록했습니다. Agent 목록 화면에서 <b>무응답</b> 상태로
                보이며, 프로버가 접속하면 자동으로 <b>연결됨</b> 으로 바뀝니다.
              </p>
            )}
            {registerState === "error" && (
              <p className="mt-2 text-[11px] text-error-600 dark:text-error-400">
                등록하지 못했습니다: {registerError}
              </p>
            )}
            {registered.length > 0 && (
              <p className="mt-2 text-[11px] text-gray-500 dark:text-gray-400">
                이번 세션에서 등록:{" "}
                <span className="font-mono">{registered.join(", ")}</span>
              </p>
            )}
          </div>

          <div className="mt-3">
            <button
              type="button"
              aria-expanded={showConfig}
              onClick={() => setShowConfig((visible) => !visible)}
              className="rounded-md border border-gray-300 px-3 py-2 text-xs font-semibold text-gray-700 transition hover:bg-gray-50 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
            >
              {showConfig ? "설정값 숨기기" : "설정값 보기"}
            </button>
            {showConfig && (
              <div className="mt-2">
                <pre className="overflow-x-auto rounded bg-gray-50 p-3 font-mono text-[11px] text-gray-700 dark:bg-gray-900 dark:text-gray-200">
                  {configPreview}
                </pre>
              </div>
            )}
          </div>

          {/* 설정을 손으로 옮기지 않도록 서버가 채운 Agent 파일을 내려줍니다. */}
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={handleDownloadBundle}
              disabled={agentName.trim() === "" || !serverReady || bundleState === "downloading"}
              title={
                agentName.trim() === ""
                  ? "Agent 이름을 먼저 입력하세요 (배포 예정 등록 이름과 같아야 합니다)"
                  : "default.conf / README.txt / 바이너리/스크립트가 담긴 tar.gz 를 내려받습니다"
              }
              className="rounded-lg bg-brand-500 px-3.5 py-2 text-xs font-semibold text-white shadow-sm transition hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {bundleState === "downloading"
                ? "Agent 파일 준비 중..."
                : "Agent 다운로드 (tar.gz)"}
            </button>
            <span className="text-[11px] text-gray-500 dark:text-gray-400">
              서버가 <b>이 장치에 맞는 값</b>을 채워 넣습니다. 손으로 옮기면
              AGENT_NAME 이 어긋나 같은 장치가 두 줄로 보일 수 있습니다.
            </span>
          </div>

          {bundleState === "done" && (
            <p className="mt-2 text-[11px] text-success-600 dark:text-success-400">
              ✓ Agent 다운로드가 완료되었습니다. 장비에서{" "}
              <code className="font-mono">tar -xzf sonar-agent-*.tar.gz</code> 로 풀면{" "}
              <span className="font-mono">Installer/</span> 폴더가 나옵니다. 그 안의{" "}
              <span className="font-mono">README.txt</span> 를 순서대로 따르세요.
              실행 파일과 SQLite 템플릿도 포함되어 있습니다.
            </p>
          )}
          {bundleState === "error" && (
            <p className="mt-2 text-[11px] text-error-600 dark:text-error-400">
              Agent 다운로드에 실패했습니다: {bundleError}
            </p>
          )}

          {/* 배포 뒤 갈래 — 배포 화면과 같은 기준으로 안내합니다. */}
          <div className="my-4">
            <Branch_Divider
              orientation="horizontal"
              label="OR"
              hint="서버 연결 가능 여부로 갈립니다"
            />
          </div>

          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {/* 갈래 A: 온라인 */}
            <div className="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
              <div className="mb-1.5 flex items-center gap-2">
                <span className="font-semibold text-gray-800 dark:text-white/90">
                  서버에 연결 가능
                </span>
                <Badge size="sm" color="success">
                  온라인
                </Badge>
              </div>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                프로버를 실행하면 30초 주기로 텔레메트리를 자동 전송합니다. 잠시 뒤
                프로젝트 편집 화면에서 서브넷이 자동 수집됩니다.
              </p>
            </div>

            {/* 갈래 B: 오프라인 */}
            <div className="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
              <div className="mb-1.5 flex items-center gap-2">
                <span className="font-semibold text-gray-800 dark:text-white/90">
                  또는 연결이 불가능한 경우
                </span>
                <Badge size="sm" color="info">
                  오프라인
                </Badge>
              </div>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                프로버가 설정을 JSON 파일로 남깁니다. 그 파일을 서브넷 단계의{" "}
                <span className="font-medium">Import Offline Prober Data</span>{" "}
                카드에 끌어다 놓으면 서버가 같은 파서로 변환합니다.
              </p>
              <code className="mt-2 block rounded bg-gray-100 px-2 py-1.5 font-mono text-[10px] text-gray-700 dark:bg-gray-900 dark:text-gray-300">
                ./sonar_validator_prober --export snapshot.json
              </code>
            </div>
          </div>

          {onImportOffline && (
            <button
              type="button"
              onClick={onImportOffline}
              className="mt-3 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-xs font-medium text-gray-700 transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-white/[0.03]"
            >
              오프라인 데이터 가져오기 화면으로 이동
            </button>
          )}
        </div>
      )}

      <OPNsenseConfigModal
        isOpen={opnsenseOpen}
        onClose={() => setOpnsenseOpen(false)}
        initialNodeId={agentName.trim() || undefined}
        deviceLabel={managementServerIPAddr || null}
        onSaved={() => {
          setOpnsenseSavedCount((count) => count + 1);
          onCredentialSaved?.();
        }}
      />
    </div>
  );
}
