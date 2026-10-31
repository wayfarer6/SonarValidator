import type { ReactNode } from "react";


export type BranchDividerOrientation = "vertical" | "horizontal";

export interface Branch_DividerProps {
  orientation?: BranchDividerOrientation;
  label?: string;
  hint?: string;
  startLabel?: string;
  endLabel?: string;
  children?: ReactNode;
  className?: string;
}

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

function BranchLabel({ text }: { text?: string }) {
  if (!text) return null;
  return (
    <span className="shrink-0 text-[10px] font-medium uppercase tracking-wide text-gray-400 dark:text-gray-500">
      {text}
    </span>
  );
}

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
