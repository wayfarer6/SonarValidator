import type { ApiTopology } from "../api/types";


const LEVEL_LABEL: Record<number, string> = {
  3: "Confidential Zone",
  2: "Sensitive Zone",
  1: "Open Zone",
};

export function sanitizeMermaidId(id: string): string {
  return "n_" + Array.from(id, (character) => character.codePointAt(0)!.toString(16)).join("_");
}

export function topologyToMermaid(data: ApiTopology | null): string {
  if (!data) return "";
  if (data.nodes.length === 0) {
    return 'flowchart TD\n  Empty["서브넷이 없습니다"]';
  }

  const lines: string[] = ["flowchart LR"];

  // ⚠️ 인터넷은 프로젝트의 "대역" 이 아니라 경계 바깥입니다. 존 서브그래프에
  //    넣으면 "Open Zone 안에 인터넷" 처럼 보여 의미가 뒤바뀝니다.
  const internetNodes = data.nodes.filter(isInternet);
  const deviceNodes = data.nodes.filter((node) => node.kind === "device");
  const zoneNodes = data.nodes.filter((node) => !isInternet(node) && node.kind !== "device");

  // 등급별 서브그래프로 묶어 존 경계를 시각화합니다.
  const byLevel = new Map<number | null, typeof data.nodes>();
  for (const node of zoneNodes) {
    const bucket = byLevel.get(node.level);
    if (bucket) bucket.push(node);
    else byLevel.set(node.level, [node]);
  }

  // 높은 등급(폐쇄망)을 위에 배치합니다.
  const ordered = [...byLevel.entries()].sort(
    (a, b) => (b[0] ?? 0) - (a[0] ?? 0),
  );

  for (const [level, nodes] of ordered) {
    const groupId = `Zone${level ?? "unknown"}`;
    const groupLabel = level ? LEVEL_LABEL[level] ?? `Level ${level}` : "미지정";
    lines.push(`  subgraph ${groupId}["${groupLabel}"]`);
    for (const node of nodes) {
      // 등급별로 노드 모양을 다르게 해 색 없이도 구분되게 합니다.
      const label = nodeLabel(node, false);
      lines.push(`    ${sanitizeMermaidId(node.id)}${nodeShape(node, label)}`);
    }
    lines.push("  end");
    if (level === null) lines.push(`  style ${groupId} fill:#f8fafc,stroke:#cbd5e1,color:#475569;`);
  }

  // 인터넷은 존 밖에 원형(지구본)으로 둡니다.
  for (const node of internetNodes) {
    lines.push(`  ${sanitizeMermaidId(node.id)}${nodeShape(node, nodeLabel(node, false))}`);
  }

  for (const node of deviceNodes) {
    lines.push(`  ${sanitizeMermaidId(node.id)}${nodeShape(node, nodeLabel(node, false))}`);
  }

  const nodeIds = new Set(data.nodes.map((node) => sanitizeMermaidId(node.id)));
  const linkStyles: string[] = [];
  let linkIndex = 0;
  for (const edge of data.edges) {
    const sourceId = sanitizeMermaidId(edge.source);
    const targetId = sanitizeMermaidId(edge.target);
    if (!nodeIds.has(sourceId) || !nodeIds.has(targetId)) {
      // 참조가 깨진 간선은 건너뜁니다 — 떠 있는 노드를 만들지 않습니다.
      continue;
    }
    const observed = edge.observed === true || edge.origin === "DISCOVERED";
    const arrow = edge.internet
      ? "-.->"
      : observed
        ? "---"
        : edge.forbidden
          ? "==>"
          : "-->";
    const label = edge.blocked ? "차단 확인" : edge.layer === "L2" ? `VLAN ${edge.vlan_id ?? ""}` : edge.internet
      ? "인터넷"
      : observed
        ? "라우팅"
        : edge.port
          ? `:${edge.port}`
          : "전체";
    lines.push(`  ${sourceId} ${arrow}|${label}| ${targetId}`);
    if (edge.blocked) {
      linkStyles.push(`  linkStyle ${linkIndex} stroke:#991b1b,stroke-width:3px,stroke-dasharray:2 4;`);
    } else if (edge.forbidden) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#dc2626,stroke-width:3px;`,
      );
    } else if (edge.internet) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#2563eb,stroke-width:2px,stroke-dasharray:5 4;`,
      );
    } else if (observed) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#64748b,stroke-width:2px,stroke-dasharray:5 4;`,
      );
    }
    linkIndex++;
  }

  // 스타일
  lines.push(...CLASS_DEFS);
  for (const node of data.nodes) {
    lines.push(`  class ${sanitizeMermaidId(node.id)} ${nodeClass(node)};`);
  }
  lines.push(...linkStyles);

  return lines.join("\n");
}

