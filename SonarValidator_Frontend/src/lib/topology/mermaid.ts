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

  // 등급별 서브그래프로 묶어 존 경계를 시각화합니다.
  const byLevel = new Map<number | null, typeof data.nodes>();
  for (const node of data.nodes) {
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
      const shape =
        level === 3
          ? `{{"${label}"}}`
          : level === 2
            ? `(["${label}"])`
            : `["${label}"]`;
      lines.push(`    ${sanitizeMermaidId(node.id)}${shape}`);
    }
    lines.push("  end");
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
    const arrow = edge.forbidden ? "==>" : "-->";
    const label = edge.port ? `:${edge.port}` : "전체";
    lines.push(`  ${sourceId} ${arrow}|${label}| ${targetId}`);
    if (edge.forbidden) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#dc2626,stroke-width:3px;`,
      );
    }
    linkIndex++;
  }

  // 스타일
  lines.push(
    "  classDef confidential fill:#fee2e2,stroke:#dc2626,stroke-width:2px;",
    "  classDef sensitive fill:#f3e8ff,stroke:#9333ea,stroke-width:2px;",
    "  classDef open fill:#dcfce7,stroke:#16a34a,stroke-width:2px;",
    QUARANTINE_CLASS_DEF,
  );
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
 * 노드의 최종 class 를 정합니다.
 *
 * <p>⚠️ 격리가 등급보다 <b>우선</b>합니다. 격리된 장치는 운영자가 방금 껐다는
 * 사실이 가장 중요하고, 등급 색으로 덮이면 "아직 정상" 처럼 보입니다.
 */
function nodeClass(node: ApiTopology["nodes"][number]): string {
  return node.quarantined ? "quarantined" : levelClass(node.level);
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
  const name = includeCidr ? `${node.label}<br/>${node.cidr}` : node.label;
  return node.quarantined ? `${QUARANTINE_MARK}${name}` : name;
}

const CLASS_DEFS = [
  "  classDef confidential fill:#fee2e2,stroke:#dc2626,stroke-width:2px;",
  "  classDef sensitive fill:#f3e8ff,stroke:#9333ea,stroke-width:2px;",
  "  classDef open fill:#dcfce7,stroke:#16a34a,stroke-width:2px;",
  QUARANTINE_CLASS_DEF,
];

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
    const label = nodeLabel(node, true);
    const shape =
      node.level === 3
        ? `{{"${label}"}}`
        : node.level === 2
          ? `(["${label}"])`
          : `["${label}"]`;
    lines.push(`  ${sanitizeMermaidId(node.id)}${shape}`);
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
    const arrow = edge.forbidden ? "==>" : "-->";
    const label = edge.port ? `:${edge.port}` : "전체";
    lines.push(`  ${sourceId} ${arrow}|${label}| ${targetId}`);
    if (edge.forbidden) {
      linkStyles.push(
        `  linkStyle ${linkIndex} stroke:#dc2626,stroke-width:3px;`,
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

  // 노드 id → 등급 레벨
  const levelOf = new Map<string, number | null>();
  for (const node of data.nodes) {
    levelOf.set(node.id, node.level);
  }

  // 존별 서브넷 개수
  const countByLevel = new Map<number | null, number>();
  // 존별 격리된 서브넷 개수
  const quarantinedByLevel = new Map<number | null, number>();
  for (const node of data.nodes) {
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
        ? `${count}개 · ${QUARANTINE_MARK}${isolated}개 격리`
        : `${count}개`;
    const label = `${name}<br/>${detail}`;
    const shape = level === 3 ? `{{"${label}"}}` : `["${label}"]`;
    lines.push(`  ${id}${shape}`);
  }

  // 존 사이 간선: 같은 존 내부 연결은 접고, 존 간 연결만 남깁니다.
  const zoneEdges = new Set<string>();
  const zoneForbidden = new Set<string>();
  for (const edge of data.edges) {
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

  lines.push(...CLASS_DEFS);
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