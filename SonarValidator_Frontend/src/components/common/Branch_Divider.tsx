import type { ReactNode } from "react";

/**
 * 환경 구성 방식이 갈라지는 지점을 표시하는 분할선 컴포넌트입니다.
 *
 * <h2>왜 단순 `border` 가 아니라 컴포넌트인가</h2>
 * 프로버 배포 화면에서는 "서버에 연결되는가" 에 따라 이후 절차가
 * <b>완전히 달라집니다.</b>
 *
 * <pre>
 *   공통: 장비에 맞는 Prober 다운로드
 *                  │
 *          ┌───────┴───────┐
 *          │  Branch_Divider (OR)
 *          ▼               ▼
 *    온라인 구성        오프라인 구성
 *    서버로 직접 전송    JSON 스냅샷 → 업로드
 * </pre>
 *
 * <p>이 갈림길을 그냥 여백으로 두면 운영자는 <b>두 절차를 동시에 하는 것</b>으로
 * 오해합니다. 예를 들어 서버 연결이 안 되는 장비에 IP/Port 를 넣고
 * "왜 안 올라오지" 를 반복하게 됩니다. 그래서 "여기서 갈라진다" 를
 * 시각적으로 명확히 표시하고, 각 갈래를 라벨로 구분합니다.
 *
 * <h2>orientation 선택 기준</h2>
 * <ul>
 *   <li>{@code horizontal} — <b>위(공통)와 아래(갈래들)</b>를 나눌 때.
 *       좌우로 선을 그리고 가운데에 배지를 둡니다.</li>
 *   <li>{@code vertical} — <b>좌우 두 갈래</b>를 나눌 때.
 *       위아래로 선을 그리고 가운데에 배지를 둡니다.
 *       좁은 화면에서 두 갈래가 세로로 쌓이면 자동으로 가로 모양으로 바뀝니다.</li>
 * </ul>
 *
 * <h2>접근성</h2>
 * 시각적 구분선이지만 스크린리더에게는 {@code role="separator"} 로 알립니다.
 * 순수 장식이 아니라 "여기서 분기한다" 는 의미가 있기 때문입니다.
 * 라벨이 없으면 장식이므로 {@code aria-hidden} 으로 숨깁니다.
 */

/** 분할선의 방향입니다. */
export type BranchDividerOrientation = "vertical" | "horizontal";

export interface Branch_DividerProps {
  /** 분할선 방향 (기본: horizontal). */
  orientation?: BranchDividerOrientation;
  /**
   * 중앙 배지 문구입니다. (기본: "OR")
   *
   * <p>갈래가 배타적일 때는 `OR`, 순차 단계일 때는 `NEXT` 처럼 상황에 맞게
   * 바꿔 쓸 수 있습니다. 빈 문자열을 주면 배지를 그리지 않습니다.
   */
  label?: string;
  /** 배지 아래에 붙는 보조 설명입니다. (선택) */
  hint?: string;
  /** 갈래의 시작 측 라벨입니다. (예: "온라인") */
  startLabel?: string;
  /** 갈래의 끝 측 라벨입니다. (예: "오프라인") */
  endLabel?: string;
  /** 배지 자리에 넣을 내용. 지정하면 {@link label} 대신 렌더링합니다. */
  children?: ReactNode;
  className?: string;
}

/** 중앙 배지 한 개를 렌더링합니다. */
function DividerBadge({
  label,
  hint,
  children,
}: {
  label?: string;
  hint?: string;
  children?: ReactNode;
}) {
  // 자식을 주면 그것을, 아니면 라벨을 그립니다. 둘 다 없으면 배지 자체를 생략합니다.
  if (!children && !label) return null;

  return (
    <span className="flex shrink-0 flex-col items-center gap-0.5">
      {children ?? (
        <span className="rounded-full border border-gray-200 bg-white px-2.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400">
          {label}
        </span>
      )}
      {hint && (
        <span className="text-[10px] text-gray-400 dark:text-gray-500">{hint}</span>
      )}
    </span>
  );
}

/** 갈래 측 라벨입니다. 없으면 아무것도 그리지 않습니다. */
function BranchLabel({ text }: { text?: string }) {
  if (!text) return null;
  return (
    <span className="shrink-0 text-[10px] font-medium uppercase tracking-wide text-gray-400 dark:text-gray-500">
      {text}
    </span>
  );
}

/**
 * 환경 구성 방식 분할선입니다.
 *
 * <h2>사용 예</h2>
 * <pre>{@code
 * // 위(공통 절차)와 아래(두 갈래)를 나누기
 * <Branch_Divider orientation="horizontal" label="OR" />
 *
 * // 좌우 두 갈래를 나누기 (좁은 화면에서는 자동으로 가로가 됨)
 * <Branch_Divider orientation="vertical" startLabel="온라인" endLabel="오프라인" />
 * }</pre>
 */
export function Branch_Divider({
  orientation = "horizontal",
  label = "OR",
  hint,
  startLabel,
  endLabel,
  children,
  className = "",
}: Branch_DividerProps) {
  const badge = <DividerBadge label={label} hint={hint} children={children} />;

  // 라벨도 배지도 없으면 순수 장식이므로 스크린리더에서 숨깁니다.
  const decorative = !children && !label && !startLabel && !endLabel;

  if (orientation === "vertical") {
    return (
      <div
        // 좁은 화면(xl 미만)에서는 가로 분할선으로 바뀝니다.
        // 이유: 두 갈래가 세로로 쌓이면 세로선이 의미를 잃습니다.
        className={`flex flex-row items-center xl:h-full xl:flex-col ${className}`}
        role={decorative ? undefined : "separator"}
        aria-orientation="vertical"
        aria-hidden={decorative ? true : undefined}
      >
        <BranchLabel text={startLabel} />

        {/* 좌측(세로 모드에서는 상단) 선 */}
        <span className="mx-2 h-px flex-1 bg-gray-200 xl:mx-0 xl:my-2 xl:h-auto xl:w-px dark:bg-gray-700" />

        {badge}

        {/* 우측(세로 모드에서는 하단) 선 */}
        <span className="mx-2 h-px flex-1 bg-gray-200 xl:mx-0 xl:my-2 xl:h-auto xl:w-px dark:bg-gray-700" />

        <BranchLabel text={endLabel} />
      </div>
    );
  }

  return (
    <div
      className={`flex flex-col items-center ${className}`}
      role={decorative ? undefined : "separator"}
      aria-orientation="horizontal"
      aria-hidden={decorative ? true : undefined}
    >
      <BranchLabel text={startLabel} />

      <div className="flex w-full items-center gap-2">
        {/* 가로 모드에서도 선은 항상 좌우로 뻗습니다. (세로 흐름을 나누는 선) */}
        <span className="h-px flex-1 bg-gray-200 dark:bg-gray-700" />
        {badge}
        <span className="h-px flex-1 bg-gray-200 dark:bg-gray-700" />
      </div>

      <BranchLabel text={endLabel} />
    </div>
  );
}

export default Branch_Divider;
