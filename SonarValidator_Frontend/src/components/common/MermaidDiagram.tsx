import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { renderMermaid } from "../../lib/mermaid";


const MIN_SCALE = 0.25;
const MAX_SCALE = 4;

const ZOOM_STEP = 0.25;

const FALLBACK_RATIO = 0.6;

function prepareSvg(rawSvg: string): { svg: string; ratio: number } {
  const openTagMatch = rawSvg.match(/<svg\b[^>]*>/i);
  if (!openTagMatch) {
    return { svg: rawSvg, ratio: FALLBACK_RATIO };
  }

  const openTag = openTagMatch[0];

  // viewBox 에서 본질 크기를 얻습니다. (예: "4 4 552 466.37")
  let ratio = FALLBACK_RATIO;
  const viewBoxMatch = openTag.match(/viewBox="([^"]+)"/i);
  if (viewBoxMatch) {
    const parts = viewBoxMatch[1].trim().split(/[\s,]+/).map(Number);
    if (parts.length === 4 && parts[2] > 0 && parts[3] > 0) {
      ratio = parts[3] / parts[2];
    }
  }

  const newOpenTag = openTag
    // Mermaid 의 인라인 스타일을 통째로 제거합니다.
    // (max-width 뿐 아니라 확대를 방해할 수 있는 다른 인라인 값도 함께)
    .replace(/\sstyle="[^"]*"/i, "")
    .replace(/\swidth="[^"]*"/i, "")
    .replace(/\sheight="[^"]*"/i, "")
    .replace(
      /<svg/i,
      '<svg width="100%" height="100%" preserveAspectRatio="xMidYMid meet"',
    );

  return { svg: rawSvg.replace(openTag, newOpenTag), ratio };
}

interface MermaidDiagramProps {
  chart: string;
  className?: string;
  showZoomControls?: boolean;
  minHeight?: number;
}

