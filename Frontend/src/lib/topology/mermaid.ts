import type { ApiTopology } from "../api/types";

/**
 * 백엔드 토폴로지({@link ApiTopology})를 Mermaid flowchart 소스로 바꿉니다.
 *
 * <h2>왜 별도 모듈인가</h2>
 * 같은 변환 로직이 Network Management 화면과 대시보드의 Latest Topology 카드에
 * 모두 필요합니다. 화면마다 복사해 두면 등급 색/모양 규칙이 조금씩 어긋나
 * "같은 데이터인데 다른 그림" 이 됩니다. 그래서 한 곳에 둡니다.
 *
 * <h2>노드 모양으로 등급을 구분하는 이유</h2>
 * 색만 다르면 흑백 인쇄나 색각 이상에서 구분이 어렵습니다. 등급별로
 * <b>모양</b>까지 다르게 해서 색 없이도 읽히게 합니다.
 */

/** 등급 레벨 → 존 이름. 서버 legend 와 같은 순서를 씁니다. */
const LEVEL_LABEL: Record<number, string> = {
  3: "Confidential Zone",
  2: "Sensitive Zone",
  1: "Open Zone",
};

/**
 * Mermaid 식별자로 안전한 문자열로 바꿉니다.
 *
 * <p>하이픈/점이 들어간 id(예: {@code 10.10.131.0-24})는 Mermaid 가
 * 노드 경계로 해석해 파싱 오류를 냅니다. 영숫자와 밑줄만 남깁니다.
 */
export function sanitizeMermaidId(id: string): string {
  return id.replace(/[^A-Za-z0-9_]/g, "_");
}

/**
 * 토폴로지를 Mermaid 소스로 변환합니다.
 *
 * @param data 토폴로지 (null 이면 빈 문자열)
 * @returns Mermaid flowchart 소스
 */
