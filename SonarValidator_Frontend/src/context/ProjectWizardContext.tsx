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
import { updateProject, type SubnetInput } from "../lib/api/projects";
import { getProjectForEditing } from "../lib/api/discovery";
import type { ApiRule, ApiSubnet } from "../lib/api/types";

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
 *   <li>{@code GET /api/v1/projects/{id}/editing} — 서버가 저장한 서브넷/규칙.
 *       비어 있으면 서버가 수집 결과로 만든 초안을 함께 내려줍니다.</li>
 *   <li>{@code GET /api/v1/network/discovered/{id}} — 초안이 아직 없을 때의
 *       폴백. 수집된 장치 인터페이스 주소에서 직접 서브넷을 만듭니다.</li>
 * </ol>
 *
 * <h2>등급을 추측하지 않는다</h2>
 * <p>서버는 주소 대역만 보고 등급을 정하지 않습니다(잘못된 등급은 잘못된 경보를
 * 만듭니다). 수집 초안의 등급은 {@code null}(미분류) 이고
 * {@code manually_edited=false} 입니다. 그 값을 그대로 화면에 옮기고, 등급
 * 결정은 사람이 하도록 남겨 둡니다.
 */
export type SubnetClass = "Confidential" | "Sensitive" | "Open";

/** 위저드가 다루는 서브넷 한 건. (서버는 snake_case, 화면은 camelCase) */
export interface WizardSubnet {
  id: string;
  cidr: string;
  subnetClass: SubnetClass | null;
  vlanId?: number | null;
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
  setSubnetClass: (subnetId: string, subnetClass: SubnetClass | null) => void;
  updateSubnet: (subnetId: string, patch: Partial<WizardSubnet>) => void;
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

/** 서버 서브넷을 화면 표기로 바꿉니다. snake_case → camelCase 경계입니다. */
function toWizardSubnet(subnet: ApiSubnet): WizardSubnet {
  return {
    id: subnet.id,
    cidr: subnet.cidr,
    // 서버가 미분류로 보낸 값은 그대로 유지합니다.
    subnetClass: subnet.subnet_class,
    vlanId: subnet.vlan_id,
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
    vlan_id: subnet.vlanId,
    subnet_class: subnet.subnetClass,
    name: subnet.name,
    agent_id: subnet.agentId,
    // 사람이 등급을 지정했으므로 "확인됨" 으로 표시합니다.
    manually_edited: subnet.manuallyEdited,
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
      const project = await getProjectForEditing(projectId);
      return {
        subnets: project.subnets.map(toWizardSubnet),
        rules: project.rules ?? [],
        draft: project.draft === true,
        draftNote: project.draft_note ?? null,
      };
    }

    return { subnets: [], rules: [], draft: false, draftNote: null };
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

  const updateSubnet = useCallback((subnetId: string, patch: Partial<WizardSubnet>) => {
    const next = subnets.map(subnet => subnet.id === subnetId
      ? {...subnet, ...patch, manuallyEdited: true} : subnet);
    setSubnets(next);
    if (projectId && active) void runSave(projectId, {subnets: next.map(toSubnetInput)});
  }, [subnets, projectId, active, runSave]);
  const setSubnetClass = useCallback((subnetId: string, subnetClass: SubnetClass | null) => {
    updateSubnet(subnetId, {subnetClass});
  }, [updateSubnet]);

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
      updateSubnet,
    }),
    [subnets, data, loading, error, offline, reload, saving, saveError, setSubnetClass, updateSubnet],
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
