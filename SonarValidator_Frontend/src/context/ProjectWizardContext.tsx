import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useLocation, useSearchParams } from "react-router-dom";
import { useApi } from "../hooks/useApi";
import { useApiAction } from "../hooks/useApiAction";
import { getAllDiscoveredDevices, getDiscoveredDevices } from "../lib/api";
import { getProject, updateProject, type SubnetInput } from "../lib/api/projects";
import type { ApiDiscoveredDevice, ApiRule, ApiSubnet } from "../lib/api/types";

/**
 * 프로젝트 생성 마법사(위저드)가 공유하는 서브넷/규칙 상태입니다.
 *
 * <h2>더미 데이터에서 서버 연동으로</h2>
 * <p>이 파일은 원래 {@code INITIAL_SUBNETS} 라는 하드코딩 배열을
 * {@code useState} 초기값으로 쓰고 있었습니다. 그런데 배열 선언부가
 * <b>주석 처리</b>되면서 참조만 남아
 * {@code ReferenceError: INITIAL_SUBNETS is not defined} 가 발생했고,
 * 이 Provider 가 {@code main.tsx} 에서 {@code <App/>} 를 감싸고 있었기 때문에
 * <b>모든 화면이 빈 화면</b>이 되었습니다. (에러 바운더리 없음)
 *
 * <p>지금은 목록을 <b>백엔드에서 가져옵니다</b>. 하드코딩 배열은 지우지 않고
 * 주석으로 남겨 두었습니다 — 서버 응답이 어떤 형태였는지 비교 기준이 되고,
 * 서버 연동을 잠시 끄고 화면을 확인할 때 다시 켤 수 있기 때문입니다.
 *
 * <h2>데이터 출처 (REST)</h2>
 * <p>Agent(C++ Prober)와의 WebSocket 채널
 * ({@code /api/v1/management}, {@code /api/v1/telemetry})은 <b>Agent 전용
 * Envelope 프로토콜</b>이고 브라우저용 데이터 피드가 아닙니다. 따라서 화면은
 * 같은 데이터를 REST 로 읽습니다.
 *
 * <ol>
 *   <li>{@code GET /api/v1/projects/{id}} — 서버가 저장한 서브넷/규칙.
 *       비어 있으면 서버가 수집 결과로 만든 초안을 함께 내려줍니다.</li>
 *   <li>{@code GET /api/v1/network/discovered/{id}} — 초안이 아직 없을 때의
 *       폴백. 수집된 장치 인터페이스 주소에서 직접 서브넷을 만듭니다.</li>
 * </ol>
 *
 * <h2>등급을 추측하지 않는다</h2>
 * <p>서버는 주소 대역만 보고 등급을 정하지 않습니다(잘못된 등급은 잘못된 경보를
 * 만듭니다). 수집 초안의 등급은 항상 {@code Open} 이고
 * {@code manually_edited=false} 입니다. 그 값을 그대로 화면에 옮기고, 등급
 * 결정은 사람이 하도록 남겨 둡니다.
 */
export type SubnetClass = "Confidential" | "Sensitive" | "Open";

/** 위저드가 다루는 서브넷 한 건. (서버는 snake_case, 화면은 camelCase) */
export interface WizardSubnet {
  id: string;
  cidr: string;
  subnetClass: SubnetClass;
  /** 장치 인터페이스 이름 등 사람이 읽을 이름. */
  name: string | null;
  /** 이 서브넷을 보고한 Agent 식별자. */
  agentId: string | null;
  /** 서버 수집값이면 false → 화면에서 "확인 필요" 로 노출합니다. */
  manuallyEdited: boolean;
}

/** 위저드가 다루는 규칙 한 건. 서버 계약을 그대로 유지합니다. */
export type WizardRule = ApiRule;

/** 화면이 한 번에 읽는 묶음. fetcher 반환형입니다. */
interface WizardSnapshot {
  subnets: WizardSubnet[];
  rules: WizardRule[];
  draft: boolean;
  draftNote: string | null;
}

