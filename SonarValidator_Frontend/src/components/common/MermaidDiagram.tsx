import { useEffect, useState } from "react";
import { renderMermaid } from "../../lib/mermaid";

interface MermaidDiagramProps {
  chart: string;
  className?: string;
}

// mermaid 차트 소스를 받아 SVG로 렌더링하는 공통 컴포넌트
export default function MermaidDiagram({ chart, className = "" }: MermaidDiagramProps) {
  const [svg, setSvg] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    renderMermaid(chart)
      .then((rendered) => {
        if (!cancelled) {
          setSvg(rendered);
          setError("");
        }
      })
      .catch((err) => {
        if (!cancelled) setError(String(err));
      });
    return () => {
      cancelled = true;
    };
  }, [chart]);

  if (error) {
    return (
      <div className="p-4 text-xs text-red-600 dark:text-red-400">
        다이어그램 렌더링 실패: {error}
      </div>
    );
  }

  return (
    <div
      className={`flex justify-center overflow-x-auto ${className}`}
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}
