import { useMemo, useState } from "react";
import Badge from "../ui/badge/Badge";
import Branch_Divider from "../common/Branch_Divider";
import OPNsenseConfigModal from "../opnsense/OPNsenseConfigModal";

/**
 * 프로젝트에 Agent(Prober)를 추가하는 카드입니다.
 *
 * <h2>배포 화면(`ProjectCreation.tsx`)의 `Deploy & Download` 를 재사용한 이유</h2>
 * 장비 카드 목록과 이미지, OPNsense 만 API 자격증명이 필요하다는 사실은
 * 배포 화면과 <b>완전히 같습니다.</b> 화면마다 카드를 다시 쓰면 한쪽만
 * 장비가 늘어나는 불일치가 생깁니다. 그래서 장비 목록을 이 컴포넌트로
 * 옮기고, 배포 화면과 프로젝트 목록이 같은 정의를 씁니다.
 *
 * <h2>프로젝트 목록에서 쓰는 방식</h2>
 * 프로젝트 행의 <b>Add Agent</b> 를 누르면 그 행 아래에 이 카드가 펼쳐집니다.
 * 편집 화면(`ProjectEditor`)은 서브넷/규칙/검증만 다루고 배포는 여기서
 * 하도록 역할을 나눴습니다. (편집 화면에 배포를 섞으면 "지금 무엇을
 * 저장하는가" 가 흐려집니다)
 *
 * <h2>선택한 장비로 설정 미리보기를 보여주는 이유</h2>
 * 프로버는 {@code Installer/default.conf} 의 세 값(SERVER_IP / SERVER_PORT /
 * NODE_TYPE)으로 동작합니다. 장비만 고르고 끝내면 운영자는 그 값을 어디에
 * 어떻게 넣는지 모릅니다. 그래서 장비를 고르면 <b>실제로 넣을 설정 파일
 * 내용</b>을 그대로 만들어 보여줍니다. 복사해서 그대로 쓸 수 있습니다.
 *
 * <h2>⚠️ 서버에 Agent 를 등록하지는 않습니다</h2>
 * 백엔드에는 Agent <b>등록 API 가 없습니다</b>
 * ({@code AgentStatusController} 는 조회/푸시만 제공). Agent 는 프로버가
 * 먼저 WebSocket 으로 접속해야 나타나므로, 여기서는 <b>배포에 필요한 값과
 * 파일을 안내하는 역할</b>만 합니다. OPNsense 만 예외로, 기존
 * 자격증명 API({@code PUT /api/v1/opnsense/credentials/{agentId}})를 실제로
 * 호출합니다.
 */

/** 배포 가능한 장비 한 종류. */
export interface AgentDeviceType {
  /** 화면에 보이는 이름. */
  label: string;
  /** 로고 이미지 주소. */
  image: string;
  /** 로고 대체 텍스트. */
  imageAlt: string;
  /** 프로버 `default.conf` 에 넣을 NODE_TYPE. */
  nodeType: "Router" | "Switch" | "VM" | "Firewall";
  /** 별도 안내가 필요하면 표시 (OPNsense 의 "API 설정 필요"). */
  note?: string;
  /** 클릭 시 장비 카드 대신 설정 모달을 여는지. */
  opensCredentialModal?: boolean;
}

/**
 * 장비 목록입니다.
 *
 * <p>순서는 배포 화면과 동일하게 두었습니다. 화면마다 순서가 다르면 같은
 * 장비를 찾을 때 눈이 다시 훑어야 합니다.
 *
 * <p>`for poc` 가 붙은 항목은 랩 검증용입니다. 실장비(Cisco/Arista/OPNsense/
 * Linux)와 섞이지 않도록 이름에 표시를 유지합니다.
 */
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
  /** 카드가 속한 프로젝트 키. 제목/안내에 사용합니다. */
  projectId: string;
  /** 카드 접기 콜백. */
  onClose: () => void;
  /**
   * Management Server IP 초기값.
   *
   * <p>배포 화면은 좌측 입력란에서 받은 값을 넘겨, 운영자가 같은 주소를
   * 두 번 입력하지 않게 합니다. (두 입력란이 다른 값을 가지면 어느 쪽이
   * 실제 설정인지 알 수 없습니다)
   */
  initialServerIp?: string;
  /** Management Server Port 초기값. 비우면 프로버 기본값 3000 입니다. */
  initialServerPort?: string;
  /** OPNsense 자격증명 저장 완료 콜백. (부모가 개수를 셀 때 사용) */
  onCredentialSaved?: () => void;
  /** 오프라인 데이터 가져오기 화면으로 이동. 없으면 버튼을 숨깁니다. */
  onImportOffline?: () => void;
}

