import { createContext, useContext, useState, type ReactNode } from "react";

export type SubnetClass = "Confidential" | "Sensitive" | "Open";

export interface WizardSubnet {
  id: string;
  cidr: string;
  subnetClass: SubnetClass;
}

interface ProjectWizardContextValue {
  subnets: WizardSubnet[];
  setSubnetClass: (subnetId: string, subnetClass: SubnetClass) => void;
}

// TODO: 추후 백엔드/WebSocket에서 탐지된 서브넷 목록으로 교체
// const INITIAL_SUBNETS: WizardSubnet[] = [
//   { id: "Subnet-0001", cidr: "192.168.0.x/24", subnetClass: "Open" },
//   { id: "Subnet-0002", cidr: "192.168.10.x/24", subnetClass: "Sensitive" },
//   { id: "Subnet-0003", cidr: "192.168.20.x/24", subnetClass: "Sensitive" },
//   { id: "Subnet-0004", cidr: "10.0.0.x/24", subnetClass: "Confidential" },
//   { id: "Subnet-0005", cidr: "172.16.0.x/24", subnetClass: "Open" },
// ];

const ProjectWizardContext = createContext<ProjectWizardContextValue | null>(null);

export function ProjectWizardProvider({ children }: { children: ReactNode }) {
  const [subnets, setSubnets] = useState<WizardSubnet[]>(INITIAL_SUBNETS);

  const setSubnetClass = (subnetId: string, subnetClass: SubnetClass) => {
    setSubnets((prev) =>
      prev.map((subnet) => (subnet.id === subnetId ? { ...subnet, subnetClass } : subnet)),
    );
  };

  return (
    <ProjectWizardContext.Provider value={{ subnets, setSubnetClass }}>
      {children}
    </ProjectWizardContext.Provider>
  );
}

export function useProjectWizard() {
  const ctx = useContext(ProjectWizardContext);
  if (!ctx) {
    throw new Error("useProjectWizard must be used within ProjectWizardProvider");
  }
  return ctx;
}
