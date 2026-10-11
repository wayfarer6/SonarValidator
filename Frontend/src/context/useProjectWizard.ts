import { createContext, useContext } from "react";
import type { ApiRule } from "../lib/api/types";

/**
 * 프로젝트 생성 마법사 컨텍스트의 값과 훅, 공용 타입입니다.
 *
 * <h2>⚠️ 왜 Provider 파일과 분리했는가</h2>
 * Fast Refresh 는 한 파일이 <b>컴포넌트만</b> export 할 때 동작합니다.
 * {@code ProjectWizardProvider} 와 {@code useProjectWizard} 를 같은 파일에 두면
 * 훅 export 때문에 핫 리로드가 전체 새로고침으로 떨어집니다.
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

export interface ProjectWizardContextValue {
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

export const ProjectWizardContext = createContext<ProjectWizardContextValue | null>(null);

/**
 * 프로젝트 위저드 컨텍스트를 사용합니다.
 *
 * @returns 위저드 상태와 동작
 * @throws ProjectWizardProvider 밖에서 호출한 경우
 */
export function useProjectWizard() {
  const ctx = useContext(ProjectWizardContext);
  if (!ctx) {
    throw new Error("useProjectWizard must be used within ProjectWizardProvider");
  }
  return ctx;
}