interface ProjectWizardContextValue {
  subnets: WizardSubnet[];
  rules: WizardRule[];
  /** 서버 수집 결과로 만든 초안인지 여부. */
  draft: boolean;
  draftNote: string | null;
  loading: boolean;
  error: string | null;
  /** 백엔드 자체에 닿지 못한 경우(서버 미기동). */
  offline: boolean;
  reload: () => void;
  /** 등급 저장 진행 중 여부. (project_id 가 있을 때만 저장합니다) */
  saving: boolean;
  saveError: string | null;
  setSubnetClass: (subnetId: string, subnetClass: SubnetClass) => void;
}

// ---------------------------------------------------------------------------
// 아래는 서버 연동 이전에 쓰던 더미 데이터입니다.
// 지우지 않고 남겨 둡니다 — 서버 응답과 형태를 비교하거나, 백엔드 없이
// 화면만 확인할 때 되살려 쓰기 위해서입니다.
// (되살릴 때는 useState 초기값을 이 배열로 바꾸면 됩니다)
// ---------------------------------------------------------------------------
// const INITIAL_SUBNETS: WizardSubnet[] = [
//   { id: "Subnet-0001", cidr: "192.168.0.x/24", subnetClass: "Open" },
//   { id: "Subnet-0002", cidr: "192.168.10.x/24", subnetClass: "Sensitive" },
//   { id: "Subnet-0003", cidr: "192.168.20.x/24", subnetClass: "Sensitive" },
//   { id: "Subnet-0004", cidr: "10.0.0.x/24", subnetClass: "Confidential" },
//   { id: "Subnet-0005", cidr: "172.16.0.x/24", subnetClass: "Open" },
// ];

const ProjectWizardContext = createContext<ProjectWizardContextValue | null>(null);

/** 서버 수집 초안의 기본 등급. (사람이 확인하기 전까지의 값) */
const DEFAULT_CLASS: SubnetClass = "Open";

/**
 * 서브넷 초안이 만들어졌지만 아직 저장하지 않았다는 안내 문구.
 * 서버의 {@code draft_note} 와 같은 취지를 프론트 폴백에서도 유지합니다.
 */
const FALLBACK_DRAFT_NOTE =
  "수집된 설정에서 만든 초안입니다. 등급은 확인 전까지 Open 이며, 수집된 방화벽 규칙은 검토 전까지 비활성 상태입니다.";

/**
 * 주소를 네트워크 주소 기준 CIDR 로 정규화합니다.
 *
 * <p>{@code 10.10.131.7/24} 와 {@code 10.10.131.0/24} 를 같은 키로 만들기 위한
 * 처리이며 백엔드 {@code PolicySubnet.normalizeCidr} 와 같은 규칙입니다.
 * 주소가 아니면 null 을 돌려주어 호출부가 건너뛰게 합니다.
 *
 * @param address 인터페이스 주소 ({@code 10.0.0.5} 또는 {@code 10.0.0.5/24})
 * @returns 정규화된 CIDR, 해석 실패 시 null
 */
function normalizeToCidr(address: string | null | undefined): string | null {
  if (!address) return null;
  const trimmed = address.trim();
  if (trimmed === "") return null;

  // 프리픽스가 없으면 호스트 단위(/32)로 봅니다.
  const [addressPart, prefixPart] = (trimmed.includes("/") ? trimmed : `${trimmed}/32`).split("/");

  const octets = addressPart.split(".").map((part) => Number(part));
  if (
    octets.length !== 4 ||
    octets.some((value) => !Number.isInteger(value) || value < 0 || value > 255)
  ) {
    return null;
  }
  const prefix = Number(prefixPart);
  if (!Number.isInteger(prefix) || prefix < 0 || prefix > 32) return null;

  // 자바의 `0xFFFFFFFFL << (32 - prefix)` 와 동일한 마스크. prefix=0 이면 0.
  const mask = prefix === 0 ? 0 : (0xffffffff << (32 - prefix)) >>> 0;
  const value = ((octets[0] << 24) | (octets[1] << 16) | (octets[2] << 8) | octets[3]) >>> 0;
  const network = (value & mask) >>> 0;

  return `${(network >>> 24) & 0xff}.${(network >>> 16) & 0xff}.${(network >>> 8) & 0xff}.${
    network & 0xff
  }/${prefix}`;
}

/** 백엔드의 {@code "Subnet-%04d"} 와 같은 규칙의 식별자를 만듭니다. */
function subnetIdOf(sequence: number): string {
  return `Subnet-${String(sequence).padStart(4, "0")}`;
}