export function topologyToMermaid(data: ApiTopology | null): string {
  if (!data) return "";
  if (data.nodes.length === 0) {
    return 'flowchart TD\n  Empty["서브넷이 없습니다"]';
  }

  const lines: string[] = ["flowchart TD"];

  // ⚠️ 인터넷은 프로젝트의 "대역" 이 아니라 경계 바깥입니다. 존 서브그래프에
  //    넣으면 "Open Zone 안에 인터넷" 처럼 보여 의미가 뒤바뀝니다.
  const internetNodes = data.nodes.filter(isInternet);
  // VLAN은 편집 여부와 관계없이 논리 네트워크입니다. vlan_id가 없는 자동 수집
  // 인터페이스 대역과 라우팅 장비만 Physical View에 둡니다.
  const physicalNodes = data.nodes.filter(isPhysicalSource);
  const zoneNodes = data.nodes.filter(
    (node) => !isInternet(node) && !isPhysicalSource(node),
  );

  // 실제 장비와 VLAN이 아닌 인터페이스 대역입니다.
  if (physicalNodes.length > 0) {
    lines.push('  subgraph PhysicalView["Physical View"]');
    lines.push("    direction LR");
    for (const node of physicalNodes) {
      lines.push(
        `    ${sanitizeMermaidId(node.id)}${nodeShape(node, nodeLabel(node, true))}`,
      );
    }
    lines.push("  end");
  }

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

  if (ordered.length > 0) {
    lines.push('  subgraph LogicalView["Logical View"]');
    lines.push("    direction TD");
    for (const [level, nodes] of ordered) {
      const groupId = `Zone${level ?? "unknown"}`;
      const groupLabel = level ? LEVEL_LABEL[level] ?? `Level ${level}` : "미지정";
      lines.push(`    subgraph ${groupId}["${groupLabel}"]`);
      for (const node of nodes) {
        // 등급별로 노드 모양을 다르게 해 색 없이도 구분되게 합니다.
        const label = nodeLabel(node, false);
        lines.push(`      ${sanitizeMermaidId(node.id)}${nodeShape(node, label)}`);
      }
      lines.push("    end");
    }
    lines.push("  end");
  }

  // 인터넷은 존 밖에 원형(지구본)으로 둡니다.
  for (const node of internetNodes) {
    lines.push(`  ${sanitizeMermaidId(node.id)}${nodeShape(node, nodeLabel(node, false))}`);
  }

  // 간선: 금지 조합은 굵은 빨간 화살표로 강조합니다.
  //
  // ⚠️ 노드에 없는 간선은 **그리지 않습니다.**
  //    Mermaid 는 선언되지 않은 식별자를 만나면 **노드를 자동 생성**합니다.
  //    그래서 예전에 서버가 간선에 CIDR 을 넣었을 때 `10_0_8_0_24` 가
  //    서브넷과 무관한 노드로 떠 있었습니다.
  //    서버를 고쳤지만, 같은 유형의 어긋남이 다시 생겨도 **그림이 깨지지 않게** 합니다.
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
    // 관측(라우팅) 간선은 "정책" 이 아니라 사실이므로 점선으로 그립니다.
    // 정책 규칙과 같은 화살표 모양으로 그리면 "이게 허용 규칙인가 실제 연결인가"
    // 를 구분할 수 없습니다.
    //
    // 인터넷 간선은 방향이 있습니다(안에서 밖으로 나감) → 점선 화살표.
    // 대역 간 라우팅은 양방향입니다 → 점선 무방향.
    const observed = edge.observed === true || edge.origin === "DISCOVERED";
    const { arrow, label } = edgePresentation(edge);
    lines.push(edgeLine(sourceId, arrow, label, targetId));
    if (edge.forbidden) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#dc2626,stroke-width:3px;`,
      );
    } else if (edge.internet) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#2563eb,stroke-width:2px,stroke-dasharray:5 4;`,
      );
    } else if (edge.edge_type === "physical_link" || edge.edge_type === "vlan_membership"
        || edge.edge_type === "attachment") {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#475569,stroke-width:2px;`,
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
  if (physicalNodes.length > 0) {
    lines.push(
      "  style PhysicalView fill:#f8fafc,stroke:#64748b,stroke-width:1px,stroke-dasharray:4 4;",
    );
  }
  if (ordered.length > 0) {
    lines.push(
      "  style LogicalView fill:transparent,stroke:#94a3b8,stroke-width:1px,stroke-dasharray:4 4;",
    );
  }
  for (const node of data.nodes) {
    lines.push(`  class ${sanitizeMermaidId(node.id)} ${nodeClass(node)};`);
  }
  lines.push(...linkStyles);

  return lines.join("\n");
}

/** 등급 레벨 → Mermaid class 이름. */
function levelClass(level: number | null): string {
  return level === 3 ? "confidential" : level === 2 ? "sensitive" : "open";
}

/**
 * 격리된 노드의 스타일입니다.
 *
 * <p>점선 + 굵은 빨강으로 그립니다. 등급 색과 다른 <b>점선</b>을 쓰는 이유는,
 * 색각 이상이나 흑백 인쇄에서도 "이 장치는 꺼졌다" 가 구분되어야 하기
 * 때문입니다.
 */
const QUARANTINE_CLASS_DEF =
  "  classDef quarantined fill:#fecaca,stroke:#991b1b,stroke-width:3px,stroke-dasharray:5 3,color:#7f1d1d;";

/**
 * 인터넷 노드의 스타일입니다.
 *
 * <p>공개망(초록)과도 구분되어야 합니다 — 인터넷은 "프로젝트의 한 대역" 이 아니라
 * <b>경계 밖</b>이라는 뜻이기 때문입니다. 그래서 파란 계열 + 원형(지구본)을 씁니다.
 */
const INTERNET_CLASS_DEF =
  "  classDef internet fill:#dbeafe,stroke:#2563eb,stroke-width:3px,color:#1e3a8a;";

