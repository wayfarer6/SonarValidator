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
      const shape =
        level === 3
          ? `{{"${node.label}"}}`
          : level === 2
            ? `(["${node.label}"])`
            : `["${node.label}"]`;
      lines.push(`    ${sanitizeMermaidId(node.id)}${shape}`);
    }
    lines.push("  end");
  }

  // 간선: 금지 조합은 굵은 빨간 화살표로 강조합니다.
  const linkStyles: string[] = [];
  let linkIndex = 0;
  for (const edge of data.edges) {
    const arrow = edge.forbidden ? "==>" : "-->";
    const label = edge.port ? `:${edge.port}` : "전체";
    lines.push(
      `  ${sanitizeMermaidId(edge.source)} ${arrow}|${label}| ${sanitizeMermaidId(edge.target)}`,
    );
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
  );
  for (const node of data.nodes) {
    const cls =
      node.level === 3
        ? "confidential"
        : node.level === 2
          ? "sensitive"
          : "open";
    lines.push(`  class ${sanitizeMermaidId(node.id)} ${cls};`);
  }
  lines.push(...linkStyles);

  return lines.join("\n");
}