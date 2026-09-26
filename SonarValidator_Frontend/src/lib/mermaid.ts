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

/**
 * Mermaid 가 실패할 때 DOM 에 남기는 찌꺼기를 치웁니다.
 *
 * <h2>⚠️ 왜 필요한가</h2>
 * {@code mermaid.render()} 가 파싱에 실패하면 임시 컨테이너
 * ({@code id="dmermaid-render-N"})와 그것을 감싸는 SVG 를 <b>DOM 에 남긴 채</b>
 * 예외를 던집니다. 그러면 화면에 "Syntax error in text" 박스가 그대로 보이고,
 * 그 요소는 React 가 만든 것이 아니라서 <b>리렌더로도 사라지지 않습니다.</b>
 *
 * <p>차트가 바뀌어도 이전 실패의 흔적이 남아 "왜 아직도 오류가 있지" 를
 * 만듭니다. 그래서 실패 직후 우리가 지웁니다.
 *
 * @param id render 에 넘긴 id (mermaid 는 여기에 {@code d} 를 붙입니다)
 */
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

/**
 * mermaid 차트 소스를 SVG 문자열로 렌더링합니다.
 *
 * @param chart Mermaid 소스 (빈 문자열이면 빈 문자열을 돌려줍니다)
 * @returns SVG 문자열
 */
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