export default function MermaidDiagram({
  chart,
  className = "",
  showZoomControls = true,
  minHeight,
}: MermaidDiagramProps) {
  const [rawSvg, setRawSvg] = useState("");
  const [error, setError] = useState("");

  const [scale, setScale] = useState(1);

  const [baseWidth, setBaseWidth] = useState(0);

  const wrapperRef = useRef<HTMLDivElement>(null);
  const viewportRef = useRef<HTMLDivElement>(null);

  const scaleRef = useRef(1);
  scaleRef.current = scale;

  const dragState = useRef<{
    active: boolean;
    startX: number;
    startY: number;
    startScrollLeft: number;
    startScrollTop: number;
  } | null>(null);

  // 차트가 바뀌면 배율을 초기화합니다.
  // (이전 차트의 배율이 새 차트에 그대로 적용되면 혼란스럽습니다)
  useEffect(() => {
    setScale(1);
  }, [chart]);

  useEffect(() => {
    let cancelled = false;

    if (!chart) {
      setRawSvg("");
      setError("");
      return () => {
        cancelled = true;
      };
    }

    renderMermaid(chart)
      .then((rendered) => {
        if (!cancelled) {
          setRawSvg(rendered);
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

  useEffect(() => {
    const wrapper = wrapperRef.current;
    if (!wrapper) return;

    const measure = () => {
      const width = wrapper.clientWidth;
      if (width > 0) {
        setBaseWidth((previous) => (previous === width ? previous : width));
      }
    };

    measure();

    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(wrapper);
    return () => observer.disconnect();
  }, []);

  // SVG 수정과 기준 높이 계산을 렌더 시점에 확정합니다. (effect 불필요)
  const prepared = useMemo(() => prepareSvg(rawSvg), [rawSvg]);

  const baseHeight = useMemo(() => {
    if (baseWidth <= 0) return 0;
    return Math.round(baseWidth * prepared.ratio);
  }, [baseWidth, prepared.ratio]);

  const ready = baseWidth > 0 && baseHeight > 0 && rawSvg.length > 0;

  const clampScale = useCallback((value: number): number => {
    return Math.min(MAX_SCALE, Math.max(MIN_SCALE, Math.round(value * 100) / 100));
  }, []);

  const zoomIn = useCallback(() => {
    setScale((previous) => clampScale(previous + ZOOM_STEP));
  }, [clampScale]);

  const zoomOut = useCallback(() => {
    setScale((previous) => clampScale(previous - ZOOM_STEP));
  }, [clampScale]);

  const resetZoom = useCallback(() => {
    setScale(1);
    const viewport = viewportRef.current;
    if (viewport) {
      viewport.scrollLeft = 0;
      viewport.scrollTop = 0;
    }
  }, []);

  const zoomAtPoint = useCallback(
    (nextScale: number, clientX: number, clientY: number) => {
      const viewport = viewportRef.current;
      const current = scaleRef.current;
      const clamped = clampScale(nextScale);

      if (!viewport) {
        setScale(clamped);
        return;
      }

      const ratio = clamped / current;
      const rect = viewport.getBoundingClientRect();

      // 커서 아래의 콘텐츠 좌표를 유지하도록 스크롤을 보정합니다.
      const offsetX = clientX - rect.left;
      const offsetY = clientY - rect.top;
      const contentX = (viewport.scrollLeft + offsetX) * ratio;
      const contentY = (viewport.scrollTop + offsetY) * ratio;

      setScale(clamped);

      // 배율이 DOM 에 반영된 뒤 스크롤을 보정해야 범위가 맞습니다.
      requestAnimationFrame(() => {
        viewport.scrollLeft = contentX - offsetX;
        viewport.scrollTop = contentY - offsetY;
      });
    },
    [clampScale],
  );

  useEffect(() => {
    const viewport = viewportRef.current;
    if (!viewport || !showZoomControls) return;

    const onWheel = (event: WheelEvent) => {
      // Ctrl/⌘ 없이 굴린 휠은 페이지 스크롤로 남겨 둡니다.
      if (!event.ctrlKey && !event.metaKey) return;
      event.preventDefault();
      const direction = event.deltaY < 0 ? 1 : -1;
      zoomAtPoint(scaleRef.current + direction * ZOOM_STEP, event.clientX, event.clientY);
    };

    viewport.addEventListener("wheel", onWheel, { passive: false });
    return () => viewport.removeEventListener("wheel", onWheel);
  }, [showZoomControls, zoomAtPoint]);

  // 드래그 이동 (확대 상태에서만)

  const handlePointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    // 확대하지 않았으면 스크롤할 것이 없으므로 드래그를 시작하지 않습니다.
    if (scaleRef.current <= 1) return;
    const viewport = viewportRef.current;
    if (!viewport) return;

    dragState.current = {
      active: true,
      startX: event.clientX,
      startY: event.clientY,
      startScrollLeft: viewport.scrollLeft,
      startScrollTop: viewport.scrollTop,
    };
    // 포인터를 붙잡아 두면 요소 밖으로 나가도 move 이벤트를 계속 받습니다.
    event.currentTarget.setPointerCapture?.(event.pointerId);
  };

  const handlePointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    const state = dragState.current;
    const viewport = viewportRef.current;
    if (!state?.active || !viewport) return;

    viewport.scrollLeft = state.startScrollLeft - (event.clientX - state.startX);
    viewport.scrollTop = state.startScrollTop - (event.clientY - state.startY);
  };

  const endDrag = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!dragState.current?.active) return;
    dragState.current = null;
    event.currentTarget.releasePointerCapture?.(event.pointerId);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    const key = event.key;
    if (key === "+" || key === "=" || key === "Add") {
      event.preventDefault();
      zoomIn();
    } else if (key === "-" || key === "_" || key === "Subtract") {
      event.preventDefault();
      zoomOut();
    } else if (key === "0") {
      event.preventDefault();
      resetZoom();
    }
  };

  if (error) {
    return (
      <div className="p-4 text-xs text-red-600 dark:text-red-400">
        다이어그램 렌더링 실패: {error}
      </div>
    );
  }

  const percent = Math.round(scale * 100);
  const canZoomIn = scale < MAX_SCALE;
  const canZoomOut = scale > MIN_SCALE;

  return (
    <div ref={wrapperRef} className="relative flex w-full flex-col">
      {/* 툴바 */}
      {showZoomControls && (
        <div className="mb-1.5 flex items-center justify-end gap-1">
          <ZoomButton
            label="축소"
            symbol="−"
            disabled={!canZoomOut}
            onClick={zoomOut}
            title="축소 (Ctrl+휠 아래, 키보드 -)"
          />

          <button
            type="button"
            onClick={resetZoom}
            title="100% 로 되돌리기 (키보드 0)"
            className="min-w-[52px] rounded-md border border-gray-300 bg-white px-2 py-1 text-[11px] font-medium tabular-nums text-gray-700 shadow-sm transition hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
          >
            {percent}%
          </button>

          <ZoomButton
            label="확대"
            symbol="+"
            disabled={!canZoomIn}
            onClick={zoomIn}
            title="확대 (Ctrl+휠 위, 키보드 +)"
          />
        </div>
      )}

      {/* 뷰포트: 스크롤 담당 */}
      <div
        ref={viewportRef}
        tabIndex={showZoomControls ? 0 : -1}
        onKeyDown={handleKeyDown}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        style={minHeight ? { minHeight } : undefined}
        // 확대 상태에서는 grab 커서로 이동 가능함을 알립니다.
        className={`flex justify-center overflow-auto focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
          scale > 1 ? "cursor-grab active:cursor-grabbing" : ""
        } ${className}`}
        aria-label="네트워크 다이어그램"
      >
        {/* spacer: 스크롤 범위 = 기준 크기 × 배율.
            transform 은 레이아웃에 반영되지 않으므로 이 값을 명시해야
            스크롤바가 올바른 범위를 갖습니다. */}
        <div
          style={
            ready
              ? { width: baseWidth * scale, height: baseHeight * scale }
              : undefined
          }
        >
          {/* content: 실제 확대 대상 */}
          <div
            style={
              ready
                ? {
                    width: baseWidth,
                    height: baseHeight,
                    transform: `scale(${scale})`,
                    // 좌상단 기준이어야 scrollLeft/Top 계산과 좌표가 일치합니다.
                    transformOrigin: "top left",
                  }
                : { transform: "none" }
            }
            className={ready ? "" : "min-h-[60px] w-full"}
            dangerouslySetInnerHTML={{ __html: prepared.svg }}
          />
        </div>
      </div>
    </div>
  );
}

function ZoomButton({
  symbol,
  label,
  disabled,
  onClick,
  title,
}: {
  symbol: string;
  label: string;
  disabled: boolean;
  onClick: () => void;
  title: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      aria-label={label}
      className="flex h-6 w-6 items-center justify-center rounded-md border border-gray-300 bg-white text-sm font-semibold leading-none text-gray-700 shadow-sm transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
    >
      {symbol}
    </button>
  );
}