/**
 * 수집된 장치 목록에서 서브넷 초안을 만듭니다.
 *
 * <p>백엔드 {@code ProjectView.subnetsFromDevice} 를 프론트에서 그대로 옮긴
 * 폴백입니다. 서버가 초안을 내려주기 전(또는 프로젝트 키 없이 진입했을 때)
 * 화면이 비지 않도록 하기 위한 것으로, <b>평소에는 서버 초안을 우선</b>합니다.
 *
 * <p>인터페이스 주소를 CIDR 로 정규화하고 중복을 제거합니다. 주소가 없거나
 * 해석할 수 없는 인터페이스는 건너뜁니다.
 *
 * @param devices 수집된 장치 목록
 * @returns 서브넷 초안 목록
 */
function subnetsFromDevices(devices: ApiDiscoveredDevice[]): WizardSubnet[] {
  const result: WizardSubnet[] = [];
  const seen = new Set<string>();

  for (const device of devices) {
    for (const iface of device.interfaces ?? []) {
      for (const address of iface.addresses ?? []) {
        const cidr = normalizeToCidr(address);
        // 같은 대역이 여러 장치/인터페이스에서 반복 보고됩니다. 한 번만 남깁니다.
        if (cidr === null || seen.has(cidr)) continue;
        seen.add(cidr);

        result.push({
          id: subnetIdOf(result.length + 1),
          cidr,
          subnetClass: iface.subnet_class ?? DEFAULT_CLASS,
          name: iface.name ?? null,
          agentId: device.agent_id ?? null,
          manuallyEdited: false,
        });
      }
    }
  }
  return result;
}

/** 서버 서브넷을 화면 표기로 바꿉니다. snake_case → camelCase 경계입니다. */
function toWizardSubnet(subnet: ApiSubnet): WizardSubnet {
  return {
    id: subnet.id,
    cidr: subnet.cidr,
    // 서버는 등급을 추측하지 않으므로 null 이 올 수 있습니다. 기본값을 둡니다.
    subnetClass: subnet.subnet_class ?? DEFAULT_CLASS,
    name: subnet.name ?? null,
    agentId: subnet.agent_id ?? null,
    manuallyEdited: subnet.manually_edited ?? false,
  };
}

/** 화면 상태를 서버 본문으로 되돌립니다. 저장 시에만 씁니다. */
function toSubnetInput(subnet: WizardSubnet): SubnetInput {
  return {
    id: subnet.id,
    cidr: subnet.cidr,
    subnet_class: subnet.subnetClass,
    name: subnet.name,
    agent_id: subnet.agentId,
    // 사람이 등급을 지정했으므로 "확인됨" 으로 표시합니다.
    manually_edited: true,
  };
}

/**
 * 위저드 상태를 백엔드에서 읽어 제공합니다.
 *
 * <p>{@code ?project_id=} 를 읽어야 하므로 반드시 <b>Router 안쪽</b>에
 * 마운트해야 합니다. (이전에는 {@code main.tsx} 에서 {@code <App/>} 바깥에
 * 있었기 때문에 쿼리스트링을 읽을 수 없었습니다)
 */
