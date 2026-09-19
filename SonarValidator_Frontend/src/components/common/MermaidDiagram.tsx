import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { renderMermaid } from "../../lib/mermaid";

/**
 * Mermaid 차트를 SVG 로 렌더링하고 **확대/축소 컨트롤**을 제공하는 공통 컴포넌트입니다.
 *
 * <h2>왜 확대/축소가 필요한가</h2>
 * 토폴로지는 프로젝트가 커질수록 노드가 늘어나 글자가 읽을 수 없을 만큼
 * 작아집니다. 반대로 노드가 3~4개뿐일 때는 화면이 휑하게 남습니다.
 * 하나의 배율로 두 상황을 모두 만족할 수 없으므로 사용자가 조절하게 합니다.
 *
 * <h2>배율(scale) 적용 방식과 3중 구조</h2>
 * SVG 를 CSS {@code transform: scale()} 로 키우면 <b>레이아웃 크기는 그대로</b>라
 * 스크롤바가 생기지 않습니다. 그래서 스크롤 범위를 만드는 별도 요소가 필요합니다.
 *
 * <pre>
 *   wrapper  — 기준 폭을 재는 곳 (스크롤바 영향을 받지 않음)
 *     └ viewport (overflow:auto)                ← 스크롤 담당
 *         └ spacer (width/height = 기준 × 배율)   ← 스크롤 범위 생성
 *             └ content (기준 크기, transform: scale) ← 실제 확대 대상
 * </pre>
 *
 * <h2>⚠️ 확대가 안 되던 원인 2가지 (실제로 겪음)</h2>
 *
 * <h3>1. 측정 피드백 루프 → 다이어그램이 300px 로 수축</h3>
 * 처음에는 콘텐츠의 {@code offsetWidth} 를 재서 그 값을 다시 콘텐츠 width 로
 * 설정했습니다. 그러면 악순환이 생깁니다.
 * <pre>
 *   width 설정 → SVG 가 그 폭에 맞춰 줄어듦 → 다시 재면 더 작음 → 또 설정 → …
 * </pre>
 * 뷰포트가 570px 인데 콘텐츠가 300px 로 측정되어 발견했습니다.
 * <b>증상이 조용합니다</b> — 오류도 없고 버튼도 눌리는데 다이어그램만 작게 나옵니다.
 *
 * <h3>2. Mermaid 의 인라인 {@code max-width} → 확대해도 552px 에서 멈춤</h3>
 * Mermaid 는 SVG 에 {@code style="max-width: 552px"} 를 넣습니다. 이 때문에
 * 배율을 200% 로 올려도 SVG 가 552px 이상 커지지 않았습니다.
 * 게다가 이 스타일을 <b>effect 에서 나중에 지우면 효과가 없었습니다</b>.
 * React 가 {@code dangerouslySetInnerHTML} 로 SVG 를 다시 주입할 때
 * 인라인 스타일도 함께 되돌리기 때문입니다.
 * (같은 코드를 개발자 도구에서 직접 실행하면 유지되어, 원인 파악이 어려웠습니다)
 *
 * <h2>해결: SVG 문자열을 <b>주입하기 전에</b> 고친다</h2>
 * 위 두 문제를 한 번에 없애려고, 렌더된 SVG 문자열을 주입 전에 직접 수정합니다.
 * React 가 되돌릴 여지가 없고, 기준 크기도 같은 파싱에서 얻습니다.
 *
 * <ol>
 *   <li><b>기준 폭</b>: wrapper 의 {@code clientWidth} (스크롤바 영향 없음)</li>
 *   <li><b>기준 높이</b>: SVG {@code viewBox} 의 가로세로비 × 기준 폭</li>
 * </ol>
 * {@code viewBox} 는 Mermaid 가 항상 넣는 <b>본질 크기</b>라 컨테이너 폭과
 * 무관합니다. 그래서 배율을 바꿔도 기준이 흔들리지 않습니다.
 *
 * <h2>제공하는 조작</h2>
 * <ul>
 *   <li>− / + 버튼</li>
 *   <li>배율 표시 + 100% 로 되돌리기</li>
 *   <li>확대 상태에서 <b>드래그로 이동</b> (확대만 되고 이동이 안 되면 쓸 수 없음)</li>
 *   <li>{@code Ctrl/⌘ + 휠} 확대/축소 (일반 휠은 페이지 스크롤로 남겨 둠)</li>
 *   <li>키보드 {@code +} / {@code -} / {@code 0}</li>
 * </ul>
 *
 * <h2>일반 휠을 가로채지 않는 이유</h2>
 * 휠 확대는 편하지만, 다이어그램 위에서 휠을 굴렸을 때 페이지가 멈추면
 * 사용자는 스크롤이 고장 난 것으로 느낍니다. 그래서 {@code Ctrl} 을 누른
 * 경우에만 확대하고, 그 외에는 브라우저 기본 스크롤을 그대로 둡니다.
 */

