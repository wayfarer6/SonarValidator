import MermaidDiagram from "../components/common/MermaidDiagram";

// CSO(Confidential, Sensitive, Open) 등급별 라우팅 및 서브넷 구조 정의
const mermaidChart = `
    flowchart LR
        subgraph Open [Open Network Zone]
            H1[Host H1<br/>192.168.1.10] --> SW1[Switch SW1<br/>192.168.1.0/24]
        end

        SW1 --> R1{Router R1}

        subgraph Sensitive [Sensitive Network Zone]
            R1 -->|192.168.5.0/24| R2[Router R2]
            R2 --> SW2[Switch SW2<br/>192.168.2.0/24] --> H2[Host H2]
        end

        subgraph Confidential [Confidential Network Zone]
            R1 -->|192.168.6.0/24| R3[Router R3]
            R3 --> SW3[Switch SW3<br/>192.168.3.0/24] --> H3[Host H3]
        end

        subgraph Restricted [Restricted / Internal Zone]
            R1 -->|192.168.7.0/24| R4[Router R4]
            R4 --> SW4[Switch SW4<br/>192.168.4.0/24] --> H4[Host H4]
        end

        classDef open fill:#e3f2fd,stroke:#1565c0,stroke-width:2px;
        classDef sensitive fill:#fff3e0,stroke:#ef6c00,stroke-width:2px;
        classDef confidential fill:#ffebee,stroke:#c62828,stroke-width:2px;
        
        class H1,SW1 open;
        class H2,SW2,R2 sensitive;
        class H3,SW3,R3 confidential;
`;

export default function NetworkTopologyMermaid() {
  return (
    <MermaidDiagram
      chart={mermaidChart}
      className="rounded-xl bg-white py-4 dark:bg-gray-900"
    />
  );
}