export function ProjectWizardProvider({ children }: { children: ReactNode }) {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get("project_id");

  /**
   * 서브넷 조회는 <b>마법사 화면에서만</b> 일어나야 합니다.
   *
   * <p>이 Provider 는 {@code AppLayout} 안쪽에 있어 대시보드/정책/로그 등
   * 로그인 후 모든 화면을 감쌉니다. 경로를 구분하지 않으면 관계없는 화면에서도
   * 프로젝트 조회가 나가고, 특히 프로젝트가 없을 때
   * {@code GET /api/v1/network/discovered} 폴백까지 실행됩니다. 그래서
   * <b>필요한 경로에서만</b> fetcher 가 실제로 요청하게 합니다.
   */
  const { pathname } = useLocation();
  const active = pathname.startsWith("/project/create");

  /**
   * 서브넷/규칙을 가져옵니다.
   *
   * <p>프로젝트 키가 있으면 프로젝트를 먼저 봅니다. 서버가 저장된 정책을
   * 우선하고, 비어 있을 때만 수집 초안을 내려주기 때문에 이 순서가 곧
   * "운영자가 고친 등급을 자동 수집이 덮어쓰지 않는다" 는 보장이 됩니다.
   */
  const fetchSnapshot = useCallback(async (): Promise<WizardSnapshot> => {
    // 마법사 화면이 아니면 요청하지 않습니다. (네트워크 탭을 조용하게 유지)
    if (!active) {
      return { subnets: [], rules: [], draft: false, draftNote: null };
    }

    if (projectId !== null && projectId !== "") {
      const project = await getProject(projectId);
      const saved = project.subnets ?? [];

      if (saved.length > 0) {
        // 이미 저장된 정책이 있습니다. 그대로 씁니다.
        return {
          subnets: saved.map(toWizardSubnet),
          rules: project.rules ?? [],
          draft: project.draft === true,
          draftNote: project.draft_note ?? null,
        };
      }

      // 서버에 저장된 서브넷이 없습니다. 아직 초안도 만들어지지 않은 상태이므로
      // 수집된 장치에서 직접 만듭니다. (서버가 초안을 내려주면 위 분기에서 끝납니다)
      const discovered = await getDiscoveredDevices(projectId);
      const subnets = subnetsFromDevices(discovered.devices ?? []);
      return {
        subnets,
        rules: project.rules ?? [],
        draft: subnets.length > 0,
        draftNote: subnets.length > 0 ? (project.draft_note ?? FALLBACK_DRAFT_NOTE) : null,
      };
    }

    // 프로젝트 키 없이 진입한 경우(예: 마법사 링크에 키가 빠진 경우).
    // 전체 수집 현황에서 만들어 화면이 비지 않게 합니다.
    const discovered = await getAllDiscoveredDevices();
    const subnets = subnetsFromDevices(discovered.devices ?? []);
    return {
      subnets,
      rules: [],
      draft: subnets.length > 0,
      draftNote: subnets.length > 0 ? FALLBACK_DRAFT_NOTE : null,
    };
  }, [projectId, active]);

  const { data, loading, error, offline, reload } = useApi(fetchSnapshot, [projectId, active]);

  // 등급 변경은 화면에 먼저 반영하고, 프로젝트 키가 있으면 서버에도 저장합니다.
  // (서버 저장이 실패해도 편집 자체는 계속할 수 있어야 하므로 낙관적 갱신입니다)
  const save = useApiAction(updateProject);

  const [subnets, setSubnets] = useState<WizardSubnet[]>([]);

  // 서버 응답이 도착하면 로컬 편집 상태로 복사합니다.
  // (응답을 직접 쓰면 화면에서 등급을 바꾼 뒤 새로고침에 지워집니다)
  useEffect(() => {
    setSubnets(data?.subnets ?? []);
  }, [data]);

  useEffect(() => {
    if (save.error !== null) {
      console.warn("[ProjectWizard] 서브넷 등급 저장 실패:", save.error);
    }
  }, [save.error]);

  const { run: runSave, submitting: saving, error: saveError } = save;

  const setSubnetClass = useCallback(
    (subnetId: string, subnetClass: SubnetClass) => {
      setSubnets((prev) => {
        const next = prev.map((subnet) =>
          subnet.id === subnetId ? { ...subnet, subnetClass, manuallyEdited: true } : subnet,
        );

        if (projectId !== null && projectId !== "" && active) {
          // subnets 만 보냅니다. 서버는 null 인 필드를 건드리지 않으므로
          // 규칙 목록은 그대로 남습니다. (`replacePolicy` 계약)
          void runSave(projectId, { subnets: next.map(toSubnetInput) });
        }
        return next;
      });
    },
    [projectId, active, runSave],
  );

  const value = useMemo<ProjectWizardContextValue>(
    () => ({
      subnets,
      rules: data?.rules ?? [],
      draft: data?.draft ?? false,
      draftNote: data?.draftNote ?? null,
      loading,
      error,
      offline,
      reload,
      saving,
      saveError,
      setSubnetClass,
    }),
    [subnets, data, loading, error, offline, reload, saving, saveError, setSubnetClass],
  );

  return (
    <ProjectWizardContext.Provider value={value}>{children}</ProjectWizardContext.Provider>
  );
}

export function useProjectWizard() {
  const ctx = useContext(ProjectWizardContext);
  if (!ctx) {
    throw new Error("useProjectWizard must be used within ProjectWizardProvider");
  }
  return ctx;
}