/** 자동 수집됐지만 운영자가 아직 논리 등급을 확정하지 않은 노드 스타일입니다. */
const PHYSICAL_CLASS_DEF =
  "  classDef physical fill:#f1f5f9,stroke:#64748b,stroke-width:2px,color:#334155;";

/** 기본 경로에서 식별된 라우터/방화벽 장비 스타일입니다. */
const DEVICE_CLASS_DEF =
  "  classDef device fill:#e0f2fe,stroke:#0369a1,stroke-width:2px,color:#0c4a6e;";

/** 노드가 인터넷(외부) 노드인지 확인합니다. */
function isInternet(node: ApiTopology["nodes"][number]): boolean {
  return node.kind === "internet";
}

function isDevice(node: ApiTopology["nodes"][number]): boolean {
  return node.kind === "device";
}

/**
 * Physical View에 들어갈 장비/인터페이스 출처인지 확인합니다.
 *
 * VLAN은 자동 수집 직후({@code manually_edited=false})라도 논리 네트워크이므로
 * 반드시 Logical View에 둡니다. VLAN ID가 없는 자동 수집 대역만 물리 출처입니다.
 */
function isPhysicalSource(node: ApiTopology["nodes"][number]): boolean {
  if (isDevice(node)) return true;
  return (
    !isInternet(node) &&
    node.vlan_id == null &&
    node.manually_edited !== true
  );
}

/**
 * 노드 도형을 만듭니다.
 *
 * <p>인터넷은 <b>이중 원({@code ((()))})</b> 으로 그립니다. 지구본처럼 보이면서,
 * 색을 못 보는 환경에서도 다른 서브넷(사각형/육각형/스타디움)과 구분됩니다.
 *
 * @param node  토폴로지 노드
 * @param label 노드 라벨
 * @returns Mermaid 도형 조각 (예: {@code (("Internet"))})
 */
function nodeShape(node: ApiTopology["nodes"][number], label: string): string {
  if (isInternet(node)) {
    return `((("${label}")))`;
  }
  if (isDevice(node)) {
    return `[["${label}"]]`;
  }
  return node.level === 3
    ? `{{"${label}"}}`
    : node.level === 2
      ? `(["${label}"])`
      : `["${label}"]`;
}

/**
 * 노드의 최종 class 를 정합니다.
 *
 * <p>⚠️ 격리가 등급보다 <b>우선</b>합니다. 격리된 장치는 운영자가 방금 껐다는
 * 사실이 가장 중요하고, 등급 색으로 덮이면 "아직 정상" 처럼 보입니다.
 * 인터넷 노드는 서브넷이 아니므로 등급 대신 전용 색을 씁니다.
 */
function nodeClass(node: ApiTopology["nodes"][number]): string {
  if (node.quarantined) return "quarantined";
  if (isInternet(node)) return "internet";
  if (isDevice(node)) return "device";
  if (isPhysicalSource(node)) return "physical";
  return levelClass(node.level);
}

/** 격리된 노드의 라벨 접두사입니다. */
const QUARANTINE_MARK = "🛑 ";

/**
 * 노드 라벨을 만듭니다. (격리 시 표식 추가)
 *
 * @param node 토폴로지 노드
 * @param includeCidr CIDR 을 함께 표시할지
 * @returns Mermaid 라벨 문자열
 */
function nodeLabel(
  node: ApiTopology["nodes"][number],
  includeCidr: boolean,
): string {
  // 인터넷은 대역이 없으므로 CIDR 줄을 만들지 않습니다.
  const name =
    includeCidr && node.cidr ? `${node.label}<br/>${node.cidr}` : node.label;
  const reachable = node.internet_reachable ? `${name}<br/>🌐 Internet 연결` : name;
  return node.quarantined ? `${QUARANTINE_MARK}${reachable}` : reachable;
}