function levelClass(level: number | null): string {
  return level === null ? "unclassified" : level === 3 ? "confidential" : level === 2 ? "sensitive" : "open";
}

const QUARANTINE_CLASS_DEF =
  "  classDef quarantined fill:#fecaca,stroke:#991b1b,stroke-width:3px,stroke-dasharray:5 3,color:#7f1d1d;";

const INTERNET_CLASS_DEF =
  "  classDef internet fill:#dbeafe,stroke:#2563eb,stroke-width:3px,color:#1e3a8a;";

function isInternet(node: ApiTopology["nodes"][number]): boolean {
  return node.kind === "internet";
}

function nodeShape(node: ApiTopology["nodes"][number], label: string): string {
  if (isInternet(node)) {
    return `((("${label}")))`;
  }
  return node.level === 3
    ? `{{"${label}"}}`
    : node.level === 2
      ? `(["${label}"])`
      : `["${label}"]`;
}

function nodeClass(node: ApiTopology["nodes"][number]): string {
  if (node.quarantine_status === "FAILED") return "failed";
  if (node.quarantine_status === "PENDING") return "pending";
  if (node.quarantined) return "quarantined";
  if (node.observed_up === false) return "down";
  if (node.config_stale || node.connected === false) return "stale";
  if (isInternet(node)) return "internet";
  return node.kind === "device" ? "device" : levelClass(node.level);
}


function nodeLabel(
  node: ApiTopology["nodes"][number],
  includeCidr: boolean,
): string {
  // 인터넷은 대역이 없으므로 CIDR 줄을 만들지 않습니다.
  const escapeLabel = (value: string) => value.replace(/&/g, "&amp;").replace(/"/g, "#quot;")
    .replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/[\r\n]/g, " ");
  const name = includeCidr && node.cidr
    ? `${escapeLabel(node.label)}<br/>${escapeLabel(node.cidr)}` : escapeLabel(node.label);
  if (node.quarantine_status === "FAILED") return `${name}<br/>적용 실패`;
  if (node.quarantine_status === "PENDING") return `${name}<br/>${node.quarantine_requested ? "격리" : "해제"} 확인 대기`;
  if (node.quarantined) return `${name}<br/>격리 적용 확인`;
  if (node.config_stale || node.connected === false) return `${name}<br/>수집 대기 · 이전 상태`;
  return node.observed_up === false ? `${name}<br/>관측 연결 없음` : name;
}

const CLASS_DEFS = [
  "  classDef confidential fill:#fee2e2,stroke:#dc2626,stroke-width:2px;",
  "  classDef sensitive fill:#f3e8ff,stroke:#9333ea,stroke-width:2px;",
  "  classDef open fill:#dcfce7,stroke:#16a34a,stroke-width:2px;",
  "  classDef pending fill:#fef3c7,stroke:#d97706,stroke-dasharray:5 3;",
  "  classDef failed fill:#fff7ed,stroke:#ea580c,stroke-width:3px;",
  "  classDef down fill:#f1f5f9,stroke:#94a3b8,stroke-dasharray:3 3,color:#64748b;",
  "  classDef stale fill:#f8fafc,stroke:#94a3b8,stroke-dasharray:5 3,color:#64748b;",
  "  classDef device fill:#e2e8f0,stroke:#475569,stroke-width:2px;",
  "  classDef unclassified fill:#f8fafc,stroke:#64748b,stroke-width:2px;",
  QUARANTINE_CLASS_DEF,
  INTERNET_CLASS_DEF,
];

