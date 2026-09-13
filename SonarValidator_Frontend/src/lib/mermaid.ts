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

// mermaid 차트 소스를 SVG 문자열로 렌더링합니다.
export async function renderMermaid(chart: string): Promise<string> {
  renderSeq += 1;
  const { svg } = await mermaid.render(`mermaid-render-${renderSeq}`, chart);
  return svg;
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