/** 장비 카드 한 장. */
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
  initialServerIp,
  initialServerPort,
  onCredentialSaved,
  onImportOffline,
}: AgentDeployCardProps) {
  // Management Server 접속 정보. 프로버가 텔레메트리를 보낼 대상입니다.
  const [managementServerIPAddr, setManagementServerIPAddr] = useState(
    initialServerIp ?? "",
  );
  const [managementServerPort, setManagementServerPort] = useState(
    initialServerPort ?? "3000",
  );

  // 선택한 장비 (라벨로 보관해 장비 목록이 바뀌어도 깨지지 않게 합니다)
  const [selectedLabel, setSelectedLabel] = useState<string | null>(null);

  // OPNsense 자격증명 모달
  const [opnsenseOpen, setOpnsenseOpen] = useState(false);
  const [opnsenseAgentId, setOpnsenseAgentId] = useState("");
  const [opnsenseSavedCount, setOpnsenseSavedCount] = useState(0);

  const selected = useMemo(
    () => AGENT_DEVICE_TYPES.find((device) => device.label === selectedLabel) ?? null,
    [selectedLabel],
  );

  /**
   * 선택한 장비로 `default.conf` 내용을 만듭니다.
   *
   * <p>값을 비워 두면 프로버 기본값을 그대로 씁니다. 사용자가 입력하지 않은
   * 항목을 임의로 채우면 "이 값이 어디서 왔는지" 를 알 수 없게 됩니다.
   */
  const configPreview = useMemo(() => {
    const serverIp = managementServerIPAddr.trim() || "localhost";
    const serverPort = managementServerPort.trim() || "3000";
    return [
      "# agent 생성시 서버측에서 ip, port 인증서 등을 지정함",
      `SERVER_IP=${serverIp};`,
      `SERVER_PORT=${serverPort};`,
      `NODE_TYPE=${selected?.nodeType ?? "VM"};`,
    ].join("\n");
  }, [managementServerIPAddr, managementServerPort, selected]);

  const handleSelect = (device: AgentDeviceType) => {
    if (device.opensCredentialModal) {
      // OPNsense 는 프로버 설치가 아니라 REST API 접속이므로 자격증명을 받습니다.
      setOpnsenseAgentId(managementServerIPAddr.trim() || "opnsense-1");
      setOpnsenseOpen(true);
      return;
    }
    setSelectedLabel(device.label);
  };

  return (
    <div className="mt-4 rounded-2xl border border-gray-200 bg-gray-50 p-5 dark:border-gray-700 dark:bg-gray-800/50 lg:p-6">
      {/* 헤더 */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-gray-200 pb-3 dark:border-gray-700">
        <div className="flex flex-wrap items-center gap-2">
          <h4 className="font-semibold text-gray-800 dark:text-white/90">
            Deploy &amp; Download
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

      {/* Management Server 접속 정보 — 프로버가 이 주소로 텔레메트리를 보냅니다. */}
      <div className="mb-5 rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800/60">
        <p className="mb-3 text-xs font-semibold text-gray-700 dark:text-gray-200">
          Setup Management Server
        </p>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <input
            type="text"
            value={managementServerIPAddr}
            onChange={(e) => setManagementServerIPAddr(e.target.value)}
            placeholder="Set Management Server IP"
            className="w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
          />
          <input
            type="text"
            value={managementServerPort}
            onChange={(e) => setManagementServerPort(e.target.value)}
            placeholder="Set Management Server Port"
            className="w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
          />
        </div>
        <p className="mt-2 text-[11px] text-gray-500 dark:text-gray-400">
          비워 두면 프로버 기본값(<code className="font-mono">localhost:3000</code>)을
          씁니다. Prober 설치 기본 포트는 3000 입니다.
        </p>
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

          <p className="mb-2 text-xs text-gray-500 dark:text-gray-400">
            프로버의{" "}
            <code className="font-mono">Installer/default.conf</code> 에 아래 값을
            넣으세요. 저장 위치는 배포 이미지에 포함되어 있습니다.
          </p>

          <pre className="overflow-x-auto rounded bg-gray-50 p-3 font-mono text-[11px] text-gray-700 dark:bg-gray-900 dark:text-gray-200">
            {configPreview}
          </pre>

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
                ./sonar_validator_prober --export-once
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

      {/* OPNsense 설정 모달 — 기존 Modal 컴포넌트를 재사용합니다. */}
      <OPNsenseConfigModal
        isOpen={opnsenseOpen}
        onClose={() => setOpnsenseOpen(false)}
        agentId={opnsenseAgentId}
        deviceLabel={managementServerIPAddr || null}
        onSaved={() => {
          setOpnsenseSavedCount((count) => count + 1);
          onCredentialSaved?.();
        }}
      />
    </div>
  );
}