/** 확대/축소 한계입니다. 밖으로 나가면 글자가 사라지거나 의미가 없어집니다. */
const MIN_SCALE = 0.25;
const MAX_SCALE = 4;

/** 버튼 한 번에 바뀌는 배율입니다. */
const ZOOM_STEP = 0.25;

/** viewBox 를 읽지 못했을 때 쓰는 안전한 기본 가로세로비입니다. */
const FALLBACK_RATIO = 0.6;

/**
 * Mermaid 가 만든 SVG 문자열을 확대에 적합하게 고치고, 가로세로비를 뽑습니다.
 *
 * <p>수정 내용:
 * <ul>
 *   <li>인라인 {@code style="…max-width: NNNpx…"} 제거 — 확대를 막는 주범</li>
 *   <li>{@code width="100%" height="100%"} 로 고정 — 크기를 우리가 통제</li>
 *   <li>{@code preserveAspectRatio} — 늘려도 비율이 깨지지 않게</li>
 * </ul>
 *
 * @param rawSvg Mermaid 렌더 결과
 * @returns 수정된 SVG 와 가로세로비(높이/폭)
 */
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
  /** Mermaid 차트 소스. */
  chart: string;
  /** 뷰포트에 적용할 클래스 (기존 호출부 호환). */
  className?: string;
  /** 확대/축소 툴바 표시 여부 (기본 true). */
  showZoomControls?: boolean;
  /** 다이어그램 영역 최소 높이(px). */
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

  /** 배율. 1 = 컨테이너 폭에 맞춘 크기. */
  const [scale, setScale] = useState(1);

  /** 기준 폭(px). wrapper 의 clientWidth 에서 얻습니다. */
  const [baseWidth, setBaseWidth] = useState(0);

  const wrapperRef = useRef<HTMLDivElement>(null);
  const viewportRef = useRef<HTMLDivElement>(null);

  /** wheel 핸들러에서 최신 배율을 읽기 위한 ref (stale closure 방지). */
  const scaleRef = useRef(1);
  scaleRef.current = scale;

  /** 드래그 이동 상태. ref 로 두어 이동 중 리렌더를 만들지 않습니다. */
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

  /**
   * 기준 폭을 측정합니다.
   *
   * <p>wrapper(스크롤 영역의 <b>바깥</b>) 를 재는 이유: 뷰포트를 재면
   * 확대했을 때 나타나는 스크롤바가 폭을 줄여 기준이 계속 변하고,
   * 콘텐츠를 재면 위에 적은 악순환이 생깁니다.
   */
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

  /** 배율을 한계 안으로 제한합니다. */
  const clampScale = useCallback((value: number): number => {
    return Math.min(MAX_SCALE, Math.max(MIN_SCALE, Math.round(value * 100) / 100));
  }, []);

  /**
   * 한 단계 확대합니다.
   *
   * <p>함수형 업데이트를 쓰는 이유는 <b>버튼 연타</b> 때문입니다.
   * {@code scale + STEP} 을 직접 계산하면 연타 시 아직 갱신되지 않은 이전
   * 값으로 계산되어 누적되지 않습니다. (3번 눌러 150% 가 되는 증상으로 확인)
   */
  const zoomIn = useCallback(() => {
    setScale((previous) => clampScale(previous + ZOOM_STEP));
  }, [clampScale]);

  /** 한 단계 축소합니다. */
  const zoomOut = useCallback(() => {
    setScale((previous) => clampScale(previous - ZOOM_STEP));
  }, [clampScale]);

  /** 배율을 100% 로 되돌리고 스크롤도 처음으로 돌립니다. */
  const resetZoom = useCallback(() => {
    setScale(1);
    const viewport = viewportRef.current;
    if (viewport) {
      viewport.scrollLeft = 0;
      viewport.scrollTop = 0;
    }
  }, []);

  /**
   * 커서 위치를 기준으로 확대/축소합니다.
   *
   * <p>기준점을 고정하지 않으면 보고 있던 곳이 화면 밖으로 밀려나
   * "확대했더니 다른 데를 보게 되는" 문제가 생깁니다.
   */
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

  /**
   * Ctrl/⌘ + 휠 확대입니다.
   *
   * <p>React 의 onWheel 은 브라우저에 따라 passive 로 등록되어
   * preventDefault 가 무시될 수 있습니다. 그래서 네이티브 리스너를
   * {@code passive: false} 로 직접 답니다.
   */
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

  // ---------------------------------------------------------------------------
  // 드래그 이동 (확대 상태에서만)
  // ---------------------------------------------------------------------------

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

  /** 키보드 조작입니다. 포커스가 있을 때만 동작합니다. */
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

/** 확대/축소 버튼 한 개입니다. 비활성 사유를 title 로 알립니다. */
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