export function topologyToDetailedMermaid(data: ApiTopology | null): string {
  if (!data) return "";
  if (data.nodes.length === 0) {
    return 'flowchart TD\n  Empty["서브넷이 없습니다"]';
  }

  const lines: string[] = ["flowchart LR"];
  for (const node of data.nodes) {
    // CIDR 을 라벨에 넣고, Mermaid 의 <br/> 로 두 줄로 나눕니다.
    // (인터넷은 대역이 없어 nodeLabel 이 이름만 씁니다)
    const label = nodeLabel(node, true);
    lines.push(`  ${sanitizeMermaidId(node.id)}${nodeShape(node, label)}`);
  }

  const linkStyles: string[] = [];
  let linkIndex = 0;
  // 기본 뷰와 같은 이유로 노드에 없는 간선은 건너뜁니다.
  const detailedNodeIds = new Set(data.nodes.map((node) => sanitizeMermaidId(node.id)));
  for (const edge of data.edges) {
    const sourceId = sanitizeMermaidId(edge.source);
    const targetId = sanitizeMermaidId(edge.target);
    if (!detailedNodeIds.has(sourceId) || !detailedNodeIds.has(targetId)) {
      continue;
    }
    // 상세 뷰도 기본 뷰와 같은 규칙으로 관측 간선을 점선으로 그립니다.
    const observed = edge.observed === true || edge.origin === "DISCOVERED";
    const arrow = edge.internet
      ? "-.->"
      : observed
        ? "---"
        : edge.forbidden
          ? "==>"
          : "-->";
    const label = edge.blocked ? "차단 확인" : edge.layer === "L2" ? `VLAN ${edge.vlan_id ?? ""}` : edge.internet
      ? "인터넷"
      : observed
        ? "라우팅"
        : edge.port
          ? `:${edge.port}`
          : "전체";
    lines.push(`  ${sourceId} ${arrow}|${label}| ${targetId}`);
    if (edge.blocked) {
      linkStyles.push(`  linkStyle ${linkIndex} stroke:#991b1b,stroke-width:3px,stroke-dasharray:2 4;`);
    } else if (edge.forbidden) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#dc2626,stroke-width:3px;`,
      );
    } else if (edge.internet) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#2563eb,stroke-width:2px,stroke-dasharray:5 4;`,
      );
    } else if (observed) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#64748b,stroke-width:2px,stroke-dasharray:5 4;`,
      );
    }
    linkIndex++;
  }

  lines.push(...CLASS_DEFS);
  for (const node of data.nodes) {
    lines.push(`  class ${sanitizeMermaidId(node.id)} ${nodeClass(node)};`);
  }
  lines.push(...linkStyles);

  return lines.join("\n");
}

