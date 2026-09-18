import type { SubnetClass } from "../../lib/api/types";

/** 등급 순서와 표시 정보입니다. 레벨이 클수록 민감합니다. */
export const ZONE_CLASSES: {
  value: SubnetClass;
  label: string;
  level: number;
  /** 배지 클래스 */
  badgeClass: string;
  /** 행 배경 클래스 */
  rowClass: string;
  description: string;
}[] = [
  {
    value: "Confidential",
    label: "Confidential",
    level: 3,
    badgeClass: "bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300",
    rowClass: "bg-red-50/40 dark:bg-red-900/10",
    description: "기밀망. 외부(Open)와 직접 연결 금지",
  },
  {
    value: "Sensitive",
    label: "Sensitive",
    level: 2,
    badgeClass: "bg-orange-100 text-orange-800 dark:bg-orange-900/40 dark:text-orange-300",
    rowClass: "bg-orange-50/40 dark:bg-orange-900/10",
    description: "내부 업무망. 양쪽 등급과 연결 가능",
  },
  {
    value: "Open",
    label: "Open",
    level: 1,
    badgeClass: "bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300",
    rowClass: "bg-blue-50/40 dark:bg-blue-900/10",
    description: "공개망. 기밀망과 직접 연결 금지",
  },
];

/**
 * 등급 문자열에 해당하는 표시 정보를 찾습니다.
 *
 * @param value 등급 (null 허용)
 * @returns 표시 정보, 없으면 null
 */
export function zoneInfo(value: SubnetClass | null) {
  if (!value) return null;
  return ZONE_CLASSES.find((zone) => zone.value === value) ?? null;
}

/**
 * 두 등급의 직접 연결이 금지되는지 판단합니다.
 *
 * <p>서버({@code ZoneClass.forbidsDirectConnection})와 같은 규칙입니다.
 * 화면에서 즉시 피드백을 주기 위한 것이며, 최종 판정은 항상 서버 결과입니다.
 *
 * @param source 출발 등급
 * @param target 도착 등급
 * @returns 금지되면 true
 */
export function isForbiddenPair(
  source: SubnetClass | null,
  target: SubnetClass | null,
): boolean {
  const sourceZone = zoneInfo(source);
  const targetZone = zoneInfo(target);
  // 등급을 모르면 판정하지 않습니다. (오탐 방지 — 서버와 동일한 원칙)
  if (!sourceZone || !targetZone) return false;
  return Math.abs(sourceZone.level - targetZone.level) >= 2;
}

/** 사람이 읽는 금지 사유를 만듭니다. */
export function forbiddenReason(
  source: SubnetClass | null,
  target: SubnetClass | null,
): string {
  return `${source ?? "?"} ↔ ${target ?? "?"} 직접 연결은 허용되지 않습니다.`;
}