const CLASS_DEFS = [
  "  classDef confidential fill:#fee2e2,stroke:#dc2626,stroke-width:2px;",
  "  classDef sensitive fill:#f3e8ff,stroke:#9333ea,stroke-width:2px;",
  "  classDef open fill:#dcfce7,stroke:#16a34a,stroke-width:2px;",
  PHYSICAL_CLASS_DEF,
  DEVICE_CLASS_DEF,
  QUARANTINE_CLASS_DEF,
  INTERNET_CLASS_DEF,
];

/** 간선의 의미에 맞는 방향과 짧은 라벨을 반환합니다. */
function edgePresentation(edge: ApiTopology["edges"][number]): {
  arrow: string;
  label: string;
} {
  if (edge.edge_type === "attachment" || edge.edge_type === "vlan_membership"
      || edge.edge_type === "physical_link") {
    return { arrow: "---", label: "" };
  }
  if (edge.edge_type === "default_route") {
    return {
      arrow: "-.->",
      label: edge.next_hop ? `기본 경로 ${edge.next_hop}` : "기본 경로",
    };
  }
  if (edge.internet || edge.edge_type === "internet_egress") {
    return {
      arrow: "-.->",
      label: edge.next_hop ? `외부 GW ${edge.next_hop}` : "인터넷",
    };
  }
  const observed = edge.observed === true || edge.origin === "DISCOVERED";
  if (observed) {
    return { arrow: "---", label: "라우팅" };
  }
  if (edge.forbidden) {
    return { arrow: "==>", label: edge.port ? `:${edge.port}` : "전체" };
  }
  return { arrow: "-->", label: edge.port ? `:${edge.port}` : "전체" };
}

/** 라벨이 없는 연결선에 빈 Mermaid 라벨 상자를 만들지 않습니다. */
function edgeLine(source: string, arrow: string, label: string, target: string): string {
  return label
    ? `  ${source} ${arrow}|${label}| ${target}`
    : `  ${source} ${arrow} ${target}`;
}