export function topologyToZoneMermaid(data: ApiTopology | null): string {
  if (!data) return "";
  if (data.nodes.length === 0) {
    return 'flowchart TD\n  Empty["서브넷이 없습니다"]';
  }




  const internetNode = data.nodes.find(isInternet);
  const zoneSource = data.nodes.filter((node) => !isInternet(node));

  // 노드 id → 등급 레벨 (존 간선 유도용 — 인터넷은 별도 처리)
  const levelOf = new Map<string, number | null>();
  for (const node of zoneSource) {
    levelOf.set(node.id, node.level);
  }

  // 존별 서브넷 개수
  const countByLevel = new Map<number | null, number>();
  // 존별 격리된 서브넷 개수
  const quarantinedByLevel = new Map<number | null, number>();
  for (const node of zoneSource) {
    countByLevel.set(node.level, (countByLevel.get(node.level) ?? 0) + 1);
    if (node.quarantined) {
      quarantinedByLevel.set(
        node.level,
        (quarantinedByLevel.get(node.level) ?? 0) + 1,
      );
    }
  }

  const lines: string[] = ["flowchart TD"];
  for (const [level, count] of [...countByLevel.entries()].sort(
    (a, b) => (b[0] ?? 0) - (a[0] ?? 0),
  )) {
    const id = zoneNodeId(level);
    const name = level ? LEVEL_LABEL[level] ?? `Level ${level}` : "미지정";
    const isolated = quarantinedByLevel.get(level) ?? 0;
    // 존 안에 꺼진 장치가 있으면 개수를 함께 보여줍니다.
    // "이 존에 격리된 서브넷이 2개" 는 존 단위 그림에서 놓치기 쉬운 정보입니다.
    const detail =
      isolated > 0
        ? `${count}개 · ${isolated}개 격리 확인`
        : `${count}개`;
    const label = `${name}<br/>${detail}`;
    const shape = level === 3 ? `{{"${label}"}}` : `["${label}"]`;
    lines.push(`  ${id}${shape}`);
  }

  // 인터넷은 존 밖에 원형(지구본)으로 둡니다.
  if (internetNode) {
    lines.push(
      `  ${sanitizeMermaidId(internetNode.id)}((("${internetNode.label}")))`,
    );
  }

  // 존 사이 간선: 같은 존 내부 연결은 접고, 존 간 연결만 남깁니다.
  // 인터넷 간선은 존으로 접지 않고 "존 → 인터넷" 으로 따로 그립니다.
  const zoneEdges = new Set<string>();
  const zoneForbidden = new Set<string>();
  const zoneBlocked = new Set<string>();
  const zoneUnblocked = new Set<string>();
  const internetFromZones = new Set<string>();
  const internetForbidden = new Set<string>();
  const internetBlocked = new Set<string>();
  const internetUnblocked = new Set<string>();
  for (const edge of data.edges) {
    if (edge.internet) {
      // 노출된 서브넷의 존을 찾아 그 존에서 인터넷으로 잇습니다.
      const zone = levelOf.get(edge.source);
      if (zone === undefined) continue;
      internetFromZones.add(String(zone));
      (edge.blocked ? internetBlocked : internetUnblocked).add(String(zone));
      if (edge.forbidden) internetForbidden.add(String(zone));
      continue;
    }
    const src = levelOf.get(edge.source);
    const dst = levelOf.get(edge.target);
    if (src === undefined || dst === undefined) continue;
    if (src === dst) continue;
    const key = `${src}->${dst}`;
    zoneEdges.add(key);
    (edge.blocked ? zoneBlocked : zoneUnblocked).add(key);
    if (edge.forbidden) zoneForbidden.add(key);
  }

  const linkStyles: string[] = [];
  let linkIndex = 0;
  for (const key of zoneEdges) {
    const [src, dst] = key.split("->");
    const forbidden = zoneForbidden.has(key);
    const arrow = forbidden ? "==>" : "-->";
    const state = zoneBlocked.has(key) ? (zoneUnblocked.has(key) ? "일부 차단" : "차단 확인") : "구성 연결";
    lines.push(
      `  ${zoneNodeId(toLevel(src))} ${arrow}|${forbidden ? "등급 건너뜀 · " : ""}${state}| ${zoneNodeId(toLevel(dst))}`,
    );
    if (forbidden) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#dc2626,stroke-width:3px;`,
      );
    }
    linkIndex++;
  }

  // 존 → 인터넷 간선 (점선 화살표). 기밀망이 인터넷으로 나가면 빨간색입니다.
  if (internetNode) {
    for (const zone of internetFromZones) {
      const forbidden = internetForbidden.has(zone);
      lines.push(
        `  ${zoneNodeId(toLevel(zone))} -.->|${internetBlocked.has(zone) ? (internetUnblocked.has(zone) ? "일부 차단" : "차단 확인") : forbidden ? "인터넷 노출" : "인터넷"}| ${sanitizeMermaidId(internetNode.id)}`,
      );
      if (forbidden) {
        linkStyles.push(
          `  linkStyle ${linkIndex} stroke:#dc2626,stroke-width:3px;`,
        );
      } else {
        linkStyles.push(
          `  linkStyle ${linkIndex} stroke:#2563eb,stroke-width:2px,stroke-dasharray:5 4;`,
        );
      }
      linkIndex++;
    }
  }

  lines.push(...CLASS_DEFS);
  for (const level of countByLevel.keys()) {



    const isolated = (quarantinedByLevel.get(level) ?? 0) > 0;
    lines.push(
      `  class ${zoneNodeId(level)} ${isolated ? "quarantined" : levelClass(level)};`,
    );
  }
  lines.push(...linkStyles);

  return lines.join("\n");
}

function zoneNodeId(level: number | null): string {
  return level === null ? "ZoneUnknown" : `Zone${level}`;
}

function toLevel(value: string): number | null {
  if (value === "undefined" || value === "unknown" || value === "null") {
    return null;
  }
  const parsed = Number(value);
  return Number.isNaN(parsed) ? null : parsed;
}
