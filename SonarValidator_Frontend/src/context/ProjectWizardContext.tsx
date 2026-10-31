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

export type SubnetClass = "Confidential" | "Sensitive" | "Open";

export interface WizardSubnet {
  id: string;
  cidr: string;
  subnetClass: SubnetClass | null;
  vlanId?: number | null;
  name: string | null;
  agentId: string | null;
  manuallyEdited: boolean;
}

export type WizardRule = ApiRule;

interface WizardSnapshot {
  subnets: WizardSubnet[];
  rules: WizardRule[];
  draft: boolean;
  draftNote: string | null;
}

interface ProjectWizardContextValue {
  subnets: WizardSubnet[];
  rules: WizardRule[];
  draft: boolean;
  draftNote: string | null;
  loading: boolean;
  error: string | null;
  offline: boolean;
  reload: () => void;
  saving: boolean;
  saveError: string | null;
  setSubnetClass: (subnetId: string, subnetClass: SubnetClass | null) => void;
  updateSubnet: (subnetId: string, patch: Partial<WizardSubnet>) => void;
}


const ProjectWizardContext = createContext<ProjectWizardContextValue | null>(null);

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

export function ProjectWizardProvider({ children }: { children: ReactNode }) {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get("project_id");

  const { pathname } = useLocation();
  const active = pathname.startsWith("/project/create");

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