/**
 * 상세 뷰: 서브넷 CIDR 까지 라벨에 포함해 그립니다.
 *
 * <p>기본 {@link topologyToMermaid} 는 서브넷 이름만 보여줍니다. 실제 주소를
 * 확인해야 하는 검토 단계에서는 CIDR 이 필요합니다.
 */
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
    const { arrow, label } = edgePresentation(edge);
    lines.push(edgeLine(sourceId, arrow, label, targetId));
    if (edge.forbidden) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#dc2626,stroke-width:3px;`,
      );
    } else if (edge.internet) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#2563eb,stroke-width:2px,stroke-dasharray:5 4;`,
      );
    } else if (edge.edge_type === "physical_link" || edge.edge_type === "vlan_membership"
        || edge.edge_type === "attachment") {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#475569,stroke-width:2px;`,
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

/**
 * 논리 뷰: 등급 존(Zone) 단위로 접어서 그립니다.
 *
 * <p>서브넷이 수십 개면 전체 그림을 읽기 어렵습니다. 검토/보고 단계에서는
 * "어느 존이 어느 존과 직접 연결되는가" 만 보이면 충분합니다. 존 사이 간선은
 * 실제 규칙에서 유도하므로 서버 데이터가 바뀌면 이 그림도 함께 바뀝니다.
 */
export function topologyToZoneMermaid(data: ApiTopology | null): string {
  if (!data) return "";
  if (data.nodes.length === 0) {
    return 'flowchart TD\n  Empty["서브넷이 없습니다"]';
  }

  // ⚠️ 인터넷은 프로젝트의 한 존이 아닙니다. 존 개수에 넣으면
  //    "Open Zone 4개(인터넷 포함)" 처럼 실제 대역 수가 틀어집니다.
  //    id/라벨은 응답의 노드에서 가져옵니다 — 하드코딩하면 서버가 바뀔 때 깨집니다.
  const internetNode = data.nodes.find(isInternet);
  // 자동 수집 기본값(Open)은 논리 Open Zone 확정이 아닙니다. 운영자가 등급을
  // 확인한 노드만 존 단위 Logical View 집계에 포함합니다.
  const zoneSource = data.nodes.filter(
    (node) => !isInternet(node) && !isPhysicalSource(node),
  );

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

  const lines: string[] = [
    "flowchart TD",
    // 등급 존을 장비 배선도가 아닌 논리 뷰로 명시합니다.
    '  subgraph LogicalView["Logical View"]',
    "    direction TD",
  ];
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
        ? `${count}개 · ${QUARANTINE_MARK}${isolated}개 격리`
        : `${count}개`;
    const label = `${name}<br/>${detail}`;
    const shape = level === 3 ? `{{"${label}"}}` : `["${label}"]`;
    lines.push(`    ${id}${shape}`);
  }
  lines.push("  end");

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
  const internetFromZones = new Set<string>();
  const internetForbidden = new Set<string>();
  for (const node of zoneSource) {
    if (node.internet_reachable) {
      internetFromZones.add(String(node.level));
      if (node.level === 3) internetForbidden.add(String(node.level));
    }
  }
  for (const edge of data.edges) {
    if (edge.internet) {
      // 노출된 서브넷의 존을 찾아 그 존에서 인터넷으로 잇습니다.
      const zone = levelOf.get(edge.source);
      if (zone === undefined) continue;
      internetFromZones.add(String(zone));
      if (edge.forbidden) internetForbidden.add(String(zone));
      continue;
    }
    const src = levelOf.get(edge.source);
    const dst = levelOf.get(edge.target);
    if (src === undefined || dst === undefined) continue;
    if (src === dst) continue;
    const key = `${src}->${dst}`;
    zoneEdges.add(key);
    if (edge.forbidden) zoneForbidden.add(key);
  }

  const linkStyles: string[] = [];
  let linkIndex = 0;
  for (const key of zoneEdges) {
    const [src, dst] = key.split("->");
    const forbidden = zoneForbidden.has(key);
    const arrow = forbidden ? "==>" : "-->";
    lines.push(
      `  ${zoneNodeId(toLevel(src))} ${arrow}|${forbidden ? "등급 건너뜀" : "허용"}| ${zoneNodeId(toLevel(dst))}`,
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
        `  ${zoneNodeId(toLevel(zone))} -.->|${forbidden ? "인터넷 노출" : "인터넷"}| ${sanitizeMermaidId(internetNode.id)}`,
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
  lines.push(
    "  style LogicalView fill:transparent,stroke:#94a3b8,stroke-width:1px,stroke-dasharray:4 4;",
  );
  for (const level of countByLevel.keys()) {
    // 존 안에 격리된 서브넷이 하나라도 있으면 그 존을 격리 색으로 칠합니다.
    // 어느 서브넷인지는 상세 뷰에서 확인합니다 — 존 뷰는 "어디를 봐야 하나"
    // 를 알려주는 역할입니다.
    const isolated = (quarantinedByLevel.get(level) ?? 0) > 0;
    lines.push(
      `  class ${zoneNodeId(level)} ${isolated ? "quarantined" : levelClass(level)};`,
    );
  }
  lines.push(...linkStyles);

  return lines.join("\n");
}

/** 존 노드 id. 레벨이 null 이어도 안전한 문자열을 만듭니다. */
function zoneNodeId(level: number | null): string {
  return level === null ? "ZoneUnknown" : `Zone${level}`;
}

/** 문자열 레벨을 숫자로 되돌립니다. (null 표기는 "unknown") */
function toLevel(value: string): number | null {
  if (value === "undefined" || value === "unknown" || value === "null") {
    return null;
  }
  const parsed = Number(value);
  return Number.isNaN(parsed) ? null : parsed;
}
