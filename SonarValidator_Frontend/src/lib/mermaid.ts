import mermaid from "mermaid";

// 앱 전체에서 한 번만 초기화합니다. (startOnLoad: false — 명시적 render만 사용)
mermaid.initialize({
  startOnLoad: false,
  theme: "default",
  securityLevel: "loose",
  flowchart: {
    useMaxWidth: true,
    htmlLabels: true,
    curve: "basis",
  },
});

let renderSeq = 0;

function removeRenderArtifacts(id: string): void {
  if (typeof document === "undefined") return;

  const leaked = document.getElementById(`d${id}`);
  if (leaked) {
    leaked.remove();
    return;
  }
  // 컨테이너를 못 찾으면 id 가 붙은 SVG 라도 치웁니다.
  const svg = document.getElementById(id);
  if (svg && svg.parentElement && svg.parentElement.id.startsWith("dmermaid")) {
    svg.parentElement.remove();
  }
}

export async function renderMermaid(chart: string): Promise<string> {
  // 빈 차트는 렌더러가 구문 오류를 던집니다. 호출부의 실수이므로 조용히 비웁니다.
  if (!chart || !chart.trim()) {
    return "";
  }

  renderSeq += 1;
  const id = `mermaid-render-${renderSeq}`;
  try {
    const { svg } = await mermaid.render(id, chart);
    return svg;
  } catch (error) {
    // 실패 시 남는 DOM 찌꺼기를 치우고 그대로 다시 던집니다.
    // (호출부가 오류를 표시할 수 있어야 하므로 삼키지 않습니다)
    removeRenderArtifacts(id);
    throw error;
  }
}

// 차트를 SVG 파일로 다운로드합니다.
export async function exportMermaidSvg(chart: string, filename: string): Promise<void> {
  const svg = await renderMermaid(chart);
  const blob = new Blob([svg], { type: "image/svg+xml;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename.endsWith(".svg") ? filename : `${filename}.svg`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export default mermaid;
